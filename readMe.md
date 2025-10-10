# 🐾 I Love Pet

I Love Pet은 펫샵 전자상거래 플랫폼을 위한 MSA(Microservice Architecture) 기반 시스템입니다.  
사용자 관리, 상품 카탈로그, 주문, 결제 등 각 도메인을 독립된 서비스로 구성하였으며,  
서비스 간 통신은 Kafka를 활용한 비동기 이벤트 기반 메시징 방식으로 처리됩니다.


## 📅 개발 기간

**2025. 07 ~ 2025. 09 (약 2개월, 1차 MVP)**

### 주요 개발 마일스톤
- **Phase 1** (2025. 08): 프로젝트 초기모델 개발
- **Phase 2** (2025. 09): 서비스간 통신을 WebClient를 이용한 동기방식에서 Kafka를 이용한 이벤트 기반 구조로 변경
- **Phase 3** (2025. 10): MSA 인프라 고도화를 위한 API Gateway 및 Service Discovery 도입


## 🏗 시스템 아키텍처

프로젝트는 4개의 마이크로서비스, API Gateway, Discovery Service, 그리고 React 기반 프론트엔드로 구성됩니다.

<img width="1600" height="1332" alt="제목을-입력해주세요_-003_(2)" src="https://github.com/user-attachments/assets/17023655-587b-49e1-b96d-15a6e7ba1ae8" />


## 🛠 기술 스택

### 백엔드 서비스
- **Language**: Kotlin
- **Framework**: Spring Boot 3.5
- **MSA Infrastructure**:
  - Spring Cloud Gateway (API Gateway)
  - Spring Cloud Netflix Eureka (Service Discovery)
- **Database**: MySQL 8.x (각 서비스별 독립 DB)
- **ORM**: JPA (Hibernate) & QueryDsl
- **Message Queue**: Apache Kafka
- **Cache**: Redis
- **Container**: Docker & Docker Compose

### 프론트엔드
- **Framework**: React 19.1.1
- **Router**: React Router DOM 7.8.0
- **Payment**: TossPayments SDK
- **Testing**: Testing Library

### 인프라
- **Container Orchestration**: Docker Compose
- **Service Discovery**: Spring Cloud Netflix Eureka
- **API Gateway**: Spring Cloud Gateway
- **Message Broker**: Kafka + Zookeeper
- **Cache**: Redis
- **Monitoring**: Kafka UI, Prometheus

## 📋 서비스 목록

| 서비스 | 포트 | 설명 | 주요 기능 |
|--------|------|------|-----------|
| **API Gateway** | 8000 | API 단일 진입점 | 라우팅, 로드밸런싱, CORS 관리 |
| **Discovery Service** | 8761 | 서비스 레지스트리 | 서비스 등록/조회, 헬스체크 |
| **User Service** | Dynamic | 회원 관리 | 회원가입, 조회, 존재확인 |
| **Product Service** | Dynamic | 상품 관리 | 상품 등록/조회, 재고관리 |
| **Order Service** | Dynamic | 주문 관리 | 주문 생성, 상태 관리 |
| **Payment Service** | Dynamic | 결제 관리 | 결제 요청/승인, 상태 추적 |
| **Frontend** | 3000 | 사용자 인터페이스 | React 기반 웹 애플리케이션 |



## 📂 프로젝트 구조

```
i-love-pet/
├── api-gateway/           # API Gateway (Spring Cloud Gateway)
├── discovery-service/     # Service Discovery (Eureka Server)
├── user-service/          # 회원 관리 서비스
├── product-service/       # 상품 관리 서비스
├── order-service/         # 주문 관리 서비스
├── payment-service/       # 결제 관리 서비스
├── front/                 # React 프론트엔드
├── docker-compose.yml     # 전체 시스템 실행
└── .env                   # 환경 변수 설정
```



## 📊 주요 기술적 특징

- **MSA Infrastructure**:
  - API Gateway를 통한 단일 진입점 (Single Entry Point)
  - Service Discovery를 통한 동적 서비스 등록/조회
  - Client-side Load Balancing (Eureka + Ribbon)
- **Event-Driven Architecture**: Kafka 기반 비동기 메시징
- **SAGA Pattern**: 분산 트랜잭션 관리 및 보상 트랜잭션
- **Idempotency**: Redis를 활용한 멱등성 보장
- **Dead Letter Queue**: 실패 이벤트 처리 및 재시도
- **Database Per Service**: 서비스별 독립 데이터베이스


## 🚀 빠른 시작

### 사전 요구사항
- Docker & Docker Compose
- Node.js (프론트엔드 개발 시)
- JDK 17+ (백엔드 개발 시)

### 1. 환경 변수 설정
프로젝트 루트에 `.env` 파일을 생성하고 다음 내용을 설정:

```env
USER_MYSQL_URL=jdbc:mysql://user-mysql:3306/lovepet
PRODUCT_MYSQL_URL=jdbc:mysql://product-mysql:3306/lovepet
ORDER_MYSQL_URL=jdbc:mysql://order-mysql:3306/lovepet
PAYMENT_MYSQL_URL=jdbc:mysql://payment-mysql:3306/lovepet

# 테스트용 MySQL URL (부하테스트 시 사용)
USER_MYSQL_URL_TEST=jdbc:mysql://user-mysql-test:3306/lovepet
PRODUCT_MYSQL_URL_TEST=jdbc:mysql://product-mysql-test:3306/lovepet
ORDER_MYSQL_URL_TEST=jdbc:mysql://order-mysql-test:3306/lovepet
PAYMENT_MYSQL_URL_TEST=jdbc:mysql://payment-mysql-test:3306/lovepet

MYSQL_USERNAME=root
MYSQL_PASSWORD=123456
MYSQL_ROOT_PASSWORD=123456
MYSQL_DATABASE=lovepet

# SPRING_SERVICE_PROFILE (default or load-test)
#SPRING_SERVICE_PROFILE=load-test
SPRING_SERVICE_PROFILE=default
```

