# 🐾 I Love Pet

I Love Pet은 펫샵 전자상거래 플랫폼을 위한 MSA(Microservice Architecture) 기반 시스템입니다.  
사용자 관리, 상품 카탈로그, 주문, 결제 등 각 도메인을 독립된 서비스로 구성하였으며,  
서비스 간 통신은 Kafka를 활용한 비동기 이벤트 기반 메시징 방식으로 처리됩니다.


## 📅 개발 기간

**2025. 07 ~ 2025. 09 (약 2개월, 1차 MVP)**

### 주요 개발 마일스톤
- **Phase 1** (2025. 08): 프로젝트 초기모델 개발
- **Phase 2** (2025. 09): 서비스간 통신을 WebClient를 이용한 동기방식에서 Kafka를 이용한 이벤트 기반 구조로 변경


## 🏗 시스템 아키텍처

프로젝트는 4개의 마이크로서비스와 React 기반 프론트엔드로 구성됩니다.

```mermaid
graph TB
    Frontend[🌐 Frontend<br/>React<br/>:3000]
    
    subgraph "Microservices"
        UserService[👤 User Service<br/>:8080]
        ProductService[📦 Product Service<br/>:8081]
        OrderService[🛒 Order Service<br/>:8082]
        PaymentService[💳 Payment Service<br/>:8083]
    end
    
    subgraph "Infrastructure"
        Kafka[📨 Kafka<br/>:9092]
        Redis[🗄️ Redis<br/>:6379]
        KafkaUI[📊 Kafka UI<br/>:8090]
    end
    
    subgraph "Databases"
        UserDB[(👤 User MySQL<br/>:3306)]
        ProductDB[(📦 Product MySQL<br/>:3307)]
        OrderDB[(🛒 Order MySQL<br/>:3308)]
        PaymentDB[(💳 Payment MySQL<br/>:3309)]
    end
    
    Frontend --> UserService
    Frontend --> ProductService
    Frontend --> OrderService
    Frontend --> PaymentService
    
    UserService --> UserDB
    ProductService --> ProductDB
    OrderService --> OrderDB
    PaymentService --> PaymentDB
    
    ProductService --> Kafka
    OrderService --> Kafka
    PaymentService --> Kafka
    
    ProductService --> Redis
    OrderService --> Redis
    PaymentService --> Redis
    
    Kafka --> KafkaUI
```

## 🛠 기술 스택

### 백엔드 서비스
- **Language**: Kotlin
- **Framework**: Spring Boot 3.5
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
- **Service Discovery**: 내장 네트워크
- **Message Broker**: Kafka + Zookeeper
- **Cache**: Redis
- **Monitoring**: Kafka UI

## 📋 서비스 목록

| 서비스 | 포트 | 설명 | 주요 기능 |
|--------|------|------|-----------|
| **User Service** | 8080 | 회원 관리 | 회원가입, 조회, 존재확인 |
| **Product Service** | 8081 | 상품 관리 | 상품 등록/조회, 재고관리 |
| **Order Service** | 8082 | 주문 관리 | 주문 생성, 상태 관리 |
| **Payment Service** | 8083 | 결제 관리 | 결제 요청/승인, 상태 추적 |
| **Frontend** | 3000 | 사용자 인터페이스 | React 기반 웹 애플리케이션 |



## 📂 프로젝트 구조

```
i-love-pet/
├── user-service/          # 회원 관리 서비스
├── product-service/       # 상품 관리 서비스
├── order-service/         # 주문 관리 서비스
├── payment-service/       # 결제 관리 서비스
├── front/                 # React 프론트엔드
├── docker-compose.yml     # 전체 시스템 실행
└── .env                   # 환경 변수 설정
```



## 📊 주요 기술적 특징

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
추후 공개에정
```

### 2. 전체 시스템 실행
```bash
# 모든 서비스 빌드 및 실행
docker-compose up -d --build

# 로그 확인
docker-compose logs -f [service-name]

