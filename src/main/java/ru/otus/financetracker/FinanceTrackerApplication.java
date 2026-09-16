package ru.otus.financetracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.otus.financetracker.configuration.ApplicationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ApplicationProperties.class)
@EnableScheduling
public class FinanceTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceTrackerApplication.class, args);
    }
}
