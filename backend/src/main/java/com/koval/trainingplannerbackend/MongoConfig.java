package com.koval.trainingplannerbackend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Forces LocalDate and LocalDateTime to be stored/read in UTC,
 * preventing timezone shifts (e.g. 2025-02-14 stored as 2025-02-13T23:00Z in CET).
 */
@Configuration
@EnableCaching
public class MongoConfig {

    @Value("${mongo.pool.max-size:100}")
    private int poolMaxSize;

    @Value("${mongo.pool.min-size:10}")
    private int poolMinSize;

    @Value("${mongo.pool.max-wait-ms:5000}")
    private int poolMaxWaitMs;

    @Value("${mongo.pool.max-connection-idle-ms:60000}")
    private int poolMaxConnectionIdleMs;

    @Value("${mongo.pool.max-connection-life-ms:1800000}")
    private int poolMaxConnectionLifeMs;

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                new LocalDateToDateConverter(),
                new DateToLocalDateConverter(),
                new LocalDateTimeToDateConverter(),
                new DateToLocalDateTimeConverter(),
                new IntegerToBooleanConverter()
        ));
    }

    /**
     * Tunes the MongoDB connection pool. Defaults are sized for a single Spring Boot instance
     * handling AI tool storms (each chat turn fans out to several Mongo reads).
     * Override per-environment via the {@code mongo.pool.*} properties.
     */
    @Bean
    public MongoClientSettingsBuilderCustomizer mongoPoolCustomizer() {
        return builder -> builder.applyToConnectionPoolSettings(pool -> pool
                .maxSize(poolMaxSize)
                .minSize(poolMinSize)
                .maxWaitTime(poolMaxWaitMs, TimeUnit.MILLISECONDS)
                .maxConnectionIdleTime(poolMaxConnectionIdleMs, TimeUnit.MILLISECONDS)
                .maxConnectionLifeTime(poolMaxConnectionLifeMs, TimeUnit.MILLISECONDS));
    }

    static class LocalDateToDateConverter implements Converter<LocalDate, Date> {
        @Override
        public Date convert(LocalDate source) {
            return Date.from(source.atStartOfDay(ZoneOffset.UTC).toInstant());
        }
    }

    static class DateToLocalDateConverter implements Converter<Date, LocalDate> {
        @Override
        public LocalDate convert(Date source) {
            return source.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
    }

    static class LocalDateTimeToDateConverter implements Converter<LocalDateTime, Date> {
        @Override
        public Date convert(LocalDateTime source) {
            return Date.from(source.toInstant(ZoneOffset.UTC));
        }
    }

    static class DateToLocalDateTimeConverter implements Converter<Date, LocalDateTime> {
        @Override
        public LocalDateTime convert(Date source) {
            return LocalDateTime.ofInstant(source.toInstant(), ZoneOffset.UTC);
        }
    }

    @ReadingConverter
    static class IntegerToBooleanConverter implements Converter<Integer, Boolean> {
        @Override
        public Boolean convert(Integer source) {
            return source != 0;
        }
    }
}
