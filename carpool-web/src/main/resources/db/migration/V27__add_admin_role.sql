-- Adds ADMIN to the role ENUM column in users table.
-- Required for @PreAuthorize("hasRole('ADMIN')") to work on admin endpoints.
ALTER TABLE users
    MODIFY COLUMN role ENUM('PASSENGER', 'DRIVER', 'BOTH', 'ADMIN')
    NOT NULL DEFAULT 'PASSENGER';