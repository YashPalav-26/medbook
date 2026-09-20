# MedBook — JPA Basics

A beginner-friendly walkthrough of the JPA layer introduced in **Step 7**, covering
what each piece does, why it exists, and how a Java object becomes a row in MySQL.

---

## The Big Picture

```
Java entity (User.java)
    ↓   JPA annotations (@Entity, @Table, @Column, @Id ...)
Hibernate (the JPA implementation)
    ↓   generates SQL (INSERT, SELECT, UPDATE, DELETE)
MySQL (the `medbook` database)
```

You write a plain Java class and decorate it with annotations. You never write SQL
for routine CRUD. Hibernate reads the annotations, works out the mapping to a table,
and generates the SQL. **Spring Data JPA** sits on top of Hibernate and writes the
boring query boilerplate for you.

---

## 1. What is JPA?

**JPA** = *Jakarta Persistence API* (formerly *Java Persistence API*, hence the
`jakarta.persistence.*` package names).

It is a **specification** — a set of interfaces (`EntityManager`, `EntityManagerFactory`,
`Query`) and annotations. It says *what* persistence should look like, not *how* it works.
You cannot "run JPA" on its own; you need an implementation.

## 2. What is Hibernate?

**Hibernate** is the most popular **implementation** of the JPA specification. It is
the engine that actually translates entity operations into JDBC calls and SQL.

In MedBook, Spring Boot's `spring-boot-starter-data-jpa` pulls in Hibernate, and the
startup log confirms which version is running:

```
HHH000001: Hibernate ORM core version 7.4.5.Final
```

## 3. What is Spring Data JPA?

**Spring Data JPA** is a Spring layer *above* JPA/Hibernate. Its headline feature:
you declare an interface with method names, and it **writes the queries for you**.

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);   // no SQL written by you
}
```

At startup, Spring inspects `findByEmail`, parses it as `find` + `By` + `Email`,
and generates `SELECT ... FROM users WHERE email = ?`. No `@Query`, no JDBC code.

## 4. What is an entity?

An **entity** is a Java class that JPA treats as a *persistable* object — one that
maps to a row in a database table. Instances are ordinary objects, but they also have
a persistent identity tied to a database row.

MedBook currently has two:

- `com.medbook.entity.Role` → the `roles` table
- `com.medbook.entity.User` → the `users` table

## 5. What does `@Entity` mean?

`@Entity` tells Hibernate: *this class is a persistable entity, please manage it*.
Without it, the class is just a POJO that Hibernate ignores.

It must be a **top-level, non-final class** with a no-arg constructor (Hibernate creates
instances via reflection when reading rows).

## 6. What does `@Table` mean?

`@Table(name = "users")` says *this entity maps to the table named `users`*.
Without it, Hibernate would guess the table name from the class name. Being explicit
avoids surprises — the table is `users`, not `user`.

In MedBook:

```java
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "email", columnNames = "email"))
public class User { ... }
```

## 7. What does `@Id` mean?

`@Id` marks the **primary key attribute** — the property whose value uniquely identifies
a row. Every entity must have exactly one.

## 8. What does `@GeneratedValue` mean?

`@GeneratedValue` tells Hibernate how primary key values are produced. MedBook's
`roles`/`users` tables use MySQL `AUTO_INCREMENT`, so the matching strategy is:

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(name = "id")
private Long id;
```

`GenerationType.IDENTITY` means: *let the database generate the id* (MySQL's
auto-increment). After `save()`, Hibernate reads back the produced id and populates
`id` on the returned object.

## 9. What does `@Column` mean?

`@Column` maps a field to a specific database column, and lets you declare schema
facts so Hibernate can validate them:

```java
@Column(name = "email", nullable = false, length = 255, unique = true)
private String email;
```

- `name` → the exact column name in MySQL
- `nullable = false` → the column is `NOT NULL`
- `length = 255` → `VARCHAR(255)`
- `unique = true` → backed by a unique constraint

Without `@Column`, Hibernate would guess `passwordHash` → column `password_hash` is
**wrong** (it would try `password_hash`... actually Hibernate's default is the raw field
name `passwordHash`). Naming differs from the DB, so every column is mapped explicitly —
the database is the source of truth, not a guess.

### Two MedBook-specific mapping decisions

**(a) `failed_login_attempts` is `TINYINT UNSIGNED`.** Hibernate maps `short` → `SMALLINT`
and `int` → `INTEGER`, but the real column is MySQL `TINYINT`. With `ddl-auto=validate`
that mismatch is a hard error:

```
Schema validation: wrong column type encountered in column [failed_login_attempts]
in table [users]; found [tinyint unsigned (Types#TINYINT)], but expecting [smallint (Types#SMALLINT)]
```

The fix is to pin the JDBC type while keeping a comfortable Java integer:

```java
@Column(name = "failed_login_attempts", nullable = false)
@JdbcTypeCode(SqlTypes.TINYINT)
private int failedLoginAttempts = 0;
```

