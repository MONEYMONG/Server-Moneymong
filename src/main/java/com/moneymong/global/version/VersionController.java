package com.moneymong.global.version;

import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.global.version.dto.VersionRequest;
import com.moneymong.global.version.exception.VersionTooLowException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/version")
public class VersionController {

    @Value("${app.minimum.version}")
    private String minimumAppVersion;

    @PostMapping
    public String checkVersion(@RequestBody VersionRequest request) {
        if (isVersionLower(request.getVersion(), minimumAppVersion)) {
            throw new VersionTooLowException(ErrorCode.VERSION_TOO_LOW);
        }

        return "Version is up to date.";
    }

    private boolean isVersionLower(String currentVersion, String minimumVersion) {
        String[] currentParts = currentVersion.split("\\.");
        String[] minimumParts = minimumVersion.split("\\.");

        int length = Math.max(currentParts.length, minimumParts.length);
        for (int i = 0; i < length; i++) {
            int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            int minimumPart = i < minimumParts.length ? Integer.parseInt(minimumParts[i]) : 0;

            if (currentPart < minimumPart) {
                return true;
            } else if (currentPart > minimumPart) {
                return false;
            }
        }
        return false;
    }
}
