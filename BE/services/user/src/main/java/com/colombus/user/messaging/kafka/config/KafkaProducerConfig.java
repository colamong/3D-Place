package com.colombus.user.messaging.kafka.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
public class KafkaProducerConfig {

    // ====== JSON 템플릿 (DLQ 전용) ======
    @Bean
    public ProducerFactory<String, Object> jsonProducerFactory(KafkaProperties props) {
        Map<String, Object> cfg = new HashMap<>(props.buildProducerProperties());
        cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        cfg.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        cfg.put(ProducerConfig.ACKS_CONFIG, "all");
        cfg.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        cfg.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        cfg.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd");
        return new DefaultKafkaProducerFactory<>(cfg);
    }

    @Bean
    @Qualifier("jsonKafkaTemplate")
    public KafkaTemplate<String, Object> jsonKafkaTemplate(ProducerFactory<String, Object> pf) {
        return new KafkaTemplate<>(pf);
    }
    
    // ====== BYTES 템플릿 (도메인 이벤트 전용) ======
    @Bean
    public ProducerFactory<String, byte[]> bytesProducerFactory(KafkaProperties props) {
        Map<String, Object> cfg = new HashMap<>(props.buildProducerProperties());
        cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        cfg.put(ProducerConfig.ACKS_CONFIG, "all");
        cfg.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        cfg.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        cfg.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd");
        return new DefaultKafkaProducerFactory<>(cfg);
    }

    @Bean
    @Qualifier("bytesKafkaTemplate")
    public KafkaTemplate<String, byte[]> bytesKafkaTemplate(ProducerFactory<String, byte[]> pf) {
        return new KafkaTemplate<>(pf);
    }
}
