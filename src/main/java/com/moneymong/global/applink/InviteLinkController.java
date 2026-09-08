package com.moneymong.global.applink;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InviteLinkController {

    private final URI appStoreUri;
    private final URI playStoreUri;

    public InviteLinkController(
            @Value("${applink.app-store-url}") String appStoreUrl,
            @Value("${applink.play-store-url}") String playStoreUrl
    ) {
        this.appStoreUri = URI.create(appStoreUrl);
        this.playStoreUri = URI.create(playStoreUrl);
    }

    /**
     * 앱이 설치된 기기에서는 유니버설 링크/앱 링크로 앱이 바로 열리고,
     * 앱이 없는 경우에만 브라우저가 이 엔드포인트로 들어오므로 스토어로 보낸다.
     */
    @GetMapping("/invite")
    public ResponseEntity<Void> redirectToStore(
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        URI storeUri = isIos(userAgent) ? appStoreUri : playStoreUri;
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(storeUri)
                .build();
    }

    private boolean isIos(String userAgent) {
        if (userAgent == null) {
            return false;
        }
        String lowerCase = userAgent.toLowerCase();
        return lowerCase.contains("iphone") || lowerCase.contains("ipad") || lowerCase.contains("ipod");
    }
}
