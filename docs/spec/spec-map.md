# spec-map.md — 메인화면 지도 연동

## 1. 개요

MainPage에 카카오맵을 연동하여 (1) 현재 위치, (2) 출발지·도착지 마커, (3) 막차 경로 폴리라인을 표시한다.
경로 좌표는 ODsay `loadLane` API에서 가져오며, ODsay API 키 보호를 위해 백엔드 프록시 엔드포인트를 경유한다.

- 지도 SDK: 카카오맵 JavaScript SDK + `react-kakao-maps-sdk`
- 작업 범위: 프론트엔드(Phase 1~3) + 백엔드(Phase 3)
- 배포 단위: Phase별로 독립 배포 가능

## 2. 목표 / 비목표

### 목표
- 사용자가 지도에서 현재 위치, 출발·도착 지점, 막차 경로를 시각적으로 확인할 수 있다
- 위치 권한 거부, API 실패 등 실패 케이스에서도 기존 기능(막차 조회)이 깨지지 않는다

### 비목표 (이번 범위 제외)
- 지도 위 정류장/역 탭 인터랙션 (마커 클릭 → 상세 정보)
- 실시간 차량 위치 표시
- 경로 대안 비교 표시
- 지도 기반 출발지/도착지 선택 (기존 PlaceSearchModal 유지)

## 3. 사전 조건

- [ ] 카카오 Developers JavaScript 키 발급 확인 (REST API 키와 별개)
- [ ] 플랫폼 → Web에 `http://localhost:3000` 등록 확인 (배포 주소는 고정 IP/도메인 확정 후 추가)
- [ ] `frontend/index.html`에 SDK 스크립트 추가 (`autoload=false`)
- [ ] `npm install react-kakao-maps-sdk` 완료
- [ ] JavaScript 키는 `.env`의 `VITE_KAKAO_JS_KEY`로 관리, `index.html`에서 `%VITE_KAKAO_JS_KEY%`로 주입

---

## Phase 1: 기본 지도 + 현재 위치

### 화면 요구사항
- MainPage 상단(검색 카드 위 또는 아래 — 구현 시 결정)에 지도 영역 추가
- 지도 높이: 고정 높이(예: `h-64`)로 시작, 기존 스크롤 단일화 구조를 깨지 않을 것
- GPS 좌표 획득 성공 시: 해당 좌표를 `center`로 설정하고 현재 위치 마커 표시
- 기존 MainPage의 GPS 위치 로직을 재사용한다 (중복 `getCurrentPosition` 호출 금지)

### 실패 처리
- 위치 권한 거부 / 획득 실패 시: 서울시청 좌표(37.5665, 126.9780)를 center로 fallback, 현재 위치 마커는 표시하지 않음
- SDK 로드 실패 시(스크립트 차단 등): 지도 영역 대신 안내 문구 표시, 나머지 화면은 정상 동작

### 기술 사항
- `autoload=false`이므로 `kakao.maps.load()` 콜백 이후 렌더링 (react-kakao-maps-sdk의 `useKakaoLoader` 사용 검토)
- 지도 컴포넌트는 `MapView.jsx`(가칭)로 분리, MainPage는 좌표만 props로 전달

### 완료 기준
- 위치 권한 허용/거부 양쪽에서 지도가 정상 표시된다
- 지도 추가 후에도 기존 MainPage 기능(검색, 최근 검색, 탭바)이 동일하게 동작한다

---

## Phase 2: 출발지·도착지 마커

### 화면 요구사항
- PlaceSearchModal에서 선택된 출발지/도착지 좌표를 지도에 마커로 표시
- 출발지·도착지 마커는 현재 위치 마커와 시각적으로 구분 (색상 또는 라벨)
- 두 지점이 모두 설정되면 `LatLngBounds`로 두 마커가 모두 보이도록 지도 범위 자동 조정
- 한 지점만 설정된 경우: 해당 지점을 center로 이동

### 데이터 흐름
- 출발지/도착지 좌표는 MainPage가 이미 보유한 상태(state)를 그대로 사용
- 좌표 출처는 Kakao Local API 검색 결과 (x=경도, y=위도 문자열 → Number 변환 주의)

### 완료 기준
- 출발지만, 도착지만, 둘 다 설정된 세 가지 케이스에서 마커와 지도 범위가 올바르게 표시된다
- 지점 재선택 시 이전 마커가 남지 않는다

---

## Phase 3: 막차 경로 폴리라인

### 배경: ODsay loadLane 흐름
1. 기존 막차 조회에 사용 중인 `searchPubTransPathT` 응답의 `result.path[n].info.mapObj` 값을 확보
2. `mapObj`를 ODsay `loadLane` API에 전달하면 경로 좌표 배열이 반환됨
    - 요청 형식: `mapObject=0:0@{mapObj}` (ODsay 공식 문서 기준으로 구현 시 재확인)
    - 응답: `result.lane[].section[].graphPos[]` — `x`(경도), `y`(위도)
