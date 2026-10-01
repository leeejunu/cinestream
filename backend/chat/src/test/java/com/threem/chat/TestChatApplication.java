package com.threem.chat;

import org.springframework.boot.SpringApplication;

/**
 * docker compose 없이 Testcontainers로 인프라를 띄워 앱을 실행한다: {@code ./gradlew bootTestRun}
 */
public class TestChatApplication {

    public static void main(String[] args) {
        SpringApplication.from(ChatApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
