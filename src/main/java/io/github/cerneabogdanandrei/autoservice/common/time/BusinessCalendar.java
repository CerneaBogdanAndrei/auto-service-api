package io.github.cerneabogdanandrei.autoservice.common.time;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Translates calendar dates used in the API (filters, reports) into instants, using the time
 * zone the shop operates in. Timestamps are stored in UTC; "a day" or "a month" always means
 * the shop's local day or month.
 */
@Component
public class BusinessCalendar {

    private final ZoneId zone;

    public BusinessCalendar(@Value("${app.business-time-zone}") ZoneId zone) {
        this.zone = zone;
    }

    public ZoneId zone() {
        return zone;
    }

    /** First instant of the given local date. */
    public Instant startOf(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }

    /** First instant after the given local date (exclusive end of a whole-day range). */
    public Instant endOf(LocalDate date) {
        return startOf(date.plusDays(1));
    }

    public YearMonth monthOf(Instant instant) {
        return YearMonth.from(instant.atZone(zone));
    }
}
