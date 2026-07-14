# 이력서 성과 항목 - 근거 확인 질문 (2026-07-10)

> **목적**: 이력서에 기재하기 전에 반드시 확인해야 할 5가지 기술적 사항 명시
> 
> **사용 방법**: 각 질문에 답변한 후, `resume-achievements-draft.md`의 해당 섹션을 업데이트하여 최종 이력서 작성

---

## 🔴 **필수 확인 질문**

### ❓ 질문 1: Kakao OAuth2.0 구현 완성도

**질문**: KakaoAuthService에서 실제 "Authorization Code Flow" 구현이 **완성**되었는가?

**세부 확인 사항**:
- [ ] Authorization Code (클라이언트에서 받은 `code`) → Kakao API 호출로 access_token 획득?
- [ ] access_token으로 Kakao에 사용자 정보 조회 (id, email 등)?
- [ ] 획득한 정보로 User 엔티티 저장 (provider='KAKAO', providerId='kakao_id')?
- [ ] 로그인 토큰 (AT/RT) 발급?

**현황**:
- ✅ KakaoCallbackPage.jsx에서 콜백 처리는 확인 (frontend/src/pages/KakaoCallbackPage.jsx)
- ⚠️ 백엔드 KakaoAuthService 구현 상세 미확인

**필요한 근거**:
```
1. KakaoAuthService.java 전체 코드
2. KakaoAuthController.java (POST /auth/kakao/callback 엔드포인트)
3. application.yml의 kakao 설정 (client-id, client-secret, redirect-uri)
```

**이력서 기재 영향도**: 높음 🔴
- "Kakao OAuth2.0 연동" vs "Kakao 콜백 처리" 표현이 완전히 다름
- 미완성 시: "준비 중" 또는 "Frontend 콜백만" 표기

---

### ❓ 질문 2: FavoriteService 미커버 부분

**질문**: test-coverage.md에서 FavoriteService가 "96% 커버리지"라고 했는데, 나머지 4%는 무엇인가?

**세부 확인 사항**:
- [ ] 9개 메서드 모두 테스트 되었는가?
- [ ] 미커버 부분은 "에러 핸들링 경로"인가, 아니면 "특정 메서드"인가?
- [ ] 해당 미커버 부분이 "필수적"인가, 아니면 "엣지 케이스"인가?

**현황**:
- 문서: "Instructions: 121 / 126 (96%)", "미커버 부분: 5 instructions"
- 상세 설명 없음

**필요한 근거**:
```
1. build/reports/jacoco/test/html/com.lasttrain.favorite.service/index.html
   (JaCoCo 리포트에서 FavoriteService 클릭)
2. 빨강으로 표시된 라인 확인
```

**이력서 기재 영향도**: 중간 🟡
- 96% vs 100% 차이는 크지 않음
- 다만 "거의 완벽한" vs "100% 달성" 표현 구분 필요

---

### ❓ 질문 3: TransitCacheWriter 구현 완성도

**질문**: TransitCacheWriter.saveOrUpdate()는 실제로 구현되고 **작동**하는가?

**세부 확인 사항**:
- [ ] TransitCacheWriter.java 파일이 존재하는가?
- [ ] saveOrUpdate(transitType, cacheKey, dayType, lastTime) 메서드 구현?
- [ ] 버스(Lazy Caching) 호출 시에만 DB에 저장하는가?
- [ ] 지하철(Eager Caching) 메서드에서는 호출되지 않는가?

**현황**:
- ✅ TransitCacheService.java에서 호출 코드는 보임 (getSeoulBusLastTime, getGyeonggiBusLastTime)
- ❌ TransitCacheWriter.java 구현체는 미확인

**필요한 근거**:
```
1. backend/src/main/java/com/lasttrain/transit/service/TransitCacheWriter.java
2. 메서드 구현 부분 (UPDATE vs INSERT 로직)
3. LastTransitScheduleRepository.findBy... 호출 부분
```

**이력서 기재 영향도**: 높음 🔴
- "Lazy Caching 구현" vs "Lazy Caching 계획" 완전히 다름
- 실제 작동 확인 필수

---

### ❓ 질문 4: 성능 메트릭의 측정 환경

