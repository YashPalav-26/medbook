package com.medbook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the MedBook Backend application.
 *
 * Spring Boot automatically configures:
 * - Embedded servlet container (Tomcat by default)
 * - Spring MVC for REST APIs
 * - Component scanning in the com.medbook package
 */
@SpringBootApplication
public class MedbookBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedbookBackendApplication.class, args);
    }
}