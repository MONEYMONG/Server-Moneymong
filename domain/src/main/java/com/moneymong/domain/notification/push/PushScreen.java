package com.moneymong.domain.notification.push;

/**
 * 알림을 눌렀을 때 앱이 여는 화면. FCM data의 {@code screen}으로 보낸다.
 *
 * <p>앱이 모르는 값을 받으면 앱만 연다. 새 화면을 추가하면 앱도 함께 배포해야 한다.
 */
public enum PushScreen {
    // 장부 화면. agencyId가 필요하다.
    LEDGER
}
