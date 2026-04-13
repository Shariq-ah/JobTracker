package com.jobtracker.jobtracker;

import com.jobtracker.jobtracker.service.JobService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JobtrackerApplication {

	public static void main(String[] args) {

        SpringApplication.run(JobtrackerApplication.class, args);

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
