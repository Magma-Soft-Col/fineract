package org.apache.fineract.portfolio.savings.data.simulation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SimulationResultData {

    private List<RecurringDepositScheduleInstallmentData> installments = new ArrayList<>();

    public SimulationResultData() {
    }

}
