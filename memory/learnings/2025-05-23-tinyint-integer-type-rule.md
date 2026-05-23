# TINYINT Must Map to Integer, Not byte

**Date:** 2025-05-23  
**Context:** Adding a `seats` column to the ride table using MySQL TINYINT

## What Happened

Mapped `TINYINT` column to Java `byte` in the entity. App failed to start with:

```
SchemaManagementException: Schema-validation: wrong column type encountered in column [seats] in table [ride];
  found [tinyint (Types#TINYINT)], but expecting [tinyint (Types#INTEGER)]
```

## Why

Hibernate's schema validation expects `TINYINT` columns to map to `Types#INTEGER` (i.e., Java `Integer`), not `Types#TINYINT` (Java `byte`). The `byte` primitive type maps to `Types#TINYINT` in the JDBC type system, which Hibernate treats as a different type from the DB's TINYINT.

## Fix

Always use `Integer` (boxed) for `TINYINT` columns. Never use `byte` or `Byte`.

```java
// Wrong
private byte seats;

// Correct
private Integer seats;
```

## Rule Added To

`db-engineer.md` agent — type mapping table includes explicit note: "TINYINT → Integer (not byte)"