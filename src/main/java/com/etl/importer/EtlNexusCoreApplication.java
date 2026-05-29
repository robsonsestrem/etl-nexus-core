package com.etl.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EtlNexusCoreApplication {

    private static final Logger logger = LoggerFactory.getLogger(EtlNexusCoreApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(EtlNexusCoreApplication.class, args);
        logger.info("Application started successfully.");
    }
}
