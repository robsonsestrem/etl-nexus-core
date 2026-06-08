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
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;

@Service
public class PatientImportService {
    
    /*
     * Equilibrar o consumo de memória Heap versus a latência de rede e overhead de escrita do MongoDB.
     * Define um limite de 200 documentos por BulkWrite para otimizar o throughput sem exceder o limite de 16MB por comando do protocolo wire do Mongo.
     */
    private static final int BATCH_SIZE = 200;
    
    private static final Logger logProcess = LoggerFactory.getLogger("patient.process");
    private static final Logger logInsert = LoggerFactory.getLogger("patient.insert");
    private static final Logger logUpsert = LoggerFactory.getLogger("patient.upsert");
    private static final Logger logError = LoggerFactory.getLogger("patient.error");
    private static final Logger logDuplicate = LoggerFactory.getLogger("patient.duplicate");

    private final MongoTemplate mongoTemplate;
    private final MongoConverter mongoConverter;    

    public PatientImportService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.mongoConverter = mongoTemplate.getConverter();        
    }

    /*
     * Desonerar a thread de requisição (HTTP) para processamentos de longa duração (ETL).
     * Utiliza a anotação @Async para delegar o processamento a um TaskExecutor dedicado, processando batches sequencialmente para evitar locks excessivos.
     */
    @Async("patientImportExecutor")
    public void importPatients(List<ExcelPatientRow> rows) {
        if (rows == null || rows.isEmpty()) {            
            this.logPatientProcess(logProcess, "importPatients", "INFO", "N/A", "N/A", "N/A", "Nenhuma linha recebida para importação");
            return;
        }

        long startTime = System.currentTimeMillis();
        this.logPatientProcess(logProcess, "importPatients", "INFO", "N/A", "N/A", "N/A", "Iniciando importação de " + rows.size() + " registros de arquivo Excel");

        // Deduplicação global prévia para garantir que o mesmo paciente não apareça em múltiplos batches
        List<ExcelPatientRow> uniqueRows = deduplicateAll(rows);
        int numBatches = (uniqueRows.size() + BATCH_SIZE - 1) / BATCH_SIZE;

        int totalProcessed = 0;
        int totalErrors = 0;

        // Processamento sequencial dos batches: elimina race conditions mantendo a thread assíncrona principal
        for (int i = 0; i < numBatches; i++) {
            int start = i * BATCH_SIZE;
            int end = Math.min(start + BATCH_SIZE, uniqueRows.size());
            List<ExcelPatientRow> batch = uniqueRows.subList(start, end);
            int batchNumber = i + 1;

            try {
                BatchResult result = processBatch(batch, batchNumber, numBatches);
                totalProcessed += result.processed;
                totalErrors += result.errors;
            } catch (Exception e) {
                this.logPatientError(logError, "BatchProcessing", "ERROR", "", "", "", "Erro processando batch sequencial: " + e.getMessage());
                totalErrors += batch.size();
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        this.logPatientProcess(logProcess, "importPatients", "INFO", "N/A", "N/A", "N/A", "Importação concluída. Total recebido: " + rows.size() + ", Total únicos: " + uniqueRows.size() + ", Processados: " + totalProcessed + ", Erros: " + totalErrors + ", Duração: " + duration + "ms");
    }

    /*
     * Reduzir drasticamente o tempo de IO e garantir atomicidade parcial por lote.
     * Implementa o padrão Batch Read (fetchExistingPatients) para mitigar o problema N+1 e utiliza BulkOperations.upsert para consolidar múltiplas escritas em uma única chamada.
     */
    private BatchResult processBatch(List<ExcelPatientRow> batch, int batchNumber, int totalBatches) {
        int processedCount = 0;
        int errorCount = 0;

        // 1. Extração de chaves para o Batch Read
        Set<String> cpfs = new HashSet<>();
        Set<String> carteirinhas = new HashSet<>();
        for (ExcelPatientRow row : batch) {
            if (row.getCpf() != null) cpfs.add(row.getCpf());
            if (row.getCarteirinha() != null) carteirinhas.add(row.getCarteirinha());
        }

        // 2. Busca em lote (Batch Read) para evitar N+1 (findOne isolado)
        Map<String, Patient> existingPatientsMap = fetchExistingPatients(cpfs, carteirinhas);

        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Patient.class);

        for (ExcelPatientRow row : batch) {
            try {
                Patient patient = PatientMapper.toDomain(row);
                String cpf = extractIdentifierValue(patient.getIdentifiers(), IdentifierType.CPF.name());
                String carteirinha = extractIdentifierValue(patient.getIdentifiers(), IdentifierType.CARTEIRINHA.name());
                String patientName = getPatientNameSafely(patient);
                Criteria criteria = null;
                Query query = null;

                Patient existingPatient = null;
                if (cpf != null && existingPatientsMap.containsKey(cpf)) {
                    existingPatient = existingPatientsMap.get(cpf);
                } else if (carteirinha != null && existingPatientsMap.containsKey(carteirinha)) {
                    existingPatient = existingPatientsMap.get(carteirinha);
                }

                if (cpf != null || carteirinha != null) {
                    criteria = buildSearchCriteria(cpf, carteirinha);

                    if (criteria == null) {
                        this.logPatientError(logError, "CriteriaBuilding", "ERROR", patientName, cpf, carteirinha, "Não foi possível construir criteria para paciente.");
                        errorCount++;
                        continue;
                    }

                    query = Query.query(criteria);
                    Update update = buildUpsertUpdate(patient, existingPatient);

                    if (query == null || update == null) {
                        this.logPatientError(logError, "BulkOperations", "ERROR", patientName, cpf, carteirinha, "Não foi possível construir query ou update para paciente.");
                        errorCount++;
                        continue;
                    }

                    bulkOps.upsert(query, update);

                    this.logPatientOperation(logUpsert, "UPSERT", "INFO", patientName, cpf, carteirinha, "Bulk upsert queued :: ProcessedCount = " + processedCount);
                } else {
                    bulkOps.insert(patient);
                    this.logPatientOperation(logInsert, "INSERT", "INFO", patientName, cpf, carteirinha, "Paciente inserido sem CPF/CARTEIRINHA");
                }
                processedCount++;
            } catch (Exception e) {
                errorCount++;
                this.logPatientError(logError, "BulkOperations", "ERROR", "", "", "", "Erro processando objeto ExcelPatientRow: " + e.getMessage());
            }
        }

        try {
            bulkOps.execute();            
            this.logPatientProcess(logProcess, "importPatients", "INFO", "N/A", "N/A", "N/A", "Batch " + batchNumber + "/" + totalBatches + " executado com sucesso. Registros: " + batch.size() + ", Erros: " + errorCount);
        } catch (Exception e) {
            this.logPatientError(logError, "BulkOperations", "ERROR", "", "", "", "Erro executando BulkOperations no batch {}/{} " + batchNumber + "/" + totalBatches + " " + e.getMessage());
            errorCount += batch.size();
        }

        return new BatchResult(processedCount, errorCount);
    }

    /*
     * Minimizar round-trips ao banco de dados durante a validação de existência.
     * Agrupa CPFs e Carteirinhas em uma consulta única utilizando $or e $elemMatch, mapeando os resultados em memória para acesso rápido.
     */
    private Map<String, Patient> fetchExistingPatients(Set<String> cpfs, Set<String> carteirinhas) {
        Map<String, Patient> map = new HashMap<>();
        if (cpfs.isEmpty() && carteirinhas.isEmpty()) {
            return map;
        }

        Criteria criteria = new Criteria();
        List<Criteria> orCriterias = new ArrayList<>();

        if (!cpfs.isEmpty()) {
            orCriterias.add(Criteria.where("identifiers").elemMatch(
                    Criteria.where("type").is(IdentifierType.CPF.name()).and("value").in(cpfs)
            ));
        }
        if (!carteirinhas.isEmpty()) {
            orCriterias.add(Criteria.where("identifiers").elemMatch(
                    Criteria.where("type").is(IdentifierType.CARTEIRINHA.name()).and("value").in(carteirinhas)
            ));
        }

        criteria.orOperator(orCriterias.toArray(new Criteria[0]));
        List<Patient> patients = mongoTemplate.find(Query.query(criteria), Patient.class);

        for (Patient p : patients) {
            String cpf = extractIdentifierValue(p.getIdentifiers(), IdentifierType.CPF.name());
            String cart = extractIdentifierValue(p.getIdentifiers(), IdentifierType.CARTEIRINHA.name());
            if (cpf != null) map.put(cpf, p);
            if (cart != null) map.put(cart, p);
        }
        return map;
    }

    /*
     * Garantir a idempotência da operação e preservar dados históricos (createdAt).
     * Utiliza setOnInsert para campos imutáveis pós-criação e set para campos de atualização obrigatória (updatedAt, identifiers).
     */
    private Update buildUpsertUpdate(Patient patient, Patient existingPatient) {
        Update update = new Update();
        update.setOnInsert("_id", patient.get_id());
        update.setOnInsert("active", true);
        update.setOnInsert("createdAt", new Date());
        update.set("updatedAt", new Date());
        update.setOnInsert("gender", patient.getGender() != null ? patient.getGender() : "UNKNOWN");
        update.setOnInsert("birthdate", patient.getBirthdate());
        update.setOnInsert("name", patient.getName());

        List<Document> mergedIdentifiers = mergeIdentifiers(existingPatient, patient);
        if (!mergedIdentifiers.isEmpty()) {
            // Substitui o array com a versão mesclada e unificada em memória
            update.set("identifiers", mergedIdentifiers);
        }

        return update;
    }

    /*
     * Evitar a poluição do array de identificadores com entradas redundantes.
     * Realiza o merge em memória utilizando um Set e uma chave composta (Type|Value|Ref), garantindo unicidade lógica antes da persistência.
     */
    private List<Document> mergeIdentifiers(Patient existingPatient, Patient newPatient) {
        List<Document> merged = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        // Chave composta apenas por type, value e assigner.reference
        Function<Identifier, String> keyGen = id -> {
            String type = id.getType() != null ? id.getType() : "";
            String value = id.getValue() != null ? id.getValue() : "";
            String ref = (id.getAssigner() != null && id.getAssigner().getReference() != null)
                    ? id.getAssigner().getReference() : "";
            return type + "|" + value + "|" + ref;
        };

        // 1. Preserva identificadores existentes
        if (existingPatient != null && existingPatient.getIdentifiers() != null) {
            for (Identifier id : existingPatient.getIdentifiers()) {
                String key = keyGen.apply(id);
                if (seenKeys.add(key)) {
                    merged.add(identifierToDocument(id));
                }
            }
        }

        // 2. Adiciona novos apenas se a chave composta for inédita
        if (newPatient != null && newPatient.getIdentifiers() != null) {
            for (Identifier id : newPatient.getIdentifiers()) {
                String key = keyGen.apply(id);
                if (seenKeys.add(key)) {
                    merged.add(identifierToDocument(id));
                }
            }
        }

        return merged;
    }

    /*
     * Converter objetos de domínio para o formato BSON compatível com o driver nativo do MongoDB.
     * Mapeia manualmente os campos e utiliza o MongoConverter para tipos complexos como 'assigner', garantindo consistência no schema.
     */
    private Document identifierToDocument(Identifier id) {
        Document doc = new Document("use", NameUse.OFFICIAL.name())
                .append("type", id.getType())
                .append("value", id.getValue())
                .append("system", id.getSystem());

        if (id.getAssigner() != null) {
            doc.append("assigner", mongoConverter.convertToMongoType(id.getAssigner()));
        }
        return doc;
    }

    /*
     * Eliminar duplicatas óbvias no arquivo de origem antes de iniciar as transações de banco.     
     */
    private List<ExcelPatientRow> deduplicateAll(List<ExcelPatientRow> rows) {
        Map<String, ExcelPatientRow> uniqueMap = new LinkedHashMap<>();
        for (ExcelPatientRow row : rows) {
            String key = row.getCpf() != null ? row.getCpf() : row.getCarteirinha();
            if (key != null) {
                ExcelPatientRow existing = uniqueMap.putIfAbsent(key, row);
                if (existing != null) {
                    logDuplicate.warn("Linha duplicada por CPF/CARTEIRINHA :: " + existing);
                }
            } else {
                uniqueMap.put(UUID.randomUUID().toString(), row);
            }
        }
        return new ArrayList<>(uniqueMap.values());
    }

    /*
     * Definir a regra de negócio para identificação única do paciente (CPF ou Carteirinha).     
     */
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

    private String getPatientNameSafely(Patient patient) {
        if (patient.getName() != null && !patient.getName().isEmpty() && 
            patient.getName().get(0).getGiven() != null && !patient.getName().get(0).getGiven().isEmpty()) {
            return patient.getName().get(0).getGiven().get(0);
        }
        return "N/A";
    }

    private void logPatientOperation(Logger logger, String operation, String level, String name, String cpf, String carteirinha, String observations) {        
        String json = String.format(
            "{\"level\":\"%s\",\"operation\":\"%s\",\"patientName\":\"%s\",\"cpf\":\"%s\",\"carteirinha\":\"%s\",\"observations\":\"%s\"}",            
            level,
            operation,
            name != null ? name.replace("\"", "\\\"") : "N/A",
            cpf != null ? cpf : "N/A",
            carteirinha != null ? carteirinha : "N/A",
            observations != null ? observations.replace("\"", "\\\"") : ""
        );
        logger.info(json);
    }

    private void logPatientError(Logger logger, String operation, String level, String name, String cpf, String carteirinha, String observations) {        
        String json = String.format(
            "{\"level\":\"%s\",\"operation\":\"%s\",\"patientName\":\"%s\",\"cpf\":\"%s\",\"carteirinha\":\"%s\",\"observations\":\"%s\"}",            
            level,
            operation,
            name != null ? name.replace("\"", "\\\"") : "N/A",
            cpf != null ? cpf : "N/A",
            carteirinha != null ? carteirinha : "N/A",
            observations != null ? observations.replace("\"", "\\\"") : ""
        );
        logger.error(json);
    }

    private void logPatientProcess(Logger logger, String operation, String level, String name, String cpf, String carteirinha, String observations) {        
        String json = String.format(
            "{\"level\":\"%s\",\"operation\":\"%s\",\"patientName\":\"%s\",\"cpf\":\"%s\",\"carteirinha\":\"%s\",\"observations\":\"%s\"}",            
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