front 패키지 하위에 `.env` 파일을 생성하고 다음 내용을 설정:

```env
REACT_APP_TOSS_CLIENT_KEY = test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm
```

### 2. 전체 시스템 실행
```bash
# 각 서비스별 프로젝트 빌드
cd api-gateway && ./gradlew clean build -x test && cd ..
cd discovery-service && ./gradlew clean build -x test && cd ..
cd order-service && ./gradlew clean build -x test && cd ..
cd payment-service && ./gradlew clean build -x test && cd ..
cd product-service && ./gradlew clean build -x test && cd ..
cd user-service && ./gradlew clean build -x test && cd ..

# 모든 서비스 빌드 및 실행
docker-compose up -d --build

# 로그 확인
docker-compose logs -f [service-name]

# 상태 확인
docker-compose ps

# 프론트엔드 실행
cd front
npm install
npm start
```

### 3. 서비스 접속 확인
- **API Gateway**: http://localhost:8000
- **Discovery Service**: http://localhost:8761
- **Microservices** (API Gateway를 통한 접속):
  - User Service: http://localhost:8000/user-service
  - Product Service: http://localhost:8000/product-service
  - Order Service: http://localhost:8000/order-service
  - Payment Service: http://localhost:8000/payment-service
- **Kafka UI**: http://localhost:8090
- **Frontend**: http://localhost:3000 (별도 실행 필요)

## 🔧 개발 환경 설정

### 프론트엔드 개발
```bash
cd front
npm install
npm start
```

### 개별 서비스 개발
```bash
# 각 서비스 디렉토리에서
./gradlew bootRun
```

## 📊 데이터베이스 스키마

각 서비스는 독립적인 MySQL 데이터베이스를 사용합니다:
- **user-mysql**: Port 3306
- **product-mysql**: Port 3307
- **order-mysql**: Port 3308
- **payment-mysql**: Port 3309

### 🗂 ERD (Entity Relationship Diagram)
![project_erd.png](document/project_erd.png)


## 🔄 Kafka 토픽 구조

서비스 간 이벤트 통신에 사용되는 주요 Kafka 토픽:

### 주문 처리 플로우
```
product.information.request   # 상품 정보 조회 요청
product.information.response  # 상품 정보 응답
payment.prepare              # 결제 준비 요청
payment.prepared             # 결제 준비 완료
product.stock.decrease       # 재고 차감 요청
product.stock.decreased      # 재고 차감 완료/실패
payment.pending              # 결제 진행 요청
payment.confirmed            # 결제 완료
product.stock.rollback       # 재고 롤백 (보상 트랜잭션)
```

### 접속 정보
- **Kafka Broker**: localhost:9092
- **Zookeeper**: localhost:2181
- **Kafka UI**: localhost:8090 (토픽/메시지 모니터링)

## 📋 주문 처리 플로우

### 1. 정상 주문 처리 - 결제 요청

<img width="3840" height="3474" alt="성공_결제준비" src="https://github.com/user-attachments/assets/1e9726ab-ca94-424f-9919-c869a1cacb79" />

### 2. 정상 주문 처리 - 결제확정

<img width="3400" height="3840" alt="성공_결제확정" src="https://github.com/user-attachments/assets/7ef29293-6052-4e69-94d4-5e8a9e32c718" />


---

### 3. 주문 실패 처리

#### 3-1. 재고부족

<img width="4374" height="3586" alt="Untitled diagram-2025-10-10-102742" src="https://github.com/user-attachments/assets/0d76853c-4874-4689-bf08-a7c70ea5953b" />

#### 3-2. 잔액부족

<img width="2866" height="3840" alt="실패_결제실패" src="https://github.com/user-attachments/assets/bd95fffc-fc65-49eb-b5d5-0c6f5a3e21e9" />


## 🔧 개발 및 테스트

### 로컬 개발 환경
```bash
# 개별 서비스 실행
cd [service-directory]
./gradlew bootRun

# 프론트엔드 개발 서버
cd front
npm install && npm start
```

### 주요 API 엔드포인트
```
POST   /api/orders/prepare     # 주문 준비
POST   /api/orders/confirm     # 주문 확정
GET    /api/orders/{orderId}   # 주문 상태 조회
GET    /api/products           # 상품 목록
POST   /api/users/register     # 회원가입
```

### 테스트 실행
```bash
# 전체 서비스 테스트
./gradlew test

# 특정 서비스 테스트
cd order-service && ./gradlew test

# 프론트엔드 테스트
cd front && npm test
```

## 🔍 모니터링 도구

- **Kafka UI**: http://localhost:8090 (토픽, 메시지 모니터링)
- **Docker Stats**: `docker-compose logs -f [service-name]`


## 🎬 프로젝트 시연영상

- **youtube**: https://www.youtube.com/watch?v=N6IL4091ePg
