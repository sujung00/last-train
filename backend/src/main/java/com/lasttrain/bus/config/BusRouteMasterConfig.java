package com.lasttrain.bus.config;

import com.lasttrain.bus.service.BusRouteMasterLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

/**
 * 버스 노선 마스터 데이터 초기화 Configuration
 *
 * 역할:
 *   - 앱 시작 후 BusRouteMasterLoader를 호출하여 마스터 데이터 초기화
 *   - 데이터베이스에 기본 버스 노선 데이터를 준비
 *   - BusBatchScheduler가 시작되기 전에 데이터 존재 보장
 *
 * 시점:
 *   - Spring ApplicationContext 시작 완료 후 (ContextRefreshedEvent)
 *   - 모든 Bean이 로드된 후 실행되므로 안전
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class BusRouteMasterConfig {

    private final BusRouteMasterLoader busRouteMasterLoader;

    /**
     * Spring 컨텍스트 시작 후 마스터 데이터 초기화
     *
     * 이벤트: ContextRefreshedEvent
     *   - Spring ApplicationContext가 완전히 초기화된 후 발생
     *   - 모든 Bean이 생성되고 의존성이 주입된 후 실행
     *   - 따라서 BusRouteMasterLoader를 안전하게 호출 가능
     */
    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationStart() {
        log.info("[BusRouteMasterConfig] Spring 컨텍스트 시작 완료, 버스 노선 마스터 데이터 초기화 시작");

        try {
            busRouteMasterLoader.initializeBusRoutes();
            log.info("[BusRouteMasterConfig] 버스 노선 마스터 데이터 초기화 완료");
            log.info(busRouteMasterLoader.getStatistics());
        } catch (Exception e) {
            log.error("[BusRouteMasterConfig] 버스 노선 마스터 데이터 초기화 중 오류 발생", e);
            // 초기화 실패 시에도 앱 시작은 계속되도록 함 (필요시 변경 가능)
        }
    }
}
