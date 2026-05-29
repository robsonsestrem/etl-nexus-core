package com.etl.importer.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class ExcelPatientRow {

    @ExcelProperty("SEQ")
    private Integer seq;

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
    private String dataNascimento;

    @ExcelProperty("DATA_ADESAO")
    private String dataAdesao;

    @ExcelProperty("DATA_CANCELAMENTO")
    private String dataCancelamento;

    @ExcelProperty("TIPO_DEPENDENTE")
    private String tipoDependente;

    /**
     * Remove caracteres especiais do CPF, mantendo apenas dígitos.
     */
    public void sanitizeCpf() {
        if (this.cpf != null) {
            this.cpf = this.cpf.replaceAll("\\D", "");
        }
    }

    /**
     * Divide o nome completo em partes (array de strings) usando espaço como delimitador.
     * Essa é uma adapatação para o campo "nome" que é armazenado como array de strings no MongoDB, mas vem como string no Excel.     
     */
    public String[] getNomeArray() {
        if (this.nome == null || this.nome.isEmpty()) {
            return new String[0];
        }
        return this.nome.split("\\s+");
    }
}