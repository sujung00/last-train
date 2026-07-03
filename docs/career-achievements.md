# 막차알리미 (Late Train Alert) - 프로젝트 성과

## 1. 외부 API 통합 및 Fallback 전략

- ODsay·서울버스·경기버스 3종 외부 API를 통합하고 API 장애 시 DB Fallback 자동 전환 구조 설계
- 352회 대규모 성능 테스트 기반 **97.44% API 성공률·평균 15.68ms 응답 시간** 달성 (DB Fallback 시 7.78ms)

## 2. 도메인별 캐시 전략 차별화

- 지하철: 스케줄러 기반 사전 적재(Eager Caching) → 요청 지연 최소화
- 버스: 요청 시 즉시 저장(Lazy Caching) → DB 저장소 효율성 극대화
- 각 캐시 전략이 도메인 특성(데이터 변경 빈도)에 최적화되도록 구현

## 3. 인증 및 보안

- JWT 기반 토큰 발급(Access Token + Refresh Token) + Redis Refresh Token 저장(7일 TTL)으로 **Refresh Token Rotation** 구현 및 탈취된 토큰 무한 재발급 방지
- BCrypt 단방향 해시 기반 비밀번호 암호화로 DB 유출 시 평문 노출 방지
- 이메일 유무/비밀번호 틀림을 동일 에러코드로 응답해 계정 열거 공격(Account Enumeration) 방어

## 4. 알림 시스템

- Web Push 기반 실시간 막차 알림(VAPID 표준) + Redis Delay Queue로 예약된 알림 관리
- 같은 브라우저 재구독 시 기존 예약을 Redis에서 먼저 취소한 후 DB 삭제하는 **upsert 패턴**으로 동시성 안전성 확보

## 5. 테스트 및 검증

- TransitCacheService 13개 단위 테스트 작성으로 **41% → 73% 커버리지 달성**(Instructions 기준: 351→625개)
- API 실패·예외 처리·Fallback 시나리오·메트릭 시스템까지 포괄적 검증(7개 신규 테스트로 가능)
- 전체 39개 테스트 중 **100% 통과**로 안정성 검증 (AuthService 100%, FavoriteService 96% 커버리지)

## 6. 성능 측정 및 모니터링

- System.currentTimeMillis() 기반 HTTP 호출 전후 시간 측정으로 **외부 API 응답 시간 정확 측정**(15.68ms)
- AtomicInteger/AtomicLong 동시성 안전 카운터로 API 성공/실패·Fallback 히트/미스 통계 수집
- GET /admin/transit/metrics 엔드포인트로 실시간 성과 지표 조회 및 리셋 기능 제공

## 7. 예외 처리 및 안정성

- RuntimeException 발생 시에도 자동으로 DB Fallback을 시도하는 **다층 방어 로직** 구현
- API 호출 실패 + DB 미스 상황(최악의 경우)도 정상 처리(null 반환)로 서비스 연속성 보장
- 로깅(DEBUG/WARN) 기반 문제 추적으로 장애 원인 파악 용이

## 8. 아키텍처 및 설계

- 실시간 API 우선 조회 → JSON 파싱 → DB 저장 → 반환 흐름으로 **항상 최신 데이터 제공**
- 캐시 키를 transitType + cacheKey + dayType 조합으로 설계해 요일별 데이터 구분 관리
- 외부 API 클라이언트(OdsayClient·SeoulBusArrivalClient·GyeonggiBusRouteClient)를 주입받아 **느슨한 결합** 구현
