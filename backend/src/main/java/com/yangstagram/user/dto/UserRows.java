package com.yangstagram.user.dto;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JDBC 조회 결과에서 {@link UserSummary}를 만든다.
 * 쿼리에는 {@link #COLUMNS}를 별칭 접두사와 함께 넣는다. 예: {@code select ${UserRows.cols("u")} ...}
 */
public final class UserRows {

    private UserRows() {
    }

    /** 별칭 a의 사용자 컬럼을 "a_id, a_username ..." 형태로 고른다. */
    public static String cols(String a) {
        return String.join(", ",
                a + ".id as " + a + "_id",
                a + ".username as " + a + "_username",
                a + ".full_name as " + a + "_full_name",
                a + ".avatar_url as " + a + "_avatar_url",
                a + ".avatar_seed as " + a + "_avatar_seed",
                a + ".is_verified as " + a + "_is_verified",
                a + ".is_private as " + a + "_is_private");
    }

    public static UserSummary read(ResultSet rs, String a) throws SQLException {
        return new UserSummary(
                rs.getLong(a + "_id"),
                rs.getString(a + "_username"),
                rs.getString(a + "_full_name"),
                rs.getString(a + "_avatar_url"),
                rs.getString(a + "_avatar_seed"),
                rs.getBoolean(a + "_is_verified"),
                rs.getBoolean(a + "_is_private"));
    }
}