3. 좌표 배열을 카카오맵 `Polyline`으로 렌더링

### 백엔드 작업

#### 3-1. mapObj 응답 포함 (✅ 확인 완료)

**현황:**
- ❌ 현재 `GET /api/v1/last-train` 응답에 `mapObj` **미포함**
- ❌ LastTrainCalculator에서 `path[].info.mapObj` 추출 안 함 (버려짐)
- ❌ RouteResponse.RouteItem에 mapObj 필드 없음
- ❌ LastTransitSchedule DB에 mapObj 컬럼 없음

**필요한 수정 사항:**

**3-1a. RouteResponse.RouteItem에 mapObj 필드 추가**
```java
@Schema(description = "경로 항목")
public record RouteItem(
    @Schema(description = "출발 마감 시각 (HH:mm)", example = "23:11")
    String departureDeadline,

    @Schema(description = "현재 탑승 가능 상태")
    CurrentStatus currentStatus,

    @Schema(description = "환승 구간 목록")
    List<TransferDto> transfers,
    
    @Schema(description = "ODsay loadLane API용 mapObj (Phase 3에서 사용)", example = "0:0@1|0@0")
    String mapObj
) {}
```

**3-1b. LastTrainCalculator.calculate()에서 mapObj 추출**
- `processPath()` 메서드 수정: 각 path에서 `path.path("info").path("mapObj")` 추출
- RouteItem 생성 시 mapObj 값 전달
- 예시: `new RouteResponse.RouteItem(departureDeadline, currentStatus, transfers, mapObj)`

**3-1c. RouteService에서 mapObj를 응답에 포함**
- LastTrainCalculator.calculate()에서 반환된 RouteItem의 mapObj가 응답에 그대로 포함됨 (자동)

**3-1d. 배치/캐시 구조에서 mapObj 저장**
- ⚠️ **현황**: 즐겨찾기 없는 경로에서 API Fallback 시 DB에 저장되지 않음
- 📍 **필요한 수정**:
  1. **LastTransitSchedule 엔티티에 mapObj 컬럼 추가**
     ```java
     @Column(columnDefinition = "TEXT")
     private String mapObj;  // ODsay searchPubTransPathT 응답의 path[].info.mapObj
     ```
  2. **DB 마이그레이션 스크립트** (Flyway/JPA DDL)
     ```sql
     ALTER TABLE last_transit_schedule ADD COLUMN map_obj TEXT;
     ```
  3. **TransitCacheWriter.saveOrUpdate() 메서드 수정**
     - mapObj도 함께 저장하도록 변경 (현재는 lastTime만 저장)
     - 서울/경기 버스의 API Fallback 시 mapObj 전달 필요
  
- ✅ **결과**: API Fallback 후 DB에 저장된 데이터에도 mapObj 포함 → 다음 요청에서 mapObj 반환 가능

**주의**: 즐겨찾기 경로는 이미 배치에서 저장되므로 mapObj 존재, 신규 경로만 영향받음

#### 3-2. loadLane 프록시 엔드포인트
- `GET /api/v1/route/lane?mapObj={mapObj}`
- 동작: ODsay `loadLane` 호출 → 프론트에 필요한 좌표 데이터로 변환하여 반환
- 응답 형식(안):
  ```json
  {
    "sections": [
      {
        "trafficType": 1,
        "coords": [ { "lat": 37.56, "lng": 126.97 }, ... ]
      }
    ]
  }
  ```
    - `trafficType`: 1=지하철, 2=버스 (ODsay 규격, 구현 시 재확인)
- ODsay API 키 '+' 문자 인코딩 이슈 기존 해결 방식(UriComponentsBuilder) 동일 적용
- 예외 처리: AppException + ErrorCode 기존 체계 준수 (외부 API 실패 시 상태코드는 기존 ErrorCode 정책에 맞춰 결정)
- SecurityConfig: 해당 엔드포인트 permitAll 여부 결정 (기존 `/api/v1/last-train`이 permitAll이므로 동일 정책 권장)

#### 3-3. 백엔드 테스트
- loadLane 응답 파싱 단위 테스트 (정상 / 빈 결과 / 파싱 실패)
- 기존 TestContainers 통합 테스트 체계에 맞춰 작성

### 프론트엔드 작업
- ResultPage 또는 MainPage에서 막차 조회 성공 후 `mapObj`로 `/api/v1/route/lane` 호출
- 반환된 sections를 Polyline으로 렌더링
    - 지하철 구간 / 버스 구간 색상 구분 (색상 값은 디자인 확정 시 결정)
