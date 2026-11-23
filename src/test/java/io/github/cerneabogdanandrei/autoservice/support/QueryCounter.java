package io.github.cerneabogdanandrei.autoservice.support;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

/**
 * Counts the JDBC statements Hibernate prepares, to prove that list endpoints run a fixed
 * number of queries regardless of the number of rows (no N+1).
 */
public final class QueryCounter {

    private final Statistics statistics;

    QueryCounter(EntityManagerFactory entityManagerFactory) {
        this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        this.statistics.setStatisticsEnabled(true);
    }

    public void reset() {
        statistics.clear();
    }

    public long statements() {
        return statistics.getPrepareStatementCount();
    }
}
