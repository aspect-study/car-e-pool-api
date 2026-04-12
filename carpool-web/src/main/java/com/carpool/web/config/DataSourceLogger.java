package com.carpool.web.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Slf4j
@Component
public class DataSourceLogger {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @PostConstruct
    public void logUrl() {
        log.info(">>> spring.datasource.url = {}", datasourceUrl);
    }
}

