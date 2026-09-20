package com.dilshandrapers.syncmaster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SyncMasterApplication {

	public static void main(String[] args) {
		SpringApplication.run(SyncMasterApplication.class, args);
	}

}
