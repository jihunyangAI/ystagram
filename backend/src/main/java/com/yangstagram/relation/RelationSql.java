package com.yangstagram.relation;

/**
 * 차단·숨기기 조건을 쿼리에 끼워 넣는 조각. 파라미터 :me(보는 사람)가 있어야 한다.
 * 차단은 양방향: 내가 막았든 상대가 나를 막았든 서로의 콘텐츠가 보이지 않는다 (기획서 §6.1 1번).
 */
public final class RelationSql {

    private RelationSql() {
    }

    /** col(사용자 id 컬럼)과 나 사이에 차단이 없다 */
    public static String notBlocked(String col) {
        return " " + """
                not exists (select 1 from blocks bk where (bk.blocker_id = :me and bk.blocked_id = %1$s)
                                                    or (bk.blocker_id = %1$s and bk.blocked_id = :me))
                """.formatted(col);
    }

    /** 내가 col의 게시물을 숨기지 않았다 (피드용) */
    public static String postsNotMuted(String col) {
        return " not exists (select 1 from mutes mu where mu.user_id = :me and mu.muted_id = %s and mu.posts)"
                .formatted(col);
    }

    /** 내가 col의 스토리를 숨기지 않았다 (트레이용) */
    public static String storiesNotMuted(String col) {
        return " not exists (select 1 from mutes mu where mu.user_id = :me and mu.muted_id = %s and mu.stories)"
                .formatted(col);
    }
}
