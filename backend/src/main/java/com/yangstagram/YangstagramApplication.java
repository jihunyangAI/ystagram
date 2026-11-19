package com.yangstagram;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class YangstagramApplication {

    public static void main(String[] args) {
        SpringApplication.run(YangstagramApplication.class, args);
    }
}
