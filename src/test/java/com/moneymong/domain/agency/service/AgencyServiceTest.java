package com.moneymong.domain.agency.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;

import com.moneymong.domain.agency.api.request.CreateAgencyRequest;
import com.moneymong.domain.agency.api.response.AgencyResponse;
import com.moneymong.domain.agency.api.response.AgencyUserResponse;
import com.moneymong.domain.agency.api.response.AgencyUserResponses;
import com.moneymong.domain.agency.api.response.CreateAgencyResponse;
import com.moneymong.domain.agency.api.response.SearchAgencyResponse;
import com.moneymong.domain.agency.entity.Agency;
import com.moneymong.domain.agency.entity.AgencyUser;
import com.moneymong.domain.agency.entity.enums.AgencyType;
import com.moneymong.domain.agency.entity.enums.AgencyUserRole;
import com.moneymong.domain.agency.exception.BlockedAgencyUserException;
import com.moneymong.domain.agency.repository.AgencyRepository;
import com.moneymong.domain.agency.repository.AgencyUserRepository;
import com.moneymong.domain.invitationcode.entity.CertificationStatus;
import com.moneymong.domain.invitationcode.entity.InvitationCode;
import com.moneymong.domain.invitationcode.entity.InvitationCodeCertification;
import com.moneymong.domain.invitationcode.exception.CertificationNotExistException;
import com.moneymong.domain.invitationcode.repository.InvitationCodeCertificationRepository;
import com.moneymong.domain.invitationcode.repository.InvitationCodeRepository;
import com.moneymong.domain.ledger.entity.Ledger;
import com.moneymong.domain.ledger.repository.LedgerRepository;
import com.moneymong.domain.ledger.service.manager.LedgerService;
import com.moneymong.domain.user.entity.UserUniversity;
import com.moneymong.domain.user.repository.UserUniversityRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AgencyServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long AGENCY_ID = 10L;
    private static final String UNIVERSITY_NAME = "머니몽대학교";

    @Mock
    private AgencyUserRepository agencyUserRepository;
    @Mock
    private AgencyRepository agencyRepository;
    @Mock
    private UserUniversityRepository userUniversityRepository;
    @Mock
    private LedgerRepository ledgerRepository;
    @Mock
    private InvitationCodeRepository invitationCodeRepository;
    @Mock
    private InvitationCodeCertificationRepository invitationCodeCertificationRepository;
    @Mock
    private LedgerService ledgerService;

    @InjectMocks
    private AgencyService agencyService;

    @Test
    @DisplayName("소속 목록 조회 시 회원 대학의 일반 소속을 페이지 단위로 반환한다.")
    void getAgencyList() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        Agency agency = agency(AGENCY_ID, "동아리", AgencyType.GENERAL, 3);
        givenUserUniversity();
        given(agencyRepository.findByUniversityNameAndAgencyTypeByPaging(UNIVERSITY_NAME, AgencyType.GENERAL, pageable))
                .willReturn(new PageImpl<>(List.of(agency), pageable, 21));

        // when
        SearchAgencyResponse response = agencyService.getAgencyList(USER_ID, pageable);

        // then
        assertThat(response.getTotalCount()).isEqualTo(21);
        assertThat(response.getAgencyList())
                .extracting(AgencyResponse::getId, AgencyResponse::getName, AgencyResponse::getHeadCount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(AGENCY_ID, "동아리", 3));
    }

    @Test
    @DisplayName("대학 정보가 없는 회원은 대학명 null로 소속 목록을 조회한다.")
    void getAgencyListWithoutUniversity() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        given(userUniversityRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(agencyRepository.findByUniversityNameAndAgencyTypeByPaging(null, AgencyType.GENERAL, pageable))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        SearchAgencyResponse response = agencyService.getAgencyList(USER_ID, pageable);

        // then
        assertThat(response.getAgencyList()).isEmpty();
        assertThat(response.getTotalCount()).isZero();
    }

    @Test
    @DisplayName("소속 생성 시 생성자를 운영진으로 등록하고 장부, 초대코드, 인증 정보를 함께 저장한다.")
    void create() {
        // given
        givenUserUniversity();
        CreateAgencyRequest request = new CreateAgencyRequest("머니몽 동아리", AgencyType.IN_SCHOOL_CLUB);
        willAnswer(invocation -> {
            Agency saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", AGENCY_ID);
            return saved;
        }).given(agencyRepository).save(any(Agency.class));

        // when
        CreateAgencyResponse response = agencyService.create(USER_ID, request);

        // then
        assertThat(response.getId()).isEqualTo(AGENCY_ID);

        ArgumentCaptor<Agency> agencyCaptor = ArgumentCaptor.forClass(Agency.class);
        then(agencyRepository).should().save(agencyCaptor.capture());
        Agency savedAgency = agencyCaptor.getValue();
        assertThat(savedAgency.getAgencyName()).isEqualTo("머니몽 동아리");
        assertThat(savedAgency.getAgencyType()).isEqualTo(AgencyType.IN_SCHOOL_CLUB);
        assertThat(savedAgency.getHeadCount()).isEqualTo(1);
        assertThat(savedAgency.getUniversityName()).isEqualTo(UNIVERSITY_NAME);
        assertThat(savedAgency.getAgencyUsers())
                .singleElement()
                .satisfies(agencyUser -> {
                    assertThat(agencyUser.getUserId()).isEqualTo(USER_ID);
                    assertThat(agencyUser.getAgencyUserRole()).isEqualTo(AgencyUserRole.STAFF);
                });

        ArgumentCaptor<Ledger> ledgerCaptor = ArgumentCaptor.forClass(Ledger.class);
        then(ledgerRepository).should().save(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getAgency()).isSameAs(savedAgency);
        assertThat(ledgerCaptor.getValue().getTotalBalance()).isZero();

        ArgumentCaptor<InvitationCode> codeCaptor = ArgumentCaptor.forClass(InvitationCode.class);
        then(invitationCodeRepository).should().save(codeCaptor.capture());
        assertThat(codeCaptor.getValue().getAgencyId()).isEqualTo(AGENCY_ID);
        assertThat(codeCaptor.getValue().getCode()).hasSize(6).containsOnlyDigits();

        ArgumentCaptor<InvitationCodeCertification> certCaptor =
                ArgumentCaptor.forClass(InvitationCodeCertification.class);
        then(invitationCodeCertificationRepository).should().save(certCaptor.capture());
        assertThat(certCaptor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(certCaptor.getValue().getAgencyId()).isEqualTo(AGENCY_ID);
        assertThat(certCaptor.getValue().getStatus()).isEqualTo(CertificationStatus.DONE);
    }

    @Test
    @DisplayName("키워드로 회원 대학의 소속을 검색한다.")
    void search() {
        // given
        givenUserUniversity();
        Agency agency = agency(AGENCY_ID, "머니몽", AgencyType.GENERAL, 5);
        given(agencyRepository.findByKeyword(UNIVERSITY_NAME, "머니")).willReturn(List.of(agency));

        // when
        List<AgencyResponse> responses = agencyService.search(USER_ID, "머니");

        // then
        assertThat(responses)
                .extracting(AgencyResponse::getId, AgencyResponse::getName, AgencyResponse::getType)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(AGENCY_ID, "머니몽", AgencyType.GENERAL));
    }

    @Test
    @DisplayName("운영진은 인증 여부 확인 없이 소속원 목록을 조회한다.")
    void getAgencyUserListByStaff() {
        // given
        givenAgencyUser(AgencyUserRole.STAFF);
        List<AgencyUserResponse> users = List.of(
                new AgencyUserResponse(1L, USER_ID, "운영진", AgencyUserRole.STAFF),
                new AgencyUserResponse(2L, 2L, "회원", AgencyUserRole.MEMBER)
        );
        given(agencyUserRepository.findByAgencyId(AGENCY_ID)).willReturn(users);

        // when
        AgencyUserResponses response = agencyService.getAgencyUserList(USER_ID, AGENCY_ID);

        // then
        assertThat(response.getCount()).isEqualTo(2);
        assertThat(response.getAgencyUsers()).isEqualTo(users);
        then(invitationCodeCertificationRepository).should(never()).findByUserIdAndAgencyId(any(), any());
    }

    @Test
    @DisplayName("초대코드 인증을 마친 일반 회원은 소속원 목록을 조회한다.")
    void getAgencyUserListByCertifiedMember() {
        // given
        givenAgencyUser(AgencyUserRole.MEMBER);
        given(invitationCodeCertificationRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(InvitationCodeCertification.of(USER_ID, AGENCY_ID, CertificationStatus.DONE)));
        given(agencyUserRepository.findByAgencyId(AGENCY_ID)).willReturn(List.of());

        // when
        AgencyUserResponses response = agencyService.getAgencyUserList(USER_ID, AGENCY_ID);

        // then
        assertThat(response.getCount()).isZero();
        then(agencyUserRepository).should().findByAgencyId(AGENCY_ID);
    }

    @Test
    @DisplayName("초대코드 인증 기록이 없는 일반 회원이 소속원 목록을 조회하면 예외가 발생한다.")
    void getAgencyUserListByUncertifiedMember() {
        // given
        givenAgencyUser(AgencyUserRole.MEMBER);
        given(invitationCodeCertificationRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> agencyService.getAgencyUserList(USER_ID, AGENCY_ID))
                .isInstanceOf(CertificationNotExistException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVITATION_CODE_NOT_CERTIFIED_USER);
        then(agencyUserRepository).should(never()).findByAgencyId(any());
    }

    @Test
    @DisplayName("강제퇴장된 회원이 소속원 목록을 조회하면 예외가 발생한다.")
    void getAgencyUserListByBlockedUser() {
        // given
        givenAgencyUser(AgencyUserRole.BLOCKED);

        // when & then
        assertThatThrownBy(() -> agencyService.getAgencyUserList(USER_ID, AGENCY_ID))
                .isInstanceOf(BlockedAgencyUserException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BLOCKED_AGENCY_USER);
        then(agencyUserRepository).should(never()).findByAgencyId(any());
    }

    @Test
    @DisplayName("소속에 가입하지 않은 회원이 소속원 목록을 조회하면 예외가 발생한다.")
    void getAgencyUserListByNonMember() {
        // given
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> agencyService.getAgencyUserList(USER_ID, AGENCY_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AGENCY_USER_NOT_FOUND);
    }

    @Test
    @DisplayName("내가 가입한 소속 목록을 조회한다.")
    void getMyAgency() {
        // given
        Agency first = agency(1L, "첫번째", AgencyType.GENERAL, 1);
        Agency second = agency(2L, "두번째", AgencyType.STUDENT_COUNCIL, 4);
        given(agencyUserRepository.findAgencyListByUserId(USER_ID)).willReturn(List.of(first, second));

        // when
        List<AgencyResponse> responses = agencyService.getMyAgency(USER_ID);

        // then
        assertThat(responses).extracting(AgencyResponse::getId).containsExactly(1L, 2L);
        assertThat(responses).extracting(AgencyResponse::getName).containsExactly("첫번째", "두번째");
    }

    @Test
    @DisplayName("운영진이 소속을 삭제하면 소속이 삭제된다.")
    void deleteByStaff() {
        // given
        givenAgencyUser(AgencyUserRole.STAFF);
        Agency agency = agency(AGENCY_ID, "머니몽", AgencyType.GENERAL, 1);
        given(agencyRepository.findById(AGENCY_ID)).willReturn(Optional.of(agency));

        // when
        agencyService.delete(USER_ID, AGENCY_ID);

        // then
        then(agencyRepository).should().delete(agency);
    }

    @Test
    @DisplayName("운영진이 아닌 회원이 소속을 삭제하면 INVALID_AGENCY_USER_ACCESS 예외가 발생하고 삭제되지 않는다.")
    void deleteByMember() {
        // given
        givenAgencyUser(AgencyUserRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> agencyService.delete(USER_ID, AGENCY_ID))
                .isInstanceOf(InvalidAccessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_AGENCY_USER_ACCESS);
        then(agencyRepository).should(never()).findById(any());
        then(agencyRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("강제퇴장된 회원이 소속을 삭제하면 예외가 발생하고 삭제되지 않는다.")
    void deleteByBlockedUser() {
        // given
        givenAgencyUser(AgencyUserRole.BLOCKED);

        // when & then
        assertThatThrownBy(() -> agencyService.delete(USER_ID, AGENCY_ID))
                .isInstanceOf(BlockedAgencyUserException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BLOCKED_AGENCY_USER);
        then(agencyRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("소속에 가입하지 않은 회원이 소속을 삭제하면 예외가 발생한다.")
    void deleteByNonMember() {
        // given
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> agencyService.delete(USER_ID, AGENCY_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AGENCY_USER_NOT_FOUND);
        then(agencyRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("존재하지 않는 소속을 삭제하면 예외가 발생한다.")
    void deleteNotExistingAgency() {
        // given
        givenAgencyUser(AgencyUserRole.STAFF);
        given(agencyRepository.findById(AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> agencyService.delete(USER_ID, AGENCY_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AGENCY_NOT_FOUND);
        then(agencyRepository).should(never()).delete(any());
    }

    private void givenUserUniversity() {
        given(userUniversityRepository.findByUserId(USER_ID))
                .willReturn(Optional.of(UserUniversity.of(USER_ID, UNIVERSITY_NAME, 3)));
    }

    private void givenAgencyUser(AgencyUserRole role) {
        Agency agency = agency(AGENCY_ID, "머니몽", AgencyType.GENERAL, 1);
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(AgencyUser.of(agency, USER_ID, role)));
    }

    private Agency agency(Long id, String name, AgencyType type, int headCount) {
        return Agency.builder()
                .id(id)
                .agencyName(name)
                .agencyType(type)
                .headCount(headCount)
                .universityName(UNIVERSITY_NAME)
                .build();
    }
}
