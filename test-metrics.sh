#!/bin/bash

# =============================================
# 막차알리미 성과 측정 스크립트
# 사용법: chmod +x test-metrics.sh && ./test-metrics.sh
# =============================================

BASE="http://localhost:8080/api/v1/last-train"
METRICS_URL="http://localhost:8080/admin/transit/metrics"
RESET_URL="http://localhost:8080/admin/transit/metrics/reset"
DELAY=0.7

SUCCESS=0
FAIL=0
TOTAL=0

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# 파라미터를 개별로 받아서 --data-urlencode로 한글 인코딩 처리
# 사용법: call_api "레이블" originLat originLng "출발역명" destLat destLng "도착역명"
call_api() {
  local label=$1
  local oLat=$2
  local oLng=$3
  local oName=$4
  local dLat=$5
  local dLng=$6
  local dName=$7
  TOTAL=$((TOTAL + 1))

  echo -ne "  [$TOTAL] $label ... "

  RESPONSE=$(curl -s --max-time 10 -G "$BASE" \
    --data-urlencode "originLat=$oLat" \
    --data-urlencode "originLng=$oLng" \
    --data-urlencode "originName=$oName" \
    --data-urlencode "destLat=$dLat" \
    --data-urlencode "destLng=$dLng" \
    --data-urlencode "destName=$dName")

  if echo "$RESPONSE" | grep -q '"success":true'; then
    echo -e "${GREEN}✅ 성공${NC}"
    SUCCESS=$((SUCCESS + 1))
  else
    echo -e "${RED}❌ 실패${NC}"
    echo "    응답: $(echo "$RESPONSE" | head -c 100)"
    FAIL=$((FAIL + 1))
  fi

  sleep $DELAY
}

# =============================================
echo ""
echo -e "${CYAN}=====================================${NC}"
echo -e "${CYAN}  막차알리미 성과 측정 시작          ${NC}"
echo -e "${CYAN}=====================================${NC}"
echo ""

# 메트릭 초기화
echo -e "${YELLOW}▶ 메트릭 초기화 중...${NC}"
curl -s -X POST "$RESET_URL" > /dev/null
echo -e "  ${GREEN}완료${NC}"
echo ""

# ── 지하철 단일 노선 ──────────────────────────
echo -e "${BLUE}🚇 지하철 단일 노선 (6개)${NC}"

call_api "2호선: 강남역 → 여의도역"      37.4979 127.0276 "강남역"     37.5203 126.9245 "여의도역"
call_api "4호선: 사당역 → 서울역"        37.4764 126.9814 "사당역"     37.5544 126.9706 "서울역"
call_api "5호선: 광화문역 → 방배역"      37.5713 126.9769 "광화문역"   37.4807 127.0088 "방배역"
call_api "9호선: 김포공항 → 신논현역"    37.5597 126.7944 "김포공항역" 37.5040 127.0255 "신논현역"
call_api "1호선: 서울역 → 수원역"        37.5544 126.9706 "서울역"     37.2636 127.0087 "수원역"
call_api "경의중앙선: 서울역 → 홍대입구" 37.5544 126.9706 "서울역"     37.5573 126.9241 "홍대입구역"

echo ""

# ── 서울버스 단일 노선 ────────────────────────
echo -e "${BLUE}🚌 서울버스 단일 노선 (4개)${NC}"

call_api "서울버스: 서울시청 → 강남역"  37.5656 126.9769 "서울시청"          37.4979 127.0276 "강남역"
call_api "서울버스: 동대문 → 명동역"    37.5689 127.0098 "동대문역사문화공원" 37.5636 126.9852 "명동역"
call_api "서울버스: 홍대입구 → 삼성역"  37.5573 126.9241 "홍대입구역"        37.5087 127.0632 "삼성역"
call_api "서울버스: 잠실역 → 신도림역"  37.5132 127.1000 "잠실역"            37.5082 126.8912 "신도림역"

echo ""

# ── 경기버스 단일 노선 ────────────────────────
echo -e "${BLUE}🚍 경기버스 단일 노선 (3개)${NC}"

call_api "경기버스: 수원역 → 동서울터미널" 37.2636 127.0087 "수원역"  37.5454 127.0957 "동서울터미널"
call_api "경기버스: 일산역 → 서울역"       37.6788 126.7679 "일산역"  37.5544 126.9706 "서울역"
call_api "경기버스: 야탑역(분당) → 강남역" 37.4115 127.1270 "야탑역"  37.4979 127.0276 "강남역"

echo ""

# ── 혼합 노선 (버스 + 지하철) ─────────────────
echo -e "${BLUE}🔀 혼합 노선 버스+지하철 (8개)${NC}"

call_api "혼합: 하남시청 → 강남역 (버스→지하철)"     37.5389 127.2167 "하남시청" 37.4979 127.0276 "강남역"
call_api "혼합: 구리역 → 홍대입구 (버스→지하철)"     37.5943 127.1296 "구리역"   37.5573 126.9241 "홍대입구역"
call_api "혼합: 의정부역 → 강남역 (지하철 환승)"     37.7382 127.0471 "의정부역" 37.4979 127.0276 "강남역"
call_api "혼합: 부평역 → 잠실역 (지하철+환승)"       37.4897 126.7219 "부평역"   37.5132 127.1000 "잠실역"
call_api "혼합: 판교역 → 신촌역 (버스→지하철)"       37.3947 127.1112 "판교역"   37.5552 126.9365 "신촌역"
call_api "혼합: 안양역 → 건대입구역 (버스+지하철)"   37.3942 126.9568 "안양역"   37.5403 127.0698 "건대입구역"
call_api "혼합: 장기역(김포) → 여의도역 (버스→지하철)" 37.6236 126.7127 "장기역" 37.5203 126.9245 "여의도역"
call_api "혼합: 화정역(고양) → 강남역 (버스+지하철)" 37.6317 126.8320 "화정역"   37.4979 127.0276 "강남역"

echo ""

# ── 호출 결과 요약 ────────────────────────────
echo -e "${CYAN}=====================================${NC}"
echo -e "${CYAN}  호출 결과 요약${NC}"
echo -e "${CYAN}=====================================${NC}"
echo -e "  전체 호출: ${TOTAL}회"
echo -e "  ${GREEN}성공: ${SUCCESS}회${NC}"
echo -e "  ${RED}실패: ${FAIL}회${NC}"
echo ""

# ── 최종 메트릭 출력 ──────────────────────────
echo -e "${CYAN}=====================================${NC}"
echo -e "${CYAN}  📊 최종 성과 메트릭${NC}"
echo -e "${CYAN}=====================================${NC}"
echo ""

METRICS=$(curl -s "$METRICS_URL")

if echo "$METRICS" | grep -q '"success":true'; then
  echo "$METRICS" | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(data['data'])
" 2>/dev/null || echo "$METRICS"
else
  echo -e "${RED}메트릭 조회 실패. 앱이 실행 중인지 확인해주세요.${NC}"
fi

echo ""
echo -e "${CYAN}=====================================${NC}"
echo -e "${GREEN}  ✅ 측정 완료! 위 수치를 이력서에 사용하세요.${NC}"
echo -e "${CYAN}=====================================${NC}"
echo ""