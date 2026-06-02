package com.etl.importer.domain;

public enum SystemIdentification {
    CPF,
    CARTEIRINHA,
    MATRICULA_FUNCIONAL,
    CHAVE_UNICA,
    MATRICULA_SAP;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}

