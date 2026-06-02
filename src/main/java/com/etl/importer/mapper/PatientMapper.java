package com.etl.importer.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.etl.importer.domain.Assigner;
import com.etl.importer.domain.Identifier;
import com.etl.importer.domain.IdentifierType;
import com.etl.importer.domain.NameUse;
import com.etl.importer.domain.Patient;
import com.etl.importer.domain.PatientName;
import com.etl.importer.domain.SystemIdentification;
import com.etl.importer.excel.ExcelPatientRow;
import com.etl.importer.util.DateUtilsETL;

public class PatientMapper {

    private static final Logger log = LoggerFactory.getLogger(PatientMapper.class);
    private static final Assigner DEFAULT_ASSIGNER = new Assigner();

    public static Patient toDomain(ExcelPatientRow row) {        
                
        List<String> givenNames = Arrays.asList(row.getNome().split("\\s+"));
        PatientName patientName = new PatientName();
        patientName.setGiven(givenNames);
        
        ArrayList<PatientName> names = new ArrayList<>();
        names.add(patientName);

        String cpf = sanitize(row.getCpf());
        String carteirinha = sanitize(row.getCarteirinha());
        String matriculaFuncional = sanitize(row.getMatriculaFuncional());
        String chaveUnica = sanitize(row.getChaveUnica());
        String matriculaSap = sanitize(row.getMatriculaSap());
        
        ArrayList<Identifier> identifiers = new ArrayList<>();
        if (cpf != null && !cpf.trim().isEmpty()) {
            identifiers.add(buildIdentifier(IdentifierType.CPF.name(), cpf, SystemIdentification.CPF.toString()));
        }
        if (carteirinha != null && !carteirinha.trim().isEmpty()) {
            identifiers.add(buildIdentifier(IdentifierType.CARTEIRINHA.name(), carteirinha, SystemIdentification.CARTEIRINHA.toString()));
        }
        if (matriculaFuncional != null && !matriculaFuncional.trim().isEmpty()) {
            identifiers.add(buildIdentifier(IdentifierType.PRN.name(), matriculaFuncional, SystemIdentification.MATRICULA_FUNCIONAL.toString()));
        }
        if (chaveUnica != null && !chaveUnica.trim().isEmpty()) {
            identifiers.add(buildIdentifier(IdentifierType.PRN.name(), chaveUnica, SystemIdentification.CHAVE_UNICA.toString()));
        }
        if (matriculaSap != null && !matriculaSap.trim().isEmpty()) {
            identifiers.add(buildIdentifier(IdentifierType.PRN.name(), matriculaSap, SystemIdentification.MATRICULA_SAP.toString()));
        }
        
        LocalDate birthDate = (DateUtilsETL.convertToLocalDate(row.getDataDeNascimento()));
        
        Patient patient = new Patient(
                UUID.randomUUID().toString(),
                true,
                null,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                birthDate,
                names,
                identifiers
        );

        log.info("Patient created from Excel row: {}", row);
        
        return patient;
    }
    
    private static String sanitize(String value) {
        if (value == null) return null;
        return value.replaceAll("[^a-zA-Z0-9]", "");
    }

    private static Identifier buildIdentifier(String type, String value, String systemIdentification) {
        Identifier identifier = new Identifier();
        identifier.setUse(NameUse.OFFICIAL.name());
        identifier.setType(type);
        identifier.setValue(value);
        identifier.setAssigner(DEFAULT_ASSIGNER);
        identifier.setSystem("urn:IMPORT_BY_FILE:" + systemIdentification);
        return identifier;
    }
}