**질문**: performance-metrics.md의 "97.44% 성공률, 15.68ms"는 어느 환경에서 측정한 결과인가?

**세부 확인 사항**:
- [ ] 측정 환경: localhost:8080 (로컬 dev)? 아니면 AWS 프로덕션?
- [ ] 외부 API 상태: Odsay/서울버스/경기버스 API가 모두 정상 운영 중이었는가?
- [ ] 네트워크: 로컬 → API 직접 호출 vs AWS 네트워크 경유?
- [ ] 환경 변수: DB, Redis 모두 정상 구성?

**현황**:
- 문서: "측정 환경: localhost:8080 개발 서버" (performance-metrics.md:11)
- ✅ test-metrics.sh로 실제 352회 테스트 기록

**필요한 근거**:
```
1. test-metrics.sh 실행 결과 로그
2. 측정 날짜: 2026-07-02 14:00~16:00 (예시, 실제 시간 확인)
3. API 상태 모니터링 (Odsay/서울버스 정상이었는가?)
```

**이력서 기재 영향도**: 높음 🔴
- 로컬 개발 서버 기준 vs 프로덕션 기준 성능 차이 크면 오해 발생
- "개발 환경 테스트 결과" vs "프로덕션 검증 결과" 구분 필요

---

### ❓ 질문 5: Web Push 알림 발송 구현 완성도

**질문**: Web Push 알림이 **실제로 발송되는가**, 아니면 "발송 준비 단계"인가?

**세부 확인 사항**:
- [ ] frontend/public/sw.js에서 push event listener 구현 완료?
- [ ] NotificationQueueService.enqueue() → Redis ZSET 등록까지는 완성?
- [ ] @Scheduled 워커가 실제로 1초마다 Queue 확인?
- [ ] WebPushService에서 VAPID 키로 서명 후 HTTP 요청으로 푸시 발송까지 완성?

**현황**:
- ✅ frontend/public/sw.js 파일 확인 (push event handler 있음)
- ✅ NotificationQueueService.enqueue() 구현 확인
- ⚠️ @Scheduled 워커 구현 미확인
- ⚠️ WebPushService 구현 완성도 미확인

**필요한 근거**:
```
1. backend/src/main/java/com/lasttrain/notification/service/WebPushService.java
   - sendPush() 메서드에서 VAPID 서명 + HTTP POST 구현?
   
2. 스케줄러 클래스 (예: NotificationScheduler.java)
   - @Scheduled(fixedRate = 1000) 메서드
   - Redis ZRANGE로 지금 발송할 항목 확인
   - WebPushService.sendPush() 호출
   
3. 실제 테스트 기록
   - 브라우저에서 푸시 알림이 나타났는가?
   - 콘솔 로그 기록
```

**이력서 기재 영향도**: 높음 🔴
- "Web Push 알림 시스템 구현" vs "Web Push 인프라 준비" 완전히 다름
- 실제 작동 확인 필수

---

## 📋 **확인 질문 정리표**

| # | 질문 | 현황 | 우선순위 | 영향도 | 근거 파일 |
|---|------|------|---------|--------|----------|
| 1 | Kakao OAuth2.0 완성도 | ⚠️ 부분 확인 | 🔴 높음 | 높음 | KakaoAuthService.java |
| 2 | FavoriteService 미커버 | ✅ 확인됨 (96%) | 🟢 낮음 | 중간 | JaCoCo 리포트 |
| 3 | TransitCacheWriter 구현 | ⚠️ 미확인 | 🔴 높음 | 높음 | TransitCacheWriter.java |
| 4 | 성능 측정 환경 | ✅ 로컬 env 확인 | 🟡 중간 | 높음 | test-metrics.sh 로그 |
| 5 | Web Push 발송 | ⚠️ 부분 확인 | 🔴 높음 | 높음 | WebPushService.java |

---

## 🔧 **추가 검증 가능 항목**

아래는 이미 코드에서 확인된 항목들입니다 (재확인 불필요):

### ✅ 이미 확인된 항목

