package com.moneymong.domain.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneymong.domain.ledger.api.request.CreateLedgerRequestV2;
import com.moneymong.domain.ledger.api.request.UpdateLedgerRequestV2;
import com.moneymong.domain.ledger.api.response.LedgerDetailInfoView;
import com.moneymong.domain.ledger.api.response.LedgerDetailInfoViewV2;
import com.moneymong.domain.ledger.api.response.LedgerDocumentInfoView;
import com.moneymong.domain.ledger.api.response.ledger.LedgerInfoViewDetail;
import com.moneymong.domain.ledger.api.response.ledger.LedgerInfoViewV2;
import com.moneymong.domain.ledger.entity.enums.FundType;
import com.moneymong.domain.ledger.service.manager.LedgerDetailService;
import com.moneymong.domain.ledger.service.manager.LedgerService;
import com.moneymong.domain.ledger.service.reader.LedgerReader;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.support.ControllerTestSupport;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.http.MediaType;

class LedgerControllerV2Test extends ControllerTestSupport {

    private static final Long LEDGER_ID = 3L;
    private static final Long DETAIL_ID = 30L;
    private static final String PAYMENT_DATE = "2024-03-01T10:00:00+09:00";

    @Mock
    private LedgerService ledgerService;

    @Mock
    private LedgerReader ledgerReader;

    @Mock
    private LedgerDetailService ledgerDetailService;

    @Override
    protected Object controller() {
        return new LedgerControllerV2(ledgerService, ledgerReader, ledgerDetailService);
    }

    private LedgerInfoViewV2 ledgerInfoViewV2() {
        LedgerInfoViewDetail detail = LedgerInfoViewDetail.builder()
                .id(DETAIL_ID)
                .storeInfo("편의점")
                .fundType(FundType.INCOME)
                .amount(5000)
                .balance(5000)
                .order(1)
                .build();
        return LedgerInfoViewV2.builder()
                .id(LEDGER_ID)
                .totalBalance(5000)
                .ledgerInfoViewDetails(List.of(detail))
                .ledgerDetailTotalCount(1L)
                .agencyName("머니몽")
                .build();
    }

