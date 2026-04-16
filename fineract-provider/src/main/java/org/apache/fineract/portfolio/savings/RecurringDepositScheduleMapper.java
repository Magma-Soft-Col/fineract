package org.apache.fineract.portfolio.savings;

import org.apache.fineract.portfolio.savings.data.simulation.RecurringDepositScheduleInstallmentData;
import org.apache.fineract.portfolio.savings.domain.RecurringDepositScheduleInstallment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RecurringDepositScheduleMapper {

    @Mapping(target = "installmentNumber", source = "installmentNumber")
    @Mapping(target = "installmentDate", source = "dueDate")
    @Mapping(target = "amount", source = "depositAmount")
    @Mapping(target = "extraAmount", source = "depositAmountExtra")
    @Mapping(target = "interestRate", source = "interestRate")
    @Mapping(target = "interestAmount", source = "interestAmount")
    @Mapping(target = "cumulatedAmount", ignore = true)
    RecurringDepositScheduleInstallmentData map(RecurringDepositScheduleInstallment source);

    List<RecurringDepositScheduleInstallmentData> map(List<RecurringDepositScheduleInstallment> source);

}