- 폴리라인 표시 시 전체 경로가 보이도록 bounds 조정
- 로딩 중: 지도는 유지, 폴리라인만 지연 표시 (스피너 불필요)

### 실패 처리
- loadLane 호출 실패 시: 폴리라인 없이 출발·도착 마커만 유지, 막차 시간 정보는 정상 표시 (경로 시각화는 부가 기능 — 실패가 핵심 기능을 막지 않음)

### 완료 기준
- 지하철 단독 / 버스 단독 / 지하철+버스 혼합 경로에서 폴리라인이 구간별로 그려진다
- loadLane 실패 시에도 막차 조회 결과 화면이 정상 동작한다

---

## 4. 구현 전 확인 필요 사항

- [x] **1. `searchPubTransPathT` 응답 파싱 코드에서 `mapObj` 처리**
  - ✅ 확인 완료: mapObj를 버리고 있음 (추출 안 함)
  - ✅ 수정 계획: LastTrainCalculator에서 `path[].info.mapObj` 추출 → RouteItem에 포함

- [x] **2. 배치/캐시 구조(DB 저장 데이터)에 mapObj 저장**
  - ✅ 확인 완료: DB에 저장 안 함 (LastTransitSchedule에 mapObj 컬럼 없음)
  - ✅ 수정 계획: 
    - LastTransitSchedule에 `mapObj TEXT` 컬럼 추가
    - DB 마이그레이션 스크립트 필요
    - TransitCacheWriter.saveOrUpdate()에 mapObj 저장 로직 추가

- [ ] **3. loadLane 요청/응답 스펙 ODsay 공식 문서 대조**
  - 현재 본 문서의 형식은 초안
  - 요청: `mapObject=0:0@{mapObj}` 형식 검증 필요
  - 응답: `result.lane[].section[].graphPos[].x/y` 구조 검증 필요

- [ ] **4. 지도 표시 위치 및 높이 디자인 결정**
  - MainPage 상단 / 검색 카드 위 / 검색 카드 아래 중 선택
  - 고정 높이 (h-64) vs 동적 높이

- [ ] **5. 폴리라인 렌더링 위치 결정**
  - MainPage에서 렌더링 vs ResultPage에서 렌더링
  - 현재 화면 흐름: MainPage → 검색 → ResultPage이므로 **ResultPage 권장**

## 5. Phase 3 백엔드 구현 세부 계획

### 5-1. DB 마이그레이션 (선행 작업)
```sql
-- Flyway 스크립트: V{version}__add_mapobj_to_transit_schedule.sql
ALTER TABLE last_transit_schedule ADD COLUMN map_obj TEXT;

-- 기존 데이터 NULL 처리 (배치 실행 시 갱신됨)
UPDATE last_transit_schedule SET map_obj = '' WHERE map_obj IS NULL;
```

### 5-2. 백엔드 구현 순서
1. **LastTrainCalculator 수정** (mapObj 추출)
   - `processPath()` 메서드: `path.path("info").path("mapObj").asText()` 추출
   - RouteItem 생성자 호출: mapObj 값 전달
   - 파싱 실패 시: null 반환 (폴리라인 미표시는 비기능 요구)

2. **RouteResponse.RouteItem 수정** (mapObj 필드 추가)
   - record에 String mapObj 필드 추가
   - Swagger @Schema 주석 추가

3. **TransitCacheWriter 수정** (mapObj 저장)
   - `saveOrUpdate()` 메서드: mapObj 파라미터 추가
   - LastTransitSchedule 엔티티 저장 시 mapObj 포함

4. **TransitCacheService 수정** (API Fallback 시 mapObj 저장)
   - `getSeoulBusLastTime()`: API Fallback 후 transitCacheWriter 호출 시 mapObj 전달
   - `getGyeonggiBusLastTime()`: 동일하게 mapObj 전달
   - 지하철의 경우: DB 저장 안 하므로 수정 불필요

5. **LastTransitSchedule 엔티티 수정**
   - mapObj 필드 추가 + Getter 자동 생성

6. **테스트**
   - 단위 테스트: mapObj 추출 검증
   - 통합 테스트: API Fallback 후 DB 저장 검증

### 5-3. 프론트엔드 작업
- 막차 조회 응답에서 mapObj 추출
- mapObj → `/api/v1/route/lane?mapObj={mapObj}` 호출
- 응답 좌표 → Polyline 렌더링

---

## 6. 작업 순서 요약

| Phase | 영역 | 배포 가능 시점 |
|-------|------|----------------|
| 1 | 프론트 | 기본 지도 + 현재 위치 완료 시 |
| 2 | 프론트 | 마커 표시 완료 시 |
| 3 | 백 + 프론트 | mapObj 확보 → 프록시 → 폴리라인 순 |

tasks-map.md에서 Phase별 T-001~ 단위로 세분화한다.