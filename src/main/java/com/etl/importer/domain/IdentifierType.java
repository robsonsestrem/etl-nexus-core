package com.etl.importer.domain;

public enum IdentifierType {
    CPF,
    CARTEIRINHA,
    PRN;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
