package com.yangstagram.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** Lab 계정 전환기용: 사용자 이름·이름으로 찾고 팔로워 많은 순. */
    @Query("""
            select u from User u
            where u.deletedAt is null
              and (:q = '' or lower(u.username) like lower(concat('%', :q, '%'))
                           or lower(u.fullName) like lower(concat('%', :q, '%')))
            order by u.followersCount desc, u.id asc
            """)
    List<User> searchForLab(@Param("q") String q, Pageable pageable);
}
