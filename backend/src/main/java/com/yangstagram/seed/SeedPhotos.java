package com.yangstagram.seed;

import com.yangstagram.media.MediaProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 가짜 게시물에 쓸 사진 목록.
 * seed-media/photos/manifest.json(download.mjs가 만듦)이 있으면 그 사진을,
 * 없으면 서버가 그려 주는 그라데이션 그림(/media/ph)을 쓴다 → 사진을 안 받아도 시드는 동작.
 */
@Component
class SeedPhotos {

    private static final Logger log = LoggerFactory.getLogger(SeedPhotos.class);

    record Photo(String url, String thumbUrl, int width, int height, String ratio) {
    }

    record ManifestEntry(String file, String thumb, int width, int height, String ratio) {
    }

    private final MediaProperties props;
    private final JsonMapper json;

    SeedPhotos(MediaProperties props, JsonMapper json) {
        this.props = props;
        this.json = json;
    }

    /** 비율별 사진 묶음 (캐러셀은 같은 비율끼리 묶는다) */
    Map<String, List<Photo>> load() {
        Map<String, List<Photo>> byRatio = new LinkedHashMap<>();
        Path manifest = props.seed().resolve("manifest.json");
        if (Files.isRegularFile(manifest)) {
            try {
                ManifestEntry[] entries = json.readValue(manifest.toFile(), ManifestEntry[].class);
                for (ManifestEntry e : entries) {
                    byRatio.computeIfAbsent(e.ratio(), k -> new ArrayList<>()).add(new Photo(
                            "/media/seed/" + e.file(), "/media/seed/" + e.thumb(), e.width(), e.height(), e.ratio()));
                }
                if (!byRatio.isEmpty()) {
                    return byRatio;
                }
            } catch (RuntimeException e) {
                log.warn("사진 목록을 읽지 못해 그라데이션 그림을 씁니다: {}", e.getMessage());
            }
        } else {
            log.info("가짜 데이터 사진이 없어 그라데이션 그림을 씁니다. 사진을 쓰려면: node seed-media/download.mjs");
        }
        // 사진이 없을 때: 번호마다 색이 다른 그림
        int[][] sizes = {{1080, 1350}, {1080, 1080}, {1080, 566}};
        String[] names = {"4:5", "1:1", "1.91:1"};
        int[] counts = {70, 20, 10};
        int n = 0;
        for (int r = 0; r < sizes.length; r++) {
            int w = sizes[r][0];
            int h = sizes[r][1];
            for (int i = 0; i < counts[r]; i++, n++) {
                byRatio.computeIfAbsent(names[r], k -> new ArrayList<>()).add(new Photo(
                        "/media/ph/%d.svg?w=%d&h=%d".formatted(n, w, h),
                        "/media/ph/%d.svg?w=%d&h=%d".formatted(n, 480, h * 480 / w), w, h, names[r]));
            }
        }
        return byRatio;
    }
}
