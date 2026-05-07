package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private CompletedSessionRepository sessionRepository;
    @Mock
    private com.koval.trainingplannerbackend.auth.UserRepository userRepository;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(sessionRepository, userRepository);
    }

    private User userWithFtp(int ftp) {
        User u = new User();
        u.setFtp(ftp);
        return u;
    }

    private User userWithThresholdPace(int paceSec) {
        User u = new User();
        u.setFunctionalThresholdPace(paceSec);
        return u;
    }

    private User userWithCss(int cssSec) {
        User u = new User();
        u.setCriticalSwimSpeed(cssSec);
        return u;
    }

    private CompletedSession session(String sport, int durationSec, double avgPower, double avgSpeed) {
        CompletedSession s = new CompletedSession();
        s.setSportType(sport);
        s.setTotalDurationSeconds(durationSec);
        s.setAvgPower(avgPower);
        s.setAvgSpeed(avgSpeed);
        return s;
    }

    @Nested
    class ComputeAndAttachMetrics {

        @Test
        void nullUser_doesNothing() {
            CompletedSession s = session("CYCLING", 3600, 200, 0);
            service.computeAndAttachMetrics(s, null);
            assertNull(s.getTss());
        }

        @Test
        void zeroDuration_doesNothing() {
            CompletedSession s = session("CYCLING", 0, 200, 0);
            service.computeAndAttachMetrics(s, userWithFtp(250));
            assertNull(s.getTss());
        }

        @Test
        void cycling_computesFromFtp() {
            CompletedSession s = session("CYCLING", 3600, 250, 0);
            service.computeAndAttachMetrics(s, userWithFtp(250));

            assertEquals(1.0, s.getIntensityFactor(), 0.001, "IF should be 1.0 at FTP");
            assertEquals(100.0, s.getTss(), 0.1, "TSS should be 100 for 1h at FTP");
        }

        @Test
        void cycling_belowFtp() {
            CompletedSession s = session("CYCLING", 3600, 200, 0);
            service.computeAndAttachMetrics(s, userWithFtp(250));

            assertEquals(0.8, s.getIntensityFactor(), 0.001);
            assertEquals(64.0, s.getTss(), 0.1); // 0.8^2 * 100
        }

        @Test
        void cycling_noFtp_usesRpeFallback() {
            CompletedSession s = session("CYCLING", 3600, 200, 0);
            s.setRpe(7);
            service.computeAndAttachMetrics(s, userWithFtp(0));

            assertEquals(0.7, s.getIntensityFactor(), 0.001);
            assertNotNull(s.getTss());
        }

        @Test
        void cycling_noFtp_noRpe_leavesNull() {
            CompletedSession s = session("CYCLING", 3600, 200, 0);
            service.computeAndAttachMetrics(s, userWithFtp(0));
            assertNull(s.getTss());
        }

        @Test
        void running_computesFromThresholdPace() {
            // ThresholdPace=300 sec/km → threshold speed = 1000/300 = 3.333 m/s
            // avgSpeed = 3.333 → IF = 1.0
            CompletedSession s = session("RUNNING", 3600, 0, 1000.0 / 300.0);
            service.computeAndAttachMetrics(s, userWithThresholdPace(300));

            assertEquals(1.0, s.getIntensityFactor(), 0.01);
            assertEquals(100.0, s.getTss(), 1.0);
        }

        @Test
        void swimming_computesFromCss() {
            // CSS=90 sec/100m → threshold speed = 100/90 = 1.111 m/s
            CompletedSession s = session("SWIMMING", 3600, 0, 100.0 / 90.0);
            service.computeAndAttachMetrics(s, userWithCss(90));

            assertEquals(1.0, s.getIntensityFactor(), 0.01);
        }

        @Test
        void brick_triesPowerFirst_thenPace() {
            User u = new User();
            u.setFtp(250);
            u.setFunctionalThresholdPace(300);

            // Has power → uses cycling formula
            CompletedSession s = session("BRICK", 3600, 200, 3.0);
            service.computeAndAttachMetrics(s, u);
            assertEquals(0.8, s.getIntensityFactor(), 0.001);
        }

        @Test
        void brick_fallsToPace_whenNoPower() {
            User u = new User();
            u.setFtp(250);
            u.setFunctionalThresholdPace(300);

            CompletedSession s = session("BRICK", 3600, 0, 1000.0 / 300.0);
            service.computeAndAttachMetrics(s, u);
            assertEquals(1.0, s.getIntensityFactor(), 0.01);
        }
    }

    @Nested
    class ComputeBlockDistances {

        @Test
        void estimatesDistanceFromSpeed() {
            CompletedSession s = new CompletedSession();
            s.setAvgSpeed(10.0); // 10 m/s
            s.setBlockSummaries(List.of(
                    new CompletedSession.BlockSummary("WU", "WARMUP", 600, 100, 100, 80, 120, null)
            ));

            service.computeBlockDistances(s);

            assertEquals(6000.0, s.getBlockSummaries().get(0).distanceMeters(), 0.1);
        }

        @Test
        void preservesExistingDistance() {
            CompletedSession s = new CompletedSession();
            s.setAvgSpeed(10.0);
            s.setBlockSummaries(List.of(
                    new CompletedSession.BlockSummary("WU", "WARMUP", 600, 100, 100, 80, 120, 5000.0)
            ));

            service.computeBlockDistances(s);

            assertEquals(5000.0, s.getBlockSummaries().get(0).distanceMeters(), 0.1,
                    "Should not overwrite existing distance");
        }

        @Test
        void noBlocks_doesNothing() {
            CompletedSession s = new CompletedSession();
            s.setAvgSpeed(10.0);
            s.setBlockSummaries(null);

            service.computeBlockDistances(s); // should not throw
        }

        @Test
        void zeroSpeed_doesNothing() {
            CompletedSession s = new CompletedSession();
            s.setAvgSpeed(0);
            s.setBlockSummaries(List.of(
                    new CompletedSession.BlockSummary("WU", "WARMUP", 600, 100, 100, 80, 120, null)
            ));

            service.computeBlockDistances(s);
            assertNull(s.getBlockSummaries().get(0).distanceMeters());
        }
    }

    @Nested
    class GeneratePmc {

        @Test
        void emptyHistory_producesDecayOnlyPoints() {
            when(sessionRepository.findByUserIdAndCompletedAtGreaterThanEqualOrderByCompletedAtAsc(eq("u1"), any())).thenReturn(List.of());

            LocalDate from = LocalDate.of(2024, 1, 1);
            LocalDate to = LocalDate.of(2024, 1, 3);
            List<AnalyticsService.PmcDataPoint> pmc = service.generatePmc("u1", from, to);

            assertEquals(3, pmc.size());
            // No TSS → all values should be zero or near-zero
            for (var p : pmc) {
                assertEquals(0, p.dailyTss(), 0.001);
                assertEquals(0, p.ctl(), 0.1);
                assertEquals(0, p.atl(), 0.1);
            }
        }

        @Test
        void singleSession_ctlGrowsSlowly_atlGrowsFast() {
            CompletedSession s = session("CYCLING", 3600, 250, 0);
            s.setTss(100.0);
            s.setCompletedAt(LocalDateTime.of(2024, 1, 1, 10, 0));

            when(sessionRepository.findByUserIdAndCompletedAtGreaterThanEqualOrderByCompletedAtAsc(eq("u1"), any())).thenReturn(List.of(s));

            LocalDate from = LocalDate.of(2024, 1, 1);
            LocalDate to = LocalDate.of(2024, 1, 2);
            List<AnalyticsService.PmcDataPoint> pmc = service.generatePmc("u1", from, to);

            assertEquals(2, pmc.size());

            // Day 1 (session day): ATL should respond more than CTL
            var day1 = pmc.get(0);
            assertTrue(day1.atl() > day1.ctl(),
                    "ATL (%.1f) should be higher than CTL (%.1f) after single session"
                            .formatted(day1.atl(), day1.ctl()));

            // TSB = CTL - ATL should be negative after training
            assertTrue(day1.tsb() < 0, "TSB should be negative after a training day");
        }

        @Test
        void swimmingSessions_excludedFromPmc() {
            CompletedSession swim = session("SWIMMING", 3600, 0, 1.0);
            swim.setTss(80.0);
            swim.setCompletedAt(LocalDateTime.of(2024, 1, 1, 10, 0));

            when(sessionRepository.findByUserIdAndCompletedAtGreaterThanEqualOrderByCompletedAtAsc(eq("u1"), any())).thenReturn(List.of(swim));

            var pmc = service.generatePmc("u1", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1));

            assertEquals(0, pmc.get(0).dailyTss(), 0.001, "Swimming TSS should be excluded from PMC");
        }

        @Test
        void restDay_emaDecays() {
            CompletedSession s = session("CYCLING", 3600, 250, 0);
            s.setTss(100.0);
            s.setCompletedAt(LocalDateTime.of(2024, 1, 1, 10, 0));

            when(sessionRepository.findByUserIdAndCompletedAtGreaterThanEqualOrderByCompletedAtAsc(eq("u1"), any())).thenReturn(List.of(s));

            var pmc = service.generatePmc("u1", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 5));

            // After day 1 (training), subsequent rest days should show decaying ATL
            double atlDay1 = pmc.get(0).atl();
            double atlDay5 = pmc.get(4).atl();
            assertTrue(atlDay5 < atlDay1,
                    "ATL should decay on rest days: day1=%.1f, day5=%.1f".formatted(atlDay1, atlDay5));
        }
    }
}