# 상태 확인
docker-compose ps
```

### 3. 서비스 접속 확인
- User Service: http://localhost:8080/api/users
- Product Service: http://localhost:8081/api/products
- Order Service: http://localhost:8082/api/orders
- Payment Service: http://localhost:8083/api/payments
- Kafka UI: http://localhost:8090
- Frontend: http://localhost:3000 (별도 실행 필요)

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

마이크로서비스 아키텍처에서 각 서비스는 독립된 데이터베이스를 가지며, 서비스 간 논리적 관계는 점선으로 표현됩니다.

```mermaid
erDiagram
    %% User Service Database
    User {
        BIGINT id PK "AUTO_INCREMENT"
        VARCHAR name "30, NOT NULL"
        VARCHAR email "50, NOT NULL"
        VARCHAR phone_number "20, NULLABLE"
        DATETIME created_at "NOT NULL"
    }

    %% Product Service Database
    Product {
        BIGINT id PK "AUTO_INCREMENT"
        VARCHAR name "255, NOT NULL"
        BIGINT price "NOT NULL"
        INT stock "NOT NULL"
        DATETIME created_at "NOT NULL"
        DATETIME updated_at "NULLABLE"
    }

    %% Order Service Database
    Order {
        BIGINT id PK "AUTO_INCREMENT"
        BIGINT user_id "NOT NULL"
        VARCHAR user_name "NOT NULL"
        VARCHAR order_no "32, UNIQUE"
        ENUM status "OrderStatus"
        VARCHAR payment_method "NOT NULL"
        BIGINT price "DEFAULT 0"
        BIGINT payment_id "NULLABLE"
        DATETIME created_at "NOT NULL"
        DATETIME updated_at "NULLABLE"
        VARCHAR description "NULLABLE"
    }

    OrderItem {
        BIGINT id PK "AUTO_INCREMENT"
        BIGINT product_id "NOT NULL"
        VARCHAR product_name "NOT NULL"
        INT quantity "NOT NULL"
        BIGINT price "NOT NULL"
        BIGINT order_id "NULLABLE"
    }

    %% Payment Service Database
    Payment {
        BIGINT id PK "AUTO_INCREMENT"
        BIGINT user_id "NOT NULL"
        BIGINT order_id "NOT NULL"
        VARCHAR payment_key "UNIQUE, NOT NULL"
        BIGINT amount "NOT NULL"
        ENUM status "PaymentStatus"
        VARCHAR method "30, NULLABLE"
        DATETIME requested_at "NOT NULL"
        DATETIME approved_at "NULLABLE"
        DATETIME failed_at "NULLABLE"
        DATETIME canceled_at "NULLABLE"
        DATETIME refunded_at "NULLABLE"
        VARCHAR fail_reason "200, NULLABLE"
        DATETIME updated_at "NULLABLE"
        VARCHAR description "NULLABLE"
    }

    PaymentLog {
        BIGINT id PK "AUTO_INCREMENT"
        BIGINT payment_id "NOT NULL"
        ENUM log_type "LogType"
        VARCHAR message "NULLABLE"
        DATETIME created_at "NOT NULL"
    }

    %% 물리적 관계 (같은 DB 내)
    Order ||--o{ OrderItem : "order_id"
    Payment ||--o{ PaymentLog : "payment_id"

    %% 논리적 관계 (서비스 간, 점선으로 표현)
    User ||..o{ Order : "user_id (logical)"
    Product ||..o{ OrderItem : "product_id (logical)"
    Order ||..o| Payment : "order_id, payment_id (logical)"
```

#### 📝 ERD 범례
- **실선 (—)**: 물리적 외래키 관계 (동일 데이터베이스 내)
- **점선 (...)**: 논리적 관계 (서로 다른 마이크로서비스 간)

#### 🔗 서비스 간 논리적 관계
1. **User ↔ Order**: `User.id` ↔ `Order.user_id`
2. **Order ↔ Payment**: `Order.id` ↔ `Payment.order_id`, `Order.payment_id` ↔ `Payment.id`
3. **Product ↔ OrderItem**: `Product.id` ↔ `OrderItem.product_id`
4. **Order ↔ OrderItem**: `Order.id` ↔ `OrderItem.order_id` (현재 미구현)

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

### 1. 정상 주문 처리

```mermaid
sequenceDiagram autonumber
    participant Frontend
    participant OrderService
    participant ProductService
    participant PaymentService
    participant Redis
    participant Kafka
    participant TossPayments

    Note over Frontend,TossPayments: 결제 준비 흐름

    Frontend->>OrderService: POST /api/orders/prepare
    OrderService->>Redis: 주문번호 생성요청
    Redis->>OrderService: 주문번호 생성 (yyyyMMdd00000001~)
    OrderService->>OrderService: 주문 생성 (CREATED)
    OrderService->>Kafka: product.information.request 이벤트 발행
    OrderService->>Frontend: 주문번호 응답
    
    loop 최대 30초간 폴링
        Frontend->>OrderService: 주문상태 폴링(1request/sec)
        OrderService->>Frontend: 주문상태 응답
    end

    Kafka->>ProductService: product.information.request 이벤트 수신
    ProductService->>ProductService: 상품정보 확인 (상품명, 가격, 재고)
    ProductService->>Kafka: product.information.response 이벤트 발행

    Kafka->>OrderService: product.information.response 이벤트 수신
    OrderService->>OrderService: 결제금액 저장, 주문상태 변경(VALIDATION_SUCCESS)
    OrderService->>Kafka: payment.prepare 이벤트 발행

    Kafka->>PaymentService: payment.prepare 이벤트 수신
    PaymentService->>PaymentService: 결제정보 저장, 임시 PaymentKey발행
    PaymentService->>Kafka: payment.prepared 이벤트 발행

    Kafka->>OrderService: payment.prepared 이벤트 수신
    OrderService->>OrderService: PaymentId 맵핑, 주문상태 변경(PREPARED)



    Frontend->>OrderService: 주문상태 폴링
    OrderService->>Frontend: 주문상태 응답(PREPARED)


    Frontend->>TossPayments: 결제 요청
    TossPayments->>Frontend: 결제 준비 완료

    Note over Frontend,TossPayments: 결제 확정 흐름

    Frontend->>OrderService: POST /api/orders/confirm
    OrderService->>OrderService: 주문상태 변경(DECREASE_STOCK)
    OrderService->>Redis: PaymentKey 저장
    OrderService->>Kafka: product.stock.decrease 이벤트 발행
    OrderService->>Frontend: 주문상태 응답


    loop 최대 30초간 폴링
        Frontend->>OrderService: 주문상태 폴링(1request/sec)
        OrderService->>Frontend: 주문상태 응답
    end


    Kafka->>ProductService: product.stock.decrease 이벤트 수신
    ProductService->>Redis: 재고차감시도 기록(멱등처리)
    ProductService->>ProductService: 재고차감
    ProductService->>Redis: 재고차감시도 기록삭제
    ProductService->>Kafka: product.stock.decreased 이벤트 발행

    Kafka->>OrderService: product.stock.decreased 이벤트 수신
    OrderService->>Redis: PaymentKey 조회
    Redis->>OrderService: PaymentKey 응답
    OrderService->>OrderService: 주문상태 변경(PAYMENT_PENDING)
    OrderService->>Kafka: payment.pending 이벤트 발행

    Kafka->>PaymentService: payment.pending 이벤트 수신
    PaymentService->>TossPayments: 결제승인 API 호출
    TossPayments->>PaymentService: 결제승인 결과
    PaymentService->>PaymentService: PaymentKey맵핑, 결제정보 저장
    PaymentService->>Kafka: payment.confirmed 이벤트 발행

    Kafka->>OrderService: payment.confirmed 이벤트 수신
    OrderService->>OrderService: 주문상태변경(CONFIRMED)

    Frontend->>OrderService: 주문상태 폴링
    OrderService->>Frontend: 주문상태 응답(CONFIRMED)
    
    Note over Frontend: 주문 완료 - 사용자에게 성공 메시지 표시

```
---

### 2. 주문 실패 처리
#### 2-1. 재고부족

```mermaid
sequenceDiagram autonumber
    participant Frontend
    participant OrderService
    participant ProductService
    participant PaymentService
    participant Redis
    participant Kafka
    participant DLQ as Dead Letter Queue
    participant TossPayments

    Note over Frontend,TossPayments: 재고부족 실패 시나리오

    Frontend->>TossPayments: 결제 요청
    TossPayments->>Frontend: 결제 준비 완료


    Frontend->>OrderService: POST /api/orders/confirm
    OrderService->>OrderService: 주문상태 변경(DECREASE_STOCK)
    OrderService->>Redis: PaymentKey 저장
    OrderService->>Kafka: product.stock.decrease 이벤트 발행
    OrderService->>Frontend: 주문번호 응답
    
    loop 폴링 지속
        Frontend->>OrderService: 주문상태 폴링
        OrderService->>Frontend: 주문상태 응답(DECREASE_STOCK)
    end

    Kafka->>ProductService: product.stock.decrease 이벤트 수신
    ProductService->>Redis: 재고차감시도 기록(멱등처리)
    
    alt 재고 부족
        ProductService->>ProductService: 재고 확인 → 부족 판정
        ProductService->>DLQ: product.stock.decrease-dlt 이벤트 발행
        ProductService->>Kafka: product.stock.decreased(success=fail) 이벤트 발행
    end

    Kafka->>OrderService: product.stock.decreased(success=fail) 이벤트 수신
    OrderService->>OrderService: 주문상태 변경(DECREASE_STOCK_FAILED)

    Frontend->>OrderService: 주문상태 폴링
    OrderService->>Frontend: 주문상태 응답(DECREASE_STOCK_FAILED)
    
    Note over Frontend: 사용자에게 "재고 부족으로 주문이 취소되었습니다" 메시지 표시
```

#### 2-2. 잔액부족

```mermaid
sequenceDiagram autonumber
    participant Frontend
    participant OrderService
    participant ProductService
    participant PaymentService
    participant Redis

    participant Kafka
    participant DLQ as Dead Letter Queue
    participant TossPayments

    Note over Frontend,TossPayments: 장애상황2. 잔액부족


    Frontend->>TossPayments: 결제 요청
    TossPayments->>Frontend: 결제 준비 완료


    Frontend ->> OrderService: POST /api/orders/confirm
    OrderService ->> OrderService: 주문상태 변경(DECREASE_STOCK)
    OrderService ->> Redis: PaymentKey 저장
    OrderService ->> Kafka: product.stock.decrease 이벤트 발행
    OrderService ->> Frontend: 주문번호 응답
    
    loop 폴링 지속
        Frontend->>OrderService: 주문상태 폴링
        OrderService->>Frontend: 주문상태 응답(DECREASE_STOCK)
    end


    Kafka ->> ProductService: product.stock.decrease 이벤트 수신
    ProductService->>Redis: 재고차감시도 기록(멱등처리)
    ProductService->>ProductService: 재고차감
    ProductService->>Redis: 재고차감시도 기록삭제
    ProductService->>Kafka: product.stock.decreased 이벤트 발행(success = fail)

    Kafka ->> OrderService: product.stock.decreased 이벤트 수신
    OrderService->>Redis: PaymentKey 조회
    Redis->>OrderService: PaymentKey 응답
        OrderService->>OrderService: 주문상태 변경(PAYMENT_PENDING)
        OrderService->>Kafka: payment.pending 이벤트 발행


    Kafka->>PaymentService: payment.pending 이벤트 수신
    alt 잔액부족으로 결재실패
    PaymentService->>TossPayments: 결제승인 API 호출
        TossPayments->>PaymentService: 결제실패 응답
    PaymentService->>PaymentService: PaymentKey맵핑, 결제정보 저장
        PaymentService->>PaymentService: 장애로그 저장
        PaymentService->>DLQ:payment.pending-dlt 이벤트 발행
        DLQ->>PaymentService:payment.pending-dlt 이벤트 처리
        PaymentService->>PaymentService: 결제상태 상태 변경 및 실패로그 저장
        PaymentService->>Kafka: payment.confirmed.fail 이벤트 발행
    end

    Kafka->>OrderService: payment.confirmed.fail 이벤트 수신
    OrderService->>OrderService: 주문상태 변경(PAYMENT_FAILED)


    Frontend->>OrderService: 주문상태 폴링
    OrderService->>Frontend: 주문상태 응답(PAYMENT_FAILED)
    Note over Frontend: 사용자에게 "잔액이 부족합니다" 메시지 표시

    Note over Frontend,TossPayments: 보상트랜잭션 - 재고롤백 이벤트
    OrderService->>Kafka: product.stock.rollback 이벤트 발행
    Kafka->>ProductService: product.stock.rollback 이벤트 수신
    ProductService->>Redis: 재고복구시도 기록(중복 재고 복구를 위한 멱등처리)
    ProductService->>ProductService: 재고 복구
    ProductService->>Redis: 재고복구시도 기록
    alt 재고 복구 실패
        ProductService->>DLQ: 실패이벤트 저장(로깅)
    end

```




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
- **Service Health**: 각 서비스 `/health` 엔드포인트
