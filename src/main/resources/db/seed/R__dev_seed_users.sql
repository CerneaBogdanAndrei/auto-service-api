-- =============================================================================
-- DEVELOPMENT / TEST DATA ONLY.
-- This location (classpath:db/seed) is enabled exclusively by the "dev" and "test"
-- profiles, so these well-known credentials never reach other environments.
--
-- Credentials (documented in the README):
--   admin    / Admin123!     (ADMIN)
--   owner    / Owner123!     (OWNER)
--   employee / Employee123!  (EMPLOYEE)
-- =============================================================================

INSERT INTO users (username, password_hash, full_name, role)
VALUES ('admin', '$2a$10$oqlP5GhJmZO4YGvajGtnvePrz.so/hm44LkVsRDGFc7EjOTY48/za', 'Ana Admin', 'ADMIN'),
       ('owner', '$2a$10$3Kr.qDXhCQUbmxmS0U5qQefYT3ce3kQ7/nuoWcoVnQ1nxrEtYzf3m', 'Paul Owner', 'OWNER'),
       ('employee', '$2a$10$CbQ9Qu5mda5mK25jktxhQ.8kAecluwi95iVpQIWLo/TjVKWUu6rXu', 'Mihai Mechanic', 'EMPLOYEE')
ON CONFLICT DO NOTHING;
