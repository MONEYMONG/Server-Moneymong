package com.moneymong.domain.ledger.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class UpdateLedgerRequestV2 {
    @NotBlank(message = "storeInfo를 입력해주세요.")
    @Size(min = 1, max = 20, message = "storeInfo 1자 - 20자 입력해주세요.")
    private String storeInfo;

    @NotNull(message = "amount를 입력해주세요.")
    private Integer amount;

    @NotBlank(message = "description를 입력해주세요.")
    private String description;

    @NotNull(message = "paymentDate를 입력해주세요.")
    private ZonedDateTime paymentDate;

    @Size(max = 12, message = "영수증 12개 이하 입력해주세요.")
    private List<String> receiptImageUrls;

    @Size(max = 12, message = "증빙 자료 12개 이하 입력해주세요.")
    private List<String> documentImageUrls;
}
