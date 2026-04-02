package org.apache.fineract.portfolio.savings.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.calendar.domain.Calendar;
import org.apache.fineract.portfolio.calendar.domain.CalendarEntityType;
import org.apache.fineract.portfolio.calendar.domain.CalendarFrequencyType;
import org.apache.fineract.portfolio.calendar.domain.CalendarInstance;
import org.apache.fineract.portfolio.calendar.domain.CalendarInstanceRepository;
import org.apache.fineract.portfolio.calendar.domain.CalendarType;
import org.apache.fineract.portfolio.calendar.service.CalendarUtils;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.apache.fineract.portfolio.group.domain.Group;
import org.apache.fineract.portfolio.savings.DepositAccountType;
import org.apache.fineract.portfolio.savings.data.DepositAccountDataValidator;
import org.apache.fineract.portfolio.savings.data.simulation.SimulationResultData;
import org.apache.fineract.portfolio.savings.domain.DepositAccountAssembler;
import org.apache.fineract.portfolio.savings.domain.RecurringDepositAccount;
import org.apache.fineract.portfolio.savings.domain.RecurringDepositScheduleInstallment;
import org.apache.fineract.portfolio.savings.domain.interest.PostingPeriod;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Set;

import static org.apache.fineract.portfolio.savings.DepositsApiConstants.isCalendarInheritedParamName;
import static org.apache.fineract.portfolio.savings.DepositsApiConstants.recurringFrequencyParamName;
import static org.apache.fineract.portfolio.savings.DepositsApiConstants.recurringFrequencyTypeParamName;

@RequiredArgsConstructor
@Slf4j
@Service
public class DepositSimulationPlatformService {

    private final PlatformSecurityContext context;
    private final DepositAccountAssembler depositAccountAssembler;
    private final DepositAccountDataValidator depositAccountDataValidator;
    private final ConfigurationDomainService configurationDomainService;
    private final CalendarInstanceRepository calendarInstanceRepository;

    public SimulationResultData simulateRecurringDeposit(final JsonCommand command) {

        this.depositAccountDataValidator.validateRecurringDepositForSubmit(command.json());
        final boolean isPreMatureClosure = false;
        final MathContext mc = MathContext.DECIMAL64;
        final AppUser submittedBy = this.context.authenticatedUser();

        final boolean isSavingsInterestPostingAtCurrentPeriodEnd = this.configurationDomainService.isSavingsInterestPostingAtCurrentPeriodEnd();

        final Integer financialYearBeginningMonth = this.configurationDomainService.retrieveFinancialYearBeginningMonth();

        final RecurringDepositAccount account = (RecurringDepositAccount) this.depositAccountAssembler.assembleFrom(command, submittedBy, DepositAccountType.RECURRING_DEPOSIT);

        final CalendarInstance calendarInstance = getCalendarInstance(command, account);
        final Calendar calendar = calendarInstance.getCalendar();

        final PeriodFrequencyType frequencyType =
                CalendarFrequencyType.from(CalendarUtils.getFrequency(calendar.getRecurrence()));

        int frequency = CalendarUtils.getInterval(calendar.getRecurrence());
        frequency = frequency == -1 ? 1 : frequency;

        account.generateSchedule(frequencyType, frequency, calendar);

        List<PostingPeriod> data = account.updateMaturityDateAndAmount(mc, isPreMatureClosure, isSavingsInterestPostingAtCurrentPeriodEnd, financialYearBeginningMonth);
        account.validateApplicableInterestRate();
        return factoryDataSimulation(data, account);
    }

