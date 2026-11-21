package com.yangstagram.media;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 미디어 파일 위치.
 *
 * @param storageDir 업로드한 파일을 저장하는 폴더 (Git 제외)
 * @param seedDir    가짜 데이터용 사진 폴더 (seed-media/download.mjs로 받아 둠, Git 제외)
 */
@ConfigurationProperties("yangstagram.media")
public record MediaProperties(Path storageDir, Path seedDir) {

    public MediaProperties {
        if (storageDir == null) storageDir = Path.of("../storage");
        if (seedDir == null) seedDir = Path.of("../seed-media/photos");
        storageDir = storageDir.toAbsolutePath().normalize();
        seedDir = seedDir.toAbsolutePath().normalize();
    }

    public Path uploadDir() {
        return storageDir.resolve("uploads");
    }
}
