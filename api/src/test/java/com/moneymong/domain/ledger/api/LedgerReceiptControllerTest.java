package com.moneymong.domain.ledger.api;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneymong.domain.ledger.api.request.CreateLedgerReceiptRequest;
import com.moneymong.domain.ledger.service.manager.LedgerReceiptManager;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.support.ControllerTestSupport;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;

class LedgerReceiptControllerTest extends ControllerTestSupport {

    private static final Long DETAIL_ID = 30L;
    private static final Long RECEIPT_ID = 400L;

    @Mock
    private LedgerReceiptManager ledgerReceiptManager;

    @Override
    protected Object controller() {
        return new LedgerReceiptController(ledgerReceiptManager);
    }

    @Test
    @DisplayName("장부 상세 내역에 영수증을 등록한다.")
    void createLedgerReceipt() throws Exception {
        List<String> urls = List.of("https://receipt/1");

        mockMvc.perform(post("/api/v1/ledger-detail/{detailId}/ledger-receipt", DETAIL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CreateLedgerReceiptRequest(urls))))
                .andExpect(status().isOk());

        then(ledgerReceiptManager).should().createReceipts(DETAIL_ID, urls);
    }

    @Test
    @DisplayName("영수증이 12개를 넘으면 400을 반환한다.")
    void createLedgerReceiptWithTooMany() throws Exception {
        mockMvc.perform(post("/api/v1/ledger-detail/{detailId}/ledger-receipt", DETAIL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CreateLedgerReceiptRequest(Collections.nCopies(13, "https://receipt")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL-400"))
                .andExpect(jsonPath("$.messages[0]").value("영수증 12개 이하 입력해주세요."));

        then(ledgerReceiptManager).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("영수증을 삭제한다.")
    void deleteLedgerReceipt() throws Exception {
        mockMvc.perform(delete("/api/v1/ledger-detail/{detailId}/ledger-receipt/{receiptId}", DETAIL_ID, RECEIPT_ID))
                .andExpect(status().isOk());

        then(ledgerReceiptManager).should().removeReceipt(USER_ID, DETAIL_ID, RECEIPT_ID);
    }

    @Test
    @DisplayName("존재하지 않는 영수증을 삭제하면 404를 반환한다.")
    void deleteLedgerReceiptNotFound() throws Exception {
        willThrow(new NotFoundException(ErrorCode.LEDGER_RECEIPT_NOT_FOUND))
                .given(ledgerReceiptManager).removeReceipt(USER_ID, DETAIL_ID, RECEIPT_ID);

        mockMvc.perform(delete("/api/v1/ledger-detail/{detailId}/ledger-receipt/{receiptId}", DETAIL_ID, RECEIPT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEDGER-006"));
    }
}
