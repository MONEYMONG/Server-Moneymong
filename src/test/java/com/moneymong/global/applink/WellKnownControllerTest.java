package com.moneymong.global.applink;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneymong.support.ControllerTestSupport;





import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class WellKnownControllerTest extends ControllerTestSupport {

    @Override
    protected Object controller() {
        return new WellKnownController("well-known/assetlinks-dev.json");
    }

    @Test
    @DisplayName("Apple App Site Association 파일을 JSON으로 제공한다.")
    void appleAppSiteAssociation() throws Exception {
        mockMvc.perform(get("/.well-known/apple-app-site-association"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.applinks").exists());
    }

    @Test
    @DisplayName("설정된 Android assetlinks 파일을 JSON으로 제공한다.")
    void androidAssetLinks() throws Exception {
        mockMvc.perform(get("/.well-known/assetlinks.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].target.package_name").value("com.moneymong.moneymong.tb"));
    }
}
