package com.etl.importer.service;

import com.etl.importer.domain.Patient;
import com.etl.importer.excel.ExcelPatientRow;
import com.etl.importer.repository.PatientRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PatientImportService {

    private static final Logger log = LoggerFactory.getLogger(PatientImportService.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Async("patientImportExecutor")    
    public void importPatients(List<ExcelPatientRow> rows) {
        if (rows == null || rows.isEmpty()) {
            log.warn("No rows to import");
            return;
        }
        
        // fluxo atual usa BulkOperations com upsert, que é superior a queries individuais
        var bulkOps = mongoTemplate.bulkOps(BulkMode.UNORDERED, Patient.class);
        int successCount = 0;
        int errorCount = 0;

        for (var row : rows) {
            try {
                var patient = new Patient();
                patient.setNameFromString(row.nome());
                patient.setBirthdateFromString(row.dataNascimento());
                patient.setSanitizedCpf(row.cpf());

                var criteria = Criteria.where("cpf").is(patient.getCpf());
                var update = Update.update("name", patient.getName())
                        .set("birthdate", patient.getBirthdate())
                        .set("cpf", patient.getCpf())
                        .set("matriculaSap", row.matriculaSap())
                        .set("chaveUnica", row.chaveUnica());

                bulkOps.upsert(new Query(criteria), update);
                successCount++;
                log.debug("Added upsert for patient with CPF: {}", patient.getCpf());
            } catch (Exception e) {
                errorCount++;
                log.error("Error processing row for CPF: {} - {}", row.cpf(), e.getMessage(), e);
            }
        }

        try {
            var results = bulkOps.execute();
            log.info("Bulk operation completed. Success: {}, Errors: {}", successCount, errorCount);
        } catch (Exception e) {
            log.error("Bulk operation execution failed", e);
        }
    }
}
