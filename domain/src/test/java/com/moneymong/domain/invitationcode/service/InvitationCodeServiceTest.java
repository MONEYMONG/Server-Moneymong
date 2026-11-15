package com.moneymong.domain.invitationcode.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.moneymong.domain.agency.entity.Agency;
import com.moneymong.domain.agency.entity.AgencyUser;
import com.moneymong.domain.agency.entity.enums.AgencyType;
import com.moneymong.domain.agency.entity.enums.AgencyUserRole;
import com.moneymong.domain.agency.exception.AlreadyAgencyUserExistException;
import com.moneymong.domain.agency.repository.AgencyUserRepository;
import com.moneymong.domain.agency.service.AgencyUserService;
import com.moneymong.domain.invitationcode.service.dto.request.CertifyInvitationCodeRequest;
import com.moneymong.domain.invitationcode.service.dto.response.CertifyInvitationCodeResponse;
import com.moneymong.domain.invitationcode.service.dto.response.InvitationCodeResponse;
import com.moneymong.domain.invitationcode.entity.CertificationStatus;
import com.moneymong.domain.invitationcode.entity.InvitationCode;
import com.moneymong.domain.invitationcode.entity.InvitationCodeCertification;
import com.moneymong.domain.invitationcode.repository.InvitationCodeCertificationRepository;
import com.moneymong.domain.invitationcode.repository.InvitationCodeRepository;
import com.moneymong.global.exception.custom.BusinessException;
import com.moneymong.global.exception.custom.InvalidAccessException;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvitationCodeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long AGENCY_ID = 10L;
    private static final String CODE = "123456";
    private static final String MASTER_KEY = "master-key-placeholder";

    @Mock
    private InvitationCodeRepository invitationCodeRepository;
    @Mock
    private AgencyUserRepository agencyUserRepository;
    @Mock
    private AgencyUserService agencyUserService;
    @Mock
    private InvitationCodeCertificationRepository invitationCodeCertificationRepository;

    @InjectMocks
    private InvitationCodeService invitationCodeService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(invitationCodeService, "masterKey", MASTER_KEY);
    }

    @Test
    @DisplayName("운영진이 초대코드를 재발급하면 6자리 숫자의 새 코드로 갱신된다.")
    void updateCode() {
        // given
        givenAgencyUser(AgencyUserRole.STAFF);
        InvitationCode invitationCode = InvitationCode.of(AGENCY_ID, "abcdef");
        given(invitationCodeRepository.findByAgencyId(AGENCY_ID)).willReturn(Optional.of(invitationCode));

        // when
        InvitationCodeResponse response = invitationCodeService.updateCode(USER_ID, AGENCY_ID);

        // then
        assertThat(response.getCode()).hasSize(6).containsOnlyDigits();
        assertThat(invitationCode.getCode()).isEqualTo(response.getCode());
    }

    @Test
    @DisplayName("일반 회원이 초대코드를 재발급하면 예외가 발생하고 코드는 유지된다.")
    void updateCodeByMember() {
        // given
        givenAgencyUser(AgencyUserRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> invitationCodeService.updateCode(USER_ID, AGENCY_ID))
                .isInstanceOf(InvalidAccessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_AGENCY_USER_ACCESS);
        then(invitationCodeRepository).should(never()).findByAgencyId(anyLong());
    }

    @Test
    @DisplayName("강제퇴장된 회원이 초대코드를 재발급하면 예외가 발생한다.")
    void updateCodeByBlockedUser() {
        // given
        givenAgencyUser(AgencyUserRole.BLOCKED);

        // when & then
        assertThatThrownBy(() -> invitationCodeService.updateCode(USER_ID, AGENCY_ID))
                .isInstanceOf(InvalidAccessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_AGENCY_USER_ACCESS);
    }

    @Test
    @DisplayName("소속원이 아닌 회원이 초대코드를 재발급하면 예외가 발생한다.")
    void updateCodeByNonMember() {
        // given
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> invitationCodeService.updateCode(USER_ID, AGENCY_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AGENCY_USER_NOT_FOUND);
    }

    @Test
    @DisplayName("초대코드가 없는 소속에서 재발급하면 예외가 발생한다.")
    void updateCodeWithoutInvitationCode() {
        // given
        givenAgencyUser(AgencyUserRole.STAFF);
        given(invitationCodeRepository.findByAgencyId(AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> invitationCodeService.updateCode(USER_ID, AGENCY_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVITATION_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("운영진은 소속의 초대코드를 조회할 수 있다.")
    void getCode() {
        // given
        givenAgencyUser(AgencyUserRole.STAFF);
        given(invitationCodeRepository.findByAgencyId(AGENCY_ID))
                .willReturn(Optional.of(InvitationCode.of(AGENCY_ID, CODE)));

        // when
        InvitationCodeResponse response = invitationCodeService.getCode(USER_ID, AGENCY_ID);

        // then
        assertThat(response.getCode()).isEqualTo(CODE);
    }

    @Test
    @DisplayName("일반 회원이 초대코드를 조회하면 예외가 발생한다.")
    void getCodeByMember() {
        // given
        givenAgencyUser(AgencyUserRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> invitationCodeService.getCode(USER_ID, AGENCY_ID))
                .isInstanceOf(InvalidAccessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_AGENCY_USER_ACCESS);
        then(invitationCodeRepository).should(never()).findByAgencyId(anyLong());
    }

    @Test
    @DisplayName("올바른 초대코드로 인증하면 인증 정보를 저장하고 소속에 가입시킨다.")
    void certifyWithValidCode() {
        // given
        given(invitationCodeRepository.findByAgencyId(AGENCY_ID))
                .willReturn(Optional.of(InvitationCode.of(AGENCY_ID, CODE)));
        given(invitationCodeCertificationRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.empty());

        // when
        CertifyInvitationCodeResponse response =
                invitationCodeService.certify(new CertifyInvitationCodeRequest(CODE), USER_ID, AGENCY_ID);

        // then
        assertThat(response.isCertified()).isTrue();
        assertThat(response.getAgencyId()).isEqualTo(AGENCY_ID);
        ArgumentCaptor<InvitationCodeCertification> captor =
                ArgumentCaptor.forClass(InvitationCodeCertification.class);
        then(invitationCodeCertificationRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getAgencyId()).isEqualTo(AGENCY_ID);
        assertThat(captor.getValue().getStatus()).isEqualTo(CertificationStatus.DONE);
        then(agencyUserService).should().join(AGENCY_ID, USER_ID);
    }

    @Test
    @DisplayName("기존 인증 기록이 있으면 새로 만들지 않고 기존 인증 정보를 저장한다.")
    void certifyReusesExistingCertification() {
        // given
        InvitationCodeCertification existing =
                InvitationCodeCertification.of(USER_ID, AGENCY_ID, CertificationStatus.REVOKE);
        given(invitationCodeRepository.findByAgencyId(AGENCY_ID))
                .willReturn(Optional.of(InvitationCode.of(AGENCY_ID, CODE)));
        given(invitationCodeCertificationRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(existing));

        // when
        invitationCodeService.certify(new CertifyInvitationCodeRequest(CODE), USER_ID, AGENCY_ID);

        // then
        then(invitationCodeCertificationRepository).should().save(existing);
        then(agencyUserService).should().join(AGENCY_ID, USER_ID);
    }

    @Test
    @DisplayName("틀린 초대코드로 인증하면 인증 실패를 반환하고 가입시키지 않는다.")
    void certifyWithInvalidCode() {
        // given
        given(invitationCodeRepository.findByAgencyId(AGENCY_ID))
                .willReturn(Optional.of(InvitationCode.of(AGENCY_ID, CODE)));

        // when
        CertifyInvitationCodeResponse response =
                invitationCodeService.certify(new CertifyInvitationCodeRequest("000000"), USER_ID, AGENCY_ID);

        // then
        assertThat(response.isCertified()).isFalse();
        assertThat(response.getAgencyId()).isEqualTo(AGENCY_ID);
        then(invitationCodeCertificationRepository).should(never()).save(any());
        then(agencyUserService).should(never()).join(anyLong(), anyLong());
    }

    @Test
    @DisplayName("마스터 키로 인증하면 초대코드가 달라도 인증에 성공한다.")
    void certifyWithMasterKey() {
        // given
        given(invitationCodeRepository.findByAgencyId(AGENCY_ID))
                .willReturn(Optional.of(InvitationCode.of(AGENCY_ID, CODE)));
        given(invitationCodeCertificationRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.empty());

        // when
        CertifyInvitationCodeResponse response =
                invitationCodeService.certify(new CertifyInvitationCodeRequest(MASTER_KEY), USER_ID, AGENCY_ID);

        // then
        assertThat(response.isCertified()).isTrue();
        then(invitationCodeCertificationRepository).should().save(any(InvitationCodeCertification.class));
        then(agencyUserService).should().join(AGENCY_ID, USER_ID);
    }

    @Test
    @DisplayName("초대코드가 없는 소속으로 인증하면 예외가 발생한다.")
    void certifyWithoutInvitationCode() {
        // given
        given(invitationCodeRepository.findByAgencyId(AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> invitationCodeService.certify(
                new CertifyInvitationCodeRequest(CODE), USER_ID, AGENCY_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVITATION_CODE_NOT_FOUND);
        then(agencyUserService).should(never()).join(anyLong(), anyLong());
    }

    @Test
    @DisplayName("이미 가입한 소속의 초대코드로 인증하면 가입 중복 예외가 전파된다.")
    void certifyAlreadyJoined() {
        // given
        given(invitationCodeRepository.findByAgencyId(AGENCY_ID))
                .willReturn(Optional.of(InvitationCode.of(AGENCY_ID, CODE)));
        given(invitationCodeCertificationRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.empty());
        willThrow(new AlreadyAgencyUserExistException(ErrorCode.ALREADY_EXIST_AGENCY_USER))
                .given(agencyUserService).join(AGENCY_ID, USER_ID);

        // when & then
        assertThatThrownBy(() -> invitationCodeService.certify(
                new CertifyInvitationCodeRequest(CODE), USER_ID, AGENCY_ID))
                .isInstanceOf(AlreadyAgencyUserExistException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_EXIST_AGENCY_USER);
    }

    @Test
    @DisplayName("초대코드만으로 인증하면 코드에 해당하는 소속에 가입시킨다.")
    void certifyV2() {
        // given
        given(invitationCodeRepository.findByCode(CODE))
                .willReturn(Optional.of(InvitationCode.of(AGENCY_ID, CODE)));
        given(invitationCodeCertificationRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.empty());

        // when
        CertifyInvitationCodeResponse response =
                invitationCodeService.certifyV2(new CertifyInvitationCodeRequest(CODE), USER_ID);

        // then
        assertThat(response.isCertified()).isTrue();
        assertThat(response.getAgencyId()).isEqualTo(AGENCY_ID);
        ArgumentCaptor<InvitationCodeCertification> captor =
                ArgumentCaptor.forClass(InvitationCodeCertification.class);
        then(invitationCodeCertificationRepository).should().save(captor.capture());
        assertThat(captor.getValue().getAgencyId()).isEqualTo(AGENCY_ID);
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        then(agencyUserService).should().join(AGENCY_ID, USER_ID);
    }

    @Test
    @DisplayName("존재하지 않는 초대코드로 인증하면 예외가 발생하고 가입시키지 않는다.")
    void certifyV2WithUnknownCode() {
        // given
        given(invitationCodeRepository.findByCode("999999")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> invitationCodeService.certifyV2(new CertifyInvitationCodeRequest("999999"), USER_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVITATION_AGENCY_NOT_FOUND);
        then(invitationCodeCertificationRepository).should(never()).save(any());
        then(agencyUserService).should(never()).join(anyLong(), anyLong());
    }

    private void givenAgencyUser(AgencyUserRole role) {
        Agency agency = Agency.of("머니몽", AgencyType.GENERAL, 1, "머니몽대학교");
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(AgencyUser.of(agency, USER_ID, role)));
    }
}
