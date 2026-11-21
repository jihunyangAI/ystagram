package com.yangstagram.user;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.media.MediaStorage;
import com.yangstagram.media.StoredImage;
import com.yangstagram.user.dto.MeResponse;
import java.time.Instant;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 프로필 편집 (분석 문서 §12: 사진·이름·사용자 이름·소개·링크·대명사) */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private static final Pattern WEBSITE = Pattern.compile("^https?://[^\\s]+\\.[^\\s]+$");

    private final UserRepository userRepository;
    private final UserService userService;
    private final MediaStorage storage;
    private final LabClock clock;

    public MeController(UserRepository userRepository, UserService userService, MediaStorage storage, LabClock clock) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.storage = storage;
        this.clock = clock;
    }

    public record ProfileForm(String fullName, String username, String bio, String pronouns, String website) {
    }

    public record EditableProfile(MeResponse me, String bio, String pronouns, String website) {
        static EditableProfile of(User u) {
            return new EditableProfile(MeResponse.from(u), u.getBio(), u.getPronouns(), u.getWebsite());
        }
    }

    @GetMapping("/profile")
    public EditableProfile get() {
        return EditableProfile.of(userService.getActive(CurrentUser.requireId()));
    }

    @PatchMapping("/profile")
    @Transactional
    public EditableProfile update(@RequestBody ProfileForm form) {
        User user = userService.getActive(CurrentUser.requireId());

        if (form.username() != null) {
            String username = UsernamePolicy.normalize(form.username());
            if (!username.equals(user.getUsername())) {
                String problem = UsernamePolicy.validate(username);
                if (problem != null) throw ApiException.badRequest("INVALID_USERNAME", problem);
                if (userRepository.existsByUsername(username)) {
                    throw ApiException.conflict("USERNAME_TAKEN", "이미 사용 중인 사용자 이름입니다.");
                }
                user.setUsername(username);
            }
        }
        if (form.fullName() != null) {
            String name = form.fullName().strip();
            if (name.length() > 64) throw ApiException.badRequest("NAME_TOO_LONG", "이름은 64자까지 쓸 수 있습니다.");
            user.setFullName(name);
        }
        if (form.bio() != null) {
            String bio = form.bio().strip();
            if (bio.length() > 150) throw ApiException.badRequest("BIO_TOO_LONG", "소개는 150자까지 쓸 수 있습니다.");
            user.setBio(bio.isEmpty() ? null : bio);
        }
        if (form.pronouns() != null) {
            String pronouns = form.pronouns().strip();
            if (pronouns.length() > 30) throw ApiException.badRequest("PRONOUNS_TOO_LONG", "대명사는 30자까지 쓸 수 있습니다.");
            user.setPronouns(pronouns.isEmpty() ? null : pronouns);
        }
        if (form.website() != null) {
            String site = form.website().strip();
            if (!site.isEmpty() && !site.matches("(?i)^https?://.*")) site = "https://" + site;
            if (!site.isEmpty() && (site.length() > 200 || !WEBSITE.matcher(site).matches())) {
                throw ApiException.badRequest("INVALID_WEBSITE", "올바른 링크 주소를 입력해 주세요.");
            }
            user.setWebsite(site.isEmpty() ? null : site);
        }
        user.setUpdatedAt(Instant.now(clock));
        return EditableProfile.of(user);
    }

    /** 프로필 사진 바꾸기 (화면에서 정사각형으로 잘라서 보낸다) */
    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public MeResponse avatar(@RequestPart("file") MultipartFile file) {
        User user = userService.getActive(CurrentUser.requireId());
        StoredImage img = storage.saveImage(file);
        user.setAvatarUrl(img.thumbUrl());
        user.setUpdatedAt(Instant.now(clock));
        return MeResponse.from(user);
    }

    /** 프로필 사진 삭제 → 기본 일러스트 아바타로 */
    @DeleteMapping("/avatar")
    @Transactional
    public MeResponse removeAvatar() {
        User user = userService.getActive(CurrentUser.requireId());
        user.setAvatarUrl(null);
        user.setUpdatedAt(Instant.now(clock));
        return MeResponse.from(user);
    }
}
