package com.moneymong.domain.invitationcode.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneymong.domain.invitationcode.api.request.CertifyInvitationCodeRequest;
import com.moneymong.domain.invitationcode.api.response.CertifyInvitationCodeResponse;
import com.moneymong.domain.invitationcode.api.response.InvitationCodeResponse;
import com.moneymong.domain.invitationcode.service.InvitationCodeService;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.support.ControllerTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.http.MediaType;

class InviteCodeControllerTest extends ControllerTestSupport {

    private static final Long AGENCY_ID = 10L;

    @Mock
    private InvitationCodeService invitationCodeService;

    @Override
    protected Object controller() {
        return new InviteCodeController(invitationCodeService);
    }

    @Test
    @DisplayName("초대코드를 인증하면 인증 여부와 소속 ID를 반환한다.")
    void certify() throws Exception {
        given(invitationCodeService.certify(any(CertifyInvitationCodeRequest.class), eq(USER_ID), eq(AGENCY_ID)))
                .willReturn(CertifyInvitationCodeResponse.from(true, AGENCY_ID));

        mockMvc.perform(post("/api/v1/agencies/{agencyId}/invitation-code", AGENCY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CertifyInvitationCodeRequest("123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certified").value(true))
                .andExpect(jsonPath("$.agencyId").value(AGENCY_ID));

        ArgumentCaptor<CertifyInvitationCodeRequest> captor = ArgumentCaptor.forClass(CertifyInvitationCodeRequest.class);
        then(invitationCodeService).should().certify(captor.capture(), eq(USER_ID), eq(AGENCY_ID));
        assertThat(captor.getValue().getInvitationCode()).isEqualTo("123456");
    }

    @Test
    @DisplayName("초대코드가 비어 있으면 400을 반환한다.")
    void certifyWithBlankCode() throws Exception {
        mockMvc.perform(post("/api/v1/agencies/{agencyId}/invitation-code", AGENCY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CertifyInvitationCodeRequest(" "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL-400"));

        then(invitationCodeService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("초대코드를 조회한다.")
    void getCode() throws Exception {
        given(invitationCodeService.getCode(USER_ID, AGENCY_ID)).willReturn(InvitationCodeResponse.from("ABC123"));

        mockMvc.perform(get("/api/v1/agencies/{agencyId}/invitation-code", AGENCY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ABC123"));

        then(invitationCodeService).should().getCode(USER_ID, AGENCY_ID);
    }

    @Test
    @DisplayName("초대코드가 없으면 404를 반환한다.")
    void getCodeNotFound() throws Exception {
        given(invitationCodeService.getCode(USER_ID, AGENCY_ID))
                .willThrow(new NotFoundException(ErrorCode.INVITATION_CODE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/agencies/{agencyId}/invitation-code", AGENCY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITATION-001"));
    }

    @Test
    @DisplayName("초대코드를 재발급한다.")
    void updateCode() throws Exception {
        given(invitationCodeService.updateCode(USER_ID, AGENCY_ID)).willReturn(InvitationCodeResponse.from("NEW123"));

        mockMvc.perform(patch("/api/v1/agencies/{agencyId}/invitation-code", AGENCY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NEW123"));

        then(invitationCodeService).should().updateCode(USER_ID, AGENCY_ID);
    }
}
