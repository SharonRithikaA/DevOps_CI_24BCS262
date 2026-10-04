package com.gymms.service;

import com.gymms.dto.TrainerDto;
import com.gymms.entity.Member;
import com.gymms.entity.Trainer;
import com.gymms.exception.DuplicateResourceException;
import com.gymms.exception.InvalidMembershipException;
import com.gymms.exception.ResourceNotFoundException;
import com.gymms.repository.MemberRepository;
import com.gymms.repository.TrainerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class TrainerService {

    private final TrainerRepository trainerRepository;
    private final MemberRepository memberRepository;

    public TrainerService(TrainerRepository trainerRepository, MemberRepository memberRepository) {
        this.trainerRepository = trainerRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public List<TrainerDto.Response> list() {
        return trainerRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TrainerDto.Response get(Long id) {
        return toResponse(find(id));
    }

    public TrainerDto.Response create(TrainerDto.Request r) {
        if (trainerRepository.existsByEmailIgnoreCase(r.email().trim())) {
            throw new DuplicateResourceException("A trainer with e-mail " + r.email().trim() + " already exists");
        }
        Trainer trainer = new Trainer();
        apply(trainer, r);
        return toResponse(trainerRepository.save(trainer));
    }

    public TrainerDto.Response update(Long id, TrainerDto.Request r) {
        Trainer trainer = find(id);
        if (trainerRepository.existsByEmailIgnoreCaseAndIdNot(r.email().trim(), id)) {
            throw new DuplicateResourceException("A trainer with e-mail " + r.email().trim() + " already exists");
        }
        apply(trainer, r);
        return toResponse(trainerRepository.save(trainer));
    }

    /** Deleting a trainer un-assigns their members; the members themselves are kept. */
    public void delete(Long id) {
        Trainer trainer = find(id);
        List<Member> assigned = memberRepository.findByTrainerId(id);
        assigned.forEach(m -> m.setTrainer(null));
        memberRepository.saveAll(assigned);
        trainerRepository.delete(trainer);
    }

    /** Replaces the trainer's member list with exactly the given member ids. */
    public TrainerDto.Response assignMembers(Long trainerId, List<Long> memberIds) {
        Trainer trainer = find(trainerId);
        Set<Long> wanted = new HashSet<>(memberIds);
        if (!wanted.isEmpty() && !trainer.isActive()) {
            throw new InvalidMembershipException("Trainer " + trainer.getName() + " is not active");
        }
        List<Member> members = memberRepository.findAllById(wanted);
        if (members.size() != wanted.size()) {
            throw new ResourceNotFoundException("One or more members were not found");
        }

        List<Member> changed = new ArrayList<>();
        for (Member m : memberRepository.findByTrainerId(trainerId)) {
            if (!wanted.contains(m.getId())) {
                m.setTrainer(null);
                changed.add(m);
            }
        }
        for (Member m : members) {
            m.setTrainer(trainer);
            changed.add(m);
        }
        memberRepository.saveAll(changed);
        return toResponse(trainer);
    }

    private Trainer find(Long id) {
        return trainerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trainer not found with id " + id));
    }

    private void apply(Trainer t, TrainerDto.Request r) {
        t.setName(r.name().trim());
        t.setEmail(r.email().trim().toLowerCase());
        t.setPhone(r.phone().trim());
        t.setSpecialization(r.specialization() == null ? null : r.specialization().trim());
        t.setExperienceYears(r.experienceYears());
        t.setAvailability(r.availability() == null ? null : r.availability().trim());
        t.setActive(r.active() == null || r.active());
    }

    private TrainerDto.Response toResponse(Trainer t) {
        List<TrainerDto.MemberSummary> members = memberRepository.findByTrainerId(t.getId()).stream()
                .map(m -> new TrainerDto.MemberSummary(m.getId(), m.getMemberCode(), m.getFullName()))
                .toList();
        return new TrainerDto.Response(t.getId(), t.getName(), t.getEmail(), t.getPhone(), t.getSpecialization(),
                t.getExperienceYears(), t.getAvailability(), t.isActive(), members.size(), members);
    }
}