    private Map<String, Object> createBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("storeInfo", "편의점");
        body.put("fundType", "INCOME");
        body.put("amount", 5000);
        body.put("description", "회비");
        body.put("paymentDate", PAYMENT_DATE);
        body.put("documentImageUrls", List.of("https://document"));
        body.put("category", "회비");
        return body;
    }

    private Map<String, Object> updateBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("storeInfo", "카페");
        body.put("amount", 4000);
        body.put("description", "커피");
        body.put("paymentDate", PAYMENT_DATE);
        body.put("receiptImageUrls", List.of("https://receipt"));
        body.put("documentImageUrls", List.of());
        body.put("category", "간식비");
        return body;
    }

    @Test
    @DisplayName("V2 장부 내역을 카테고리와 함께 등록한다.")
    void createLedgerV2() throws Exception {
        LedgerDetailInfoViewV2 view = LedgerDetailInfoViewV2.builder()
                .id(DETAIL_ID)
                .storeInfo("편의점")
                .amount(5000)
                .fundType(FundType.INCOME)
                .description("회비")
                .paymentDate(ZonedDateTime.parse(PAYMENT_DATE))
                .documentImageUrls(List.of(LedgerDocumentInfoView.from(2L, "https://document")))
                .authorName("몽몽")
                .category("회비")
                .build();
        given(ledgerService.createLedgerV2(eq(USER_ID), eq(LEDGER_ID), any(CreateLedgerRequestV2.class)))
                .willReturn(view);

        mockMvc.perform(post("/api/v2/ledger/{id}", LEDGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(createBody())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(DETAIL_ID))
                .andExpect(jsonPath("$.fundType").value("INCOME"))
                .andExpect(jsonPath("$.documentImageUrls[0].id").value(2))
                .andExpect(jsonPath("$.category").value("회비"));

        ArgumentCaptor<CreateLedgerRequestV2> captor = ArgumentCaptor.forClass(CreateLedgerRequestV2.class);
        then(ledgerService).should().createLedgerV2(eq(USER_ID), eq(LEDGER_ID), captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo("회비");
        assertThat(captor.getValue().getFundType()).isEqualTo(FundType.INCOME);
        assertThat(captor.getValue().getDocumentImageUrls()).containsExactly("https://document");
    }

    @Test
    @DisplayName("증빙 자료가 12개를 넘으면 V2 장부 등록은 400을 반환한다.")
    void createLedgerV2WithTooManyDocuments() throws Exception {
        Map<String, Object> body = createBody();
        body.put("documentImageUrls", java.util.Collections.nCopies(13, "https://document"));

        mockMvc.perform(post("/api/v2/ledger/{id}", LEDGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL-400"))
                .andExpect(jsonPath("$.messages[0]").value("증빙 자료 12개 이하 입력해주세요."));

        then(ledgerService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("기간 조건으로 V2 장부 내역을 조회한다.")
    void search() throws Exception {
        given(ledgerReader.searchLedgersByPeriod(USER_ID, LEDGER_ID, 2024, 1, 2024, 3, 0, 20))
                .willReturn(ledgerInfoViewV2());

        mockMvc.perform(get("/api/v2/ledger/{id}", LEDGER_ID)
                        .param("startYear", "2024")
                        .param("startMonth", "1")
                        .param("endYear", "2024")
                        .param("endMonth", "3")
                        .param("page", "0")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(LEDGER_ID))
                .andExpect(jsonPath("$.totalBalance").value(5000))
                .andExpect(jsonPath("$.ledgerDetailTotalCount").value(1))
                .andExpect(jsonPath("$.agencyName").value("머니몽"))
                .andExpect(jsonPath("$.ledgerInfoViewDetails[0].fundType").value("INCOME"));

        then(ledgerReader).should().searchLedgersByPeriod(USER_ID, LEDGER_ID, 2024, 1, 2024, 3, 0, 20);
    }

    @Test
    @DisplayName("limit가 300을 넘으면 V2 장부 조회는 400을 반환한다.")
    void searchWithTooLargeLimit() throws Exception {
        mockMvc.perform(get("/api/v2/ledger/{id}", LEDGER_ID)
                        .param("startYear", "2024")
                        .param("startMonth", "1")
                        .param("endYear", "2024")
                        .param("endMonth", "3")
                        .param("page", "0")
                        .param("limit", "301"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL-400"));

        then(ledgerReader).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("기간과 입출금 유형 조건으로 V2 장부 내역을 조회한다.")
    void searchByFilter() throws Exception {
        given(ledgerReader.searchLedgersByPeriodAndFundType(USER_ID, LEDGER_ID, 2024, 1, 2024, 3, 0, 20, FundType.EXPENSE))
                .willReturn(ledgerInfoViewV2());

        mockMvc.perform(get("/api/v2/ledger/{id}/filter", LEDGER_ID)
                        .param("startYear", "2024")
                        .param("startMonth", "1")
                        .param("endYear", "2024")
                        .param("endMonth", "3")
                        .param("page", "0")
                        .param("limit", "20")
                        .param("fundType", "EXPENSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(LEDGER_ID));

        then(ledgerReader).should()
                .searchLedgersByPeriodAndFundType(USER_ID, LEDGER_ID, 2024, 1, 2024, 3, 0, 20, FundType.EXPENSE);
    }

    @Test
    @DisplayName("V2 장부 상세 내역을 수정한다.")
    void updateLedger() throws Exception {
        LedgerDetailInfoView view = LedgerDetailInfoView.builder()
                .id(DETAIL_ID)
                .storeInfo("카페")
                .amount(4000)
                .category("간식비")
                .receiptImageUrls(List.of())
                .documentImageUrls(List.of())
                .build();
        given(ledgerDetailService.updateLedgerDetailV2(eq(USER_ID), eq(DETAIL_ID), any(UpdateLedgerRequestV2.class)))
                .willReturn(view);

        mockMvc.perform(put("/api/v2/ledger/ledger-detail/{detailId}", DETAIL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(updateBody())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(DETAIL_ID))
                .andExpect(jsonPath("$.storeInfo").value("카페"))
                .andExpect(jsonPath("$.category").value("간식비"));

        ArgumentCaptor<UpdateLedgerRequestV2> captor = ArgumentCaptor.forClass(UpdateLedgerRequestV2.class);
        then(ledgerDetailService).should().updateLedgerDetailV2(eq(USER_ID), eq(DETAIL_ID), captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo("간식비");
        assertThat(captor.getValue().getReceiptImageUrls()).containsExactly("https://receipt");
    }

    @Test
    @DisplayName("존재하지 않는 장부 상세 내역을 수정하면 404를 반환한다.")
    void updateLedgerNotFound() throws Exception {
        given(ledgerDetailService.updateLedgerDetailV2(eq(USER_ID), eq(DETAIL_ID), any(UpdateLedgerRequestV2.class)))
                .willThrow(new NotFoundException(ErrorCode.LEDGER_DETAIL_NOT_FOUND));

        mockMvc.perform(put("/api/v2/ledger/ledger-detail/{detailId}", DETAIL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(updateBody())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEDGER-004"));
    }
}
