package dev.vigia.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendWorkerApplication.class, args);
	}

}
