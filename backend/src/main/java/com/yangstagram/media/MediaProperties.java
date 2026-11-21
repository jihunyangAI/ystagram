package com.yangstagram.media;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 미디어 파일 위치 (backend 폴더 기준 상대 경로 가능).
 * Spring의 String→Path 변환은 "../" 경로를 리소스 경로로 해석해 실패하므로 문자열로 받아 직접 바꾼다.
 *
 * @param storageDir 업로드한 파일을 저장하는 폴더 (Git 제외)
 * @param seedDir    가짜 데이터용 사진 폴더 (seed-media/download.mjs로 받아 둠, Git 제외)
 */
@ConfigurationProperties("yangstagram.media")
public record MediaProperties(String storageDir, String seedDir) {

    public Path storage() {
        return resolve(storageDir, "../storage");
    }

    public Path seed() {
        return resolve(seedDir, "../seed-media/photos");
    }

    public Path uploadDir() {
        return storage().resolve("uploads");
    }

    private static Path resolve(String value, String fallback) {
        String v = value == null || value.isBlank() ? fallback : value;
        return Path.of(v).toAbsolutePath().normalize();
    }
}
