package com.etl.importer.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.lang.NonNull;

/**
 * Configuração personalizada para MongoDB que remove o campo "_class" dos documentos.
 * Isso é feito para evitar a inclusão de metadados desnecessários nos documentos armazenados.
 */
@Configuration
public class MongoConfig {

    @Bean
    public BeanPostProcessor mappingMongoConverterBeanPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(@NonNull Object bean, @NonNull String beanName) 
                    throws BeansException {
                if (bean instanceof MappingMongoConverter) {
                    ((MappingMongoConverter) bean).setTypeMapper(new DefaultMongoTypeMapper(null));
                }
                return bean;
            }

            @Override
            public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) 
                    throws BeansException {
                return bean;
            }
        };
    }
}
