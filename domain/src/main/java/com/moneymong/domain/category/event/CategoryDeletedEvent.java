package com.moneymong.domain.category.event;

import com.moneymong.domain.category.entity.Category;

/**
 * 카테고리 삭제 직전에 발행된다.
 *
 * <p>listener는 삭제와 같은 transaction 안에서 동기로 실행된다.
 * 카테고리를 참조하는 데이터는 이 이벤트를 받아 참조를 정리해야 한다.
 */
public record CategoryDeletedEvent(Category category) {
}
