package com.examgrid.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ExamGridApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamGridApplication.class, args);
    }
}
