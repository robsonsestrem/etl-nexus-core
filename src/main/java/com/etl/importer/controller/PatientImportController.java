package com.etl.importer.controller;

import com.alibaba.excel.EasyExcel;
import com.etl.importer.excel.PatientExcelListener;
import com.etl.importer.service.PatientImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/patients")
public class PatientImportController {

    private static final Logger log = LoggerFactory.getLogger(PatientImportController.class);

    @Autowired
    private PatientImportService patientImportService;

    @PostMapping("/import")
    public ResponseEntity<?> importPatients(@RequestParam("file") MultipartFile file) {
        log.info("Recebendo requisição de importação de pacientes. Arquivo: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            log.warn("Arquivo vazio enviado.");
            return ResponseEntity.badRequest().body("Arquivo vazio.");
        }

        Path tempFilePath = null;
        try {
            // Salvar arquivo temporário
            tempFilePath = Files.createTempFile("patients_", ".xlsx");
            file.transferTo(tempFilePath.toFile());
            log.debug("Arquivo salvo temporariamente em: {}", tempFilePath);

            // Criar listener com o service injetado
            var listener = new PatientExcelListener(patientImportService);

            // Processar arquivo Excel
            EasyExcel.read(tempFilePath.toFile(), listener).sheet().doRead();

            log.info("Importação de pacientes concluída com sucesso. Total processado: {}");
            return ResponseEntity.ok("Importação concluída. Registros processados.");

        } catch (Exception e) {
            log.error("Erro durante importação de pacientes: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Erro interno: " + e.getMessage());
        } finally {
            // Limpar arquivo temporário
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