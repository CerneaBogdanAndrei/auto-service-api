package io.github.cerneabogdanandrei.autoservice.common.time;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessCalendarTest {

    private final BusinessCalendar calendar = new BusinessCalendar(ZoneId.of("Europe/Bucharest"));

    @Test
    void dayBoundariesFollowTheShopTimeZone() {
        // Bucharest is UTC+2 in winter and UTC+3 in summer
        assertThat(calendar.startOf(LocalDate.of(2030, 1, 15))).isEqualTo(Instant.parse("2030-01-14T22:00:00Z"));
        assertThat(calendar.endOf(LocalDate.of(2030, 1, 15))).isEqualTo(Instant.parse("2030-01-15T22:00:00Z"));
        assertThat(calendar.startOf(LocalDate.of(2030, 7, 15))).isEqualTo(Instant.parse("2030-07-14T21:00:00Z"));
    }

    @Test
    void monthIsTheLocalMonth() {
        // 22:30 UTC on 31 January is already 1 February in Bucharest
        assertThat(calendar.monthOf(Instant.parse("2030-01-31T22:30:00Z"))).isEqualTo(YearMonth.of(2030, 2));
        assertThat(calendar.zone()).isEqualTo(ZoneId.of("Europe/Bucharest"));
    }
}
