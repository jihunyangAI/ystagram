package com.yangstagram.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(unique = true)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 64)
    private String fullName;

    @Column(length = 150)
    private String bio;

    private String avatarUrl;

    @Column(nullable = false, length = 64)
    private String avatarSeed;

    @Column(length = 30)
    private String pronouns;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType accountType = AccountType.PERSONAL;

    @Column(length = 64)
    private String category;

    @Column(name = "is_private", nullable = false)
    private boolean privateAccount;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    @Column(name = "is_ai_profile", nullable = false)
    private boolean aiProfile;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(name = "is_fake", nullable = false)
    private boolean fake;

    @Column(length = 200)
    private String interests;

    @Column(nullable = false)
    private int followersCount;

    @Column(nullable = false)
    private int followingCount;

    @Column(nullable = false)
    private int postsCount;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant deletedAt;

    public static User signUp(String username, String email, String passwordHash, String fullName,
                              LocalDate birthDate, Instant now) {
        User user = new User();
        user.username = username;
        user.email = email;
        user.passwordHash = passwordHash;
        user.fullName = fullName;
        user.birthDate = birthDate;
        user.avatarSeed = username;
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }
}
