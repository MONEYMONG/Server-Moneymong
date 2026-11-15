package com.moneymong.domain.agency.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneymong.domain.agency.api.request.BlockAgencyUserRequest;
import com.moneymong.domain.agency.api.request.CreateAgencyRequest;
import com.moneymong.domain.agency.api.request.UpdateAgencyUserRoleRequest;
import com.moneymong.domain.agency.api.response.AgencyResponse;
import com.moneymong.domain.agency.api.response.AgencyUserResponse;
import com.moneymong.domain.agency.api.response.AgencyUserResponses;
import com.moneymong.domain.agency.api.response.CreateAgencyResponse;
import com.moneymong.domain.agency.api.response.SearchAgencyResponse;
import com.moneymong.domain.agency.entity.enums.AgencyType;
import com.moneymong.domain.agency.entity.enums.AgencyUserRole;
import com.moneymong.domain.agency.service.AgencyService;
import com.moneymong.domain.agency.service.AgencyUserService;
import com.moneymong.global.exception.custom.InvalidAccessException;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.support.ControllerTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;

class AgencyControllerTest extends ControllerTestSupport {

    private static final Long AGENCY_ID = 10L;

    @Mock
    private AgencyService agencyService;

    @Mock
    private AgencyUserService agencyUserService;

    @Override
    protected Object controller() {
        return new AgencyController(agencyService, agencyUserService);
    }

