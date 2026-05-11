package com.koval.trainingplannerbackend.training.history;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;

/**
 * Read-only paginated, week-aligned, filterable browsing of completed sessions.
 * Keeps the windowing math out of the write-path {@link SessionService}.
 */
@Service
public class SessionHistoryQueryService {

    private final MongoTemplate mongoTemplate;

    public SessionHistoryQueryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Filters that may be applied to a window query.
     * All fields are optional; null values mean "no constraint on this dimension".
     */
    public record WindowFilters(
            String sport,
            LocalDate from,
            LocalDate to,
            Integer durationMinSec,
            Integer durationMaxSec,
            Double tssMin,
            Double tssMax) {}

    /**
     * Result of a window query. {@code windowStart} and {@code windowEnd} bracket a
     * Monday-aligned date range (windowEnd exclusive), so the client can group by
     * week without ever seeing a partial week split across pages.
     */
    public record SessionWindowResult(
            List<CompletedSession> sessions,
            LocalDate windowStart,
            LocalDate windowEnd,
            boolean hasMore) {}

    /**
     * List completed sessions in a Monday-aligned window of size {@code weeks} ending
     * just before {@code before}. {@code before} is snapped up to the next Monday if
     * it isn't already one, so weeks are never cut at a page boundary.
     *
     * Pagination contract: to fetch the previous window, pass {@code before =
     * result.windowStart()}. Stop when {@code result.hasMore()} is false.
     */
    public SessionWindowResult listWindow(String userId, LocalDate before, int weeks, WindowFilters f) {
        int clampedWeeks = Math.max(1, Math.min(52, weeks));
        LocalDate windowEnd = snapToNextMondayInclusive(before != null
                ? before
                : LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        LocalDate windowStart = windowEnd.minusWeeks(clampedWeeks);

        LocalDateTime rangeLo = windowStart.atStartOfDay();
        LocalDateTime rangeHi = windowEnd.atStartOfDay();
        if (f != null && f.from() != null) {
            LocalDateTime fromDt = f.from().atStartOfDay();
            if (fromDt.isAfter(rangeLo)) rangeLo = fromDt;
        }
        if (f != null && f.to() != null) {
            LocalDateTime toDt = f.to().plusDays(1).atStartOfDay();
            if (toDt.isBefore(rangeHi)) rangeHi = toDt;
        }
        if (!rangeLo.isBefore(rangeHi)) {
            return new SessionWindowResult(Collections.emptyList(), windowStart, windowEnd,
                    hasMoreBefore(userId, windowStart, f));
        }

        Criteria criteria = Criteria.where("userId").is(userId)
                .and("completedAt").gte(rangeLo).lt(rangeHi);
        applyNonDateFilters(criteria, f);

        Query query = new Query(criteria).with(Sort.by(Sort.Direction.DESC, "completedAt"));
        List<CompletedSession> sessions = mongoTemplate.find(query, CompletedSession.class);

        return new SessionWindowResult(sessions, windowStart, windowEnd,
                hasMoreBefore(userId, windowStart, f));
    }

    private boolean hasMoreBefore(String userId, LocalDate windowStart, WindowFilters f) {
        LocalDateTime hi = windowStart.atStartOfDay();
        if (f != null && f.from() != null && !f.from().atStartOfDay().isBefore(hi)) {
            return false;
        }
        Criteria c = Criteria.where("userId").is(userId).and("completedAt").lt(hi);
        if (f != null && f.from() != null) {
            c = Criteria.where("userId").is(userId)
                    .and("completedAt").gte(f.from().atStartOfDay()).lt(hi);
        }
        applyNonDateFilters(c, f);
        return mongoTemplate.exists(new Query(c), CompletedSession.class);
    }

    private void applyNonDateFilters(Criteria c, WindowFilters f) {
        if (f == null) return;
        if (f.sport() != null && !f.sport().isBlank()) {
            c.and("sportType").is(f.sport());
        }
        if (f.durationMinSec() != null && f.durationMaxSec() != null) {
            c.and("totalDurationSeconds").gte(f.durationMinSec()).lte(f.durationMaxSec());
        } else if (f.durationMinSec() != null) {
            c.and("totalDurationSeconds").gte(f.durationMinSec());
        } else if (f.durationMaxSec() != null) {
            c.and("totalDurationSeconds").lte(f.durationMaxSec());
        }
        if (f.tssMin() != null && f.tssMax() != null) {
            c.and("tss").gte(f.tssMin()).lte(f.tssMax());
        } else if (f.tssMin() != null) {
            c.and("tss").gte(f.tssMin());
        } else if (f.tssMax() != null) {
            c.and("tss").lte(f.tssMax());
        }
    }

    private LocalDate snapToNextMondayInclusive(LocalDate d) {
        int dow = d.getDayOfWeek().getValue();
        return dow == 1 ? d : d.plusDays((8 - dow) % 7);
    }
}
