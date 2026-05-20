package com.lasttrain;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TestContainers 기반 통합 테스트 공통 베이스 클래스
 *
 * 사용 방법:
 *   class FavoriteRepositoryTest extends TestContainerConfig { ... }
 *
 * withReuse(true) 활성화 조건:
 *   ~/.testcontainers.properties 파일에 아래 한 줄 추가 필요
 *   testcontainers.reuse.enable=true
 *   (없으면 withReuse()가 무시되고 매 테스트마다 컨테이너를 새로 띄움)
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
public abstract class TestContainerConfig {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("lasttraindb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    // 컨테이너가 할당한 동적 포트를 Spring 컨텍스트에 주입
    // application-test.yml의 datasource 설정을 이 값으로 덮어씀
    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
