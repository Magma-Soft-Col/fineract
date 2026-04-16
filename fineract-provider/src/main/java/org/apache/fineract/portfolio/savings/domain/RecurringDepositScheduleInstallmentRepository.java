package org.apache.fineract.portfolio.savings.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecurringDepositScheduleInstallmentRepository extends JpaRepository<RecurringDepositScheduleInstallment, Long> {

    @Query(value = "SELECT * FROM m_mandatory_savings_schedule  WHERE savings_account_id = ?1 ORDER BY installment ASC", nativeQuery = true)
    List<RecurringDepositScheduleInstallment> retrieveAllByAccountId(Long accountId);
}
