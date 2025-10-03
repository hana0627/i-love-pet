import http from 'k6/http';
import { check, sleep } from 'k6';

// 테스트 설정 (로컬 환경 최적화)
export const options = {
  stages: [
    { duration: '30s', target: 50 },   // 워밍업: 50 VU까지 증가
    { duration: '1m', target: 200 },   // 점진적 증가: 200 VU까지
    { duration: '2m', target: 200 },   // 최대 부하 유지: 200 VU 유지 (~200 TPS)
    { duration: '30s', target: 0 },    // 점진적 감소: 0 VU로
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // P95 응답시간 < 500ms
    http_req_failed: ['rate<0.01'],    // 에러율 < 1%
  },
};

export default function () {
  // 상품 목록 조회
  const response = http.get('http://localhost:8081/api/products');

  // 응답 검증
  check(response, {
    'status is 200': (r) => r.status === 200,
    'has products': (r) => {
      try {
        return JSON.parse(r.body).length > 0;
      } catch {
        return false;
      }
    },
    'response time < 1s': (r) => r.timings.duration < 1000,
  });

  sleep(1);
}

// 테스트 종료 후 결과 저장
export function handleSummary(data) {
  return {
    'load-tests/results/product-list-summary.json': JSON.stringify(data, null, 2),
  };
}