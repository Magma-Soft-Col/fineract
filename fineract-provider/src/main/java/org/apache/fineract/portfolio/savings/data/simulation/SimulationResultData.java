package org.apache.fineract.portfolio.savings.data.simulation;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class SimulationResultData {

    private List<Installment> installments = new ArrayList<>();

    public SimulationResultData() {
    }

    @Data
    public static class Installment {

        private Integer installmentNumber;
        private LocalDate installmentDate;
        private BigDecimal interestRate;
        private BigDecimal amount;
        private BigDecimal extraAmount;
        private BigDecimal interestAmount;
        private BigDecimal cumulatedAmount;

    }
}
