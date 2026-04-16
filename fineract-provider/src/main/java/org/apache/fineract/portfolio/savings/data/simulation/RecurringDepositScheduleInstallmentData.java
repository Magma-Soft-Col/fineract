package org.apache.fineract.portfolio.savings.data.simulation;

import jakarta.annotation.PostConstruct;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RecurringDepositScheduleInstallmentData {

    private Integer installmentNumber;
    private LocalDate installmentDate;
    private BigDecimal interestRate;
    private BigDecimal amount;
    private BigDecimal extraAmount;
    private BigDecimal interestAmount;
    private BigDecimal cumulatedAmount;

    @PostConstruct
    public void init() {
        if (this.extraAmount == null) {
            this.extraAmount = BigDecimal.ZERO;
        }
    }

}
