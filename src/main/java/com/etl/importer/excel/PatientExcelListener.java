package com.etl.importer.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.etl.importer.service.PatientImportService;
import com.etl.importer.excel.ExcelPatientRow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PatientExcelListener extends AnalysisEventListener<Map<Integer, String>> {

    private static final Logger log = LoggerFactory.getLogger(PatientExcelListener.class);
    private static final int BATCH_SIZE = 1000;

    private final PatientImportService patientImportService;
    private final List<ExcelPatientRow> batchList = new ArrayList<>();

    public PatientExcelListener(PatientImportService patientImportService) {
        this.patientImportService = patientImportService;
    }

    @Override
    public void invoke(Map<Integer, String> data, AnalysisContext context) {
        ExcelPatientRow row = convertMapToExcelPatientRow(data);
        batchList.add(row);
        if (batchList.size() >= BATCH_SIZE) {
            flushBatch();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("Excel analysis completed. Performing final flush.");
        flushBatch();
    }

    @Override
    public void onException(Exception exception, AnalysisContext context) throws Exception {
        log.error("Error during Excel analysis: {}", exception.getMessage(), exception);
        throw exception;
    }

    private ExcelPatientRow convertMapToExcelPatientRow(Map<Integer, String> data) {
        ExcelPatientRow row = new ExcelPatientRow();
        row.setSeq(parseInteger(data.get(0)));
        row.setContrato(data.get(1));
        row.setFamilia(data.get(2));
        row.setMatriculaFuncional(data.get(3));
        row.setChaveUnica(data.get(4));
        row.setCpf(data.get(5));
        row.setCarteirinha(data.get(6));
        row.setMatriculaSap(data.get(7));
        row.setNome(data.get(8));
        row.setDataNascimento(data.get(9));
        row.setDataAdesao(data.get(10));
        row.setDataCancelamento(data.get(11));
        row.setTipoDependente(data.get(12));
        return row;
    }

    private Integer parseInteger(String value) {
        try {
            return value != null ? Integer.valueOf(value.trim()) : null;
        } catch (NumberFormatException e) {
            log.warn("Could not parse integer from value: {}", value);
            return null;
        }
    }

    private void flushBatch() {
        if (batchList.isEmpty()) {
            log.info("Batch list is empty, skipping flush.");
            return;
        }
        List<ExcelPatientRow> batch = new ArrayList<>(batchList);
        try {
            patientImportService.importPatients(batch);
            log.info("Batch of {} patients imported successfully.", batch.size());
        } catch (Exception e) {
            log.error("Error importing batch of {} patients: {}", batch.size(), e.getMessage(), e);
        } finally {
            batchList.clear();
        }
    }
}