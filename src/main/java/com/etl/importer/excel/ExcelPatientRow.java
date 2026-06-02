package com.etl.importer.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class ExcelPatientRow {

    @ExcelProperty("SEQ")
    private String seq;

    @ExcelProperty("CONTRATO")
    private String contrato;

    @ExcelProperty("FAMILIA")
    private String familia;

    @ExcelProperty("MATRICULA_FUNCIONAL")
    private String matriculaFuncional;

    @ExcelProperty("CHAVE_UNICA")
    private String chaveUnica;

    @ExcelProperty("CPF")
    private String cpf;

    @ExcelProperty("CARTEIRINHA")
    private String carteirinha;

    @ExcelProperty("MATRICULA_SAP")
    private String matriculaSap;

    @ExcelProperty("NOME")
    private String nome;

    @ExcelProperty("DATA_DE_NASCIMENTO")
    private String dataDeNascimento;

    @ExcelProperty("DATA_ADESAO")
    private String dataAdesao;

    @ExcelProperty("DATA_CANCELAMENTO")
    private String dataCancelamento;

    @ExcelProperty("TIPO_DEPENDENTE")
    private String tipoDependente;
    
    public String sanitizeCpf() {
        if (this.cpf == null) return null;
        return this.cpf.replaceAll("[^\\d]", "");
    }

    public String[] getNomeArray() {
        if (this.nome == null || this.nome.trim().isEmpty()) return new String[0];
        return this.nome.trim().split("\\s+");
    }
    
    public void sanitizeAllIdentifiers() {
        if (this.cpf != null) {
            this.cpf = this.cpf.replaceAll("[^a-zA-Z0-9]", "");
        }
        if (this.carteirinha != null) {
            this.carteirinha = this.carteirinha.replaceAll("[^a-zA-Z0-9]", "");
        }
        if (this.matriculaFuncional != null) {
            this.matriculaFuncional = this.matriculaFuncional.replaceAll("[^a-zA-Z0-9]", "");
        }
        if (this.chaveUnica != null) {
            this.chaveUnica = this.chaveUnica.replaceAll("[^a-zA-Z0-9]", "");
        }
        if (this.matriculaSap != null) {
            this.matriculaSap = this.matriculaSap.replaceAll("[^a-zA-Z0-9]", "");
        }
    }
}
