package com.yangstagram.lab;

import com.yangstagram.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 기능 플래그 (Lab L09): 정책을 바꿔 가며 비교한다. 값은 lab_settings의 "flags".
 */
@Service
public class FeatureFlags {

    private static final String KEY = "flags";

    private final LabSettings settings;

    public FeatureFlags(LabSettings settings) {
        this.settings = settings;
    }

    /**
     * @param hashtagLimit         게시물당 해시태그 최대 개수 (현재 인스타그램 5, 예전 30)
     * @param navLayout            NEW(릴스·DM이 가운데) / CLASSIC(예전 순서: 홈·검색·만들기·릴스·프로필)
     * @param hideLikeCounts       모든 게시물의 좋아요 수를 숨긴다 (작성자 본인에게는 보임)
     * @param feedSuggestedPerPage 홈 피드 10개 중 추천 게시물 수 (0~5)
     */
    public record Flags(int hashtagLimit, String navLayout, boolean hideLikeCounts, int feedSuggestedPerPage) {

        public static final Flags DEFAULT = new Flags(5, "NEW", false, 2);

        Flags normalized() {
            if (hashtagLimit < 1 || hashtagLimit > 30) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_FLAG", "해시태그 제한은 1~30 사이입니다.");
            }
            if (!"NEW".equals(navLayout) && !"CLASSIC".equals(navLayout)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_FLAG", "탭 배치는 NEW 또는 CLASSIC입니다.");
            }
            if (feedSuggestedPerPage < 0 || feedSuggestedPerPage > 5) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_FLAG", "추천 게시물 수는 0~5 사이입니다.");
            }
            return this;
        }
    }

    public Flags get() {
        return settings.get(KEY, Flags.class).orElse(Flags.DEFAULT);
    }

    public Flags set(Flags flags) {
        settings.put(KEY, flags.normalized());
        return get();
    }

    public Flags reset() {
        settings.remove(KEY);
        return get();
    }
}
