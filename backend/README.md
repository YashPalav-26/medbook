# MedBook Backend

Backend API for the MedBook clinic appointment management system.

## Technologies

- **Spring Boot 4.1.1** - Application framework
- **Java 21** - Programming language
- **Maven** - Build tool
- **Tomcat** - Embedded servlet container

## Running the Application

```bash
cd backend
mvn spring-boot:run
```

The application starts on port **8080**.

## Endpoints

### GET /api/v1/hello

Health check endpoint that returns a welcome message.

**Response:**
```json
{
  "message": "Welcome to MedBook API"
}
```

**Example:**
```bash
curl http://localhost:8080/api/v1/hello
```

### Actuator Health

```bash
curl http://localhost:8080/actuator/health
```

## Testing

```bash
mvn test
```

## What We Learned in Step 3

### Spring Boot
Spring Boot is an opinionated framework that simplifies Spring application setup. It provides:
- Auto-configuration (senses dependencies and configures accordingly)
- Embedded servers (no need to deploy WAR files)
- Starter dependencies (bundled dependency sets)
- Actuator (production-ready features like health checks)

### Spring Initializr
Spring InitializR (start.spring.io) is the official project generator. It creates:
- Maven or Gradle build files
- Proper directory structure
- Main application class
- Configuration files
- Sample code

### Maven
Maven is a build tool that manages:
- Dependencies (from Maven Central repository)
- Build lifecycle (compile, test, package)
- Project Object Model (`pom.xml`)
- Transitive dependencies automatically

### The pom.xml
The Project Object Model file defines:
- Project coordinates (groupId, artifactId, version)
- Parent POM (inherits defaults from Spring Boot)
- Dependencies (Spring Boot starters)
- Build plugins

### Main Application Class
The entry point annotated with `@SpringBootApplication`. This annotation combines:
- `@Configuration` - marks as configuration class
- `@EnableAutoConfiguration` - auto-configures dependencies
- `@ComponentScan` - scans for Spring components

### Embedded Server
Spring Boot ships with embedded Tomcat (by default). The application is self-contained:
- No external application server needed
- `java -jar` runs the entire application
- Port configured in `application.properties`

### @RestController
Combines `@Controller` and `@ResponseBody`. Returns data directly (JSON/XML) instead of view names. Spring automatically serializes return values to HTTP responses.

### @GetMapping
Maps HTTP GET requests to handler methods. Shorthand for `@RequestMapping(method = RequestMethod.GET)`. The path `/api/v1/hello` is relative to the server root.