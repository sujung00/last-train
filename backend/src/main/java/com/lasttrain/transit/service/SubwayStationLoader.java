package com.lasttrain.transit.service;

import com.lasttrain.transit.domain.SubwayStationMaster;
import com.lasttrain.transit.repository.SubwayStationMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 전철역 마스터 데이터 CSV 로더
 *
 * 역할:
 *   - classpath:data/subway_stations.csv 파일을 읽음
 *   - 각 행을 SubwayStationMaster 엔티티로 변환
 *   - subway_station_master 테이블에 일괄 적재
 *
 * 동작 타이밍:
 *   - Spring Boot 애플리케이션이 완전히 시작된 후 1회만 실행
 *   - 이미 데이터가 있으면 스킵 (중복 적재 방지)
 *
 * CSV 파일 형식:
 *   전철역코드, 전철역명, 전철명명(영문), 호선, 외부코드, ...
 *   예시:
 *     "0150", "서울역", "Seoul", "1호선", "136", ...
 *     "0151", "서대문역", "Seodaemun", "5호선", "555", ...
 *
 * 예외 처리:
 *   - 파싱 실패한 행은 WARN 로그 출력 후 스킵
 *   - 외부코드가 비어있으면 해당 행 스킵
 *   - 예: "100-1" 같은 형식도 저장함 (숫자만 요구하지 않음)
 *
 * 로그 레벨:
 *   - 시작 전: DEBUG (조용히)
 *   - 파싱 실패: WARN
 *   - 완료: INFO (저장된 역 개수 포함)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubwayStationLoader {

    private final SubwayStationMasterRepository subwayStationMasterRepository;

    /**
     * Spring Boot 애플리케이션이 완전히 시작된 직후 자동 실행
     *
     * @EventListener(ApplicationReadyEvent.class):
     *   - ApplicationReadyEvent: Spring Context 초기화 + 모든 Bean 생성 완료
     *   - DB 트랜잭션, Repository 등을 안전하게 사용 가능
     *
     * 실행 순서:
     *   1. DB에 이미 데이터가 있는지 확인
     *   2. 있으면 스킵, 없으면 CSV 파일 읽기
     *   3. 각 행 파싱 → 엔티티 생성 → 일괄 저장
     *   4. 완료 로그 출력
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadSubwayStations() {
        // 1단계: 이미 데이터가 있는지 확인
        long existingCount = subwayStationMasterRepository.count();
        if (existingCount > 0) {
            log.debug("전철역 데이터가 이미 {}개 존재합니다. 로더를 스킵합니다.", existingCount);
            return;
        }

        log.debug("전철역 마스터 데이터 로딩을 시작합니다...");

        try {
            // 2단계: CSV 파일 읽기
            List<SubwayStationMaster> stations = readSubwayStationsFromCsv();

            // 3단계: 일괄 저장
            if (!stations.isEmpty()) {
                subwayStationMasterRepository.saveAll(stations);
                log.info("전철역 마스터 데이터 로딩 완료: {}개 역 저장됨", stations.size());
            } else {
                log.warn("CSV 파일에서 유효한 행이 없습니다.");
            }
        } catch (IOException e) {
            log.error("전철역 마스터 데이터 로딩 중 오류 발생", e);
        }
    }

    /**
     * CSV 파일을 읽고 SubwayStationMaster 리스트로 변환
     *
     * CSV 구조 (헤더 제외):
     *   [0]: stationCode (전철역코드, 따옴표 포함)
     *   [1]: stationName (전철역명, 따옴표 포함)
     *   [2]: englishName (영문명, 사용하지 않음)
     *   [3]: lineName (호선, 따옴표 포함)
     *   [4]: odsayStationId (외부코드, 따옴표 포함)
     *   [5+]: 기타 필드들 (사용하지 않음)
     *
     * 예시 CSV 행:
     *   "0150","서울역","Seoul","1호선","136"
     *   ↓
     *   SubwayStationMaster(
     *     stationCode="0150",
     *     stationName="서울역",
     *     lineName="1호선",
     *     odsayStationId="136"
     *   )
     *
     * @return 파싱된 전철역 리스트 (유효하지 않은 행은 제외)
     * @throws IOException CSV 파일 읽기 실패 시
     */
    private List<SubwayStationMaster> readSubwayStationsFromCsv() throws IOException {
        List<SubwayStationMaster> stations = new ArrayList<>();

        // ClassPathResource: classpath에서 리소스 파일 로드
        // 위치: src/main/resources/data/subway_stations.csv
        ClassPathResource resource = new ClassPathResource("data/subway_stations.csv");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // 첫 줄(헤더)은 스킵
                if (lineNumber == 1) {
                    continue;
                }

                try {
                    // CSV 행을 파싱하여 SubwayStationMaster 생성
                    SubwayStationMaster station = parseSubwayStationLine(line, lineNumber);
                    if (station != null) {
                        stations.add(station);
                    }
                } catch (Exception e) {
                    log.warn("{}번 줄 파싱 실패: {}", lineNumber, e.getMessage());
                }
            }
        }

        return stations;
    }

    /**
     * CSV 한 줄을 파싱하여 SubwayStationMaster 엔티티 생성
     *
     * 파싱 규칙:
     *   1. 쉼표(,)로 필드 분리
     *   2. 각 필드의 따옴표(") 제거
     *   3. 각 필드 양 끝의 공백 제거
     *   4. odsayStationId가 비어있으면 null 반환 (스킵)
     *   5. 유효한 필드만 추출하여 빌더로 생성
     *
     * 예시:
     *   입력: "0150","서울역","Seoul","1호선","136"
     *   처리:
     *     필드 분리: ["\"0150\"", "\"서울역\"", "\"Seoul\"", "\"1호선\"", "\"136\""]
     *     따옴표 제거: ["0150", "서울역", "Seoul", "1호선", "136"]
     *     검증: odsayStationId = "136" (비어있지 않음) ✓
     *     결과: SubwayStationMaster 객체 생성
     *
     * @param line CSV 한 줄 (따옴표 포함된 원본)
     * @param lineNumber 줄 번호 (에러 메시지용)
     * @return 파싱된 SubwayStationMaster (실패하면 null)
     */
    private SubwayStationMaster parseSubwayStationLine(String line, int lineNumber) {
        // CSV 행을 쉼표로 분리
        String[] fields = line.split(",");

        // 필드 개수 검증 (최소 5개 필드 필요)
        if (fields.length < 5) {
            throw new IllegalArgumentException(
                String.format("필드 개수 부족. 예상: 5개 이상, 실제: %d개", fields.length)
            );
        }

        // 각 필드에서 따옴표와 공백 제거
        // 예: "\"0150\"" → "0150"
        String stationCode = removeQuotesAndTrim(fields[0]);
        String stationName = removeQuotesAndTrim(fields[1]);
        // fields[2]는 영문명 (사용하지 않음)
        String lineName = removeQuotesAndTrim(fields[3]);
        String odsayStationId = removeQuotesAndTrim(fields[4]);

        // 외부코드가 비어있으면 스킵
        if (odsayStationId.isEmpty()) {
            log.debug("{}번 줄: 외부코드가 비어있어 스킵합니다.", lineNumber);
            return null;
        }

        // SubwayStationMaster 객체 생성
        // 빌더 패턴: 선택적 필드를 명시적으로 설정 가능
        return SubwayStationMaster.builder()
            .stationCode(stationCode)
            .stationName(stationName)
            .lineName(lineName)
            .odsayStationId(odsayStationId)
            .build();
    }

    /**
     * 문자열에서 따옴표와 공백 제거
     *
     * 예시:
     *   " \"서울역\" " → "서울역"
     *   "\"0150\"" → "0150"
     *   "" → ""
     *
     * 처리 순서:
     *   1. 양 끝 공백 제거 (trim)
     *   2. 앞뒤 따옴표 제거 (replaceAll)
     *   3. 다시 양 끝 공백 제거 (trim)
     *
     * @param value 따옴표가 포함된 문자열
     * @return 따옴표와 공백이 제거된 문자열
     */
    private String removeQuotesAndTrim(String value) {
        return value
            .trim()                  // 양 끝 공백 제거
            .replaceAll("^\"|\"$", "") // 앞뒤 따옴표 제거
            .trim();                 // 다시 양 끝 공백 제거
    }
}
