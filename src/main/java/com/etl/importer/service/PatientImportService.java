package com.etl.importer.service;

import com.etl.importer.domain.Identifier;
import com.etl.importer.domain.IdentifierType;
import com.etl.importer.domain.NameUse;
import com.etl.importer.domain.Patient;
import com.etl.importer.excel.ExcelPatientRow;
import com.etl.importer.mapper.PatientMapper;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class PatientImportService {
    private static final Logger log = LoggerFactory.getLogger(PatientImportService.class);
    private static final int BATCH_SIZE = 200;
    private static final Logger logInsert = LoggerFactory.getLogger("patient.insert");
    private static final Logger logUpsert = LoggerFactory.getLogger("patient.upsert");
    private static final Logger logError = LoggerFactory.getLogger("patient.error");

    private final MongoTemplate mongoTemplate;
    private final MongoConverter mongoConverter;
    private final ThreadPoolTaskExecutor patientImportExecutor;

    public PatientImportService(MongoTemplate mongoTemplate, ThreadPoolTaskExecutor patientImportExecutor) {
        this.mongoTemplate = mongoTemplate;
        this.mongoConverter = mongoTemplate.getConverter();
        this.patientImportExecutor = patientImportExecutor;
    }

    @Async("patientImportExecutor")
    public void importPatients(List<ExcelPatientRow> rows) {
        if (rows == null || rows.isEmpty()) {
            log.warn("Sem linhas para importar");
            return;
        }

        long startTime = System.currentTimeMillis();
        log.info("Iniciando importação de {} pacientes", rows.size());

        int numBatches = (rows.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        List<CompletableFuture<BatchResult>> futures = new ArrayList<>();

        for (int i = 0; i < numBatches; i++) {
            int start = i * BATCH_SIZE;
            int end = Math.min(start + BATCH_SIZE, rows.size());
            List<ExcelPatientRow> batch = rows.subList(start, end);
            int batchNumber = i + 1;

            CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(
                () -> processBatch(batch, batchNumber, numBatches),
                patientImportExecutor
            );
            futures.add(future);
        }

        int totalProcessed = 0;
        int totalErrors = 0;

        for (CompletableFuture<BatchResult> future : futures) {
            try {
                BatchResult result = future.join();
                totalProcessed += result.processed;
                totalErrors += result.errors;
            } catch (Exception e) {                
                this.logPatientOperation(logError, "CompletableFuture<BatchResult>", "ERROR", "", "", "", "Erro processando batch em paralelo: " + e.getMessage());
                totalErrors += BATCH_SIZE;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Importação concluída. Total: {}, Processados: {}, Erros: {}, Duração: {}ms", rows.size(), totalProcessed, totalErrors, duration);
    }

    private BatchResult processBatch(List<ExcelPatientRow> batch, int batchNumber, int totalBatches) {
        List<ExcelPatientRow> uniqueBatch = deduplicateBatch(batch);
        int processedCount = 0;
        int errorCount = 0;

        BulkOperations bulkOps = mongoTemplate.bulkOps(
            BulkOperations.BulkMode.UNORDERED,
            Patient.class
        );

        for (ExcelPatientRow row : uniqueBatch) {
            try {
                Patient patient = PatientMapper.toDomain(row);
                String cpf = extractIdentifierValue(patient.getIdentifiers(), IdentifierType.CPF.name());
                String carteirinha = extractIdentifierValue(patient.getIdentifiers(), IdentifierType.CARTEIRINHA.name());

                if (cpf != null || carteirinha != null) {
                    Criteria criteria = buildSearchCriteria(cpf, carteirinha);
                    Query query = Query.query(criteria);
                    Update update = buildUpsertUpdate(patient);
                    bulkOps.upsert(query, update);

                    this.logPatientOperation(logUpsert, "UPSERT", "INFO", patient.getName().get(0).getGiven().get(0), cpf, carteirinha, "Bulk upsert queued :: ProcessedCount = " + processedCount);
                } else {
                    bulkOps.insert(patient);                    
                    this.logPatientOperation(logInsert, "INSERT", "INFO", patient.getName().get(0).getGiven().get(0), cpf, carteirinha, "Paciente inserido sem CPF/CARTEIRINHA");
                }
                processedCount++;
            } catch (Exception e) {
                errorCount++;                
                this.logPatientOperation(logError, "BulkOperations", "ERROR", "", "", "", "Erro processando objeto ExcelPatientRow: " + e.getMessage() + PatientMapper.toDomain(row).toString());
            }
        }

        try {
            bulkOps.execute();
            log.info("Batch {}/{} executado com sucesso. Registros: {}, Erros: {}", batchNumber, totalBatches, uniqueBatch.size(), errorCount);
        } catch (Exception e) {            
            this.logPatientOperation(logError, "BulkOperations", "ERROR", "", "", "", "Erro executando BulkOperations no batch {}/{} " + batchNumber + "/" + totalBatches + " " + e.getMessage());
            errorCount += uniqueBatch.size();
        }

        return new BatchResult(processedCount, errorCount);
    }

    private Update buildUpsertUpdate(Patient patient) {
        Update update = new Update();

        update.setOnInsert("_id", patient.get_id());
        update.setOnInsert("active", true);
        update.setOnInsert("createdAt", new Date());
        update.set("updatedAt", new Date());
        update.setOnInsert("gender", patient.getGender() != null ? patient.getGender() : "UNKNOWN");
        update.setOnInsert("birthdate", patient.getBirthdate());
        update.setOnInsert("name", patient.getName());

        List<Identifier> identifiers = patient.getIdentifiers();
        if (identifiers != null && !identifiers.isEmpty()) {
            List<Document> identifierDocs = identifiers.stream()
                .map(id -> {
                    Document doc = new Document("use", NameUse.OFFICIAL.name())
                        .append("type", id.getType())
                        .append("value", id.getValue())
                        .append("system", id.getSystem());
                    
                    // Converte o POJO Assigner para Document via MongoConverter
                    if (id.getAssigner() != null) {
                        doc.append("assigner", mongoConverter.convertToMongoType(id.getAssigner()));
                    }
                    return doc;
                })
                .collect(Collectors.toList());

            // $addToSet garante que o identificador só será adicionado se o objeto completo for inédito
            update.addToSet("identifiers").each(identifierDocs);
        }

        return update;
    }

    private List<ExcelPatientRow> deduplicateBatch(List<ExcelPatientRow> batch) {
        Map<String, ExcelPatientRow> uniqueMap = new LinkedHashMap<>();
        for (ExcelPatientRow row : batch) {
            String key = row.getCpf() != null ? row.getCpf() : row.getCarteirinha();
            if (key != null) {
                uniqueMap.putIfAbsent(key, row);
            } else {
                uniqueMap.put(UUID.randomUUID().toString(), row);
            }
        }
        return new ArrayList<>(uniqueMap.values());
    }

    private Criteria buildSearchCriteria(String cpf, String carteirinha) {
        if (cpf != null && carteirinha != null) {
            return new Criteria().orOperator(
                Criteria.where("identifiers").elemMatch(
                    Criteria.where("type").is(IdentifierType.CPF.name()).and("value").is(cpf)
                ),
                Criteria.where("identifiers").elemMatch(
                    Criteria.where("type").is(IdentifierType.CARTEIRINHA.name()).and("value").is(carteirinha)
                )
            );
        } else if (cpf != null) {
            return Criteria.where("identifiers").elemMatch(
                Criteria.where("type").is(IdentifierType.CPF.name()).and("value").is(cpf)
            );
        } else {
            return Criteria.where("identifiers").elemMatch(
                Criteria.where("type").is(IdentifierType.CARTEIRINHA.name()).and("value").is(carteirinha)
            );
        }
    }

    private String extractIdentifierValue(List<Identifier> identifiers, String type) {
        return identifiers.stream()
            .filter(id -> type.equals(id.getType()))
            .map(Identifier::getValue)
            .findFirst()
            .orElse(null);
    }

    private void logPatientOperation(Logger logger, String operation, String level, String name, String cpf, String carteirinha, String observations) {        
        String json = String.format(
            "{\"timestamp\":\"%s\",\"level\":\"%s\",\"operation\":\"%s\",\"patientName\":\"%s\",\"cpf\":\"%s\",\"carteirinha\":\"%s\",\"observations\":\"%s\"}",
            java.time.Instant.now().toString(),
            level,
            operation,
            name != null ? name.replace("\"", "\\\"") : "N/A",
            cpf != null ? cpf : "N/A",
            carteirinha != null ? carteirinha : "N/A",
            observations != null ? observations.replace("\"", "\\\"") : ""
        );
        logger.info(json);
    }

    private record BatchResult(int processed, int errors) {}
}
