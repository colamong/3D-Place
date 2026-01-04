package com.colombus.media.subject.repository;

import static com.colombus.media.jooq.tables.SubjectRegistry.SUBJECT_REGISTRY;

import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import com.colombus.common.kafka.subject.model.type.SubjectKind;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SubjectRegistryRepository {
    
    private final DSLContext dsl;

    public int upsert(SubjectKind kind, UUID subjectId, boolean alive) {
        
        return dsl.insertInto(SUBJECT_REGISTRY)
            .set(SUBJECT_REGISTRY.KIND, kind)
            .set(SUBJECT_REGISTRY.SUBJECT_ID, subjectId)
            .set(SUBJECT_REGISTRY.ALIVE, alive)
            .onConflict(SUBJECT_REGISTRY.KIND, SUBJECT_REGISTRY.SUBJECT_ID)
            .doUpdate()
            .set(SUBJECT_REGISTRY.ALIVE, DSL.excluded(SUBJECT_REGISTRY.ALIVE))
            .execute();
    }
}
