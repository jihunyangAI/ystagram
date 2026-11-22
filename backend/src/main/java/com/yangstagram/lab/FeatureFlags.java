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
     * @param adsPerPage          홈 피드 한 쪽에 끼우는 광고 수 (0 = 광고 끔, 최대 3)
     * @param autoReply           가짜 사용자에게 DM을 보내면 몇 초 뒤 답장한다
     * @param recommendAiProfiles AI 생성 프로필의 게시물도 추천(홈 추천·탐색·릴스)에 넣는다
     * @param liveBots            가짜 라이브(영상 반복 재생)를 몇 개 켜 두고, 라이브에 가짜 시청자 댓글·하트를 보낸다
     */
    public record Flags(int hashtagLimit, String navLayout, boolean hideLikeCounts, int feedSuggestedPerPage,
                        Integer adsPerPage, Boolean autoReply, Boolean recommendAiProfiles,
                        Boolean liveBots) {

        public static final Flags DEFAULT = new Flags(5, "NEW", false, 2, 1, true, false, true);

        /** 예전에 저장한 값에는 새 항목이 없으므로 기본값으로 채운다 */
        Flags withDefaults() {
            return new Flags(hashtagLimit, navLayout, hideLikeCounts, feedSuggestedPerPage,
                    adsPerPage == null ? DEFAULT.adsPerPage : adsPerPage,
                    autoReply == null ? DEFAULT.autoReply : autoReply,
                    recommendAiProfiles == null ? DEFAULT.recommendAiProfiles : recommendAiProfiles,
                    liveBots == null ? DEFAULT.liveBots : liveBots);
        }

        public int ads() {
            return adsPerPage == null ? DEFAULT.adsPerPage : adsPerPage;
        }

        public boolean bot() {
            return autoReply == null ? DEFAULT.autoReply : autoReply;
        }

        public boolean lives() {
            return liveBots == null ? DEFAULT.liveBots : liveBots;
        }

        public boolean aiInRecs() {
            return recommendAiProfiles == null ? DEFAULT.recommendAiProfiles : recommendAiProfiles;
        }

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
            if (adsPerPage != null && (adsPerPage < 0 || adsPerPage > 3)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_FLAG", "광고 수는 0~3 사이입니다.");
            }
            return withDefaults();
        }
    }

    public Flags get() {
        return settings.get(KEY, Flags.class).orElse(Flags.DEFAULT).withDefaults();
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
