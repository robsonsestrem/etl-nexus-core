package com.etl.importer.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "patient")
public class Patient {

    @Id
    private String _id;

    private boolean active;

    private String healthmapCompanyCode;

    private String healthmapCompanyName;

    private String conexaCompanyName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String gender;

    private LocalDate birthdate;

    private ArrayList<PatientName> name;

    private ArrayList<Identifier> identifiers;
    
}
