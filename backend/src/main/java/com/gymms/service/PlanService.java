package com.gymms.service;

import com.gymms.dto.PlanDto;
import com.gymms.entity.MembershipPlan;
import com.gymms.exception.DuplicateResourceException;
import com.gymms.exception.InvalidMembershipException;
import com.gymms.exception.ResourceInUseException;
import com.gymms.exception.ResourceNotFoundException;
import com.gymms.repository.MemberRepository;
import com.gymms.repository.MembershipPlanRepository;
import com.gymms.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PlanService {

    private final MembershipPlanRepository planRepository;
    private final MemberRepository memberRepository;
    private final PaymentRepository paymentRepository;

    public PlanService(MembershipPlanRepository planRepository, MemberRepository memberRepository,
                       PaymentRepository paymentRepository) {
        this.planRepository = planRepository;
        this.memberRepository = memberRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public List<PlanDto.Response> list() {
        return planRepository.findAllByOrderByDurationMonthsAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PlanDto.Response get(Long id) {
        return toResponse(find(id));
    }

    public PlanDto.Response create(PlanDto.Request r) {
        String name = r.name().trim();
        if (planRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("A plan named '" + name + "' already exists");
        }
        MembershipPlan plan = new MembershipPlan();
        apply(plan, r);
        return toResponse(planRepository.save(plan));
    }

    public PlanDto.Response update(Long id, PlanDto.Request r) {
        MembershipPlan plan = find(id);
        if (planRepository.existsByNameIgnoreCaseAndIdNot(r.name().trim(), id)) {
            throw new DuplicateResourceException("A plan named '" + r.name().trim() + "' already exists");
        }
        apply(plan, r);
        return toResponse(planRepository.save(plan));
    }

    public void delete(Long id) {
        MembershipPlan plan = find(id);
        if (memberRepository.countByPlanId(id) > 0 || paymentRepository.existsByPlanId(id)) {
            throw new ResourceInUseException("Plan '" + plan.getName()
                    + "' is used by members or payments and cannot be deleted. Mark it inactive instead.");
        }
        planRepository.delete(plan);
    }

    private MembershipPlan find(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membership plan not found with id " + id));
    }

    private void apply(MembershipPlan plan, PlanDto.Request r) {
        if (r.durationMonths() == null || r.durationMonths() <= 0) {
            throw new InvalidMembershipException("Membership duration must be at least 1 month");
        }
        if (r.price() == null || r.price().signum() <= 0) {
            throw new InvalidMembershipException("Membership price must be greater than zero");
        }
        plan.setName(r.name().trim());
        plan.setDurationMonths(r.durationMonths());
        plan.setPrice(r.price());
        plan.setDescription(r.description() == null ? null : r.description().trim());
        plan.setActive(r.active() == null || r.active());
    }

    private PlanDto.Response toResponse(MembershipPlan p) {
        return new PlanDto.Response(p.getId(), p.getName(), p.getDurationMonths(), p.getPrice(),
                p.getDescription(), p.isActive(), memberRepository.countByPlanId(p.getId()));
    }
}
