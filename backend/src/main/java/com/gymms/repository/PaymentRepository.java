package com.gymms.repository;

import com.gymms.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findAllByOrderByPaymentDateDescIdDesc();
    List<Payment> findByMemberIdOrderByPaymentDateDescIdDesc(Long memberId);
    boolean existsByPlanId(Long planId);
    boolean existsByTransactionReference(String transactionReference);
}
