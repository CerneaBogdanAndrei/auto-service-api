package io.github.cerneabogdanandrei.autoservice.common.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseConstraintsTest {

    @Test
    void prefersConstraintNameExtractedByHibernate() {
        var hibernate = new ConstraintViolationException("insert failed",
                new SQLException("ERROR: violates constraint \"from_message\""), "uq_from_hibernate");
        var exception = new DataIntegrityViolationException("could not execute statement", hibernate);

        assertThat(DatabaseConstraints.constraintName(exception)).contains("uq_from_hibernate");
    }

    @Test
    void fallsBackToConstraintNameInDatabaseMessage() {
        var sql = new SQLException(
                "ERROR: conflicting key value violates exclusion constraint \"ex_appointments_vehicle_overlap\"");
        var exception = new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("insert failed", sql, null));

        assertThat(DatabaseConstraints.constraintName(exception)).contains("ex_appointments_vehicle_overlap");
    }

    @Test
    void returnsEmptyWhenNoConstraintIsMentioned() {
        var exception = new DataIntegrityViolationException("value too long for type character varying(20)");

        assertThat(DatabaseConstraints.constraintName(exception)).isEmpty();
    }

    @Test
    void knownConstraintsMapToTheirBusinessError() {
        assertThat(DatabaseConstraints.errorFor("ex_appointments_vehicle_overlap"))
                .hasValueSatisfying(error -> assertThat(error.code()).isEqualTo(ErrorCode.APPOINTMENT_OVERLAP));
        assertThat(DatabaseConstraints.errorFor("uq_clients_email"))
                .hasValueSatisfying(error -> assertThat(error.code()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @Test
    void unknownOrMissingConstraintHasNoSpecificError() {
        assertThat(DatabaseConstraints.errorFor("does_not_exist")).isEmpty();
        assertThat(DatabaseConstraints.errorFor(null)).isEmpty();
    }
}
