package com.moneymong.domain.ledger.api.response;

import com.moneymong.domain.ledger.entity.LedgerDetail;
import com.moneymong.domain.ledger.entity.LedgerDocument;
import com.moneymong.domain.ledger.entity.enums.FundType;
import com.moneymong.domain.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class LedgerDetailInfoViewV2 {
    private Long id;
    private String storeInfo;
    private Integer amount;
    private FundType fundType;
    private String description;
    private ZonedDateTime paymentDate;
    private List<LedgerDocumentInfoView> documentImageUrls;
    private String authorName;

    public static LedgerDetailInfoViewV2 of(
            final LedgerDetail ledgerDetail,
            final List<LedgerDocument> ledgerDocuments,
            final User user
    ) {
        return LedgerDetailInfoViewV2
                .builder()
                .id(ledgerDetail.getId())
                .storeInfo(ledgerDetail.getStoreInfo())
                .amount(ledgerDetail.getAmount())
                .fundType(ledgerDetail.getFundType())
                .description(ledgerDetail.getDescription())
                .paymentDate(ledgerDetail.getPaymentDate())
                .documentImageUrls(
                        ledgerDocuments
                                .stream()
                                .map(ledgerDocument -> LedgerDocumentInfoView.from(ledgerDocument.getId(),
                                        ledgerDocument.getDocumentImageUrl()))
                                .toList()
                )
                .authorName(user.getNickname())
                .build();
    }
}
