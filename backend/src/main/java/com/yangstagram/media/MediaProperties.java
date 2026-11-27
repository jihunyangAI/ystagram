package com.yangstagram.media;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 미디어 파일 위치 (backend 폴더 기준 상대 경로 가능).
 * Spring의 String→Path 변환은 "../" 경로를 리소스 경로로 해석해 실패하므로 문자열로 받아 직접 바꾼다.
 *
 * @param storageDir 업로드한 파일을 저장하는 폴더 (Git 제외)
 * @param seedDir    가짜 데이터용 사진 폴더 (seed-media/download.mjs로 받아 둠, Git 제외)
 * @param remote     배포용: 파일을 이 PC 폴더 대신 Supabase Storage에 둔다 (비어 있으면 로컬 폴더)
 */
@ConfigurationProperties("yangstagram.media")
public record MediaProperties(String storageDir, String seedDir, Remote remote) {

    /**
     * Supabase Storage (공개 버킷). /media/** 요청은 {@code url/storage/v1/object/public/bucket/**}로 넘긴다.
     * 버킷 안의 경로는 /media/ 뒤와 같다: seed/…, reels/…, u/…
     *
     * @param url    프로젝트 주소 (https://xxxx.supabase.co)
     * @param key    업로드용 비밀 키 (service_role 또는 secret key) — 환경 변수로만 준다
     * @param bucket 공개 버킷 이름
     */
    public record Remote(String url, String key, String bucket) {

        public boolean enabled() {
            return url != null && !url.isBlank();
        }

        public String publicBase() {
            return trim(url) + "/storage/v1/object/public/" + bucket + "/";
        }

        public String uploadBase() {
            return trim(url) + "/storage/v1/object/" + bucket + "/";
        }

        private static String trim(String u) {
            return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
        }
    }

    public MediaProperties {
        if (remote == null) remote = new Remote(null, null, "media");
        else if (remote.bucket() == null || remote.bucket().isBlank()) remote = new Remote(remote.url(), remote.key(), "media");
    }

    public Path storage() {
        return resolve(storageDir, "../storage");
    }

    public Path seed() {
        return resolve(seedDir, "../seed-media/photos");
    }

    /** 가짜 릴스 영상 폴더: 시드 사진 폴더 옆의 reels/ */
    public Path reels() {
        return seed().getParent().resolve("reels");
    }

    public Path uploadDir() {
        return storage().resolve("uploads");
    }

    private static Path resolve(String value, String fallback) {
        String v = value == null || value.isBlank() ? fallback : value;
        return Path.of(v).toAbsolutePath().normalize();
    }
}
