package com.moneymong.domain.agency.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.moneymong.domain.agency.service.dto.request.BlockAgencyUserRequest;
import com.moneymong.domain.agency.service.dto.request.UpdateAgencyUserRoleRequest;
import com.moneymong.domain.agency.entity.Agency;
import com.moneymong.domain.agency.entity.AgencyUser;
import com.moneymong.domain.agency.entity.enums.AgencyType;
import com.moneymong.domain.agency.entity.enums.AgencyUserRole;
import com.moneymong.domain.agency.exception.AlreadyAgencyUserExistException;
import com.moneymong.domain.agency.repository.AgencyRepository;
import com.moneymong.domain.agency.repository.AgencyUserRepository;
import com.moneymong.domain.invitationcode.entity.CertificationStatus;
import com.moneymong.domain.invitationcode.entity.InvitationCodeCertification;
import com.moneymong.domain.invitationcode.repository.InvitationCodeCertificationRepository;
import com.moneymong.global.exception.custom.BusinessException;
import com.moneymong.global.exception.custom.InvalidAccessException;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AgencyUserServiceTest {

    private static final Long STAFF_ID = 1L;
    private static final Long TARGET_ID = 2L;
    private static final Long AGENCY_ID = 10L;

    @Mock
    private AgencyRepository agencyRepository;
    @Mock
    private AgencyUserRepository agencyUserRepository;
    @Mock
    private InvitationCodeCertificationRepository invitationCodeCertificationRepository;

    @InjectMocks
    private AgencyUserService agencyUserService;

    @Test
    @DisplayName("운영진은 소속원의 권한을 변경할 수 있다.")
    void changeUserRole() {
        // given
        Agency agency = agency(AGENCY_ID, 2);
        givenAgencyUser(agency, STAFF_ID, AgencyUserRole.STAFF);
        AgencyUser target = givenAgencyUser(agency, TARGET_ID, AgencyUserRole.MEMBER);

        // when
        agencyUserService.changeUserRole(STAFF_ID, AGENCY_ID, roleRequest(TARGET_ID, AgencyUserRole.STAFF));

        // then
        assertThat(target.getAgencyUserRole()).isEqualTo(AgencyUserRole.STAFF);
    }

    @Test
    @DisplayName("일반 회원이 권한을 변경하려 하면 예외가 발생하고 대상 권한은 유지된다.")
    void changeUserRoleByMember() {
        // given
        Agency agency = agency(AGENCY_ID, 2);
        givenAgencyUser(agency, STAFF_ID, AgencyUserRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> agencyUserService.changeUserRole(
                STAFF_ID, AGENCY_ID, roleRequest(TARGET_ID, AgencyUserRole.STAFF)))
                .isInstanceOf(InvalidAccessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_AGENCY_USER_ACCESS);
        then(agencyUserRepository).should(never()).findByUserIdAndAgencyId(TARGET_ID, AGENCY_ID);
    }

    @Test
    @DisplayName("권한 변경 대상이 소속원이 아니면 예외가 발생한다.")
    void changeUserRoleTargetNotFound() {
        // given
        Agency agency = agency(AGENCY_ID, 1);
        givenAgencyUser(agency, STAFF_ID, AgencyUserRole.STAFF);
        given(agencyUserRepository.findByUserIdAndAgencyId(TARGET_ID, AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> agencyUserService.changeUserRole(
                STAFF_ID, AGENCY_ID, roleRequest(TARGET_ID, AgencyUserRole.STAFF)))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AGENCY_USER_NOT_FOUND);
    }

    @Test
    @DisplayName("요청자가 소속원이 아니면 권한 변경 시 예외가 발생한다.")
    void changeUserRoleRequesterNotFound() {
        // given
        given(agencyUserRepository.findByUserIdAndAgencyId(STAFF_ID, AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> agencyUserService.changeUserRole(
                STAFF_ID, AGENCY_ID, roleRequest(TARGET_ID, AgencyUserRole.STAFF)))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AGENCY_USER_NOT_FOUND);
    }

    @Test
    @DisplayName("운영진이 소속원을 강제퇴장시키면 소속원과 인증 정보가 삭제되고 인원 수가 감소한다.")
    void blockUser() {
        // given
        Agency agency = agency(AGENCY_ID, 2);
        givenAgencyUser(agency, STAFF_ID, AgencyUserRole.STAFF);
        AgencyUser target = givenAgencyUser(agency, TARGET_ID, AgencyUserRole.MEMBER);
        InvitationCodeCertification certification =
                InvitationCodeCertification.of(TARGET_ID, AGENCY_ID, CertificationStatus.DONE);
        given(invitationCodeCertificationRepository.findByUserIdAndAgencyId(TARGET_ID, AGENCY_ID))
                .willReturn(Optional.of(certification));
        given(agencyRepository.findById(AGENCY_ID)).willReturn(Optional.of(agency));

        // when
        agencyUserService.blockUser(STAFF_ID, AGENCY_ID, blockRequest(TARGET_ID));

        // then
        then(agencyUserRepository).should().delete(target);
        then(invitationCodeCertificationRepository).should().delete(certification);
        assertThat(agency.getHeadCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("일반 회원이 강제퇴장을 시도하면 예외가 발생하고 아무것도 삭제되지 않는다.")
    void blockUserByMember() {
        // given
        Agency agency = agency(AGENCY_ID, 2);
        givenAgencyUser(agency, STAFF_ID, AgencyUserRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> agencyUserService.blockUser(STAFF_ID, AGENCY_ID, blockRequest(TARGET_ID)))
                .isInstanceOf(InvalidAccessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_AGENCY_USER_ACCESS);
        then(agencyUserRepository).should(never()).delete(any());
        then(invitationCodeCertificationRepository).should(never()).delete(any());
        assertThat(agency.getHeadCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("강제퇴장 대상의 초대코드 인증 정보가 없으면 예외가 발생하고 삭제되지 않는다.")
    void blockUserWithoutCertification() {
        // given
        Agency agency = agency(AGENCY_ID, 2);
        givenAgencyUser(agency, STAFF_ID, AgencyUserRole.STAFF);
        givenAgencyUser(agency, TARGET_ID, AgencyUserRole.MEMBER);
        given(invitationCodeCertificationRepository.findByUserIdAndAgencyId(TARGET_ID, AGENCY_ID))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> agencyUserService.blockUser(STAFF_ID, AGENCY_ID, blockRequest(TARGET_ID)))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVITATION_CODE_NOT_CERTIFIED_USER);
        then(agencyUserRepository).should(never()).delete(any());
        assertThat(agency.getHeadCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("소속에 가입하면 일반 회원으로 저장되고 인원 수가 증가한다.")
    void join() {
        // given
        Agency agency = agency(AGENCY_ID, 1);
        given(agencyUserRepository.findByUserIdAndAgencyId(TARGET_ID, AGENCY_ID)).willReturn(Optional.empty());
        given(agencyRepository.findById(AGENCY_ID)).willReturn(Optional.of(agency));

        // when
        agencyUserService.join(AGENCY_ID, TARGET_ID);

        // then
        assertThat(agency.getHeadCount()).isEqualTo(2);
        ArgumentCaptor<AgencyUser> captor = ArgumentCaptor.forClass(AgencyUser.class);
        then(agencyUserRepository).should().save(captor.capture());
        assertThat(captor.getValue().getAgency()).isSameAs(agency);
        assertThat(captor.getValue().getUserId()).isEqualTo(TARGET_ID);
        assertThat(captor.getValue().getAgencyUserRole()).isEqualTo(AgencyUserRole.MEMBER);
    }

    @Test
    @DisplayName("이미 가입한 소속에 다시 가입하면 예외가 발생하고 인원 수는 변하지 않는다.")
    void joinAlreadyJoined() {
        // given
        Agency agency = agency(AGENCY_ID, 1);
        givenAgencyUser(agency, TARGET_ID, AgencyUserRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> agencyUserService.join(AGENCY_ID, TARGET_ID))
                .isInstanceOf(AlreadyAgencyUserExistException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_EXIST_AGENCY_USER);
        then(agencyUserRepository).should(never()).save(any());
        assertThat(agency.getHeadCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 소속에 가입하면 예외가 발생한다.")
    void joinNotExistingAgency() {
        // given
        given(agencyUserRepository.findByUserIdAndAgencyId(TARGET_ID, AGENCY_ID)).willReturn(Optional.empty());
        given(agencyRepository.findById(AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> agencyUserService.join(AGENCY_ID, TARGET_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AGENCY_NOT_FOUND);
        then(agencyUserRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("회원의 모든 소속 탈퇴 시 인원 수가 감소하고 인원이 0명이 된 소속만 삭제된다.")
    void deleteAll() {
        // given
        Agency soloAgency = agency(11L, 1);
        Agency sharedAgency = agency(12L, 3);
        AgencyUser soloMembership = AgencyUser.of(soloAgency, TARGET_ID, AgencyUserRole.STAFF);
        AgencyUser sharedMembership = AgencyUser.of(sharedAgency, TARGET_ID, AgencyUserRole.MEMBER);
        List<AgencyUser> memberships = List.of(soloMembership, sharedMembership);
        given(agencyUserRepository.findAllByUserId(TARGET_ID)).willReturn(memberships);

        // when
        agencyUserService.deleteAll(TARGET_ID);

        // then
        assertThat(soloAgency.getHeadCount()).isZero();
        assertThat(sharedAgency.getHeadCount()).isEqualTo(2);
        then(agencyRepository).should().delete(soloAgency);
        then(agencyRepository).should(never()).delete(sharedAgency);
        then(agencyUserRepository).should().deleteAll(memberships);
    }

    @Test
    @DisplayName("가입한 소속이 없는 회원의 소속 탈퇴는 소속을 삭제하지 않는다.")
    void deleteAllWithoutMembership() {
        // given
        given(agencyUserRepository.findAllByUserId(TARGET_ID)).willReturn(List.of());

        // when
        agencyUserService.deleteAll(TARGET_ID);

        // then
        then(agencyRepository).should(never()).delete(any());
        then(agencyUserRepository).should().deleteAll(List.of());
    }

    private AgencyUser givenAgencyUser(Agency agency, Long userId, AgencyUserRole role) {
        AgencyUser agencyUser = AgencyUser.of(agency, userId, role);
        given(agencyUserRepository.findByUserIdAndAgencyId(userId, AGENCY_ID)).willReturn(Optional.of(agencyUser));
        return agencyUser;
    }

    private Agency agency(Long id, int headCount) {
        return Agency.builder()
                .id(id)
                .agencyName("머니몽")
                .agencyType(AgencyType.GENERAL)
                .headCount(headCount)
                .universityName("머니몽대학교")
                .build();
    }

    private UpdateAgencyUserRoleRequest roleRequest(Long userId, AgencyUserRole role) {
        UpdateAgencyUserRoleRequest request = new UpdateAgencyUserRoleRequest();
        ReflectionTestUtils.setField(request, "userId", userId);
        ReflectionTestUtils.setField(request, "role", role);
        return request;
    }

    private BlockAgencyUserRequest blockRequest(Long userId) {
        BlockAgencyUserRequest request = new BlockAgencyUserRequest();
        ReflectionTestUtils.setField(request, "userId", userId);
        return request;
    }
}
