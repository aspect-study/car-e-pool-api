---
name: flyway-migration
description: Use when adding a new Flyway database migration to the carpool project. Covers version numbering, file naming, entity sync, and MapStruct mapper updates.
---

# Adding a Flyway Migration

## Step 1 — Find the next version number

Check the existing migrations:

```
carpool-web/src/main/resources/db/migration/
```

List files to find the highest `V#`. The next migration is `V{N+1}`. Never reuse or skip numbers — Flyway will fail if the checksum sequence has gaps.

Current known highest: **V44** (as of May 2026). Verify by listing the directory.

## Step 2 — Name the file

```
V{N}__short_description_in_snake_case.sql
```

Two underscores between version and description. Examples:
- `V45__add_user_bio_column.sql`
- `V45__create_ride_ratings_table.sql`

Place in `carpool-web/src/main/resources/db/migration/`.

## Step 3 — Write safe SQL

**Adding a nullable column:**
```sql
ALTER TABLE my_table ADD COLUMN new_col VARCHAR(255) NULL;
```

**Adding a NOT NULL column to a populated table — always supply a DEFAULT:**
```sql
ALTER TABLE my_table ADD COLUMN new_col INT NOT NULL DEFAULT 0;
```

**Adding an index:**
```sql
CREATE INDEX idx_my_table_new_col ON my_table (new_col);
```

**Widening a type (e.g., TINYINT → INT):** safe on MySQL; no data loss.

Never rename or drop a column in the same migration that adds a replacement — split into two migrations (add, then drop after deployment).

## Step 4 — Sync the JPA entity

Open the corresponding entity in `carpool-domain/`. Add the field with the correct Java type:

| MySQL type         | Java type     |
|--------------------|---------------|
| INT / BIGINT       | `Integer` / `Long` |
| TINYINT            | `Integer` (not `byte` — Hibernate schema validation requires `Types#INTEGER`) |
| VARCHAR            | `String`      |
| TIMESTAMP NULL     | `Instant`     |
| BOOLEAN / TINYINT(1) | `Boolean`   |

Add the column annotation if the name doesn't match the field name:

```java
@Column(name = "new_col")
private Integer newCol;
```

Update `@Table(uniqueConstraints = ...)` if the migration adds a unique constraint.

## Step 5 — Update MapStruct mapper (if new fields need exposing)

If the new column should appear in API responses, add a getter to the relevant DTO record and a mapping method (or field mapping) to `EntityMapper` in `carpool-service/`.

If the column is internal (e.g., a timestamp used only by schedulers), no mapper change is needed.

## Step 6 — Check EntityMapper and response DTOs

For any new field on an entity that already has a response DTO, verify `EntityMapper` doesn't need an `@Mapping(ignore = true)` annotation if the field has no DTO equivalent — otherwise MapStruct compile will warn or fail.

## Checklist

- [ ] Version number is exactly one higher than the current max in `db/migration/`
- [ ] File named `V{N}__description.sql` (two underscores)
- [ ] NOT NULL columns have a DEFAULT or the table is empty
- [ ] JPA entity updated with correct Java type and `@Column` if needed
- [ ] `@Table` unique constraints updated if migration adds a DB-level unique constraint
- [ ] `EntityMapper` updated if new field needs to appear in a response DTO
- [ ] No existing migration files were modified (Flyway checksums will fail)
