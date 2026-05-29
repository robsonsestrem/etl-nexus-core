package com.etl.importer.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Data
@Document(collection = "Patient")
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

    @Field("birthdate")
    private LocalDate birthdate;

    @Field("name")
    private String[] name;

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

    public void setBirthdateFromString(String dataNascimento) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        this.birthdate = LocalDate.parse(dataNascimento, formatter);
    }

    public void setNameFromString(String nomeCompleto) {
        this.name = nomeCompleto.split(" ");
    }

    public void setSanitizedCpf(String cpf) {
        this.cpf = cpf.replaceAll("[^0-9]", "");
    }
}