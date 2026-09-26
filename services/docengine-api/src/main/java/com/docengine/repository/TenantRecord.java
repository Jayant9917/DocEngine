package com.docengine.repository;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "tenants")
public class TenantRecord {
    @Id
    private UUID id;
    @SuppressWarnings("unused")
    private String name;
    @SuppressWarnings("unused")
    private String apiKey;

    protected TenantRecord() {
    }

    public UUID getId() {
        return id;
    }
}
