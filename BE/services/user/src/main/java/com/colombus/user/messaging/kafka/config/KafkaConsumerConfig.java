package com.colombus.user.messaging.kafka.config;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.BiFunction;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import com.colombus.user.messaging.kafka.props.KafkaConsumerProps;
import com.colombus.user.messaging.kafka.props.KafkaListenerProps;
import com.colombus.user.messaging.kafka.props.TopicProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {

    private final KafkaProperties kafkaProperties;
    private final KafkaConsumerProps consumerProps;

    // ====== JSON 템플릿 (DLQ 전용) ======
    @Bean
    public ConsumerFactory<String, Object> jsonConsumerFactory() {
        Map<String, Object> cfg = kafkaProperties.buildConsumerProperties();

        cfg.put(ConsumerConfig.GROUP_ID_CONFIG, consumerProps.getGroupId());
        cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumerProps.getAutoOffsetReset());
        cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cfg.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumerProps.getMaxPollRecords());
        
        cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        cfg.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        cfg.put(JsonDeserializer.TRUSTED_PACKAGES, "com.colombus.clan.kafka.dto,*");
        
        return new DefaultKafkaConsumerFactory<>(cfg);
    }

    @Bean
    @Qualifier("jsonConcurrentKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> jsonKafkaListenerContainerFactory(
        ConsumerFactory<String, Object> cf,
        KafkaListenerProps listenerProps
    ) {
        var f = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        f.setConsumerFactory(cf);
        f.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        f.setConcurrency(listenerProps.concurrency());
        f.getContainerProperties().setPollTimeout(listenerProps.pollTimeoutMs());
        return f;
    }
    
    // ====== BYTES 템플릿 (도메인 이벤트 전용) ======
    @Bean
    public ConsumerFactory<String, byte[]> bytesConsumerFactory() {
        Map<String, Object> cfg = kafkaProperties.buildConsumerProperties();
        
        cfg.put(ConsumerConfig.GROUP_ID_CONFIG, consumerProps.getGroupId());
        cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumerProps.getAutoOffsetReset());
        cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cfg.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumerProps.getMaxPollRecords());

        cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        cfg.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, ByteArrayDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(cfg);
    }

    @Bean
    @Qualifier("bytesConcurrentKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> bytesKafkaListenerContainerFactory(
        ConsumerFactory<String, byte[]> cf,
        CommonErrorHandler errorHandler,
        KafkaListenerProps listenerProps
    ) {
        var f = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
        f.setConsumerFactory(cf);
        f.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        f.setCommonErrorHandler(errorHandler);
        f.setConcurrency(listenerProps.concurrency());
        f.getContainerProperties().setPollTimeout(listenerProps.pollTimeoutMs());
        return f;
    }

    @Bean
    public CommonErrorHandler commonErrorHandler(
        @Qualifier("bytesKafkaTemplate") KafkaTemplate<String, byte[]> dltTemplate,
        TopicProperties topics,
        KafkaConsumerProps cprops
    ) {
        // 실패 시 동일 파티션의 DLT로 보냄: <원토픽>.DLT
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            dltTemplate,
            (rec, ex) -> new TopicPartition(rec.topic() + ".DLT", rec.partition())
        );
        
        BiFunction<ConsumerRecord<?,?>, Exception, Headers> headersFn = (rec, ex) -> {
            RecordHeaders h = new RecordHeaders();
            h.add(new RecordHeader(KafkaHeaders.ORIGINAL_TOPIC,
                    rec.topic().getBytes(StandardCharsets.UTF_8)));
            h.add(new RecordHeader(KafkaHeaders.ORIGINAL_PARTITION,
                    Integer.toString(rec.partition()).getBytes(StandardCharsets.UTF_8)));
            h.add(new RecordHeader(KafkaHeaders.ORIGINAL_OFFSET,
                    Long.toString(rec.offset()).getBytes(StandardCharsets.UTF_8)));
            if (ex != null) {
                h.add(new RecordHeader("error.class",
                        ex.getClass().getName().getBytes(StandardCharsets.UTF_8)));
                String msg = ex.getMessage() == null ? "" : ex.getMessage();
                h.add(new RecordHeader("error.message",
                        msg.getBytes(StandardCharsets.UTF_8)));
            }

            return h;
        };
        recoverer.setHeadersFunction(headersFn);

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(5);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(30_000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(
            IllegalArgumentException.class,
            DeserializationException.class
        );

        return errorHandler;
    }
}
