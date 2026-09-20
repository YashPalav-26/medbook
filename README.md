# MedBook

A full-stack clinic appointment management system built with Spring Boot, MySQL, and React.

## Project Status

**Current Phase:** Backend foundation / JPA foundation

### Completed
- Spring Boot backend
- MySQL schema with Flyway migration
- Spring Data JPA entities and repositories
- Hibernate schema validation
- Repository layer foundation
- Automated tests
- GitHub repository

### In Progress
- Backend domain entities
- REST API modules

### Planned
- Authentication and authorization
- Doctor management
- Patient management
- Appointment scheduling
- Availability management
- Admin dashboard
- Patient dashboard
- Doctor dashboard
- React frontend
- Deployment

## Technology Stack

### Backend
- Java 21
- Spring Boot 4.1.1
- Spring Web MVC
- Spring Data JPA
- Hibernate
- Spring JDBC
- Flyway
- MySQL 9.6
- Maven

### Frontend (planned)
- React
- Vite
- Tailwind CSS
- React Router
- Axios
- TanStack Query
- React Hook Form
- Zod
- Recharts
- Lucide React

### Testing
- Spring Boot Test
- JUnit

### Development
- Git
- GitHub
- VS Code / IntelliJ IDEA

## Architecture

```
React (planned)
    ↓
REST API
    ↓
Spring Boot Controller
    ↓
Service
    ↓
Spring Data Repository
    ↓
Hibernate/JPA
    ↓
MySQL
```

**Database Schema Ownership:** Flyway owns schema evolution. Hibernate uses `ddl-auto=validate` to ensure JPA entities match the Flyway-managed schema.

## Current Backend Structure

```
backend/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/medbook/
│   │   │   ├── MedbookBackendApplication.java
│   │   │   ├── controller/
│   │   │   │   └── HelloController.java
│   │   │   ├── entity/
│   │   │   │   ├── Role.java
│   │   │   │   └── User.java
│   │   │   ├── repository/
│   │   │   │   ├── RoleRepository.java
│   │   │   │   └── UserRepository.java
│   │   │   └── exception/
│   │   │       └── GlobalExceptionHandler.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/migration/
│   │           └── V1__create_medbook_schema.sql
│   └── test/
│       └── java/com/medbook/
│           ├── MedbookBackendApplicationTests.java
│           ├── HelloControllerTest.java
│           ├── RoleRepositoryTest.java
│           └── UserRepositoryTest.java
```

### Implemented Packages
- `controller` - REST endpoints
- `entity` - JPA entities (Role, User)
- `repository` - Spring Data JPA repositories
- `db/migration` - Flyway database migrations

## Database

### Tables (Flyway V1 migration)
- `users` - System users (patients, doctors, admins)
- `roles` - User roles (ADMIN, DOCTOR, PATIENT)
- `user_roles` - Many-to-many user-role mapping
- `doctors` - Doctor profiles with specialty associations
- `patients` - Patient profiles
- `specialties` - Medical specialties
- `doctor_availability` - Doctor weekly availability slots
- `appointments` - Appointment records
- `appointment_status_history` - Appointment status audit trail

### Design Features
- Flyway schema versioning
- Relational design with foreign keys
- CHECK constraints for data validation
- Soft deletion via `is_active` flags
- Indexes for query performance

## Current API

### Implemented Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/hello` | Hello world endpoint |
| GET | `/actuator/health` | Health check endpoint |

### Planned Endpoints
- User CRUD operations
- Doctor management
- Patient management
- Appointment booking
- Availability scheduling

## Current JPA Foundation

### Implemented Entities

**Role**
- Entity mapping for role data
- Many-to-many relationship with User

**User**
- Entity mapping for user data
- Many-to-many relationship with Role
- Username-based queries via derived methods

### Implemented Repositories
- `RoleRepository` - Role data access with derived queries
- `UserRepository` - User data access with derived queries

### Validation
Hibernate validates entities against the Flyway-managed schema at startup (`ddl-auto=validate`).

## Documentation

- [Database Basics](docs/database-basics.md) - MySQL fundamentals
- [Database Design](docs/database-design.md) - Schema design decisions
- [ER Diagram](docs/er-diagram.md) - Visual database schema
- [JPA Basics](docs/jpa-basics.md) - JPA/Hibernate integration

## Development Setup

### Prerequisites
- Java 21
- Maven 3.9+
- MySQL 9.6+

### Steps
1. Clone repository
2. Set database credentials:
   ```bash
   export DB_USERNAME=root
   export DB_PASSWORD=your_password
   ```
   Or on Windows:
   ```powershell
   $env:DB_USERNAME="root"
   $env:DB_PASSWORD="your_password"
   ```
3. Ensure MySQL is running
4. Run the backend:
   ```bash
   cd backend
   mvn spring-boot:run
   ```
5. Run tests:
   ```bash
   mvn test
   ```

### Database Configuration
Credentials are provided through environment variables:
- `DB_USERNAME` - Database username (default: root)
- `DB_PASSWORD` - Database password (default: empty)

The application uses `application.properties` with environment variable placeholders. Local overrides can use `application-local.properties` (ignored by Git).

## Roadmap

1. Environment and repository setup ✅
2. Spring Boot foundation ✅
3. MySQL + Flyway ✅
4. JPA foundation ✅
5. Doctor/Specialty entities
6. Patient entity
7. Availability
8. Appointment domain
9. Service layer
10. REST APIs
11. Validation and exception handling
12. Authentication / authorization
13. React frontend
14. Dashboards
15. Testing hardening
16. Docker
17. Deployment

## Learning Topics Covered

- Spring Boot application setup
- REST API development
- Maven build configuration
- MySQL database design
- SQL schema creation with Flyway
- JPA entity mapping
- Hibernate ORM
- Entity relationships (many-to-many)
- Derived query methods
- Schema validation
- Git version control
- GitHub repository management

## Future Features

Planned features not yet implemented:
- JWT authentication
- Role-based access control (RBAC)
- Appointment conflict detection
- Doctor availability management
- Patient dashboard
- Doctor dashboard
- Admin dashboard
- Analytics and reporting
- Responsive React frontend
- Production deployment

## Contributing / Development Notes

This is a personal portfolio project. Contributions welcome for:
- Code improvements
- Test coverage
- Documentation fixes

## License

License has not yet been selected.