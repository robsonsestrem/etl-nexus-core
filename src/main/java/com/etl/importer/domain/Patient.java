package com.etl.importer.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Document(collection = "patient")
public class Patient {

    @Id
    private String id;

    @Field("active")
    private Boolean active = true;

    @Field("createdAt")
    private LocalDateTime createdAt;

    @Field("updatedAt")
    private LocalDateTime updatedAt;

    @Field("gender")
    private String gender = "UNKNOWN";

    @Field("dataNascimento")
    private LocalDate dataNascimento;

    @Field("nome")
    private String[] nome;

    @Field("cpf")
    private String cpf;

    @Field("matriculaSap")
    private String matriculaSap;

    @Field("chaveUnica")
    private String chaveUnica;

    public Patient() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void setNameFromString(String nomeCompleto) {
        if (nomeCompleto != null && !nomeCompleto.isEmpty()) {
            this.nome = nomeCompleto.split(" ");
        } else {
            this.nome = new String[0];
        }
    }

    public void setSanitizedCpf(String cpf) {
        if (cpf != null) {
            this.cpf = cpf.replaceAll("[^0-9]", "");
        } else {
            this.cpf = null;
        }
    }
}