package com.colombus.common.utility.json;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nullable;

public final class Jsons {

    private static final Logger log = LoggerFactory.getLogger(Jsons.class);

    private Jsons() {}

    private static final ObjectMapper M;
    static { 
        M = new ObjectMapper();
        M.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        M.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        M.registerModule(new JavaTimeModule());
    }

    public static ObjectMapper getMapper() {
        return M;
    }

    /* -------------------------- 조회/갱신 도구 -------------------------- */

    /** null-safe: 값 노드에서 텍스트 추출(공백만 있으면 null) */
    public static @Nullable String textOrNull(@Nullable JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (!node.isValueNode()) return null; // object/array는 스킵
        String s = node.asText(null);
        return (s == null || s.isBlank()) ? null : s;
    }

    /** null-safe: root에서 첫 번째 일치 키의 non-blank 텍스트 반환(없으면 null) */
    public static @Nullable String firstNonBlankText(@Nullable JsonNode root, String... keys) {
        if (root == null || root.isNull() || keys == null) return null;
        for (String k : keys) {
            if (k == null) continue;
            String v = textOrNull(root.get(k));
            if (v != null) return v;
        }
        return null;
    }

    /**
     * 텍스트 평탄화:
     *   schema 예) { "avatarUrl": ["avatarUrl","avatar_url"], "timezone": ["timezone","time_zone"] }
     *   → 각 targetKey에 대해 첫 non-blank 값을 뽑아 Map으로 반환(없으면 key 미포함)
     */
    public static Map<String, String> flattenText(@Nullable JsonNode root, Map<String, String[]> schema) {
        Map<String, String> out = new LinkedHashMap<>();
        if (root == null || root.isNull() || schema == null || schema.isEmpty()) return out;

        for (var e : schema.entrySet()) {
            String key = e.getKey();
            String[] aliases = e.getValue();
            String val = firstNonBlankText(root, aliases);
            if (val != null) out.put(key, val);
        }
        return out;
    }

    /** 다양한 입력을 JsonNode로 강제 변환 (jOOQ 비의존) */
    public static JsonNode toJsonNode(ObjectMapper om, Object v) throws java.io.IOException {
        if (v == null) return om.nullNode();
        if (v instanceof JsonNode j) return j;
        if (v instanceof CharSequence cs) return om.readTree(cs.toString());
        if (v instanceof byte[] bytes) return om.readTree(bytes);
        return om.valueToTree(v); // POJO/Map/List 등
    }

    /** top-level key의 텍스트 값 (blank → null) */
    public static @Nullable String textAt(@Nullable JsonNode root, String key) {
        if (root == null || root.isNull() || !(root instanceof ObjectNode)) return null;
        return textOrNull(root.get(key));
    }

    /** dot-path("a.b.c") 텍스트 값 (blank → null) */
    public static @Nullable String textAtPath(@Nullable JsonNode root, String dotPath, char sep) {
        if (root == null || root.isNull() || dotPath == null || dotPath.isBlank()) return null;
        String[] parts = dotPath.split("\\" + sep);
        JsonNode cur = root;
        for (String p : parts) {
            if (!(cur instanceof ObjectNode)) return null;
            cur = cur.get(p);
            if (cur == null) return null;
        }
        return textOrNull(cur);
    }

    public static @Nullable String textAtPath(@Nullable JsonNode root, String dotPath) {
        return textAtPath(root, dotPath, '.');
    }

    /** top-level put: (ObjectNode 없으면 새로 만들고 복사본 반환) */
    public static JsonNode put(@Nullable JsonNode root, String key, @Nullable JsonNode value) {
        ObjectNode obj = ensureObject(root);
        if (value == null) obj.putNull(key);
        else obj.set(key, value);
        return obj;
    }

    public static JsonNode put(@Nullable JsonNode root, String key, @Nullable String value) {
        return put(root, key, value == null ? NullNode.getInstance() : TextNode.valueOf(value));
    }

    public static JsonNode put(@Nullable JsonNode root, String key, @Nullable Boolean value) {
        return put(root, key, value == null ? NullNode.getInstance() : BooleanNode.valueOf(value));
    }

