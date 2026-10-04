package com.gymms.service;

import com.gymms.entity.MembershipPlan;
import com.gymms.entity.MembershipStatus;
import com.gymms.exception.InvalidMembershipException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.gymms.service.TestData.TODAY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("MembershipService")
class MembershipServiceTest {

    private MembershipService service;

    @BeforeEach
    void setUp() {
        service = new MembershipService(TestData.CLOCK); // today = 2026-03-15
    }

    @Nested
    @DisplayName("calculateMembershipFee")
    class Fee {
        @Test
        @DisplayName("returns the plan price with 2 decimals")
        void validPlan() {
            assertEquals(new BigDecimal("3000.00"), service.calculateMembershipFee(TestData.standardPlan()));
        }

        @Test
        @DisplayName("rejects a null plan")
        void nullPlan() {
            assertThrows(InvalidMembershipException.class, () -> service.calculateMembershipFee(null));
        }

        @Test
        @DisplayName("rejects an inactive plan")
        void inactivePlan() {
            MembershipPlan inactive = TestData.plan(2L, "Old", 1, "1000", false);
            InvalidMembershipException ex = assertThrows(InvalidMembershipException.class,
                    () -> service.calculateMembershipFee(inactive));
            assertTrue(ex.getMessage().contains("not active"));
        }

        @ParameterizedTest(name = "price {0} is rejected")
        @ValueSource(strings = {"0", "-100"})
        void nonPositivePrice(String price) {
            MembershipPlan plan = TestData.plan(3L, "Bad", 1, price, true);
            assertThrows(InvalidMembershipException.class, () -> service.calculateMembershipFee(plan));
        }

        @Test
        @DisplayName("rejects zero duration")
        void zeroDuration() {
            MembershipPlan plan = TestData.plan(4L, "Bad", 0, "1000", true);
            assertThrows(InvalidMembershipException.class, () -> service.calculateMembershipFee(plan));
        }
    }

    @Nested
    @DisplayName("calculateExpiryDate")
    class Expiry {
        @ParameterizedTest(name = "{0} + {1} month(s) -> {2}")
        @CsvSource({
                "2026-01-01, 1, 2026-01-31",
                "2026-01-01, 3, 2026-03-31",
                "2026-01-01, 6, 2026-06-30",
                "2026-01-01, 12, 2026-12-31",
                "2026-01-31, 1, 2026-02-27",   // month-end: 31 Jan + 1 month = 28 Feb, minus one day
                "2024-01-31, 1, 2024-02-28"    // leap year
        })
        void calculatesLastValidDay(LocalDate start, int months, LocalDate expected) {
            assertEquals(expected, service.calculateExpiryDate(start, months));
        }

        @ParameterizedTest(name = "duration {0} is invalid")
        @ValueSource(ints = {0, -1, -12})
        void invalidDuration(int months) {
            assertThrows(InvalidMembershipException.class, () -> service.calculateExpiryDate(TODAY, months));
        }

        @Test
        @DisplayName("rejects a null start date")
        void nullStart() {
            assertThrows(InvalidMembershipException.class, () -> service.calculateExpiryDate(null, 3));
        }
    }

    @Nested
    @DisplayName("isMembershipActive / isMembershipExpired")
    class ActiveExpired {
        @Test
        @DisplayName("starting today is active")
        void startsToday() {
            assertTrue(service.isMembershipActive(TODAY, TODAY.plusDays(30)));
        }

        @Test
        @DisplayName("expiring today is still active and not expired")
        void expiresToday() {
            assertTrue(service.isMembershipActive(TODAY.minusDays(30), TODAY));
            assertFalse(service.isMembershipExpired(TODAY));
        }

        @Test
        @DisplayName("the day after the end date it is expired and inactive")
        void expiredYesterday() {
            assertFalse(service.isMembershipActive(TODAY.minusDays(30), TODAY.minusDays(1)));
            assertTrue(service.isMembershipExpired(TODAY.minusDays(1)));
        }

        @Test
        @DisplayName("a membership that has not started is not active")
        void notStartedYet() {
            assertFalse(service.isMembershipActive(TODAY.plusDays(1), TODAY.plusDays(31)));
            assertFalse(service.isMembershipExpired(TODAY.plusDays(31)));
        }

        @Test
        @DisplayName("end date before start date is invalid")
        void endBeforeStart() {
            assertThrows(InvalidMembershipException.class, () -> service.isMembershipActive(TODAY, TODAY.minusDays(1)));
        }

        @Test
        @DisplayName("null dates are invalid")
        void nullDates() {
            assertThrows(InvalidMembershipException.class, () -> service.isMembershipActive(null, TODAY));
            assertThrows(InvalidMembershipException.class, () -> service.isMembershipExpired(null));
        }
    }

    @Nested
    @DisplayName("getRemainingDays")
    class Remaining {
        @Test
        void tenDaysLeft() {
            assertEquals(10, service.getRemainingDays(TODAY.plusDays(10)));
        }

        @Test
        @DisplayName("0 on the last day")
        void lastDay() {
            assertEquals(0, service.getRemainingDays(TODAY));
        }

        @Test
        @DisplayName("0 (never negative) once expired")
        void expired() {
            assertEquals(0, service.getRemainingDays(TODAY.minusDays(5)));
        }
    }

    @Nested
    @DisplayName("getStatus")
    class Status {
        @Test
        void upcoming() {
            assertEquals(MembershipStatus.UPCOMING, service.getStatus(TODAY.plusDays(2), TODAY.plusDays(32)));
        }

        @Test
        void active() {
            assertEquals(MembershipStatus.ACTIVE, service.getStatus(TODAY.minusDays(5), TODAY.plusDays(25)));
        }

        @Test
        @DisplayName("exactly 7 days left is EXPIRING_SOON, 8 days is ACTIVE")
        void expiringSoonBoundary() {
            assertEquals(MembershipStatus.EXPIRING_SOON, service.getStatus(TODAY.minusDays(20), TODAY.plusDays(7)));
            assertEquals(MembershipStatus.ACTIVE, service.getStatus(TODAY.minusDays(20), TODAY.plusDays(8)));
        }

        @Test
        @DisplayName("last day is EXPIRING_SOON, day after is EXPIRED")
        void lastDayAndAfter() {
            assertEquals(MembershipStatus.EXPIRING_SOON, service.getStatus(TODAY.minusDays(20), TODAY));
            assertEquals(MembershipStatus.EXPIRED, service.getStatus(TODAY.minusDays(20), TODAY.minusDays(1)));
        }
    }

    @Nested
    @DisplayName("calculateRenewalStart")
    class Renewal {
        @Test
        void noPreviousMembershipStartsToday() {
            assertEquals(TODAY, service.calculateRenewalStart(null));
        }

        @Test
        void lapsedMembershipStartsToday() {
            assertEquals(TODAY, service.calculateRenewalStart(TODAY.minusDays(3)));
        }

        @Test
        void activeMembershipContinuesAfterEndDate() {
            assertEquals(TODAY.plusDays(11), service.calculateRenewalStart(TODAY.plusDays(10)));
        }

        @Test
        void endingTodayContinuesTomorrow() {
            assertEquals(TODAY.plusDays(1), service.calculateRenewalStart(TODAY));
        }
    }

    @Test
    @DisplayName("validateDates accepts a one-day membership (start == end)")
    void oneDayMembershipIsValid() {
        assertDoesNotThrow(() -> service.validateDates(TODAY, TODAY));
    }
}
