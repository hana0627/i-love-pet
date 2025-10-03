import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

// 테스트 데이터 (100명 유저, 20개 상품 가정)
const users = new SharedArray('users', function () {
  return Array.from({ length: 100 }, (_, i) => i + 1);
});

const products = new SharedArray('products', function () {
  return Array.from({ length: 20 }, (_, i) => i + 1);
});

// 테스트 설정 (로컬 환경 최적화 - 주문은 조회보다 무거움)
export const options = {
  stages: [
    { duration: '30s', target: 20 },   // 워밍업: 20 VU까지 증가
    { duration: '1m', target: 50 },    // 점진적 증가: 50 VU까지
    { duration: '2m', target: 50 },    // 최대 부하 유지: 50 VU (~25 TPS, Kafka 부하 고려)
    { duration: '30s', target: 0 },    // 점진적 감소: 0 VU로
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'], // P95 응답시간 < 2000ms (주문은 더 느릴 수 있음)
    http_req_failed: ['rate<0.05'],    // 에러율 < 5%
  },
};

export default function () {
  // 주문 생성 요청 데이터
  const payload = JSON.stringify({
    userId: 1,
    method: "카드",
    items: [
      {
        productId: 1,
        productName: '로얄캐닌 고양이 사료 1kg',
        price: 35000,
        quantity: 1,
      }
    ],
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  // 1. 주문 생성
  const createResponse = http.post('http://localhost:8082/api/orders/prepare', payload, params);

  // 주문 생성 응답 검증
  const createCheck = check(createResponse, {
    'order created (200/201)': (r) => r.status === 200 || r.status === 201,
    'has orderId': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.orderId !== undefined || body.id !== undefined;
      } catch {
        return false;
      }
    },
  });

  // 주문 생성 실패 시 폴링 스킵
  if (!createCheck) {
    sleep(2);
    return;
  }

  // 주문 ID 추출
  let orderId;
  try {
    const body = JSON.parse(createResponse.body);
    orderId = body.orderId || body.id;
  } catch (e) {
    sleep(2);
    return;
  }

  // 2. 주문 상태 폴링 (최대 30초, 1초 간격)
  let orderStatus = 'CREATED';
  let pollCount = 0;
  const maxPolls = 30;
  let finalResponse;

  while (orderStatus !== 'PREPARED' && pollCount < maxPolls) {
    sleep(1);
    pollCount++;

    const statusResponse = http.get(`http://localhost:8082/api/orders/${orderId}/status`);

    try {
      const statusBody = JSON.parse(statusResponse.body);
      orderStatus = statusBody.status || statusBody.orderStatus || 'UNKNOWN';
      finalResponse = statusResponse;
    } catch (e) {
      // 파싱 실패 시 계속 폴링
      continue;
    }
  }

  // 3. Kafka 비동기 처리 결과 검증
  check(orderStatus, {
    'order reached PREPARED': orderStatus === 'PREPARED',
  });

  check(pollCount, {
    'polling completed within 30s': pollCount < maxPolls,
  });

  sleep(2);
}

// 테스트 종료 후 결과 저장
export function handleSummary(data) {
  return {
    'load-tests/results/order-create-summary.json': JSON.stringify(data, null, 2),
  };
}