**(b) `created_at` / `updated_at` are `NOT NULL DEFAULT CURRENT_TIMESTAMP(3)`.** If the
entity sends `null` on INSERT, MySQL rejects it. Marking them read-only to Hibernate
lets the database defaults apply:

```java
@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
private LocalDateTime createdAt;
```

`insertable = false` / `updatable = false` mean *leave this column out of generated
INSERT/UPDATE statements* — MySQL fills it. Read the value back with `refresh()` after
a flush.

## 10. What is `JpaRepository`?

`JpaRepository<T, ID>` is the Swiss-army-knife interface Spring Data JPA gives you.
`T` is the entity type, `ID` is the primary key type (`Long` for `BIGINT UNSIGNED`).

```java
public interface RoleRepository extends JpaRepository<Role, Long> { ... }
```

Spring generates a proxy implementation at runtime; you only declare the interface.
MedBook has `RoleRepository` and `UserRepository`.

## 11. What does `findAll()` do?

Returns **all rows** of the table as a collection of entities — `SELECT * FROM roles`.
Used in `RoleRepositoryTest` to confirm the three seeded roles load:

```java
assertThat(roleRepository.findAll()).hasSize(3);
```

Fine for small reference tables; avoid on large tables like `appointments`.

## 12. What does `findById()` do?

Fetches **one row by primary key**: `SELECT ... FROM users WHERE id = ?`.
Returns `Optional<T>`, so you handle "not found" explicitly instead of catching a null.

## 13. What does `save()` do?

`save(entity)` does **upsert by id**:
- if `id == null` → `INSERT` (a new row; the generated id is read back)
- if `id != null` and the row exists → `UPDATE`

