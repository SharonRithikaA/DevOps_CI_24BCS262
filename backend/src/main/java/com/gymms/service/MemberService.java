package com.gymms.service;

import com.gymms.dto.MemberDto;
import com.gymms.entity.Member;
import com.gymms.entity.MembershipPlan;
import com.gymms.entity.MembershipStatus;
import com.gymms.entity.PaymentStatus;
import com.gymms.entity.Trainer;
import com.gymms.exception.DuplicateResourceException;
import com.gymms.exception.InvalidMembershipException;
import com.gymms.exception.MemberNotFoundException;
import com.gymms.exception.ResourceNotFoundException;
import com.gymms.repository.MemberRepository;
import com.gymms.repository.MembershipPlanRepository;
import com.gymms.repository.TrainerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Registration, update, lookup, search and deletion of gym members. */
@Service
@Transactional
public class MemberService {

    private final MemberRepository memberRepository;
    private final MembershipPlanRepository planRepository;
    private final TrainerRepository trainerRepository;
    private final MembershipService membershipService;

    public MemberService(MemberRepository memberRepository, MembershipPlanRepository planRepository,
                         TrainerRepository trainerRepository, MembershipService membershipService) {
        this.memberRepository = memberRepository;
        this.planRepository = planRepository;
        this.trainerRepository = trainerRepository;
        this.membershipService = membershipService;
    }

    public MemberDto.Response registerMember(MemberDto.Request request) {
        assertUnique(request, null);
        Member member = new Member();
        applyRequest(member, request);
        member.setJoinDate(membershipService.today());
        member.setPaymentStatus(PaymentStatus.PENDING);

        Member saved = memberRepository.save(member);
        if (saved.getMemberCode() == null) {
            saved.setMemberCode(String.format("GYM-%04d", saved.getId()));
            saved = memberRepository.save(saved);
        }
        return toResponse(saved);
    }

    public MemberDto.Response updateMember(Long id, MemberDto.Request request) {
        Member member = findMemberEntity(id);
        assertUnique(request, id);
        applyRequest(member, request);
        return toResponse(memberRepository.save(member));
    }

    @Transactional(readOnly = true)
    public MemberDto.Response getMember(Long id) {
        return toResponse(findMemberEntity(id));
    }

    /** Shared lookup used by other services; throws MemberNotFoundException when missing. */
    @Transactional(readOnly = true)
    public Member findMemberEntity(Long id) {
        return memberRepository.findById(id).orElseThrow(() -> new MemberNotFoundException(id));
    }

    public void deleteMember(Long id) {
        memberRepository.delete(findMemberEntity(id));
    }

    /**
     * Search, filter and sort members.
     *
     * @param query         matches name, member id, phone or e-mail (case-insensitive, partial)
     * @param status        ACTIVE (includes expiring soon), EXPIRING_SOON, EXPIRED, UPCOMING or blank for all
     * @param planId        optional membership plan filter
     * @param paymentStatus optional payment status filter
     * @param sortBy        name | memberCode | joinDate | expiry (default: newest first)
     * @param direction     asc | desc
     */
    @Transactional(readOnly = true)
    public List<MemberDto.Response> search(String query, String status, Long planId, PaymentStatus paymentStatus,
                                           String sortBy, String direction) {
        String q = query == null ? "" : query.trim().toLowerCase();
        Comparator<Member> comparator = comparatorFor(sortBy);
        if ("desc".equalsIgnoreCase(direction) || (sortBy == null || sortBy.isBlank())) {
            comparator = comparator.reversed();
        }
        return memberRepository.findAll().stream()
                .filter(m -> q.isEmpty() || matchesQuery(m, q))
                .filter(m -> planId == null || m.getPlan().getId().equals(planId))
                .filter(m -> paymentStatus == null || m.getPaymentStatus() == paymentStatus)
                .filter(m -> matchesStatus(statusOf(m), status))
                .sorted(comparator)
                .map(this::toResponse)
                .toList();
    }

    public MembershipStatus statusOf(Member m) {
        return membershipService.getStatus(m.getMembershipStartDate(), m.getMembershipEndDate());
    }

