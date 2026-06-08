package com.etl.importer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * CorePoolSize=8: mantém 8 threads sempre ativas;
 * MaxPoolSize=16: permite até 16 threads sob carga;
 * QueueCapacity=500: fila maior para absorver picos;
 * AwaitTerminationSeconds=120: tempo maior para processar todos os lotes.
 */
@Configuration
@EnableAsync(proxyTargetClass = true)
public class AsyncConfig {    
    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(name = "patientImportExecutor")
    public ThreadPoolTaskExecutor patientImportExecutor() {
        var executor = new ThreadPoolTaskExecutor();
                
        executor.setCorePoolSize(8); 
        executor.setMaxPoolSize(16); 
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("patient-import-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        
        var rejectionHandler = new ThreadPoolExecutor.CallerRunsPolicy();
        executor.setRejectedExecutionHandler(rejectionHandler);
        executor.initialize();
        
        log.info("Async executor configurado: corePoolSize=8, maxPoolSize=16, queueCapacity=500, keepAliveSeconds=60, threadNamePrefix=patient-import-, rejectionPolicy=CallerRunsPolicy");
        return executor;
    }
}
