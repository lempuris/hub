package com.refinitiv.markethub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for Market Data Hub V1.
 * 
 * This is a Spring Boot application that serves as a lightweight,
 * pass-through FIX engine for distributing market data from Refinitiv
 * to market makers (RA and CMF).
 * 
 * Key features:
 * - FIX protocol for market data distribution
 * - Hazelcast for distributed configuration
 * - PostgreSQL/TimescaleDB for operational data
 * - Holiday calendar management
 * - Simple interpolation logic
 * 
 * @author Market Data Hub Team
 * @version 1.0
 */
@SpringBootApplication
@EnableAsync           // Enables asynchronous processing for market data
@EnableScheduling      // Enables scheduled tasks for periodic operations
@EnableCaching         // Enables Spring caching support
@EnableJpaRepositories // Enables JPA repositories for database access
public class MarkethubApplication {

	/**
	 * Main entry point for the Market Data Hub application.
	 * 
	 * @param args Command line arguments
	 */
	public static void main(String[] args) {
		// Set system properties for better performance
		System.setProperty("hazelcast.logging.type", "slf4j");
		System.setProperty("quickfix.logviewer.enabled", "false");
		
		// Start the Spring Boot application
		SpringApplication.run(MarkethubApplication.class, args);
	}

}
