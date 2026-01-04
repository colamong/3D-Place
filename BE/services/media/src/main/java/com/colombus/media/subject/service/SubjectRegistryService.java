package com.colombus.media.subject.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colombus.common.kafka.subject.event.SubjectRegistEvent;
import com.colombus.media.subject.repository.SubjectRegistryRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubjectRegistryService {
    
    private final SubjectRegistryRepository repo;

    @Transactional
    public void apply(SubjectRegistEvent e) {
        repo.upsert(e.kind(), e.subjectId(), e.alive());
    }
    
}
