package com.jobtracker.jobtracker;

import com.jobtracker.jobtracker.service.JobService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.LocalDateTime;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
public class JobtrackerApplication {

	private static final Logger log = LoggerFactory.getLogger(JobtrackerApplication.class);

	public static void main(String[] args) {

        SpringApplication.run(JobtrackerApplication.class, args);

	}

	@PostConstruct
	public void logStartup() {
		log.info("========== JobTracker STARTED at " + LocalDateTime.now() + " ==========");
	}

//    @Bean
//    CommandLineRunner run(JobService jobService) {
//        return args -> jobService.checkJobs();
//    }

//    @Bean
//    CommandLineRunner run(JobService jobService) {
//        return args -> {
//            System.out.println("🔥 STARTING JOB CHECK...");
//            jobService.checkJobs();
//        };
//    }

}
