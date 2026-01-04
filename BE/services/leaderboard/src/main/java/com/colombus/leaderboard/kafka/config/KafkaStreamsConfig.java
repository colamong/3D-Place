package com.colombus.leaderboard.kafka.config;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableKafkaStreams
@RequiredArgsConstructor
public class KafkaStreamsConfig {

    private final KafkaProperties kafkaProperties;
    private final SslBundles sslBundles;

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kStreamsConfigs() {
        Map<String, Object> props = kafkaProperties.buildStreamsProperties(sslBundles);
        
        // ================================
        // 기본 애플리케이션 / 토폴로지 설정
        // ================================

        // Kafka Streams 애플리케이션 ID (consumer group id 역할도 겸함, 내부 토픽 이름 prefix로도 사용)
        props.put(
            StreamsConfig.APPLICATION_ID_CONFIG,
            "leaderboard-svc"
        );
        // 로컬 상태 스토어(RocksDB 등)가 저장될 디렉토리 경로
        props.put(
            StreamsConfig.STATE_DIR_CONFIG,
            "/tmp/leaderboard-streams"
        );
        // 토폴로지 최적화(연산/스토어 머지 등)를 활성화하여 성능 및 리소스 사용 최적화
        props.put(
            StreamsConfig.TOPOLOGY_OPTIMIZATION_CONFIG,
            StreamsConfig.OPTIMIZE
        );

        // ======================================
        // 상태 저장소 및 고가용성(HA) 관련 설정
        // ======================================
        // 내부 토픽(changelog, repartition)의 replication factor (브로커 3개 기준 권장 값)
        props.put(
            StreamsConfig.REPLICATION_FACTOR_CONFIG,
            3
        );
        // 상태 스토어의 standby replica 수 (failover 시 warm standby 용도로 사용)
        props.put(
            StreamsConfig.NUM_STANDBY_REPLICAS_CONFIG,
            1
        );
        // 애플리케이션이 더 이상 실행되지 않을 때 상태 디렉토리 정리까지 대기하는 시간 (기본 10분)
        props.put(
            StreamsConfig.STATE_CLEANUP_DELAY_MS_CONFIG,
            600_000
        );

        // =====================================
        // 처리 보장(exactly-once) / 성능 튜닝
        // =====================================
        // Exactly-once 처리 보장(EOS v2) 활성화 (트랜잭션 프로듀서/컨슈머 사용, 중복 처리 방지)
        props.put(
            StreamsConfig.PROCESSING_GUARANTEE_CONFIG,
            StreamsConfig.EXACTLY_ONCE_V2
        );
        // 한 애플리케이션 인스턴스에서 실행할 StreamThread 수 (처리 병렬도)
        props.put(
            StreamsConfig.NUM_STREAM_THREADS_CONFIG,
            2
        );
        // 처리된 레코드를 오프셋/상태에 커밋하는 주기(ms) (짧을수록 지연↓, 커밋 오버헤드↑)
        props.put(
            StreamsConfig.COMMIT_INTERVAL_MS_CONFIG,
            100
        );
        // 상태 스토어에 대한 write-back 캐시 최대 크기 – RocksDB flush/디스크 I/O 튜닝
        props.put(
            StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG,
            10 * 1024 * 1024L
        );

        // ======================================
        // 컨슈머 동작 / 재시도 관련 설정
        // ======================================
        // poll() 호출 간의 최대 허용 간격(ms). 초과 시 컨슈머 그룹에서 추방(rebalance 트리거)
        props.put(
            StreamsConfig.consumerPrefix(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG),
            300_000
        );
        // 커밋된 오프셋이 없는 파티션은 가장 처음부터(earliest) 읽도록 설정
        props.put(
            StreamsConfig.consumerPrefix(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG),
            "earliest"
        );
        // 단일 poll()에서 가져올 최대 레코드 수 (Streams 컨슈머에만 적용되도록 consumerPrefix 사용)
        props.put(
            StreamsConfig.consumerPrefix(ConsumerConfig.MAX_POLL_RECORDS_CONFIG),
            200
        );
        // 일시적인 오류/리밸런스 등으로 재시도할 때 각 재시도 간 대기 시간(ms)
        props.put(
            StreamsConfig.RETRY_BACKOFF_MS_CONFIG,
            500
        );

        // =======================
        // 프로듀서 관련 설정
        // =======================
        // 모든 ISR(replica)까지 기록이 복제되었을 때 ack (내구성↑, 지연 시간 약간↑)
        props.put(
            StreamsConfig.producerPrefix(ProducerConfig.ACKS_CONFIG),
            "all"
        );
        // 프로듀서가 배치를 전송 가능한 최대 시간(ms).
        // request.timeout.ms, linger.ms, retries 등을 모두 고려한 상한선.
        props.put(
            StreamsConfig.producerPrefix(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG),
            120_000
        ); 

        // ==================================
        // 직렬화/역직렬화 및 예외 처리 설정
        // ==================================
        // 레코드 역직렬화 실패 시 해당 레코드를 스킵하고 처리 계속(Log & continue)
        props.put(
            StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
            LogAndContinueExceptionHandler.class
        );
        props.put(
            StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
            Serdes.UUID().getClass()
        );

        return new KafkaStreamsConfiguration(props);
    }
}