package com.koval.trainingplannerbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TrainingPlannerBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrainingPlannerBackendApplication.class, args);
    }

}