    @Test
    @DisplayName("소속을 생성하면 생성된 소속 ID를 반환한다.")
    void create() throws Exception {
        given(agencyService.create(eq(USER_ID), any(CreateAgencyRequest.class)))
                .willReturn(new CreateAgencyResponse(AGENCY_ID));

        mockMvc.perform(post("/api/v1/agencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CreateAgencyRequest("머니몽", AgencyType.IN_SCHOOL_CLUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(AGENCY_ID));

        ArgumentCaptor<CreateAgencyRequest> captor = ArgumentCaptor.forClass(CreateAgencyRequest.class);
        then(agencyService).should().create(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("머니몽");
        assertThat(captor.getValue().getAgencyType()).isEqualTo(AgencyType.IN_SCHOOL_CLUB);
    }

    @Test
    @DisplayName("소속 목록을 페이지 단위로 조회한다.")
    void getAgencyList() throws Exception {
        AgencyResponse agency = new AgencyResponse(AGENCY_ID, "머니몽", 3, AgencyType.GENERAL, null);
        given(agencyService.getAgencyList(eq(USER_ID), any(Pageable.class)))
                .willReturn(new SearchAgencyResponse(List.of(agency), 1L));

        mockMvc.perform(get("/api/v1/agencies").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agencyList[0].id").value(AGENCY_ID))
                .andExpect(jsonPath("$.agencyList[0].name").value("머니몽"))
                .andExpect(jsonPath("$.agencyList[0].headCount").value(3))
                .andExpect(jsonPath("$.agencyList[0].type").value("GENERAL"))
                .andExpect(jsonPath("$.totalCount").value(1));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        then(agencyService).should().getAgencyList(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("키워드로 소속 목록을 검색한다.")
    void searchAgencyList() throws Exception {
        given(agencyService.search(USER_ID, "머니"))
                .willReturn(List.of(new AgencyResponse(AGENCY_ID, "머니몽", 3, AgencyType.STUDENT_COUNCIL, null)));

        mockMvc.perform(get("/api/v1/agencies/search").param("keyword", "머니"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(AGENCY_ID))
                .andExpect(jsonPath("$[0].type").value("STUDENT_COUNCIL"));

        then(agencyService).should().search(USER_ID, "머니");
    }

    @Test
    @DisplayName("소속 내 멤버 목록을 조회한다.")
    void getAgencyUserList() throws Exception {
        AgencyUserResponse member = new AgencyUserResponse(100L, USER_ID, "몽몽", AgencyUserRole.STAFF);
        given(agencyService.getAgencyUserList(USER_ID, AGENCY_ID))
                .willReturn(AgencyUserResponses.from(List.of(member)));

        mockMvc.perform(get("/api/v1/agencies/{agencyId}/agency-users", AGENCY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agencyUsers[0].id").value(100))
                .andExpect(jsonPath("$.agencyUsers[0].userId").value(USER_ID))
                .andExpect(jsonPath("$.agencyUsers[0].nickname").value("몽몽"))
                .andExpect(jsonPath("$.agencyUsers[0].agencyUserRole").value("STAFF"))
                .andExpect(jsonPath("$.count").value(1));

        then(agencyService).should().getAgencyUserList(USER_ID, AGENCY_ID);
    }

    @Test
    @DisplayName("소속에 속하지 않은 유저가 멤버 목록을 조회하면 404를 반환한다.")
    void getAgencyUserListNotFound() throws Exception {
        given(agencyService.getAgencyUserList(USER_ID, AGENCY_ID))
                .willThrow(new NotFoundException(ErrorCode.AGENCY_USER_NOT_FOUND));

        mockMvc.perform(get("/api/v1/agencies/{agencyId}/agency-users", AGENCY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AGENCY-USER-001"));
    }

    @Test
    @DisplayName("소속 내 멤버 권한을 변경한다.")
    void changeRole() throws Exception {
        mockMvc.perform(patch("/api/v1/agencies/{agencyId}/agency-users/roles", AGENCY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("userId", 2, "role", "STAFF"))))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateAgencyUserRoleRequest> captor = ArgumentCaptor.forClass(UpdateAgencyUserRoleRequest.class);
        then(agencyUserService).should().changeUserRole(eq(USER_ID), eq(AGENCY_ID), captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(2L);
        assertThat(captor.getValue().getRole()).isEqualTo(AgencyUserRole.STAFF);
    }

    @Test
    @DisplayName("소속 내 멤버를 강제퇴장시킨다.")
    void blockMember() throws Exception {
        mockMvc.perform(patch("/api/v1/agencies/{agencyId}/agency-users/roles/block", AGENCY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("userId", 2))))
                .andExpect(status().isOk());

        ArgumentCaptor<BlockAgencyUserRequest> captor = ArgumentCaptor.forClass(BlockAgencyUserRequest.class);
        then(agencyUserService).should().blockUser(eq(USER_ID), eq(AGENCY_ID), captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("STAFF가 아닌 유저가 강제퇴장을 시도하면 403을 반환한다.")
    void blockMemberForbidden() throws Exception {
        willThrow(new InvalidAccessException(ErrorCode.INVALID_AGENCY_USER_ACCESS))
                .given(agencyUserService).blockUser(eq(USER_ID), eq(AGENCY_ID), any(BlockAgencyUserRequest.class));

        mockMvc.perform(patch("/api/v1/agencies/{agencyId}/agency-users/roles/block", AGENCY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("userId", 2))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AGENCY-USER-002"));
    }

    @Test
    @DisplayName("내가 속한 소속 목록을 조회한다.")
    void getMyAgency() throws Exception {
        given(agencyService.getMyAgency(USER_ID))
                .willReturn(List.of(new AgencyResponse(AGENCY_ID, "머니몽", 1, AgencyType.GENERAL, null)));

        mockMvc.perform(get("/api/v1/agencies/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(AGENCY_ID))
                .andExpect(jsonPath("$[0].name").value("머니몽"));

        then(agencyService).should().getMyAgency(USER_ID);
    }

    @Test
    @DisplayName("소속을 삭제한다.")
    void deleteAgency() throws Exception {
        mockMvc.perform(delete("/api/v1/agencies/{agencyId}", AGENCY_ID))
                .andExpect(status().isOk());

        then(agencyService).should().delete(USER_ID, AGENCY_ID);
    }
}
