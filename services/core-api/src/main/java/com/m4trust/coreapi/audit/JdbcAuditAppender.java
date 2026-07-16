package com.m4trust.coreapi.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class JdbcAuditAppender implements AuditAppendPort {

    private final AuditRepository repository;

    JdbcAuditAppender(AuditRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(AuditRecord record) {
        repository.insert(record);
    }
}