    public static JsonNode put(@Nullable JsonNode root, String key, @Nullable Number value) {
        return put(root, key, value == null ? NullNode.getInstance() : M.valueToTree(value));
    }

    /** top-level remove: 복사본 반환 */
    public static JsonNode remove(@Nullable JsonNode root, String... keys) {
        ObjectNode obj = ensureObject(root);
        if (keys != null) for (String k : keys) obj.remove(k);
        return obj;
    }

    /** ObjectNode 보장 (null/비객체면 새 ObjectNode) + deep copy */
    public static ObjectNode ensureObject(@Nullable JsonNode node) {
        if (node instanceof ObjectNode on) return on.deepCopy();
        return M.createObjectNode();
    }

    /** 간단 stringify (예외 포장) */
    public static String toJson(ObjectMapper om, Object v) {
        try { return om.writeValueAsString(v); }
        catch (Exception e) { throw new IllegalArgumentException("Cannot serialize to JSON", e); }
    }
    
    public static String toJson(Object v) { return toJson(M, v); }

    /** 간단 parse */
    public static @Nullable JsonNode parseOrNull(ObjectMapper om, @Nullable CharSequence json) {
        if (json == null) return null;
        try { return om.readTree(json.toString()); }
        catch (Exception e) {
            log.warn("Failed to parse JSON: {}", json, e);
            return null;
        }
    }

    /* -------------------------- 병합 도구 -------------------------- */

    /**
     * RFC 7396 merge-patch (object 대상).<br>
     * - patch가 object가 아니면 전체를 patch로 교체<br>
     * - patch에 key:null이면 삭제<br>
     * - object/object는 재귀 병합, array/스칼라는 교체
     */
    public static JsonNode mergePatchObject(@Nullable JsonNode original, @Nullable JsonNode patch) {
        return mergePatchObject(original, patch, M);
    }

    public static JsonNode mergePatchObject(@Nullable JsonNode original, @Nullable JsonNode patch, ObjectMapper om) {
        if (patch == null || patch.isNull()) return original;          // no-op
        if (!patch.isObject()) return patch;                           // replace whole
        ObjectNode target = (original != null && original.isObject())
                ? ((ObjectNode) original).deepCopy()
                : om.createObjectNode();

        ObjectNode p = (ObjectNode) patch;
        for (var e : p.properties()) {
            String k = e.getKey();
            JsonNode v = e.getValue();

            if (v.isNull()) {
                target.remove(k);
            } else {
                JsonNode cur = target.get(k);
                if (v.isObject() && cur != null && cur.isObject()) {
                    target.set(k, mergePatchObject(cur, v, om));
                } else {
                    target.set(k, v);
                }
            }
        }
        return target;
    }

    /**
     * 깊은 병합(“왼쪽 유지, 오른쪽 덮어쓰기”)<br>
     * - object/object는 재귀 병합, array/스칼라는 오른쪽으로 교체<br>
     * - null patch는 무시
     */
    public static JsonNode deepMerge(@Nullable JsonNode left, @Nullable JsonNode right) {
        if (right == null || right.isNull()) return left;
        if (left == null || left.isNull()) return right;

        if (left.isObject() && right.isObject()) {
            ObjectNode out = ((ObjectNode) left).deepCopy();
            for (var e : ((ObjectNode) right).properties()) {
                String k = e.getKey();
                JsonNode rv = e.getValue();
                JsonNode lv = out.get(k);
                if (lv != null && lv.isObject() && rv.isObject()) {
                    out.set(k, deepMerge(lv, rv));
                } else {
                    out.set(k, rv);
                }
            }
            return out;
        }
        return right;
    }

    /** 값이 비어있는(Blank) value node 인지 */
    public static boolean isBlankValue(@Nullable JsonNode n) {
        if (n == null || n.isNull()) return true;
        if (!n.isValueNode()) return false;
        String s = n.asText(null);
        return s == null || s.isBlank();
    }

    /** equalsIgnoreCase 유틸 (null-safe) */
    public static boolean equalsIgnoreCase(@Nullable String a, @Nullable String b) {
        if (Objects.equals(a, b)) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }
}