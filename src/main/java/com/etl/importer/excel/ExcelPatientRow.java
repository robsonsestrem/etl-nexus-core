package com.etl.importer.excel;

import com.alibaba.excel.annotation.ExcelProperty;

public record ExcelPatientRow(
    @ExcelProperty("SEQ") Integer seq,
    @ExcelProperty("CONTRATO") String contrato,
    @ExcelProperty("FAMILIA") String familia,
    @ExcelProperty("MATRICULA_FUNCIONAL") String matriculaFuncional,
    @ExcelProperty("CHAVE_UNICA") String chaveUnica,
    @ExcelProperty("CPF") String cpf,
    @ExcelProperty("CARTEIRINHA") String carteirinha,
    @ExcelProperty("MATRICULA SAP") String matriculaSap,
    @ExcelProperty("NOME") String nome,
    @ExcelProperty("DATA DE NASCIMENTO") String dataNascimento,
    @ExcelProperty("DATA ADESÃO") String dataAdesao,
    @ExcelProperty("DATA CANCELAMENTO") String dataCancelamento,
    @ExcelProperty("TIPO DEPENDENTE") String tipoDependente
) {
    public String sanitizeCpf() {
        return cpf != null ? cpf.replaceAll("[^0-9]", "") : null;
    }

    public String[] getNomeArray() {
        return nome != null ? nome.split("\\s+") : new String[0];
    }
}

