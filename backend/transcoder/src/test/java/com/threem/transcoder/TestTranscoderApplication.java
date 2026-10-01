package com.threem.transcoder;

import org.springframework.boot.SpringApplication;

/**
 * docker compose 없이 Testcontainers로 인프라를 띄워 앱을 실행한다: {@code ./gradlew bootTestRun}
 */
public class TestTranscoderApplication {

    public static void main(String[] args) {
        SpringApplication.from(TranscoderApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
