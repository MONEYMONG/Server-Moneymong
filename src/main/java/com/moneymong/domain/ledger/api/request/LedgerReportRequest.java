package com.moneymong.domain.ledger.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LedgerReportRequest {

    @NotNull(message = "startYear를 입력해주세요.")
    private Integer startYear;

    @NotNull(message = "startMonth를 입력해주세요.")
    @Min(value = 1, message = "startMonth는 1 이상 입력해주세요.")
    @Max(value = 12, message = "startMonth는 12 이하 입력해주세요.")
    private Integer startMonth;

    @NotNull(message = "endYear를 입력해주세요.")
    private Integer endYear;

    @NotNull(message = "endMonth를 입력해주세요.")
    @Min(value = 1, message = "endMonth는 1 이상 입력해주세요.")
    @Max(value = 12, message = "endMonth는 12 이하 입력해주세요.")
    private Integer endMonth;
}

