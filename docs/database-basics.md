# MedBook Database Foundations

## What is a Relational Database?

A relational database organizes data into interconnected tables. Data is stored in rows and columns, similar to spreadsheets, and relationships link tables together through shared keys. This structure ensures data consistency and eliminates duplication.

## What is an RDBMS?

RDBMS stands for Relational Database Management System. It's software that manages relational databases. MySQL is an RDBMS — it handles creating databases, storing data, retrieving it, and enforcing rules to keep data valid and organized.

## What is a Database?

A database is a structured collection of organized data. It's a container that holds all the tables, data, and metadata for a specific project or application. MedBook's database holds all data related to the clinic appointment system.

## What is a Table?

A table is a collection of related data organized in rows and columns. Each table represents a single entity or concept. For example, a `patients` table stores patient information, and a `doctors` table stores doctor information.

## What is a Record/Row?

A record (also called a row) is a single entry in a table. Each row represents one instance of the entity the table describes. For example, one row in the `patients` table represents one patient.

## What is a Column?

A column (also called a field) is a single attribute or property of an entity. Each column has a name and a data type. For example, a `patients` table might have columns: `id`, `name`, `email`, `phone`, `date_of_birth`.

## What is a Primary Key?

A primary key is a column (or combination of columns) that uniquely identifies each row in a table. No two rows can have the same primary key value. Typically, each table has one primary key. For example, `id` is usually the primary key in a table — each patient or doctor gets a unique ID.

## What is a Foreign Key?

A foreign key is a column in one table that references the primary key of another table. It creates a relationship between tables. For example, an `appointments` table might have a `patient_id` column that references the `patients` table's `id` column, linking each appointment to a specific patient.

## What is a Relationship Between Tables?

A relationship connects two tables through keys. The three main types are:

- **One-to-Many**: One row in Table A can be related to many rows in Table B. For example, one patient can have many appointments.
- **Many-to-One**: Many rows in Table A relate to one row in Table B. Inverse of one-to-many.
- **Many-to-Many**: Rows in Table A can relate to many rows in Table B, and vice versa. Typically requires a junction table to represent this relationship.

## Why MedBook Uses MySQL

MySQL is a reliable, open-source RDBMS. It's:
- **Proven** — widely used in production systems worldwide
- **Free** — no licensing costs
- **Easy to set up** — simple installation and configuration
- **Scalable** — handles growth in data volume and complexity
- **Compatible** — integrates well with Java (via JDBC) and Spring Boot
- **Standards-compliant** — uses standard SQL syntax

## Why We Use utf8mb4

UTF-8 is a character encoding that supports all Unicode characters: English, accents, emoji, and characters from any language. `utf8mb4` is MySQL's full UTF-8 implementation (older `utf8` was limited).

MedBook uses `utf8mb4` because:
- Clinic systems serve diverse populations with names and addresses in multiple languages
- Future-proofs the system for international expansion
- Ensures data integrity without character corruption

## Current MedBook Database State

The `medbook` database has been created on the local MySQL server.

**Current table count: 0**

The database currently contains **zero application tables**. No patient data, doctor information, appointment records, or related tables exist yet. The database is empty and ready for schema creation.

```
MySQL Server 9.6
└── medbook database
    └── (no tables yet)
    └── tables will be added in Step 4.3
```

## Basic SQL Commands for Database Interaction

### CREATE DATABASE

Creates a new database.

```sql
CREATE DATABASE medbook;
```

**Purpose**: Allocates storage space on the MySQL server for a new database with the given name.

### SHOW DATABASES

Lists all databases on the MySQL server.

```sql
SHOW DATABASES;
```

**Purpose**: Displays a list of all databases currently managed by MySQL. Useful for verifying a database exists.

### USE

Selects a database to work with for subsequent queries.

```sql
USE medbook;
```

**Purpose**: Sets the active database context. All subsequent queries operate on this database until you switch to a different one.

### SELECT DATABASE()

Returns the name of the currently active database.

```sql
SELECT DATABASE();
```

**Purpose**: Confirms which database is currently selected. Returns `medbook` if the medbook database is active, or `NULL` if no database is selected.

### SHOW TABLES

Lists all tables in the currently selected database.

```sql
SHOW TABLES;
```

**Purpose**: Displays all tables in the active database. Currently returns an empty result for the medbook database because no tables have been created yet.

---

**Next Steps**: In Step 4.3, we will create application tables for patients, doctors, appointments, specialties, and availability slots using Flyway database migrations.