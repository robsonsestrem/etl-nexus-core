package com.etl.importer.service;

import com.etl.importer.excel.ExcelPatientRow;
import com.etl.importer.domain.Patient;
import com.etl.importer.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.data.mongodb.core.query.Query.query;

@Service
public class PatientImportService {

    private static final Logger log = LoggerFactory.getLogger(PatientImportService.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    private final AtomicInteger errorCount = new AtomicInteger(0);

    @Async("patientImportExecutor")
    public void importPatients(List<ExcelPatientRow> rows) {
        if (rows == null || rows.isEmpty()) {
            log.warn("No rows to import");
            return;
        }

        errorCount.set(0);
        log.info("Starting import of {} patient rows", rows.size());

        var bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Patient.class);

        for (var row : rows) {
            try {
                var patient = new Patient();
                patient.setNameFromString(row.getNome());
                patient.setSanitizedCpf(row.getCpf());
                patient.setDataNascimento(DateUtils.convertToLocalDate(row.getDataNascimento()));
                patient.setMatriculaSap(row.getMatriculaSap());
                patient.setChaveUnica(row.getChaveUnica());

                var cpfSanitized = patient.getCpf();
                var criteria = Criteria.where("cpf").is(cpfSanitized);
                var update = Update.update("nome", patient.getNome())
                        .set("cpf", cpfSanitized)
                        .set("dataNascimento", patient.getDataNascimento())
                        .set("matriculaSap", patient.getMatriculaSap())
                        .set("chaveUnica", patient.getChaveUnica());

                bulkOps.upsert(query(criteria), update);

            } catch (DateTimeParseException e) {
                log.error("Invalid date format for row CPF={}: {}", row.getCpf(), e.getMessage());
                errorCount.incrementAndGet();
            } catch (Exception e) {
                log.error("Error processing row CPF={}: {}", row.getCpf(), e.getMessage(), e);
                errorCount.incrementAndGet();
            }
        }

        if (!rows.isEmpty()) {
            bulkOps.execute();
            log.info("Import completed. Total rows: {}, errors: {}", rows.size(), errorCount.get());
        } else {
            log.warn("No rows to import");
        }
    }
}