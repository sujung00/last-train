package com.lasttrain.bus.service;

import com.lasttrain.bus.domain.BusRouteMaster;
import com.lasttrain.bus.repository.BusRouteMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 버스 노선 마스터 데이터 로더
 *
 * 역할:
 *   - 앱 시작 시 기본 버스 노선 데이터 준비 (향후 외부 API로 동적 로드 가능)
 *   - 관리자 요청 시 마스터 데이터 갱신
 *   - 현재는 하드코딩된 데이터로 시작, 추후 API 연동 가능
 *
 * 구현 전략:
 *   - Phase 1: 테스트용 하드코딩된 데이터 몇 개 추가
 *   - Phase 2: 실제 운영에서는 서울/경기/인천 버스 API에서 동적으로 로드
 *   - 현재는 스케줄러가 시작되기 전에 기본 데이터가 존재하도록 보장
 *
 * 사용:
 *   - @PostConstruct로 앱 시작 시 자동 실행
 *   - 또는 관리자 엔드포인트에서 수동 호출
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BusRouteMasterLoader {

    private final BusRouteMasterRepository busRouteMasterRepository;

    /**
     * 앱 시작 시 기본 버스 노선 마스터 데이터 준비
     *
     * 동작:
     *   1. DB에 데이터가 없으면 기본 데이터 삽입
     *   2. 이미 있으면 스킵 (중복 방지)
     *   3. 로그 출력
     *
     * 주의:
     *   - 현재는 테스트용 데이터만 포함
     *   - 실제 운영 시 모든 서울/경기/인천 버스 노선 로드 필요
     */
    public void initializeBusRoutes() {
        try {
            // DB에 데이터가 이미 있으면 스킵
            long existingCount = busRouteMasterRepository.count();
            if (existingCount > 0) {
                log.info("[BusRouteMasterLoader] 버스 노선 마스터 데이터 이미 존재 ({}개), 초기화 스킵",
                    existingCount);
                return;
            }

            log.info("[BusRouteMasterLoader] 버스 노선 마스터 데이터 초기화 시작");

            List<BusRouteMaster> routes = new ArrayList<>();

            // ── 서울 버스 (busCityCode: 1000) ──────────────────────────────────────
            // 예시: 실제 운영에서는 서울 버스 API에서 모든 노선 조회
            routes.add(BusRouteMaster.builder()
                .transitType("BUS_SEOUL")
                .routeId("100100053")
                .routeName("123번 (강남-부천)")
                .busCityCode(1000)
                .status("ACTIVE")
                .build());

            routes.add(BusRouteMaster.builder()
                .transitType("BUS_SEOUL")
                .routeId("100100578")
                .routeName("456번 (명동-여의도)")
                .busCityCode(1000)
                .status("ACTIVE")
                .build());

            routes.add(BusRouteMaster.builder()
                .transitType("BUS_SEOUL")
                .routeId("100100100")
                .routeName("789번 (강남역-인천)")
                .busCityCode(1000)
                .status("ACTIVE")
                .build());

            // ── 경기 버스 (busCityCode: 1050 = 시내버스) ─────────────────────────────
            // 예시: 실제 운영에서는 경기 버스 API에서 모든 노선 조회
            routes.add(BusRouteMaster.builder()
                .transitType("BUS_GYEONGGI")
                .routeId("200000037")
                .routeName("경기99번 (부천-강남)")
                .busCityCode(1050)
                .status("ACTIVE")
                .build());

            routes.add(BusRouteMaster.builder()
                .transitType("BUS_GYEONGGI")
                .routeId("200000100")
                .routeName("경기100번 (수원-서울)")
                .busCityCode(1050)
                .status("ACTIVE")
                .build());

            // ── 경기 마을버스 (busCityCode: 1030) ────────────────────────────────────
            routes.add(BusRouteMaster.builder()
                .transitType("BUS_GYEONGGI")
                .routeId("300000001")
                .routeName("부천01 (마을버스)")
                .busCityCode(1030)
                .status("ACTIVE")
                .build());

            // DB에 저장
            busRouteMasterRepository.saveAll(routes);

            log.info("[BusRouteMasterLoader] 버스 노선 마스터 데이터 초기화 완료 ({}개)",
                routes.size());

        } catch (Exception e) {
            log.error("[BusRouteMasterLoader] 버스 노선 마스터 데이터 초기화 실패", e);
            throw new RuntimeException("버스 노선 마스터 데이터 초기화 실패", e);
        }
    }

    /**
     * 특정 노선 추가 (관리자 기능)
     *
     * @param transitType 버스 타입
     * @param routeId 노선 ID
     * @param routeName 노선명
     * @param busCityCode 버스 도시 코드
     */
    @Transactional
    public void addBusRoute(String transitType, String routeId, String routeName, int busCityCode) {
        // 중복 확인
        if (busRouteMasterRepository
            .findByTransitTypeAndRouteId(transitType, routeId).isPresent()) {
            log.warn("[BusRouteMasterLoader] 이미 존재하는 노선: {}:{}", transitType, routeId);
            return;
        }

        BusRouteMaster route = BusRouteMaster.builder()
            .transitType(transitType)
            .routeId(routeId)
            .routeName(routeName)
            .busCityCode(busCityCode)
            .status("ACTIVE")
            .build();

        busRouteMasterRepository.save(route);
        log.info("[BusRouteMasterLoader] 노선 추가: {}:{}", transitType, routeId);
    }

    /**
     * 특정 노선 비활성화
     *
     * @param transitType 버스 타입
     * @param routeId 노선 ID
     */
    @Transactional
    public void deactivateBusRoute(String transitType, String routeId) {
        busRouteMasterRepository
            .findByTransitTypeAndRouteId(transitType, routeId)
            .ifPresent(route -> {
                route.updateStatus("INACTIVE");
                busRouteMasterRepository.save(route);
                log.info("[BusRouteMasterLoader] 노선 비활성화: {}:{}", transitType, routeId);
            });
    }

    /**
     * 활성 노선 통계
     */
    public String getStatistics() {
        long seoulActive = busRouteMasterRepository
            .findByTransitTypeAndStatus("BUS_SEOUL", "ACTIVE").size();
        long gyeonggiActive = busRouteMasterRepository
            .findByTransitTypeAndStatus("BUS_GYEONGGI", "ACTIVE").size();
        long incheonActive = busRouteMasterRepository
            .findByTransitTypeAndStatus("BUS_INCHEON", "ACTIVE").size();

        return String.format(
            "[버스 노선 마스터 통계]\n" +
            "서울 버스: %d개\n" +
            "경기 버스: %d개\n" +
            "인천 버스: %d개\n" +
            "합계: %d개",
            seoulActive, gyeonggiActive, incheonActive,
            seoulActive + gyeonggiActive + incheonActive
        );
    }
}
