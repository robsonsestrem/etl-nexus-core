package com.etl.importer.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.etl.importer.service.PatientImportService;
import com.etl.importer.excel.ExcelPatientRow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PatientExcelListener extends AnalysisEventListener<ExcelPatientRow> {

    private static final Logger log = LoggerFactory.getLogger(PatientExcelListener.class);
    private static final int BATCH_SIZE = 1000;

    private final PatientImportService patientImportService;
    private final List<ExcelPatientRow> batchList = new ArrayList<>();

    @Autowired
    public PatientExcelListener(PatientImportService patientImportService) {
        this.patientImportService = patientImportService;
    }

    @Override
    public void invoke(ExcelPatientRow data, AnalysisContext context) {
        log.debug("Reading row: {}", data);
        batchList.add(data);
        if (batchList.size() >= BATCH_SIZE) {
            flushBatch();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("All rows analysed. Performing final flush.");
        flushBatch();
    }

    @Override
    public void onException(Exception exception, AnalysisContext context) throws Exception {
        log.error("Error during Excel analysis: {}", exception.getMessage(), exception);
        throw exception;
    }    
    
    @Async
    public void flushBatch() {
        if (batchList.isEmpty()) {
            return;
        }
        try {
            var batch = new ArrayList<>(batchList);
            patientImportService.importPatients(batch);
            log.info("Batch imported successfully, size: {}", batch.size());
        } catch (Exception e) {
            log.error("Error importing batch: {}", e.getMessage(), e);
        } finally {
            batchList.clear();
        }
    }
}
