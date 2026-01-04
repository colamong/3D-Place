package com.colombus.common.jooq;

import com.colombus.common.utility.json.Jsons;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.jooq.Converter;
import org.jooq.JSONB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public final class JsonNodeConverter implements Converter<JSONB, JsonNode> {

    private static final Logger log = LoggerFactory.getLogger(JsonNodeConverter.class); // Added

    @Override public Class<JSONB> fromType() { return JSONB.class; }
    @Override public Class<JsonNode> toType() { return JsonNode.class; }

    @Override public JsonNode from(JSONB db) {
        if (db == null) return null;
        try { return Jsons.getMapper().readTree(db.data()); }
        catch (IOException e) {
            log.warn("Failed to parse JSONB data: {}", db.data(), e);
            throw new IllegalArgumentException("Invalid JSONB data", e);
        }
    }

    @Override public JSONB to(JsonNode node) {
        if (node == null) return null;
        try { return JSONB.valueOf(Jsons.getMapper().writeValueAsString(node)); }
        catch (JsonProcessingException e) {
            log.warn("Failed to serialize JsonNode: {}", node, e);
            throw new IllegalArgumentException("Cannot serialize JsonNode", e);
        }
    }
}