package com.yangstagram.auth;

import com.yangstagram.user.User;
import com.yangstagram.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** 로그인 시 사용자 이름 또는 이메일로 사용자를 찾는다. */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String login) {
        String key = login.trim().toLowerCase();
        User user = (key.contains("@")
                ? userRepository.findByEmailAndDeletedAtIsNull(key)
                : userRepository.findByUsernameAndDeletedAtIsNull(key))
                .orElseThrow(() -> new UsernameNotFoundException(key));
        return toAuthUser(user);
    }

    public static AuthUser toAuthUser(User user) {
        return new AuthUser(user.getId(), user.getUsername(), user.getPasswordHash());
    }
}
