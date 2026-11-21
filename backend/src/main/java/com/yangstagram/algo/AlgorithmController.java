package com.yangstagram.algo;

import com.yangstagram.algo.InterestService.AlgorithmView;
import com.yangstagram.auth.CurrentUser;
import com.yangstagram.lab.FeatureFlags;
import com.yangstagram.lab.FeatureFlags.Flags;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원님의 알고리즘(관심 주제)과, 화면이 따라야 하는 기능 플래그 */
@RestController
@RequestMapping("/api")
public class AlgorithmController {

    private final InterestService interests;
    private final FeatureFlags flags;

    public AlgorithmController(InterestService interests, FeatureFlags flags) {
        this.interests = interests;
        this.flags = flags;
    }

    public record TopicChange(String mode) {
    }

    @GetMapping("/me/algorithm")
    public AlgorithmView algorithm() {
        return interests.view(CurrentUser.requireId());
    }

    /** mode: ADD(관심 주제 추가) · REMOVE(추천에서 빼기) · RESET(직접 설정 지우기) */
    @PutMapping("/me/algorithm/{topic}")
    public AlgorithmView change(@PathVariable String topic, @RequestBody TopicChange body) {
        return interests.set(CurrentUser.requireId(), topic, body.mode());
    }

    @GetMapping("/flags")
    public Flags flags() {
        return flags.get();
    }
}
