package com.etl.importer.service;

import com.etl.importer.domain.Identifier;
import com.etl.importer.domain.IdentifierType;
import com.etl.importer.domain.Patient;
import com.etl.importer.excel.ExcelPatientRow;
import com.etl.importer.mapper.PatientMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PatientImportService {

    private static final Logger log = LoggerFactory.getLogger(PatientImportService.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Async("patientImportExecutor")
    public void importPatients(List<ExcelPatientRow> rows) {
        if (rows == null || rows.isEmpty()) {
            log.warn("Nenhuma linha para importar");
            return;
        }

        AtomicInteger errorCount = new AtomicInteger(0);
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Patient.class);

        for (ExcelPatientRow row : rows) {
            log.info("======================================================================");
            try {
                Patient patient = PatientMapper.toDomain(row);
                String cpf = extractIdentifierValue(patient.getIdentifiers(), IdentifierType.CPF.name());
                String carteirinha = extractIdentifierValue(patient.getIdentifiers(), IdentifierType.CARTEIRINHA.name());

                log.info("Processando paciente: :: Nome: " + patient.getName().get(0).getGiven().get(0) + " :: CPF: " + cpf + " :: CARTEIRINHA: " + carteirinha);

                if (cpf != null || carteirinha != null) {
                    Criteria criteria = buildSearchCriteria(cpf, carteirinha);
                    Query query = null;

                    if (criteria != null) {
                        query = Query.query(criteria);
                    }         

                    if (query != null) {
                        Patient existingPatient = mongoTemplate.findOne(query, Patient.class);
                        
                        if (existingPatient != null) {                        
                            List<Identifier> mergedIdentifiers = mergeIdentifiers(
                                existingPatient.getIdentifiers(), 
                                patient.getIdentifiers()
                            );
                            
                            Update update = new Update().set("identifiers", mergedIdentifiers);
                            bulkOps.upsert(query, update);
                            log.debug("Documento encontrado - UPDATE identifiers com merge");
                        } else {                        
                            bulkOps.insert(patient);
                            log.debug("Documento não encontrado - INSERT novo");
                        }
                    }
                } else {                    
                    bulkOps.insert(patient);
                    log.warn("Paciente inserido sem CPF e CARTEIRINHA");
                }
            } catch (Exception e) {
                errorCount.incrementAndGet();
                log.error("Erro ao processar linha: {}", row, e);
            }
        }

        try {
            bulkOps.execute();
            log.info("Importação concluída. Total: {}, Erros: {}", rows.size(), errorCount.get());
        } catch (Exception e) {
            log.error("Erro ao executar BulkOperations", e);
        }
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

    /**
     * Merge de identifiers: preserva existentes e adiciona novos do Excel     
     */
    private List<Identifier> mergeIdentifiers(List<Identifier> existing, List<Identifier> newList) {
        List<Identifier> merged = new ArrayList<>(existing);

        for (Identifier newId : newList) {
            if (newId.getValue() == null || newId.getValue().isEmpty()) {
                log.warn("Identifier do Excel com valor vazio, ignorando. Type: {}, SystemIdentification: {}", newId.getType(), newId.getSystem());
                continue;
            }

            String newType = newId.getType();
                        
            if (IdentifierType.CPF.name().equals(newType) || 
                IdentifierType.CARTEIRINHA.name().equals(newType)) {
                
                boolean alreadyExists = existing.stream().anyMatch(existingId -> existingId.getType().equals(newType));
                
                if (!alreadyExists) {
                    merged.add(newId);                    
                }
            } else {                
                merged.add(newId);                
            }
        }

        return merged;
    }
}
