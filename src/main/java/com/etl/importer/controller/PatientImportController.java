package com.etl.importer.controller;

import com.alibaba.excel.EasyExcel;
import com.etl.importer.excel.PatientExcelListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/patients")
public class PatientImportController {

    private static final Logger log = LoggerFactory.getLogger(PatientImportController.class);
    private static final String UPLOAD_DIR = "C:\\temp\\uploads";

    @Autowired
    private PatientExcelListener patientExcelListener;

    @PostMapping("/import")
    public ResponseEntity<ImportResponse> importPatients(@RequestParam("file") MultipartFile file) {
        log.info("Received import request, filename: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            log.warn("Uploaded file is empty");
            return ResponseEntity.badRequest().body(new ImportResponse(null, "FAILED", "File is empty", null));
        }

        String batchId = UUID.randomUUID().toString();
        LocalDateTime uploadedAt = LocalDateTime.now();

        try {
            // Ensure upload directory exists
            Path uploadDirPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadDirPath)) {
                Files.createDirectories(uploadDirPath);
                log.info("Created upload directory: {}", UPLOAD_DIR);
            }

            // Save file temporarily
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".") ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".xlsx";
            String tempFilename = batchId + extension;
            Path tempFilePath = uploadDirPath.resolve(tempFilename);
            file.transferTo(tempFilePath.toFile());
            log.info("File saved temporarily at: {}", tempFilePath);

            // Read Excel in a separate thread
            CompletableFuture.runAsync(() -> {
                try {
                    log.info("Starting Excel processing for batch: {}", batchId);
                    EasyExcel.read(tempFilePath.toFile(), patientExcelListener)
                            .sheet()
                            .doRead();
                    log.info("Excel processing completed for batch: {}", batchId);
                } catch (Exception e) {
                    log.error("Error processing Excel file for batch {}: {}", batchId, e.getMessage(), e);
                } finally {
                    // Clean up temp file after processing
                    try {
                        Files.deleteIfExists(tempFilePath);
                        log.info("Temporary file deleted: {}", tempFilePath);
                    } catch (IOException e) {
                        log.warn("Failed to delete temp file: {}", tempFilePath, e);
                    }
                }
            });

            ImportResponse response = new ImportResponse(batchId, "PROCESSING", "File uploaded and processing started", uploadedAt);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (IOException e) {
            log.error("Failed to save uploaded file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ImportResponse(null, "FAILED", "Failed to save file: " + e.getMessage(), null));
        } catch (Exception e) {
            log.error("Unexpected error during import: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ImportResponse(null, "FAILED", "Unexpected error: " + e.getMessage(), null));
        }
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ImportResponse> handleAllExceptions(Exception e) {
        log.error("Unhandled exception: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ImportResponse(null, "FAILED", "Internal server error: " + e.getMessage(), null));
    }

    // Inner class for response DTO
    public static class ImportResponse {
        private String batchId;
        private String status;
        private String message;
        private LocalDateTime uploadedAt;

        public ImportResponse(String batchId, String status, String message, LocalDateTime uploadedAt) {
            this.batchId = batchId;
            this.status = status;
            this.message = message;
            this.uploadedAt = uploadedAt;
        }

        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public LocalDateTime getUploadedAt() { return uploadedAt; }
        public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    }
}