Note the persistence context (Hibernate's first-level cache) is flushed at transaction
commit, so changes are only "saved" once the transaction commits.

## 14. What does `deleteById()` do?

`DELETE FROM ... WHERE id = ?`. In MedBook this is deliberately *not used* for
users/doctors/patients/appointments — the design uses soft deletion (`is_active = FALSE`)
and, for appointments, `status = 'CANCELLED'`.

## 15. How do derived query methods work?

Spring parses the **method name** and derives SQL from it:

| Method name | Generated SQL |
|---|---|
| `findByEmail(String email)` | `SELECT ... WHERE email = ?` |
| `findByName(String name)` | `SELECT ... WHERE name = ?` |
| `findById(Long id)` | `SELECT ... WHERE id = ?` |

Keywords like `And`, `Or`, `Between`, `OrderBy…Desc`, `IgnoreCase` all map to SQL
clauses. If a name can't be parsed, the app fails to start with a clear error —
which is exactly why these are validated at boot.

MedBook's two derived methods:

```java
Optional<Role> findByName(String name);   // RoleRepository
Optional<User>  findByEmail(String email); // UserRepository
```

## 16. Why are repositories separated from controllers/services?

**Separation of concerns.** Each layer has one job:

```
Controller  →  receives HTTP, returns JSON   (no SQL)
Service     →  business rules, transactions  (no SQL details)
Repository  →  data access                   (SQL, but generated)
Entity      →  data shape                    (table mapping)
```

Benefits: the same `UserRepository` can serve a REST controller, a batch job, or a test,
without duplicating queries; business rules live in one place; and data access can be
mocked in tests. MedBook has repositories but **no services or controllers yet** —
that's the next step.

## 17. Why is Hibernate validation used instead of automatic schema generation?

`spring.jpa.hibernate.ddl-auto` has several modes. MedBook pins it to the safest one:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

| Mode | Behaviour | MedBook? |
|---|---|---|
| `create` | Drop and recreate tables on every start | ✗ destructive |
| `create-drop` | `create` + drop on shutdown | ✗ destructive |
| `update` | Alter the schema to match entities | ✗ silent drift |
| **`validate`** | **Compare entities against the DB; fail on mismatch** | **✓ used** |

Reasons `validate` is correct here:

1. **Flyway owns the schema.** Schema changes are versioned, reviewed, and replayable
   via `V1__create_medbook_schema.sql`. Letting Hibernate alter tables would create a
   second, invisible source of truth.
2. **It's a safety net.** If an entity drifts from the real schema, the application
   **refuses to start** rather than silently corrupting data.
3. **Nothing is ever created or dropped** — production data is safe.

That is why this step's most valuable evidence was Hibernate starting cleanly:
validation *silently passed* because `Role`/`User` match the Flyway schema exactly.

## 18. How does the Java entity map to an existing MySQL table?

Taking `Role` as the concrete example:

| Java | Annotation | MySQL |
|---|---|---|
| `class Role` | `@Entity`, `@Table(name = "roles")` | table `roles` |
| `private Long id` | `@Id`, `@GeneratedValue(IDENTITY)`, `@Column(name="id")` | `id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY` |
| `private String name` | `@Column(name="name", nullable=false, length=50, unique=true)` | `name VARCHAR(50) NOT NULL UNIQUE` |
| `private String description` | `@Column(name="description", length=255)` | `description VARCHAR(255)` |
| `private Set<Role> roles` (in `User`) | `@ManyToMany` + `@JoinTable(name="user_roles")` | join table `user_roles` |

### Java ↔ MySQL type choices used here

| MySQL type | Java type | Notes |
|---|---|---|
| `BIGINT UNSIGNED` | `Long` | repository ID type is `Long` |
| `VARCHAR(n)` | `String` | `length` documents `n` |
| `BOOLEAN` (`TINYINT(1)`) | `boolean` | primitives keep it simple; DB default applied |
| `TINYINT UNSIGNED` | `int` + `@JdbcTypeCode(SqlTypes.TINYINT)` | see §9(a) |
| `DATETIME(3)` | `LocalDateTime` | millisecond precision; UTC audit timestamps |
| `DATE` | `LocalDate` | (used by later entities) |

### The many-to-many relationship

`User` ↔ `Role` is many-to-many through the `user_roles` junction table. `User` owns it:

```java
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(
    name = "user_roles",
    joinColumns = @JoinColumn(name = "user_id"),
    inverseJoinColumns = @JoinColumn(name = "role_id")
)
private Set<Role> roles = new HashSet<>();
```

- `joinColumns` → the join-table column pointing at **this** entity's PK (`user_id` → `users.id`)
- `inverseJoinColumns` → the column pointing at the **other** side (`role_id` → `roles.id`)
- `LAZY` → roles are only loaded when `getRoles()` is actually used, not on every user load
- `Set` rather than `List` → roles are unordered and must be distinct

`Role` carries the inverse side only:

```java
@ManyToMany(mappedBy = "roles")
private Set<User> users = new HashSet<>();
```

`mappedBy = "roles"` says: *the join table is already declared on the other side —
look there, don't create a second one.*

**Why no `UserRole` entity?** For simple RBAC (does this user have this role?) a
`@JoinTable` mapping is sufficient and much less code. A dedicated `UserRole` entity
would only be worth it if the link itself needed behaviour — e.g. `assigned_by`,
`expires_at`, or per-assignment audit. That isn't needed in V1.

**Trade-offs to be aware of:**
- Joining through `user_roles` in JPQL works, but complex reports across the junction
  are easier with explicit entities.
- `CASCADE` across a join table applies to the link rows, not the role rows.
- Removing a role from a user's set deletes the junction row — which is exactly what
  the DB's `ON DELETE CASCADE` for `user_id` also guarantees.

### `equals()` / `hashCode()` / `toString()` in JPA

These are subtle because Hibernate can load the same row multiple times as different
object instances (proxies!). MedBook's rules:

- **`equals`/`hashCode` are based on the surrogate id** — two object representations
  of the same row compare equal. Unpersisted entities (id == null) are never equal to
  each other, and `hashCode` uses a random UUID while unsaved so hash codes stay
  stable across the transition from transient to persisted.
- **`toString()` deliberately omits relationship collections** (`roles`, `users`).
  Printing a collection would trigger lazy loading (a database hit during logging!)
  and, on a bidirectional link, recurse until `StackOverflowError`.
- **`User.toString()` also omits `passwordHash`** so a hashed password can never leak
  into logs — even though no API exposes it yet.

---

## How it all fits together in MedBook today

```
MySQL (Flyway V1 owns the schema)
   ↑                |
   | SQL            | validates at startup (ddl-auto=validate)
   |                |
Hibernate 7.4.5.Final
   ↑                |
   | JPA            | @Entity / @Table / @Id / @Column / @ManyToMany
   |                |
com.medbook.entity.User, com.medbook.entity.Role
   ↑                |
   | proxies        | generated implementations
   |                |
Spring Data JPA: UserRepository, RoleRepository
   ↑                |
   |                | (no services or controllers yet — next step)
JUnit integration tests (@SpringBootTest, real MySQL)
```

### What was actually verified

- `mvn clean test` → **10 tests, 0 failures**
- Hibernate started and **validated** the existing Flyway schema (no mismatch)
- `RoleRepository.findByName("ADMIN")` returned a present `Role` from **live MySQL**
- `UserRepository.findByEmail(...)` correctly returns empty for unknown emails
- Round-trip `save()` → `findByEmail()` → lazy roles collection works against MySQL
- `created_at`/`updated_at` DB defaults populate correctly on insert
- Test transactions roll back — the shared dev database stays clean (`users` count = 0)
- `GET /api/v1/hello` → `{"message":"Welcome to MedBook API"}`
- `GET /actuator/health` → `UP`
- Still exactly 9 application tables + `flyway_schema_history`; Flyway V1 `success = 1`

### Beginner's takeaway

You learned the three-part split that the rest of MedBook builds on:

1. **Describe** your data with annotated Java classes (`@Entity`, `@Table`, `@Column`).
2. **Access** it with interfaces that write their own SQL (`JpaRepository`, `findByEmail`).
3. **Protect** the real schema by letting Flyway create it and Hibernate only
   `validate` it — so the database is the single source of truth and the app refuses
   to start if Java and MySQL ever disagree.
