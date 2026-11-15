package com.moneymong.domain.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.moneymong.domain.category.api.response.CategoryResponse;
import com.moneymong.domain.category.entity.Category;
import com.moneymong.domain.category.repository.CategoryRepository;
import com.moneymong.domain.ledger.entity.LedgerDetail;
import com.moneymong.domain.ledger.repository.LedgerDetailRepository;
import com.moneymong.global.exception.custom.BusinessException;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Long AGENCY_ID = 10L;

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private LedgerDetailRepository ledgerDetailRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    @DisplayName("카테고리를 생성하면 저장된 카테고리 정보를 반환한다.")
    void createCategory() {
        // given
        given(categoryRepository.save(any(Category.class))).willReturn(category(1L, "회식비"));

        // when
        CategoryResponse response = categoryService.createCategory("회식비", AGENCY_ID);

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
    @DisplayName("소속의 카테고리 목록을 조회한다.")
    void getCategoriesByAgencyId() {
        // given
        given(categoryRepository.findByAgencyId(AGENCY_ID))
                .willReturn(List.of(category(1L, "회식비"), category(2L, "간식비")));

        // when
        List<CategoryResponse> responses = categoryService.getCategoriesByAgencyId(AGENCY_ID);

        // then
        assertThat(responses)
                .extracting(CategoryResponse::getId, CategoryResponse::getName)
                .containsExactly(tuple(1L, "회식비"), tuple(2L, "간식비"));
    }

    @Test
    @DisplayName("카테고리를 삭제하면 해당 카테고리를 쓰던 장부 내역의 카테고리만 비우고 나머지 정보는 유지한다.")
    void deleteCategory() {
        // given
        Category category = category(1L, "회식비");
        ZonedDateTime paymentDate = ZonedDateTime.parse("2024-01-01T10:00:00+09:00");
        LedgerDetail first = ledgerDetail("가게A", 1000, "설명A", paymentDate, category);
        LedgerDetail second = ledgerDetail("가게B", 2000, "설명B", paymentDate, category);
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(ledgerDetailRepository.findAllByCategory(category)).willReturn(List.of(first, second));

        // when
        categoryService.deleteCategory(1L);

        // then
        assertThat(List.of(first, second)).allSatisfy(detail -> assertThat(detail.getCategory()).isNull());
        assertThat(first.getStoreInfo()).isEqualTo("가게A");
        assertThat(first.getAmount()).isEqualTo(1000);
        assertThat(first.getDescription()).isEqualTo("설명A");
        assertThat(first.getPaymentDate()).isEqualTo(paymentDate);
        assertThat(second.getAmount()).isEqualTo(2000);
        then(categoryRepository).should().delete(category);
    }

    @Test
    @DisplayName("존재하지 않는 카테고리를 삭제하면 예외가 발생한다.")
    void deleteNotExistingCategory() {
        // given
        given(categoryRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> categoryService.deleteCategory(1L))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LEDGER_CATEGORY_NOT_FOUND);
        then(ledgerDetailRepository).should(never()).findAllByCategory(any());
        then(categoryRepository).should(never()).delete(any());
    }

    private Category category(Long id, String name) {
        return Category.builder()
                .id(id)
                .name(name)
                .agencyId(AGENCY_ID)
                .build();
    }

    private LedgerDetail ledgerDetail(String storeInfo, int amount, String description,
                                      ZonedDateTime paymentDate, Category category) {
        return LedgerDetail.builder()
                .storeInfo(storeInfo)
                .amount(amount)
                .description(description)
                .paymentDate(paymentDate)
                .category(category)
                .build();
    }
}
