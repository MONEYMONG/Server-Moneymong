package com.moneymong.domain.ledger.api;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneymong.domain.ledger.service.dto.response.LedgerDetailInfoView;
import com.moneymong.domain.ledger.service.dto.response.LedgerReceiptInfoView;
import com.moneymong.domain.ledger.entity.enums.FundType;
import com.moneymong.domain.ledger.service.manager.LedgerDetailService;
import com.moneymong.domain.ledger.service.reader.LedgerDetailReader;
import com.moneymong.global.exception.custom.InvalidAccessException;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.support.ControllerTestSupport;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class LedgerDetailControllerTest extends ControllerTestSupport {

    private static final Long DETAIL_ID = 30L;

    @Mock
    private LedgerDetailReader ledgerDetailReader;

    @Mock
    private LedgerDetailService ledgerDetailService;

    @Override
    protected Object controller() {
        return new LedgerDetailController(ledgerDetailReader, ledgerDetailService);
    }

    @Test
    @DisplayName("장부 상세 내역을 조회한다.")
    void getLedgerDetailInfo() throws Exception {
        LedgerDetailInfoView view = LedgerDetailInfoView.builder()
                .id(DETAIL_ID)
                .storeInfo("편의점")
                .amount(5000)
                .fundType(FundType.EXPENSE)
                .description("간식")
                .paymentDate(ZonedDateTime.parse("2024-03-01T10:00:00+09:00"))
                .receiptImageUrls(List.of(LedgerReceiptInfoView.from(1L, "https://receipt")))
                .documentImageUrls(List.of())
                .authorName("몽몽")
                .build();
        given(ledgerDetailReader.getLedgerDetail(USER_ID, DETAIL_ID)).willReturn(view);

        mockMvc.perform(get("/api/v1/ledger-detail/{detailId}", DETAIL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(DETAIL_ID))
                .andExpect(jsonPath("$.storeInfo").value("편의점"))
                .andExpect(jsonPath("$.description").value("간식"))
                .andExpect(jsonPath("$.paymentDate").exists())
                .andExpect(jsonPath("$.receiptImageUrls[0].id").value(1))
                .andExpect(jsonPath("$.authorName").value("몽몽"));

        then(ledgerDetailReader).should().getLedgerDetail(USER_ID, DETAIL_ID);
    }

    @Test
    @DisplayName("존재하지 않는 장부 상세 내역을 조회하면 404를 반환한다.")
    void getLedgerDetailInfoNotFound() throws Exception {
        given(ledgerDetailReader.getLedgerDetail(USER_ID, DETAIL_ID))
                .willThrow(new NotFoundException(ErrorCode.LEDGER_DETAIL_NOT_FOUND));

        mockMvc.perform(get("/api/v1/ledger-detail/{detailId}", DETAIL_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEDGER-004"));
    }

    @Test
    @DisplayName("장부 상세 내역을 삭제한다.")
    void removeLedgerDetailInfo() throws Exception {
        mockMvc.perform(delete("/api/v1/ledger-detail/{detailId}", DETAIL_ID))
                .andExpect(status().isOk());

        then(ledgerDetailService).should().removeLedgerDetail(USER_ID, DETAIL_ID);
    }

    @Test
    @DisplayName("삭제 권한이 없으면 403을 반환한다.")
    void removeLedgerDetailInfoForbidden() throws Exception {
        willThrow(new InvalidAccessException(ErrorCode.INVALID_LEDGER_ACCESS))
                .given(ledgerDetailService).removeLedgerDetail(USER_ID, DETAIL_ID);

        mockMvc.perform(delete("/api/v1/ledger-detail/{detailId}", DETAIL_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEDGER-002"));
    }
}
