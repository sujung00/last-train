package com.lasttrain;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

/**
 * TestContainers 기반 통합 테스트 공통 베이스 클래스
 *
 * 사용 방법:
 *   class MyTest extends TestContainerConfig { ... }
 *
 * withReuse(true) 활성화 조건:
 *   ~/.testcontainers.properties 파일에 아래 한 줄 추가 필요
 *   testcontainers.reuse.enable=true
 *   (없으면 withReuse()가 무시되고 매 테스트마다 컨테이너를 새로 띄움)
 *
 * 포함된 컨테이너:
 *   - MySQL 8.0  : JPA/Repository 통합 테스트용
 *   - Redis 7    : 토큰 캐싱, 세션 등 Redis 통합 테스트용
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class TestContainerConfig {

    static final MySQLContainer<?> MYSQL;

    // Redis는 별도 모듈 없이 GenericContainer로 사용 (testcontainers 코어에 포함)
    static final GenericContainer<?> REDIS;

    static {
        MYSQL = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("lasttraindb")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);

        REDIS = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379)
                .withReuse(true);

        MYSQL.start();
        REDIS.start();
    }

    // 컨테이너가 할당한 동적 포트를 Spring 컨텍스트에 주입
    // application-test.yml의 설정을 이 값으로 덮어씀
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}