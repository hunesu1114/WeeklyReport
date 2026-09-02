package com.khs.weeklyreport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@EntityScan(basePackages = "com.khs.weeklyreport.domain")
public class WeeklyReportApplication {
    public static void main(String[] args) {
        SpringApplication.run(WeeklyReportApplication.class, args);
    }
}
