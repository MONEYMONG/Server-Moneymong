package com.moneymong.domain.ledger.service.manager;

import com.moneymong.domain.category.event.CategoryDeletedEvent;
import com.moneymong.domain.ledger.entity.LedgerDetail;
import com.moneymong.domain.ledger.repository.LedgerDetailRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 카테고리가 삭제되면 그 카테고리를 쓰던 장부 내역의 카테고리를 비운다.
 *
 * <p>카테고리 삭제와 같은 transaction에서 실행돼야 하므로 {@code @TransactionalEventListener}가 아닌
 * {@code @EventListener}를 사용한다.
 */
@Component
@RequiredArgsConstructor
public class CategoryDeletedEventListener {

    private final LedgerDetailRepository ledgerDetailRepository;

    @EventListener
    public void clearCategoryOfLedgerDetails(CategoryDeletedEvent event) {
        List<LedgerDetail> ledgerDetailsWithCategory = ledgerDetailRepository.findAllByCategory(event.category());
        for (LedgerDetail ledgerDetail : ledgerDetailsWithCategory) {
            ledgerDetail.updateLedgerDetailInfo(
                    ledgerDetail.getStoreInfo(),
                    ledgerDetail.getAmount(),
                    ledgerDetail.getDescription(),
                    ledgerDetail.getPaymentDate(),
                    null // 카테고리를 null로 설정
            );
        }
    }
}
