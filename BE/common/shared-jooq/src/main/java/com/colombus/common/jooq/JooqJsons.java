package com.colombus.common.jooq;

import org.jooq.JSONB;
import com.colombus.common.utility.json.Jsons;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JooqJsons {

    private JooqJsons() {}

    /** JSONB 지원 + 나머지는 shared-domain 변환기로 위임 */
    public static JsonNode toJsonNode(ObjectMapper om, Object v) throws java.io.IOException {
        if (v instanceof JSONB b) return om.readTree(b.data());
        return Jsons.toJsonNode(om, v);
    }
    
}
