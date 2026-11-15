package com.moneymong.domain.ledger.service.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.moneymong.domain.category.entity.Category;
import com.moneymong.domain.category.event.CategoryDeletedEvent;
import com.moneymong.domain.ledger.entity.LedgerDetail;
import com.moneymong.domain.ledger.repository.LedgerDetailRepository;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class CategoryDeletedEventListenerTest {

    private final LedgerDetailRepository ledgerDetailRepository = mock(LedgerDetailRepository.class);

    @Test
    @DisplayName("카테고리 삭제 이벤트를 받으면 해당 카테고리를 쓰던 장부 내역의 카테고리만 비우고 나머지 정보는 유지한다.")
    void clearCategoryOfLedgerDetails() {
        // given
        Category category = category();
        ZonedDateTime paymentDate = ZonedDateTime.parse("2024-01-01T10:00:00+09:00");
        LedgerDetail first = ledgerDetail("가게A", 1000, "설명A", paymentDate, category);
        LedgerDetail second = ledgerDetail("가게B", 2000, "설명B", paymentDate, category);
        given(ledgerDetailRepository.findAllByCategory(category)).willReturn(List.of(first, second));

        // when
        new CategoryDeletedEventListener(ledgerDetailRepository)
                .clearCategoryOfLedgerDetails(new CategoryDeletedEvent(category));

        // then
        assertThat(List.of(first, second)).allSatisfy(detail -> assertThat(detail.getCategory()).isNull());
        assertThat(first.getStoreInfo()).isEqualTo("가게A");
        assertThat(first.getAmount()).isEqualTo(1000);
        assertThat(first.getDescription()).isEqualTo("설명A");
        assertThat(first.getPaymentDate()).isEqualTo(paymentDate);
        assertThat(second.getAmount()).isEqualTo(2000);
    }

    @Test
    @DisplayName("Spring에서 카테고리 삭제 이벤트를 발행하면 listener가 동기로 실행된다.")
    void listenerIsInvokedByApplicationEvent() {
        // given
        Category category = category();
        LedgerDetail detail = ledgerDetail("가게A", 1000, "설명A", ZonedDateTime.now(), category);
        given(ledgerDetailRepository.findAllByCategory(category)).willReturn(List.of(detail));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(LedgerDetailRepository.class, () -> ledgerDetailRepository);
            context.registerBean(CategoryDeletedEventListener.class);
            context.refresh();

            // when
            context.publishEvent(new CategoryDeletedEvent(category));
        }

        // then: publishEvent가 반환된 시점에 이미 카테고리가 비워져 있어야 한다.
        assertThat(detail.getCategory()).isNull();
    }

    private Category category() {
        return Category.builder().id(1L).name("회식비").agencyId(10L).build();
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
