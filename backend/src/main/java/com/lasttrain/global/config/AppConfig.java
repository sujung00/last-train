package com.lasttrain.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 애플리케이션 전역 설정
 *
 * 외부 API 호출, HTTP 통신 등 공통으로 사용되는 Bean을 정의한다.
 */
@Configuration
public class AppConfig {

    /**
     * RestTemplate Bean 등록
     *
     * 외부 API(서울시 버스 API, 카카오 API 등)와의 HTTP 통신에 사용된다.
     * Spring이 자동으로 의존성 주입해주므로
     * 서비스 클래스에서 @Autowired로 주입받아 사용할 수 있다.
     *
     * @return RestTemplate 인스턴스
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
