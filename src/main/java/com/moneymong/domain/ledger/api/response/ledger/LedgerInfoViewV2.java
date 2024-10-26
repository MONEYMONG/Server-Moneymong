package com.moneymong.domain.ledger.api.response.ledger;

import com.moneymong.domain.ledger.entity.Ledger;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@Builder
@RequiredArgsConstructor
public class LedgerInfoViewV2 {
    final Long id;
    final Integer totalBalance;
    final List<LedgerInfoViewDetail> ledgerInfoViewDetails;
    final long ledgerDetailTotalCount;
    final String agencyName;

    public static LedgerInfoViewV2 from(
            Ledger ledger,
            List<LedgerInfoViewDetail> ledgerInfoViewDetails,
            long ledgerDetailTotalCount,
            String agencyName
    ) {
        return LedgerInfoViewV2.builder()
                .id(ledger.getId())
                .totalBalance(ledger.getTotalBalance())
                .ledgerInfoViewDetails(ledgerInfoViewDetails)
                .ledgerDetailTotalCount(ledgerDetailTotalCount)
                .agencyName(agencyName)
                .build();
    }
}
