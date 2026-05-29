package com.etl.importer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync(proxyTargetClass = true)
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(name = "patientImportExecutor")
    public ThreadPoolTaskExecutor patientImportExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("patient-import-");
        var rejectionHandler = new ThreadPoolExecutor.CallerRunsPolicy();
        executor.setRejectedExecutionHandler(rejectionHandler);
        executor.initialize();
        log.info("Async executor configured: corePoolSize=2, maxPoolSize=4, queueCapacity=10, keepAliveSeconds=60, threadNamePrefix=patient-import-, rejectionPolicy=CallerRunsPolicy");
        return executor;
    }
}
