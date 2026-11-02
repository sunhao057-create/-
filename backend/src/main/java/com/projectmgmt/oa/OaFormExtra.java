package com.projectmgmt.oa;

import java.math.BigDecimal;
import java.util.List;

public record OaFormExtra(
        String belongCompany,
        List<String> sealTypes,
        String copyCount,
        String lendDate,
        String returnDate,
        List<String> qualifyCompanies,
        List<String> qualifyItems,
        Long companyAuditorId,
        Long financeAuditorId,
        Long contractAuditorId,
        String companyAuditorNote,
        String financeAuditorNote,
        String contractAuditorNote,
        String contractDesc,
        String otherCertNote,
        List<Long> companionIds,
        String fromCity,
        String toCity,
        String travelMode,
        String plateNo,
        BigDecimal mileage,
        String returnPlanDate,
        String reviewTime,
        String actualReturnDate,
        BigDecimal durationHours,
        String remark,
        OaLoanMoney loan,
        OaLeaveInfo leave
) {
    public OaFormExtra withSeal(String belongCompany, List<String> sealTypes, String copyCount) {
        return new OaFormExtra(
                belongCompany,
                sealTypes,
                copyCount,
                lendDate(),
                returnDate(),
                qualifyCompanies(),
                qualifyItems(),
                companyAuditorId(),
                financeAuditorId(),
                contractAuditorId(),
                companyAuditorNote(),
                financeAuditorNote(),
                contractAuditorNote(),
                contractDesc(),
                otherCertNote(),
                companionIds(),
                fromCity(),
                toCity(),
                travelMode(),
                plateNo(),
                mileage(),
                returnPlanDate(),
                reviewTime(),
                actualReturnDate(),
                durationHours(),
                remark(),
                loan(),
                leave()
        );
    }

    public OaFormExtra withQualify(
            String lendDate,
            String returnDate,
            List<String> qualifyCompanies,
            List<String> qualifyItems,
            Long companyAuditorId,
            Long financeAuditorId,
            Long contractAuditorId,
            String companyAuditorNote,
            String financeAuditorNote,
            String contractAuditorNote,
            String contractDesc,
            String otherCertNote
    ) {
        return new OaFormExtra(
                belongCompany(),
                sealTypes(),
                copyCount(),
                lendDate,
                returnDate,
                qualifyCompanies,
                qualifyItems,
                companyAuditorId,
                financeAuditorId,
                contractAuditorId,
                companyAuditorNote,
                financeAuditorNote,
                contractAuditorNote,
                contractDesc,
                otherCertNote,
                companionIds(),
                fromCity(),
                toCity(),
                travelMode(),
                plateNo(),
                mileage(),
                returnPlanDate(),
                reviewTime(),
                actualReturnDate(),
                durationHours(),
                remark(),
                loan(),
                leave()
        );
    }

    public OaFormExtra withTrip(
            List<Long> companionIds,
            String fromCity,
            String toCity,
            String travelMode,
            String plateNo,
            BigDecimal mileage,
            String returnPlanDate,
            String reviewTime,
            String actualReturnDate,
            BigDecimal durationHours,
            String remark
    ) {
        return new OaFormExtra(
                belongCompany(),
                sealTypes(),
                copyCount(),
                lendDate(),
                returnDate(),
                qualifyCompanies(),
                qualifyItems(),
                companyAuditorId(),
                financeAuditorId(),
                contractAuditorId(),
                companyAuditorNote(),
                financeAuditorNote(),
                contractAuditorNote(),
                contractDesc(),
                otherCertNote(),
                companionIds,
                fromCity,
                toCity,
                travelMode,
                plateNo,
                mileage,
                returnPlanDate,
                reviewTime,
                actualReturnDate,
                durationHours,
                remark,
                loan(),
                leave()
        );
    }

    public OaFormExtra withLoan(String belongCompany, OaLoanMoney loan) {
        return new OaFormExtra(
                belongCompany,
                sealTypes(),
                copyCount(),
                lendDate(),
                returnDate(),
                qualifyCompanies(),
                qualifyItems(),
                companyAuditorId(),
                financeAuditorId(),
                contractAuditorId(),
                companyAuditorNote(),
                financeAuditorNote(),
                contractAuditorNote(),
                contractDesc(),
                otherCertNote(),
                companionIds(),
                fromCity(),
                toCity(),
                travelMode(),
                plateNo(),
                mileage(),
                returnPlanDate(),
                reviewTime(),
                actualReturnDate(),
                durationHours(),
                remark(),
                loan,
                leave()
        );
    }

    public OaFormExtra withLeave(OaLeaveInfo leave) {
        return new OaFormExtra(
                belongCompany(),
                sealTypes(),
                copyCount(),
                lendDate(),
                returnDate(),
                qualifyCompanies(),
                qualifyItems(),
                companyAuditorId(),
                financeAuditorId(),
                contractAuditorId(),
                companyAuditorNote(),
                financeAuditorNote(),
                contractAuditorNote(),
                contractDesc(),
                otherCertNote(),
                companionIds(),
                fromCity(),
                toCity(),
                travelMode(),
                plateNo(),
                mileage(),
                returnPlanDate(),
                reviewTime(),
                actualReturnDate(),
                durationHours(),
                remark(),
                loan(),
                leave
        );
    }

    public static OaFormExtra empty() {
        return new OaFormExtra(
                null, List.of(), null, null, null, List.of(), List.of(),
                null, null, null, null, null, null, null, null,
                List.of(), null, null, null, null, null, null, null, null, null, null,
                null,
                null
        );
    }
}
