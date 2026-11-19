package com.yangstagram;

import org.springframework.boot.SpringApplication;

public class TestYangstagramApplication {

	public static void main(String[] args) {
		SpringApplication.from(YangstagramApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