    private CalendarInstance getCalendarInstance(final JsonCommand command, RecurringDepositAccount account) {
        CalendarInstance calendarInstance = null;
        final boolean isCalendarInherited = command.booleanPrimitiveValueOfParameterNamed(isCalendarInheritedParamName);

        if (isCalendarInherited) {
            Set<Group> groups = account.getClient().getGroups();
            Long groupId = null;
            if (groups.isEmpty()) {
                final String defaultUserMessage = "Client does not belong to group/center. Cannot follow group/center meeting frequency.";
                throw new GeneralPlatformDomainRuleException(
                        "error.msg.recurring.deposit.account.cannot.create.not.belongs.to.any.groups.to.follow.meeting.frequency",
                        defaultUserMessage, account.clientId());
            } else if (groups.size() > 1) {
                final String defaultUserMessage = "Client belongs to more than one group. Cannot support recurring deposit.";
                throw new GeneralPlatformDomainRuleException("error.msg.recurring.deposit.account.cannot.create.belongs.to.multiple.groups",
                        defaultUserMessage, account.clientId());
            } else {
                Group group = groups.iterator().next();
                Group parent = group.getParent();
                Integer entityType = CalendarEntityType.GROUPS.getValue();
                if (parent != null) {
                    groupId = parent.getId();
                    entityType = CalendarEntityType.CENTERS.getValue();
                } else {
                    groupId = group.getId();
                }
                CalendarInstance parentCalendarInstance = this.calendarInstanceRepository
                        .findByEntityIdAndEntityTypeIdAndCalendarTypeId(groupId, entityType, CalendarType.COLLECTION.getValue());
                if (parentCalendarInstance == null) {
                    final String defaultUserMessage = "Meeting frequency is not attached to the Group/Center to which the client belongs to.";
                    throw new GeneralPlatformDomainRuleException(
                            "error.msg.meeting.frequency.not.attached.to.group.to.which.client.belongs.to", defaultUserMessage,
                            account.clientId());
                }
                calendarInstance = CalendarInstance.from(parentCalendarInstance.getCalendar(), account.getId(),
                        CalendarEntityType.SAVINGS.getValue());
            }
        } else {
            LocalDate calendarStartDate = account.depositStartDate();
            final Integer frequencyType = command.integerValueSansLocaleOfParameterNamed(recurringFrequencyTypeParamName);
            final PeriodFrequencyType periodFrequencyType = PeriodFrequencyType.fromInt(frequencyType);
            final Integer frequency = command.integerValueSansLocaleOfParameterNamed(recurringFrequencyParamName);

            final Integer repeatsOnDay = calendarStartDate.get(ChronoField.DAY_OF_WEEK);
            final String title = "recurring_savings_" + account.getId();

            final Calendar calendar = Calendar.createRepeatingCalendar(title, calendarStartDate, CalendarType.COLLECTION.getValue(),
                    CalendarFrequencyType.from(periodFrequencyType), frequency, repeatsOnDay, null);
            calendarInstance = CalendarInstance.from(calendar, account.getId(), CalendarEntityType.SAVINGS.getValue());
        }
        if (calendarInstance == null) {
            final String defaultUserMessage = "No valid recurring details available for recurring depost account creation.";
            throw new GeneralPlatformDomainRuleException(
                    "error.msg.recurring.deposit.account.cannot.create.no.valid.recurring.details.available", defaultUserMessage,
                    account.clientId());
        }
        return calendarInstance;
    }


    private SimulationResultData factoryDataSimulation(List<PostingPeriod> data, RecurringDepositAccount account) {
        SimulationResultData result = new SimulationResultData();
        MonetaryCurrency currency = account.getCurrency();
        BigDecimal cumulativeAmount = BigDecimal.ZERO;
        for (RecurringDepositScheduleInstallment inst : account.getDepositScheduleInstallments()) {
            SimulationResultData.Installment installment = new SimulationResultData.Installment();

            PostingPeriod period = data.stream().filter(item -> item.getPeriodInterval().startDate().equals(inst.dueDate())).findFirst().orElse(null);
            cumulativeAmount = cumulativeAmount.add(period != null ? period.getInterestEarnedRounded().getAmount() : BigDecimal.ZERO);
            installment.setInstallmentNumber(inst.installmentNumber());
            installment.setInstallmentDate(inst.dueDate());
            installment.setAmount(inst.getDepositAmount(currency).minus(inst.getDepositAmountExtra() != null ? inst.getDepositAmountExtra() : BigDecimal.ZERO).getAmount());
            installment.setExtraAmount(inst.getDepositAmountExtra());
            installment.setInterestRate(period != null ? period.getInterestRateAsFraction().multiply(BigDecimal.valueOf(100)): null);
            installment.setInterestAmount(period != null ?  period.getInterestEarnedRounded().getAmount() : null);
            installment.setCumulatedAmount(period != null ? period.getClosingBalance().plus(cumulativeAmount).getAmount() : BigDecimal.ZERO);
            result.getInstallments().add(installment);
        }

        return result;
    }
}
