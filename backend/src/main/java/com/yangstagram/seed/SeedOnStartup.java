package com.yangstagram.seed;

import com.yangstagram.user.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** DB가 비어 있으면 시작할 때 가짜 데이터를 만든다. */
@Component
public class SeedOnStartup implements ApplicationRunner {

    private final SeedService seedService;
    private final SeedProperties properties;
    private final UserRepository userRepository;

    public SeedOnStartup(SeedService seedService, SeedProperties properties, UserRepository userRepository) {
        this.seedService = seedService;
        this.properties = properties;
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.onStartupIfEmpty() && userRepository.count() == 0) {
            seedService.generate(properties.size(), properties.value());
        }
    }
}
