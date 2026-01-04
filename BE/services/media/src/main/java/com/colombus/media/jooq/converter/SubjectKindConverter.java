package com.colombus.media.jooq.converter;

import java.util.Locale;
import org.jooq.Converter;

import com.colombus.common.kafka.subject.model.type.SubjectKind;

public final class SubjectKindConverter implements Converter<String, SubjectKind> {
    @Override public Class<String> fromType() { return String.class; }
    @Override public Class<SubjectKind> toType() { return SubjectKind.class; }

    @Override
    public SubjectKind from(String db) {
        if (db == null) return null;
        return SubjectKind.valueOf(db.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public String to(SubjectKind user) {
        return (user == null) ? null : user.name();
    }
}