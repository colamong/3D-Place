package com.colombus.media.jooq.converter;

import java.util.Locale;
import org.jooq.Converter;

import com.colombus.common.kafka.subject.model.type.AssetPurpose;

public final class AssetPurposeConverter implements Converter<String, AssetPurpose> {
    @Override public Class<String> fromType() { return String.class; }
    @Override public Class<AssetPurpose> toType() { return AssetPurpose.class; }

    @Override
    public AssetPurpose from(String db) {
        if (db == null) return null;
        return AssetPurpose.valueOf(db.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public String to(AssetPurpose user) {
        return (user == null) ? null : user.name();
    }
}