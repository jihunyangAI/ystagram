package com.yangstagram.user;

import java.time.LocalDate;
import java.time.Period;

/**
 * 10대 계정 (만 18세 미만) 기본 보호 규칙.
 * <ul>
 *   <li>가입하면 비공개 계정으로 시작</li>
 *   <li>내가 팔로우하지 않는 사람은 메시지를 보낼 수 없음 (§6.6)</li>
 *   <li>내가 팔로우하지 않는 사람은 태그·멘션할 수 없음</li>
 *   <li>관심사 기반 광고 타기팅을 하지 않음</li>
 * </ul>
 */
public final class Teens {

    public static final int ADULT_AGE = 18;

    private Teens() {
    }

    public static boolean isTeen(LocalDate birthDate, LocalDate today) {
        return birthDate != null && Period.between(birthDate, today).getYears() < ADULT_AGE;
    }

    /** users 별칭 u가 10대인가 (SQL 조각) */
    public static String sql(String alias) {
        return "(%s.birth_date > current_date - interval '%d years')".formatted(alias, ADULT_AGE);
    }

    /**
     * 사용자 alias가 :actor에게서 태그·멘션·메시지를 받을 수 있는가: 10대가 아니거나, 10대가 actor를 팔로우한다.
     */
    public static String reachableBy(String alias, String actorParam) {
        return """
                (not %1$s or exists (select 1 from follows tf where tf.follower_id = %2$s.id
                 and tf.following_id = %3$s and tf.status = 'ACCEPTED'))
                """.formatted(sql(alias), alias, actorParam);
    }
}
