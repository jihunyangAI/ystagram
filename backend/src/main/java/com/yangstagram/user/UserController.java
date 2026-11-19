package com.yangstagram.user;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.user.dto.ProfileResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{username}")
    public ProfileResponse profile(@PathVariable String username) {
        User user = userService.getByUsername(username);
        boolean me = CurrentUser.id().map(id -> id.equals(user.getId())).orElse(false);
        return ProfileResponse.of(user, me);
    }
}
