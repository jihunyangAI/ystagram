package com.yangstagram.lab;

import com.yangstagram.ad.AdService;
import com.yangstagram.ad.AdService.AdStat;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lab 가짜 광고: 광고별 노출·클릭·숨기기, 켜고 끄기 */
@RestController
@RequestMapping("/api/lab/ads")
@ConditionalOnProperty(prefix = "yangstagram.lab", name = "enabled", havingValue = "true")
public class LabAdsController {

    private final AdService ads;

    public LabAdsController(AdService ads) {
        this.ads = ads;
    }

    @GetMapping
    public List<AdStat> list() {
        return ads.stats();
    }

    public record ActiveRequest(boolean active) {
    }

    @PutMapping("/{id}")
    public List<AdStat> setActive(@PathVariable long id, @RequestBody ActiveRequest req) {
        ads.setActive(id, req.active());
        return ads.stats();
    }
}
