package io.github.cerneabogdanandrei.autoservice.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Inserts fixtures straight into the database, so each integration test only goes through
 * the API for the behaviour it is actually testing.
 */
public final class TestData {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final JdbcTemplate jdbc;

    TestData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long client() {
        int n = SEQUENCE.incrementAndGet();
        return client("Client " + n, "client" + n + "@example.com");
    }

    public long client(String name, String email) {
        return jdbc.queryForObject(
                "INSERT INTO clients (name, phone, email) VALUES (?, '+40712345678', ?) RETURNING id",
                Long.class, name, email);
    }

    public long vehicle(long clientId) {
        return vehicle(clientId, "WVWZZZ1JZXW%06d".formatted(SEQUENCE.incrementAndGet()));
    }

    public long vehicle(long clientId, String vin) {
        return jdbc.queryForObject("""
                INSERT INTO vehicles (client_id, vin, license_plate, make, model, manufacture_year)
                VALUES (?, ?, 'B-123-ABC', 'Volkswagen', 'Golf', 2019) RETURNING id""",
                Long.class, clientId, vin);
    }

    public long appointment(long vehicleId, Instant start, Instant end, String status) {
        Instant completedAt = "COMPLETED".equals(status) ? end : null;
        return jdbc.queryForObject("""
                INSERT INTO appointments (vehicle_id, start_at, end_at, status, completed_at)
                VALUES (?, ?::timestamptz, ?::timestamptz, ?, ?::timestamptz) RETURNING id""",
                Long.class, vehicleId, start.toString(), end.toString(), status,
                completedAt == null ? null : completedAt.toString());
    }

    public long part(String code, String unitPrice, int stock) {
        return jdbc.queryForObject(
                "INSERT INTO parts (code, name, unit_price, stock) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, code, "Part " + code, new BigDecimal(unitPrice), stock);
    }

    public long intervention(long appointmentId, long assignedUserId, String laborCost) {
        return jdbc.queryForObject("""
                INSERT INTO interventions (appointment_id, assigned_user_id, description, labor_cost)
                VALUES (?, ?, 'Scheduled maintenance', ?) RETURNING id""",
                Long.class, appointmentId, assignedUserId, new BigDecimal(laborCost));
    }

    public void interventionPart(long interventionId, long partId, int quantity, String unitPrice) {
        jdbc.update("""
                INSERT INTO intervention_parts (intervention_id, part_id, quantity, unit_price)
                VALUES (?, ?, ?, ?)""", interventionId, partId, quantity, new BigDecimal(unitPrice));
    }

    /** Another employee, with the same password as the seeded one ({@code Employee123!}). */
    public long employee(String username) {
        return jdbc.queryForObject("""
                INSERT INTO users (username, password_hash, full_name, role)
                SELECT ?, password_hash, 'Second Mechanic', 'EMPLOYEE' FROM users WHERE username = 'employee'
                RETURNING id""", Long.class, username);
    }

    public int stockOf(long partId) {
        return jdbc.queryForObject("SELECT stock FROM parts WHERE id = ?", Integer.class, partId);
    }

    public long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
