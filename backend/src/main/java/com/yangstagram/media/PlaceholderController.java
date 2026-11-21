package com.yangstagram.media;

import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가짜 데이터용 사진을 아직 내려받지 않았을 때 쓰는 그라데이션 그림.
 * 번호마다 색이 달라서 피드가 단조롭지 않다.
 */
@RestController
public class PlaceholderController {

    @GetMapping(value = "/media/ph/{n}.svg", produces = "image/svg+xml")
    public ResponseEntity<String> placeholder(@PathVariable int n,
                                              @RequestParam(defaultValue = "1080") int w,
                                              @RequestParam(defaultValue = "1350") int h) {
        w = Math.clamp(w, 10, 2000);
        h = Math.clamp(h, 10, 2000);
        int hue = Math.floorMod(n * 47, 360);
        int hue2 = (hue + 40) % 360;
        int cx = w * (30 + Math.floorMod(n, 40)) / 100;
        int cy = h * (25 + Math.floorMod(n, 50)) / 100;
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">
                <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0" stop-color="hsl(%d,55%%,62%%)"/><stop offset="1" stop-color="hsl(%d,60%%,38%%)"/>
                </linearGradient></defs>
                <rect width="100%%" height="100%%" fill="url(#g)"/>
                <circle cx="%d" cy="%d" r="%d" fill="rgba(255,255,255,0.18)"/>
                </svg>
                """.formatted(w, h, w, h, hue, hue2, cx, cy, Math.min(w, h) / 4);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(30))).body(svg);
    }
}
