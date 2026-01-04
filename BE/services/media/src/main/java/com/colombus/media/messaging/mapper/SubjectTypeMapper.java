package com.colombus.media.messaging.mapper;

import com.colombus.media.upload.model.type.AssetPurpose;
import com.colombus.media.upload.model.type.SubjectKind;

public final class SubjectTypeMapper {

    private SubjectTypeMapper() {}

    // AssetPurpose 도메인 -> Kafka 코드
    public static com.colombus.common.kafka.subject.model.type.AssetPurpose toKafka(AssetPurpose purpose) {
        return switch (purpose) {
            case PROFILE -> com.colombus.common.kafka.subject.model.type.AssetPurpose.PROFILE;
            case BANNER  -> com.colombus.common.kafka.subject.model.type.AssetPurpose.BANNER;
        };
    }

    // AssetPurpose Kafka 코드 -> 도메인
    public static AssetPurpose fromKafka(com.colombus.common.kafka.subject.model.type.AssetPurpose purpose) {
        return switch (purpose) {
            case PROFILE -> AssetPurpose.PROFILE;
            case BANNER  -> AssetPurpose.BANNER;
        };
    }

    // SubjectKind 도메인 -> Kafka 코드
    public static com.colombus.common.kafka.subject.model.type.SubjectKind toKafka(SubjectKind kind) {
        return switch (kind) {
            case USER -> com.colombus.common.kafka.subject.model.type.SubjectKind.USER;
            case CLAN -> com.colombus.common.kafka.subject.model.type.SubjectKind.CLAN;
        };
    }

    // SubjectKind Kafka 코드 -> 도메인
    public static SubjectKind fromKafka(com.colombus.common.kafka.subject.model.type.SubjectKind kind) {
        return switch (kind) {
            case USER -> SubjectKind.USER;
            case CLAN -> SubjectKind.CLAN;
        };
    }
}