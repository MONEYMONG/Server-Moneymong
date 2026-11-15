package com.moneymong.domain.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import com.moneymong.domain.agency.entity.Agency;
import com.moneymong.domain.agency.entity.AgencyUser;
import com.moneymong.domain.agency.entity.enums.AgencyUserRole;
import com.moneymong.domain.agency.repository.AgencyUserRepository;
import com.moneymong.domain.category.service.dto.response.CategoryResponse;
import com.moneymong.domain.category.entity.Category;
import com.moneymong.domain.category.event.CategoryDeletedEvent;
import com.moneymong.domain.category.repository.CategoryRepository;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Long USER_ID = 100L;
    private static final Long AGENCY_ID = 10L;

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AgencyUserRepository agencyUserRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    @DisplayName("운영진이 카테고리를 생성하면 저장된 카테고리 정보를 반환한다.")
    void createCategory() {
        // given
        givenAgencyUser(AgencyUserRole.STAFF);
        given(categoryRepository.save(any(Category.class))).willReturn(category(1L, "회식비"));

        // when
        CategoryResponse response = categoryService.createCategory(USER_ID, "회식비", AGENCY_ID);

        // then
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("회식비");
        assertThat(response.getAgencyId()).isEqualTo(AGENCY_ID);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        then(categoryRepository).should().save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("회식비");
        assertThat(captor.getValue().getAgencyId()).isEqualTo(AGENCY_ID);
    }

    @Test
    @DisplayName("소속 회원은 소속의 카테고리 목록을 조회할 수 있다.")
    void getCategoriesByAgencyId() {
        // given
        givenAgencyUser(AgencyUserRole.MEMBER);
        given(categoryRepository.findByAgencyId(AGENCY_ID))
                .willReturn(List.of(category(1L, "회식비"), category(2L, "간식비")));

        // when
        List<CategoryResponse> responses = categoryService.getCategoriesByAgencyId(USER_ID, AGENCY_ID);

        // then
        assertThat(responses)
                .extracting(CategoryResponse::getId, CategoryResponse::getName)
                .containsExactly(tuple(1L, "회식비"), tuple(2L, "간식비"));
    }

    @Test
    @DisplayName("운영진이 카테고리를 삭제하면 카테고리 삭제 이벤트를 먼저 발행한 뒤 카테고리를 삭제한다.")
    void deleteCategory() {
        // given
        Category category = category(1L, "회식비");
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));
        givenAgencyUser(AgencyUserRole.STAFF);

        // when
        categoryService.deleteCategory(USER_ID, 1L);

        // then
        InOrder inOrder = inOrder(eventPublisher, categoryRepository);
        ArgumentCaptor<CategoryDeletedEvent> captor = ArgumentCaptor.forClass(CategoryDeletedEvent.class);
        inOrder.verify(eventPublisher).publishEvent(captor.capture());
        inOrder.verify(categoryRepository).delete(category);
        assertThat(captor.getValue().category()).isSameAs(category);
    }

    @Test
    @DisplayName("존재하지 않는 카테고리를 삭제하면 예외가 발생한다.")
    void deleteNotExistingCategory() {
        // given
        given(categoryRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> categoryService.deleteCategory(USER_ID, 1L))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LEDGER_CATEGORY_NOT_FOUND);
        then(eventPublisher).should(never()).publishEvent(any());
        then(categoryRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("운영진이 아닌 회원이 카테고리를 생성하면 INVALID_AGENCY_USER_ACCESS 예외가 발생하고 저장되지 않는다.")
    void createCategoryByMember() {
        // given
        givenAgencyUser(AgencyUserRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> categoryService.createCategory(USER_ID, "회식비", AGENCY_ID))
                .isInstanceOf(InvalidAccessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_AGENCY_USER_ACCESS);
        then(categoryRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("소속에 가입하지 않은 유저가 카테고리를 생성하면 AGENCY_USER_NOT_FOUND 예외가 발생하고 저장되지 않는다.")
    void createCategoryByNonMember() {
        // given
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> categoryService.createCategory(USER_ID, "회식비", AGENCY_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AGENCY_USER_NOT_FOUND);
        then(categoryRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("강제퇴장된 회원이 카테고리 목록을 조회하면 INVALID_AGENCY_USER_ACCESS 예외가 발생한다.")
    void getCategoriesByBlockedUser() {
        // given
        givenAgencyUser(AgencyUserRole.BLOCKED);

        // when & then
        assertThatThrownBy(() -> categoryService.getCategoriesByAgencyId(USER_ID, AGENCY_ID))
                .isInstanceOf(InvalidAccessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_AGENCY_USER_ACCESS);
        then(categoryRepository).should(never()).findByAgencyId(any());
    }

    @Test
    @DisplayName("소속에 가입하지 않은 유저가 카테고리 목록을 조회하면 AGENCY_USER_NOT_FOUND 예외가 발생한다.")
    void getCategoriesByNonMember() {
        // given
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> categoryService.getCategoriesByAgencyId(USER_ID, AGENCY_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AGENCY_USER_NOT_FOUND);
        then(categoryRepository).should(never()).findByAgencyId(any());
    }

    @Test
    @DisplayName("운영진이 아닌 회원이 카테고리를 삭제하면 INVALID_AGENCY_USER_ACCESS 예외가 발생하고 장부 내역과 카테고리가 변경되지 않는다.")
    void deleteCategoryByMember() {
        // given
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category(1L, "회식비")));
        givenAgencyUser(AgencyUserRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> categoryService.deleteCategory(USER_ID, 1L))
                .isInstanceOf(InvalidAccessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_AGENCY_USER_ACCESS);
        then(eventPublisher).should(never()).publishEvent(any());
        then(categoryRepository).should(never()).delete(any());
    }

    private void givenAgencyUser(AgencyUserRole role) {
        Agency agency = Agency.builder().id(AGENCY_ID).agencyName("머니몽 동아리").build();
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(AgencyUser.builder().id(50L).agency(agency).userId(USER_ID).agencyUserRole(role).build()));
    }

    private Category category(Long id, String name) {
        return Category.builder()
                .id(id)
                .name(name)
                .agencyId(AGENCY_ID)
                .build();
    }
}
