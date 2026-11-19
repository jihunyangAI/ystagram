package com.yangstagram.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 의 yangstagram.seed.* 설정 */
@ConfigurationProperties(prefix = "yangstagram.seed")
public record SeedProperties(boolean onStartupIfEmpty, SeedSize size, long value, String fakePassword) {

    public SeedProperties {
        if (size == null) {
            size = SeedSize.SMALL;
        }
        if (fakePassword == null || fakePassword.isBlank()) {
            fakePassword = "yang1234";
        }
    }
}
