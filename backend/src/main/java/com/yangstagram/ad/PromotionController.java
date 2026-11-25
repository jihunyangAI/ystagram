package com.yangstagram.ad;

import com.yangstagram.ad.PromotionService.PromoteRequest;
import com.yangstagram.ad.PromotionService.Promotion;
import com.yangstagram.auth.CurrentUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 홍보하기 (가짜 광고 캠페인) */
@RestController
public class PromotionController {

    private final PromotionService promotions;

    public PromotionController(PromotionService promotions) {
        this.promotions = promotions;
    }

    @PostMapping("/api/posts/{shortcode}/promotion")
    public Promotion promote(@PathVariable String shortcode, @RequestBody PromoteRequest req) {
        return promotions.promote(shortcode, CurrentUser.requireId(), req);
    }

    @GetMapping("/api/posts/{shortcode}/promotion")
    public Promotion stats(@PathVariable String shortcode) {
        return promotions.stats(shortcode, CurrentUser.requireId());
    }

    @DeleteMapping("/api/posts/{shortcode}/promotion")
    public Promotion stop(@PathVariable String shortcode) {
        return promotions.stop(shortcode, CurrentUser.requireId());
    }
}
