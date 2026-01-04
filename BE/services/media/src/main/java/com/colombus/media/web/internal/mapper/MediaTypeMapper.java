package com.colombus.media.web.internal.mapper;

import com.colombus.common.kafka.subject.model.type.AssetPurpose;
import com.colombus.common.kafka.subject.model.type.SubjectKind;
import com.colombus.media.contract.enums.AssetPurposeCode;
import com.colombus.media.contract.enums.SubjectKindCode;

public final class MediaTypeMapper {

    private MediaTypeMapper() {}

    // ================
    // AssetPurpose
    // ================
    public static AssetPurpose toDomain(AssetPurposeCode code) {
        if (code == null) return null;
        return switch (code) {
            case PROFILE -> AssetPurpose.PROFILE;
            case BANNER  -> AssetPurpose.BANNER;
        };
    }

    public static AssetPurposeCode toContract(AssetPurpose domain) {
        if (domain == null) return null;
        return switch (domain) {
            case PROFILE -> AssetPurposeCode.PROFILE;
            case BANNER  -> AssetPurposeCode.BANNER;
        };
    }

    // ================
    // SubjectKind
    // ================
    public static SubjectKind toDomain(SubjectKindCode code) {
        if (code == null) return null;
        return switch (code) {
            case USER -> SubjectKind.USER;
            case CLAN -> SubjectKind.CLAN;
        };
    }

    public static SubjectKindCode toContract(SubjectKind domain) {
        if (domain == null) return null;
        return switch (domain) {
            case USER -> SubjectKindCode.USER;
            case CLAN -> SubjectKindCode.CLAN;
        };
    }
}