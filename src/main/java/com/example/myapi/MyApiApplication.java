package com.example.myapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableJpaRepositories(
    basePackages = "com.example.myapi.repository",
    excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = com.example.myapi.repository.news.NewsRepository.class
    )
)
public class MyApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyApiApplication.class, args);
    }
}
