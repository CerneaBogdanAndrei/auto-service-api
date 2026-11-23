package io.github.cerneabogdanandrei.autoservice.migration;

import io.github.cerneabogdanandrei.autoservice.support.AbstractIntegrationTest;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the Flyway schema against a real PostgreSQL instance, in particular the
 * database-level safety nets backing the service-layer business rules.
 * Every test runs in a transaction that is rolled back afterwards.
 */
@Transactional
class SchemaMigrationIT extends AbstractIntegrationTest {

    private static final OffsetDateTime T10 = OffsetDateTime.parse("2030-01-15T10:00:00Z");
    private static final OffsetDateTime T11 = T10.plusHours(1);
    private static final OffsetDateTime T12 = T10.plusHours(2);
    private static final String VALID_VIN = "WVWZZZ1JZXW000001";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void initialMigrationIsApplied() {
        Boolean success = jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);

        assertThat(success).isTrue();
    }

    // ---------------------------------------------------------------- appointments

    @Test
    void rejectsOverlappingAppointmentsForSameVehicle() {
        long vehicleId = insertVehicle(insertClient("a@example.com"), VALID_VIN);
        insertAppointment(vehicleId, T10, T11, "SCHEDULED");

        assertViolates("ex_appointments_vehicle_overlap",
                () -> insertAppointment(vehicleId, T10.plusMinutes(30), T12, "SCHEDULED"));
    }

    @Test
    void allowsBackToBackAppointmentsForSameVehicle() {
        long vehicleId = insertVehicle(insertClient("a@example.com"), VALID_VIN);
        insertAppointment(vehicleId, T10, T11, "SCHEDULED");

        assertThatCode(() -> insertAppointment(vehicleId, T11, T12, "SCHEDULED")).doesNotThrowAnyException();
    }

    @Test
    void cancelledAppointmentDoesNotBlockTheSlot() {
        long vehicleId = insertVehicle(insertClient("a@example.com"), VALID_VIN);
        insertAppointment(vehicleId, T10, T11, "CANCELLED");

        assertThatCode(() -> insertAppointment(vehicleId, T10, T11, "SCHEDULED")).doesNotThrowAnyException();
    }

    @Test
    void allowsOverlappingAppointmentsForDifferentVehicles() {
        long clientId = insertClient("a@example.com");
        long first = insertVehicle(clientId, VALID_VIN);
        long second = insertVehicle(clientId, "WVWZZZ1JZXW000002");
        insertAppointment(first, T10, T11, "SCHEDULED");

        assertThatCode(() -> insertAppointment(second, T10, T11, "SCHEDULED")).doesNotThrowAnyException();
    }

    @Test
    void rejectsAppointmentThatEndsBeforeItStarts() {
        long vehicleId = insertVehicle(insertClient("a@example.com"), VALID_VIN);

        assertViolates("ck_appointments_interval", () -> insertAppointment(vehicleId, T11, T10, "SCHEDULED"));
    }

    @Test
    void rejectsUnknownAppointmentStatus() {
        long vehicleId = insertVehicle(insertClient("a@example.com"), VALID_VIN);

        assertViolates("ck_appointments_status", () -> insertAppointment(vehicleId, T10, T11, "PROGRAMAT"));
    }

    @Test
    void completedAppointmentRequiresCompletionTime() {
        long vehicleId = insertVehicle(insertClient("a@example.com"), VALID_VIN);

        assertViolates("ck_appointments_completed_at", () -> insertAppointment(vehicleId, T10, T11, "COMPLETED"));
    }

    // ---------------------------------------------------------------- vehicles & clients

    @ParameterizedTest
    @ValueSource(strings = {"WVWZZZ1JZXW00000", "WVWZZZ1JZXW0000012", "wvwzzz1jzxw000001", "WVWZZZ1JZXW00000O", "IVWZZZ1JZXW000001"})
    void rejectsInvalidVin(String vin) {
        long clientId = insertClient("a@example.com");

        assertThatThrownBy(() -> insertVehicle(clientId, vin)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateVin() {
        long clientId = insertClient("a@example.com");
        insertVehicle(clientId, VALID_VIN);

        assertViolates("uq_vehicles_vin", () -> insertVehicle(clientId, VALID_VIN));
    }

    @Test
    void rejectsDuplicateClientEmailIgnoringCase() {
        insertClient("john.doe@example.com");

        assertViolates("uq_clients_email", () -> insertClient("John.Doe@Example.com"));
    }

    @Test
    void clientWithVehiclesCannotBeDeleted() {
        long clientId = insertClient("a@example.com");
        insertVehicle(clientId, VALID_VIN);

        assertViolates("fk_vehicles_client", () -> jdbc.update("DELETE FROM clients WHERE id = ?", clientId));
    }

    @Test
    void vehicleWithAppointmentsCannotBeDeleted() {
        long vehicleId = insertVehicle(insertClient("a@example.com"), VALID_VIN);
        insertAppointment(vehicleId, T10, T11, "CANCELLED");

        assertViolates("fk_appointments_vehicle", () -> jdbc.update("DELETE FROM vehicles WHERE id = ?", vehicleId));
    }

    // ---------------------------------------------------------------- users, parts & interventions

    @Test
    void rejectsDuplicateUsernameIgnoringCase() {
        insertUser("mechanic", "EMPLOYEE");

        assertViolates("uq_users_username", () -> insertUser("Mechanic", "EMPLOYEE"));
    }

    @Test
    void rejectsUnknownRole() {
        assertViolates("ck_users_role", () -> insertUser("someone", "ANGAJAT"));
    }

    @Test
    void rejectsNegativeStock() {
        long partId = insertPart("FLT-001", 5);

        assertViolates("ck_parts_stock", () -> jdbc.update("UPDATE parts SET stock = stock - 6 WHERE id = ?", partId));
    }

    @Test
    void deletingInterventionRemovesItsPartLines() {
        long vehicleId = insertVehicle(insertClient("a@example.com"), VALID_VIN);
        long appointmentId = insertAppointment(vehicleId, T10, T11, "IN_PROGRESS");
        long interventionId = insertIntervention(appointmentId, insertUser("mechanic", "EMPLOYEE"));
        long partId = insertPart("FLT-001", 5);
        jdbc.update("""
                INSERT INTO intervention_parts (intervention_id, part_id, quantity, unit_price)
                VALUES (?, ?, 2, 49.90)""", interventionId, partId);

        jdbc.update("DELETE FROM interventions WHERE id = ?", interventionId);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM intervention_parts WHERE part_id = ?", Long.class, partId))
                .isZero();
    }

    @Test
    void partUsedByAnInterventionCannotBeDeleted() {
        long vehicleId = insertVehicle(insertClient("a@example.com"), VALID_VIN);
        long appointmentId = insertAppointment(vehicleId, T10, T11, "IN_PROGRESS");
        long interventionId = insertIntervention(appointmentId, insertUser("mechanic", "EMPLOYEE"));
        long partId = insertPart("FLT-001", 5);
        jdbc.update("""
                INSERT INTO intervention_parts (intervention_id, part_id, quantity, unit_price)
                VALUES (?, ?, 1, 49.90)""", interventionId, partId);

        assertViolates("fk_intervention_parts_part", () -> jdbc.update("DELETE FROM parts WHERE id = ?", partId));
    }

    // ---------------------------------------------------------------- helpers

    private void assertViolates(String constraintName, ThrowingCallable statement) {
        assertThatThrownBy(statement)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraintName);
    }

    private long insertClient(String email) {
        return jdbc.queryForObject(
                "INSERT INTO clients (name, phone, email) VALUES ('John Doe', '+40712345678', ?) RETURNING id",
                Long.class, email);
    }

    private long insertVehicle(long clientId, String vin) {
        return jdbc.queryForObject("""
                INSERT INTO vehicles (client_id, vin, license_plate, make, model, manufacture_year)
                VALUES (?, ?, 'B-123-ABC', 'Volkswagen', 'Golf', 2019) RETURNING id""",
                Long.class, clientId, vin);
    }

    private long insertAppointment(long vehicleId, OffsetDateTime start, OffsetDateTime end, String status) {
        return jdbc.queryForObject(
                "INSERT INTO appointments (vehicle_id, start_at, end_at, status) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, vehicleId, start, end, status);
    }

    private long insertUser(String username, String role) {
        return jdbc.queryForObject("""
                INSERT INTO users (username, password_hash, full_name, role)
                VALUES (?, '$2a$10$hash', 'Test User', ?) RETURNING id""",
                Long.class, username, role);
    }

    private long insertPart(String code, int stock) {
        return jdbc.queryForObject(
                "INSERT INTO parts (code, name, unit_price, stock) VALUES (?, 'Oil filter', ?, ?) RETURNING id",
                Long.class, code, new BigDecimal("49.90"), stock);
    }

    private long insertIntervention(long appointmentId, long userId) {
        return jdbc.queryForObject("""
                INSERT INTO interventions (appointment_id, assigned_user_id, description, labor_cost)
                VALUES (?, ?, 'Oil change', 150.00) RETURNING id""",
                Long.class, appointmentId, userId);
    }
}