```
✅ 1. User 도메인 설계
   - provider ENUM('EMAIL', 'KAKAO') 확인 (User.java:39-43)
   - (provider, providerId) 복합 유니크 인덱스 (User.java:19-23)

✅ 2. NotificationSubscription/Schedule 도메인
   - (user_id, endpoint) 복합 유니크 (NotificationSubscription.java:31-37)
   - lastBoardTime DATETIME 사용 (NotificationSchedule.java:58-79)

✅ 3. AuthService 인증 로직
   - BCrypt.encode/matches (AuthService.java:40-90)
   - Refresh Token Rotation (AuthService.java:104-116)
   - Account Enumeration 방어 (AuthService.java:71-87)

✅ 4. 성능 측정
   - AtomicInteger/AtomicLong 카운터 (TransitCacheService.java:78-91)
   - System.currentTimeMillis() 측정 (TransitCacheService.java:124-138)
   - 352회 테스트 수행 결과 (performance-metrics.md:9)

✅ 5. 테스트
   - TransitCacheService 13개 테스트 (test-coverage.md:45-63)
   - 39개 테스트 100% 통과 (test-coverage.md:85-98)

✅ 6. Docker/인프라
   - Docker 이미지 빌드 (Dockerfile 확인)
   - docker-compose.yml 로컬 환경 (확인 완료)
   - Flyway 마이그레이션 4개 버전 (trouble-shooting 문서 완성)
```

---

## ✍️ **확인 작업 절차**

1. **상단 5개 질문 중** "필수" 3개 먼저 확인 (1, 3, 5번)
2. **근거 파일 제공**
   - 코드 스니펫 복사
   - 또는 파일 경로 + 라인 번호 지정
3. **회신 형식**
   ```
   질문 1: Kakao OAuth2.0
   - 상태: ✅ 완성 / ⚠️ 부분 완성 / ❌ 미구현
   - 근거 파일: [파일경로]:[라인]
   - 비고: [추가 설명]
   ```
4. **최종 이력서 업데이트**
   - `resume-achievements-draft.md` 해당 섹션 수정
   - 미완성 항목은 "진행 중" 표기

---

## 📅 **예상 타임라인**

| 단계 | 소요 시간 | 상태 |
|------|----------|------|
| 1️⃣ 5개 질문 회신 | 20분 | ⏳ 대기 중 |
| 2️⃣ 근거 자료 검토 | 10분 | ⏳ 대기 중 |
| 3️⃣ 최종 이력서 작성 (한국어) | 30분 | 📅 예정 |
| 4️⃣ 최종 이력서 작성 (영문) | 20분 | 📅 예정 |
| 5️⃣ 면접 대비 Q&A | 30분 | 📅 예정 |

**예상 완료**: 약 110분 (2시간)

---

## 📞 **회신 형식**

아래 템플릿을 사용해 회신해주세요:

```markdown
## 질문 1: Kakao OAuth2.0 구현 완성도
- 상태: [ ] 완성 / [ ] 부분 완성 / [ ] 미구현
- 근거: [파일 경로 또는 코드 스니펫]
- 비고: 

## 질문 2: FavoriteService 미커버 부분
- 상태: [확인됨]
- 미커버 부분: [설명]
- 비고:

## 질문 3: TransitCacheWriter 구현 완성도
- 상태: [ ] 완성 / [ ] 부분 완성 / [ ] 미구현
- 근거: [파일 경로]
- 비고:

## 질문 4: 성능 측정 환경
- 측정 환경: [localhost vs 프로덕션]
- 측정 날짜/시간: [구체적 시간]
- API 상태: [정상 운영]
- 비고:

## 질문 5: Web Push 발송 구현 완성도
- 상태: [ ] 완성 / [ ] 부분 완성 / [ ] 미구현
- 구현 부분:
  - [ ] SW push event handler (완성)
  - [ ] Redis ZSET 등록 (완성)
  - [ ] @Scheduled 워커 (완성 / 미완성)
  - [ ] WebPushService VAPID 발송 (완성 / 미완성)
- 근거: [파일 경로]
- 비고:
```

---

**문서 작성일**: 2026-07-10  
**상태**: 🔴 확인 대기 중  
**다음 액션**: 위 5개 질문에 회신 후 `resume-achievements-draft.md` 최종 업데이트
