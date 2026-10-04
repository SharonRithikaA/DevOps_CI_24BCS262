package com.gymms.service;

import com.gymms.entity.MembershipPlan;
import com.gymms.entity.MembershipStatus;
import com.gymms.exception.InvalidMembershipException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pure membership business rules (fees, expiry dates, active/expired checks).
 * <p>
 * The "current date" always comes from the injected {@link Clock}, so every method is deterministic
 * and can be unit-tested with {@code Clock.fixed(...)}. A membership is valid from its start date
 * to its end date, both inclusive.
 */
@Service
public class MembershipService {

    /** A membership with this many days (or fewer) left is reported as EXPIRING_SOON. */
    public static final int EXPIRING_SOON_DAYS = 7;

    private final Clock clock;

    public MembershipService(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Fee for a plan. Rejects missing, inactive or mis-configured plans. */
    public BigDecimal calculateMembershipFee(MembershipPlan plan) {
        if (plan == null) {
            throw new InvalidMembershipException("Membership plan is required");
        }
        if (!plan.isActive()) {
            throw new InvalidMembershipException("Membership plan '" + plan.getName() + "' is not active");
        }
        if (plan.getDurationMonths() <= 0) {
            throw new InvalidMembershipException("Membership duration must be at least 1 month");
        }
        if (plan.getPrice() == null || plan.getPrice().signum() <= 0) {
            throw new InvalidMembershipException("Membership price must be greater than zero");
        }
        return plan.getPrice().setScale(2, RoundingMode.HALF_UP);
    }

    /** Last valid day of a membership: start + duration months - 1 day (1 Jan + 1 month = 31 Jan). */
    public LocalDate calculateExpiryDate(LocalDate startDate, int durationMonths) {
        if (startDate == null) {
            throw new InvalidMembershipException("Membership start date is required");
        }
        if (durationMonths <= 0) {
            throw new InvalidMembershipException("Membership duration must be at least 1 month");
        }
        return startDate.plusMonths(durationMonths).minusDays(1);
    }

    /** Throws when a date is missing or the end date is before the start date. */
    public void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new InvalidMembershipException("Membership start and end dates are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new InvalidMembershipException("Membership end date cannot be before the start date");
        }
    }

    /** True when today is inside [start, end] (both inclusive). */
    public boolean isMembershipActive(LocalDate startDate, LocalDate endDate) {
        validateDates(startDate, endDate);
        LocalDate today = today();
        return !today.isBefore(startDate) && !today.isAfter(endDate);
    }

    /** True only after the end date has passed; a membership ending today is not expired yet. */
    public boolean isMembershipExpired(LocalDate endDate) {
        if (endDate == null) {
            throw new InvalidMembershipException("Membership end date is required");
        }
        return today().isAfter(endDate);
    }

    /** Whole days from today until the end date: 0 on the last day and 0 for expired memberships. */
    public long getRemainingDays(LocalDate endDate) {
        if (isMembershipExpired(endDate)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(today(), endDate);
    }

    public MembershipStatus getStatus(LocalDate startDate, LocalDate endDate) {
        validateDates(startDate, endDate);
        if (today().isBefore(startDate)) {
            return MembershipStatus.UPCOMING;
        }
        if (isMembershipExpired(endDate)) {
            return MembershipStatus.EXPIRED;
        }
        return getRemainingDays(endDate) <= EXPIRING_SOON_DAYS ? MembershipStatus.EXPIRING_SOON : MembershipStatus.ACTIVE;
    }

    /** A renewal continues the day after the current end date, or starts today if it already lapsed. */
    public LocalDate calculateRenewalStart(LocalDate currentEndDate) {
        if (currentEndDate == null || isMembershipExpired(currentEndDate)) {
            return today();
        }
        return currentEndDate.plusDays(1);
    }
}