    public MemberDto.Response toResponse(Member m) {
        MembershipStatus status = statusOf(m);
        Trainer trainer = m.getTrainer();
        return new MemberDto.Response(m.getId(), m.getMemberCode(), m.getFullName(), m.getEmail(), m.getPhone(),
                m.getDateOfBirth(), m.getGender(), m.getAddress(), m.getEmergencyContact(), m.getJoinDate(),
                m.getPlan().getId(), m.getPlan().getName(), m.getMembershipStartDate(), m.getMembershipEndDate(),
                status, membershipService.getRemainingDays(m.getMembershipEndDate()),
                trainer == null ? null : trainer.getId(), trainer == null ? null : trainer.getName(),
                m.getPaymentStatus());
    }

    // ---------------------------------------------------------------- helpers

    private void applyRequest(Member member, MemberDto.Request r) {
        LocalDate today = membershipService.today();
        if (r.dateOfBirth() == null || !r.dateOfBirth().isBefore(today)) {
            throw new IllegalArgumentException("Date of birth must be in the past");
        }

        MembershipPlan plan = planRepository.findById(r.planId())
                .orElseThrow(() -> new ResourceNotFoundException("Membership plan not found with id " + r.planId()));
        boolean planUnchanged = member.getPlan() != null && member.getPlan().getId().equals(plan.getId());
        if (!plan.isActive() && !planUnchanged) {
            throw new InvalidMembershipException("Membership plan '" + plan.getName() + "' is not active");
        }

        LocalDate start = r.membershipStartDate() != null ? r.membershipStartDate()
                : (member.getMembershipStartDate() != null ? member.getMembershipStartDate() : today);
        LocalDate end = r.membershipEndDate() != null ? r.membershipEndDate()
                : membershipService.calculateExpiryDate(start, plan.getDurationMonths());
        membershipService.validateDates(start, end);

        Trainer trainer = null;
        if (r.trainerId() != null) {
            trainer = trainerRepository.findById(r.trainerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Trainer not found with id " + r.trainerId()));
        }

        member.setFullName(r.fullName().trim());
        member.setEmail(r.email().trim().toLowerCase());
        member.setPhone(r.phone().trim());
        member.setDateOfBirth(r.dateOfBirth());
        member.setGender(r.gender());
        member.setAddress(r.address() == null ? null : r.address().trim());
        member.setEmergencyContact(r.emergencyContact().trim());
        member.setPlan(plan);
        member.setMembershipStartDate(start);
        member.setMembershipEndDate(end);
        member.setTrainer(trainer);
    }

    private void assertUnique(MemberDto.Request r, Long excludeId) {
        String email = r.email().trim();
        String phone = r.phone().trim();
        boolean emailTaken = excludeId == null ? memberRepository.existsByEmailIgnoreCase(email)
                : memberRepository.existsByEmailIgnoreCaseAndIdNot(email, excludeId);
        if (emailTaken) {
            throw new DuplicateResourceException("A member with e-mail " + email + " already exists");
        }
        boolean phoneTaken = excludeId == null ? memberRepository.existsByPhone(phone)
                : memberRepository.existsByPhoneAndIdNot(phone, excludeId);
        if (phoneTaken) {
            throw new DuplicateResourceException("A member with phone " + phone + " already exists");
        }
    }

    private boolean matchesQuery(Member m, String q) {
        return contains(m.getFullName(), q) || contains(m.getMemberCode(), q)
                || contains(m.getPhone(), q) || contains(m.getEmail(), q);
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase().contains(q);
    }

    static boolean matchesStatus(MembershipStatus actual, String filter) {
        if (filter == null || filter.isBlank() || "ALL".equalsIgnoreCase(filter)) {
            return true;
        }
        if ("ACTIVE".equalsIgnoreCase(filter)) {
            return actual == MembershipStatus.ACTIVE || actual == MembershipStatus.EXPIRING_SOON;
        }
        return actual.name().equalsIgnoreCase(filter);
    }

    private static Comparator<Member> comparatorFor(String sortBy) {
        if (sortBy == null) {
            return Comparator.comparing(Member::getId);
        }
        return switch (sortBy) {
            case "name" -> Comparator.comparing(m -> m.getFullName().toLowerCase());
            case "memberCode" -> Comparator.comparing(Member::getMemberCode, Comparator.nullsLast(Comparator.naturalOrder()));
            case "joinDate" -> Comparator.comparing(Member::getJoinDate);
            case "expiry" -> Comparator.comparing(Member::getMembershipEndDate);
            default -> Comparator.comparing(Member::getId);
        };
    }
}
