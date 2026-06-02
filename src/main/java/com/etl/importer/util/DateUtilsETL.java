package com.etl.importer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
* Classe utilitária para converter strings de data e hora em objetos de tempo do Java 8.
* Detecta automaticamente o formato da string de entrada usando padrões de expressão regular e
* tenta todos os formatadores compatíveis. Suporta vários formatos comuns de data e hora.
* <p>
* Esta classe não possui estado e destina-se apenas ao uso estático.
 */
public final class DateUtilsETL {

    private static final Logger log = LoggerFactory.getLogger(DateUtilsETL.class);

    // Mapeia um padrão de expressão regular para seu DateTimeFormatter correspondente para formatos que contêm apenas datas.
    private static final Map<Pattern, DateTimeFormatter> DATE_FORMATTERS = new LinkedHashMap<>();

    // Mapeia um padrão de expressão regular para seu DateTimeFormatter correspondente para formatos de data e hora.
    private static final Map<Pattern, DateTimeFormatter> DATE_TIME_FORMATTERS = new LinkedHashMap<>();

    static {
        // Formatadores somente de data (em ordem de prioridade de detecção)
        // European: dd/MM/yyyy
        DATE_FORMATTERS.put(Pattern.compile("^\\d{2}/\\d{2}/\\d{4}$"), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        // American: MM/dd/yyyy
        DATE_FORMATTERS.put(Pattern.compile("^\\d{2}/\\d{2}/\\d{4}$"), DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        // ISO with zero-padding: yyyy/MM/dd (note: using yyyy, not YYYY; YYYY is week-year, unlikely intended)
        DATE_FORMATTERS.put(Pattern.compile("^\\d{4}/\\d{2}/\\d{2}$"), DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        // ISO without zero-padding: yyyy/M/d  (CRITICAL for EasyExcel)
        DATE_FORMATTERS.put(Pattern.compile("^\\d{4}/\\d{1,2}/\\d{1,2}$"), DateTimeFormatter.ofPattern("yyyy/M/d"));
        // ISO with hyphen: yyyy-MM-dd
        DATE_FORMATTERS.put(Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$"), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        // European with hyphen: dd-MM-yyyy
        DATE_FORMATTERS.put(Pattern.compile("^\\d{2}-\\d{2}-\\d{4}$"), DateTimeFormatter.ofPattern("dd-MM-yyyy"));

        DATE_TIME_FORMATTERS.put(Pattern.compile("^\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}:\\d{2}$"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        DATE_TIME_FORMATTERS.put(Pattern.compile("^\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}$"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        DATE_TIME_FORMATTERS.put(Pattern.compile("^\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}:\\d{2}$"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"));
        DATE_TIME_FORMATTERS.put(Pattern.compile("^\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}$"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"));
        DATE_TIME_FORMATTERS.put(Pattern.compile("^\\d{4}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}$"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
        DATE_TIME_FORMATTERS.put(Pattern.compile("^\\d{4}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}$"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));        
        DATE_TIME_FORMATTERS.put(Pattern.compile("^\\d{4}/\\d{1,2}/\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2}$"),
                DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss"));
        DATE_TIME_FORMATTERS.put(Pattern.compile("^\\d{4}/\\d{1,2}/\\d{1,2}\\s+\\d{2}:\\d{2}$"),
                DateTimeFormatter.ofPattern("yyyy/M/d HH:mm"));
        DATE_TIME_FORMATTERS.put(Pattern.compile("^\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}$"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        DATE_TIME_FORMATTERS.put(Pattern.compile("^\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}$"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        DATE_TIME_FORMATTERS.put(Pattern.compile("^\\d{2}-\\d{2}-\\d{4}\\s+\\d{2}:\\d{2}:\\d{2}$"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
        DATE_TIME_FORMATTERS.put(Pattern.compile("^\\d{2}-\\d{2}-\\d{4}\\s+\\d{2}:\\d{2}$"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));        
    }

    private DateUtilsETL() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated.");
    }

    /**
     * Converte uma string de data em um objeto {@link LocalDate}.
     * Detecta automaticamente o formato usando padrões de expressão regular e tenta todos os formatadores compatíveis.
     *
     * @param dateString a string de data a ser convertida (por exemplo, "31/12/2023", "12/31/2023", "2023/12/31", "2023/12/1", "2023-12-31", "31-12-2023")
     * @return o LocalDate convertido
     * @throws IllegalArgumentException se a string for nula, vazia ou se o seu formato não for reconhecido
     */
    public static LocalDate convertToLocalDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            throw new IllegalArgumentException("Date string must not be null or empty");
        }

        String trimmed = dateString.trim();        

        for (Map.Entry<Pattern, DateTimeFormatter> entry : DATE_FORMATTERS.entrySet()) {
            if (entry.getKey().matcher(trimmed).matches()) {
                try {
                    LocalDate result = LocalDate.parse(trimmed, entry.getValue());                    
                    return result;
                } catch (DateTimeParseException e) {
                    log.error("Regex matched but parsing failed for date '{}' with formatter '{}': {}", trimmed, entry.getValue().toString(), e.getMessage());
                }
            }
        }

        // Se nenhuma expressão regular corresponder, tente uma abordagem alternativa: tente todos os formatadores como último recurso.
        log.debug("No direct regex match for date '{}', trying all date formatters as fallback", trimmed);
        for (DateTimeFormatter formatter : DATE_FORMATTERS.values()) {
            try {
                LocalDate result = LocalDate.parse(trimmed, formatter);
                log.debug("Fallback parsing succeeded for date '{}' with format '{}'", trimmed, formatter.toString());
                return result;
            } catch (DateTimeParseException ex) {
                log.error("Unexpected error", ex.getMessage(), ex);
            }
        }

        throw new IllegalArgumentException("Unable to parse date string: '" + trimmed + "'. Supported formats: dd/MM/yyyy, MM/dd/yyyy, yyyy/MM/dd, yyyy/M/d (critical), yyyy-MM-dd, dd-MM-yyyy");
    }

    /**
     * Converte uma string de data e hora em um objeto {@link LocalDateTime}.
     * Detecta automaticamente o formato usando padrões de expressão regular e tenta todos os formatadores compatíveis.
     *
     * @param dateTimeString a string de data e hora a ser convertida (por exemplo, "31/12/2023 14:30:00", "2023/12/1 9:05", "2023-12-31 14:30")
     * @return o LocalDateTime convertido
     * @throws IllegalArgumentException se a string for nula, vazia ou se o seu formato não for reconhecido
     */
    public static LocalDateTime convertToLocalDateTime(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
            throw new IllegalArgumentException("Date-time string must not be null or empty");
        }

        String trimmed = dateTimeString.trim();        

        for (Map.Entry<Pattern, DateTimeFormatter> entry : DATE_TIME_FORMATTERS.entrySet()) {
            if (entry.getKey().matcher(trimmed).matches()) {
                try {
                    LocalDateTime result = LocalDateTime.parse(trimmed, entry.getValue());                    
                    return result;
                } catch (DateTimeParseException e) {
                    log.error("Regex matched but parsing failed for date-time '{}' with formatter '{}': {}", trimmed, entry.getValue().toString(), e.getMessage());
                }
            }
        }
       
        log.debug("No direct regex match for date-time '{}', trying all formatters as fallback", trimmed);
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS.values()) {
            try {
                LocalDateTime result = LocalDateTime.parse(trimmed, formatter);
                log.debug("Fallback parsing succeeded for date-time '{}' with format '{}'", trimmed, formatter.toString());
                return result;
            } catch (DateTimeParseException ex) {
                log.error("Unexpected error", ex.getMessage(), ex);
            }
        }

        throw new IllegalArgumentException("Unable to parse date-time string: '" + trimmed + "'. Supported formats: [date] [time] where time is HH:mm or HH:mm:ss");
    }
}