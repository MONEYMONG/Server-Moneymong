package com.moneymong.global.applink;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/.well-known")
public class WellKnownController {

    private static final Resource AASA_FILE = new ClassPathResource("well-known/apple-app-site-association.json");

    private final Resource assetLinksFile;

    public WellKnownController(@Value("${applink.assetlinks-file}") String assetLinksFile) {
        this.assetLinksFile = new ClassPathResource(assetLinksFile);
    }

    @GetMapping(value = "/apple-app-site-association", produces = MediaType.APPLICATION_JSON_VALUE)
    public Resource getAppleAppSiteAssociation() {
        return AASA_FILE;
    }

    @GetMapping(value = "/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Resource getAndroidAssetLinks() {
        return assetLinksFile;
    }
}
