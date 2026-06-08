package com.etl.importer.controller;

import com.alibaba.excel.EasyExcel;

import com.etl.importer.excel.PatientExcelListener;
import com.etl.importer.service.PatientImportService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/patients")
public class PatientImportController {

    private static final Logger log = LoggerFactory.getLogger(PatientImportController.class);

    @Autowired
    private PatientImportService patientImportService;

    @PostMapping(value = "/import", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> importPatients(@RequestParam("file") MultipartFile file) {
        log.info("Recebendo requisição de importação de pacientes. Arquivo: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            log.warn("Arquivo vazio enviado.");
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "error", "Arquivo vazio.",
                            "timestamp", System.currentTimeMillis()
                    ));
        }

        Path tempFilePath = null;
        try {
            // Salva arquivo temporário
            tempFilePath = Files.createTempFile("patients_", ".xlsx");
            file.transferTo(tempFilePath.toFile());
            log.debug("Arquivo salvo temporariamente em: {}", tempFilePath);

            // Cria listener com o service injetado
            var listener = new PatientExcelListener(patientImportService);

            // Processa arquivo Excel
            EasyExcel.read(tempFilePath.toFile(), listener).sheet().doRead();

            int processedCount = listener.getProcessedCount();
            String batchId = listener.getBatchId();            
                        
            var responseBody = new HashMap<String, Object>();
            responseBody.put("success", true);
            responseBody.put("message", "Importação concluída com sucesso.");
            responseBody.put("processedCount", processedCount);
            if (batchId != null && !batchId.isEmpty()) {
                responseBody.put("batchId", batchId);
            }
            responseBody.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(responseBody);

        } catch (Exception e) {
            log.error("Erro durante importação de pacientes: {}", e.getMessage(), e);
                        
            return ResponseEntity.status(500)
                    .body(Map.of(
                            "success", false,
                            "error", "Erro interno: " + e.getMessage(),
                            "timestamp", System.currentTimeMillis()
                    ));
        } finally {            
            if (tempFilePath != null) {
                try {
                    Files.deleteIfExists(tempFilePath);
                    log.debug("Arquivo temporário removido: {}", tempFilePath);
                } catch (IOException ex) {
                    log.warn("Não foi possível remover o arquivo temporário: {}", tempFilePath, ex);
                }
            }
        }
    }
}