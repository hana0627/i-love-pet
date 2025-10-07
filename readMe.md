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

```mermaid
graph TB
    Frontend[🌐 Frontend<br/>React<br/>:3000]

    subgraph "Gateway Layer"
        APIGateway[🚪 API Gateway<br/>Spring Cloud Gateway<br/>:8000]
        Discovery[🔍 Discovery Service<br/>Eureka Server<br/>:8761]
    end

    subgraph "Microservices"
        UserService[👤 User Service<br/>Dynamic Port]
        ProductService[📦 Product Service<br/>Dynamic Port]
        OrderService[🛒 Order Service<br/>Dynamic Port]
        PaymentService[💳 Payment Service<br/>Dynamic Port]
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

    Frontend --> APIGateway
    APIGateway --> Discovery
    APIGateway --> UserService
    APIGateway --> ProductService
    APIGateway --> OrderService
    APIGateway --> PaymentService

    UserService -.등록/조회.-> Discovery
    ProductService -.등록/조회.-> Discovery
    OrderService -.등록/조회.-> Discovery
    PaymentService -.등록/조회.-> Discovery

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

```mermaid
sequenceDiagram autonumber
    participant Frontend
    participant Order
    participant Product
    participant Payment
    participant Redis
    participant DB
    participant Kafka
    participant TossPayments

    Note over Frontend,TossPayments: 결제 준비 흐름
    Note over Frontend,Order: ※ 모든 요청은 API Gateway(8000)를 통해 라우팅됨


    Frontend->>Order: POST /api/orders/prepare (결제 준비요청)
    Order->>Redis: 주문번호 생성요청
    Redis->>Order: 주문번호 생성 (yyyyMMdd00000001~)
    Order->>DB: 주문 생성 요청
    DB->>Order: 주문 생성(CREATED)
    Order->>Kafka: product.information.request 이벤트 발행
    Order->>Frontend: 주문번호 응답
    
    loop 최대 30초간 폴링
        Frontend->>Order: 주문상태 폴링(1request/sec)
        Order->>Frontend: 주문상태 응답
    end

    Kafka->>Product: product.information.request 이벤트 수신
    Product->>DB: 상품정보 요청
    DB->>Product: 상품정보 응답 (상품명, 가격, 재고)
    Product->>Kafka: product.information.response 이벤트 발행

    Kafka->>Order: product.information.response 이벤트 수신
    Order->>DB: 결제금액 저장, 주문상태 변경 요청
    DB->>Order: 결제금액 저장, 주문상태 변경(VALIDATION_SUCCESS)
    Order->>Kafka: payment.prepare 이벤트 발행

    Kafka->>Payment: payment.prepare 이벤트 수신
    Payment->>DB: 결제정보 저장 요청
    DB->>Payment: 결제정보 저장
    Payment->>Kafka: payment.prepared 이벤트 발행

    Kafka->>Order: payment.prepared 이벤트 수신
    Order->>DB: PaymentId 맵핑, 주문상태 변경 요청
    DB->>Order: PaymentId 맵핑, 주문상태 변경(PREPARED)



    Frontend->>Order: 주문상태 폴링
    Order->>Frontend: 주문상태 응답(PREPARED)


    Frontend->>TossPayments: 결제 요청
    TossPayments->>Frontend: 결제 준비 완료
```


### 2. 정상 주문 처리 - 결제확정
```mermaid
sequenceDiagram autonumber
    participant Frontend
    participant Order
    participant Product
    participant Payment
    participant Redis
    participant DB
    participant Kafka
    participant TossPayments
    
    Note over Frontend,TossPayments: 결제 확정 흐름
    Note over Frontend,Order: ※ 모든 요청은 API Gateway(8000)를 통해 라우팅됨

    Frontend->>Order: POST /api/orders/confirm
    Order->>DB: 주문상태 변경 요청
    DB->>Order: 주문상태 변경(DECREASE_STOCK)
    Order->>Redis: PaymentKey 저장
    Order->>Kafka: product.stock.decrease 이벤트 발행
    Order->>Frontend: 주문상태 응답


    loop 최대 30초간 폴링
        Frontend->>Order: 주문상태 폴링(1request/sec)
        Order->>Frontend: 주문상태 응답
    end


    Kafka->>Product: product.stock.decrease 이벤트 수신
    
    rect rgba(205, 237, 151, 0.3)
        Product->>Redis: 재고차감시도 기록 조회
        Redis->>Product: 재고차감시도 기록 응답
        alt 기록 존재함 (이미 처리된 요청)
            Product->>Product: 재고차감 스킵(return)
        else 기록없음
          Product->>Redis: 재고차감시도 기록(멱등처리)
          Product->>DB: 재고차감 요청
          DB->>Product: 재고차감
          Product->>Redis: 재고차감시도 기록 삭제
          Product->>Kafka: product.stock.decreased 이벤트 발행
        end
    end

    Kafka->>Order: product.stock.decreased 이벤트 수신
    Order->>Redis: PaymentKey 조회
    Redis->>Order: PaymentKey 응답
    Order->>DB: 주문상태 변경 요청
    DB->>Order: 주문상태 변경(PAYMENT_PENDING)
    Order->>Kafka: payment.pending 이벤트 발행

    Kafka->>Payment: payment.pending 이벤트 수신
    Payment->>TossPayments: 결제승인 API 호출
    TossPayments->>Payment: 결제승인 결과
    Payment->>DB: PaymentKey맵핑, 결제정보 저장 요청
    DB->>Payment: PaymentKey맵핑, 결제정보 저장
    Payment->>Kafka: payment.confirmed 이벤트 발행

    Kafka->>Order: payment.confirmed 이벤트 수신
    Order->>DB: 주문상태변경 요청
    DB->>Order: 주문상태변경(CONFIRMED)

    Frontend->>Order: 주문상태 폴링
    Order->>Frontend: 주문상태 응답(CONFIRMED)
    
    Note over Frontend: 주문 완료 - 사용자에게 성공 메시지 표시

```

---

### 3. 주문 실패 처리
#### 3-1. 재고부족

```mermaid
sequenceDiagram autonumber
    participant Frontend
    participant Order
    participant Product
    participant Payment
    participant Redis
    participant DB
    participant Kafka
    participant DLQ as Dead Letter Queue
    participant TossPayments

    Note over Frontend,TossPayments: 재고부족 실패 시나리오
    Note over Frontend,Order: ※ 모든 요청은 API Gateway(8000)를 통해 라우팅됨

    Frontend->>TossPayments: 결제 요청
    TossPayments->>Frontend: 결제 준비 완료


    Frontend->>Order: POST /api/orders/confirm
    Order->>DB: 주문상태 변경 요청
    DB->>Order: 주문상태 변경(DECREASE_STOCK)
    Order->>Redis: PaymentKey 저장
    Order->>Kafka: product.stock.decrease 이벤트 발행
    Order->>Frontend: 주문번호 응답
    
    loop 폴링 지속
        Frontend->>Order: 주문상태 폴링
        Order->>Frontend: 주문상태 응답(DECREASE_STOCK)
    end

    rect rgba(255, 182, 193, 0.3)
        Kafka->>Product: product.stock.decrease 이벤트 수신
        
        Product->>Redis: 재고차감시도 기록 조회
        Product->>Redis: 재고차감시도 기록 응답
        
        alt 기록 존재함 (이미 처리된 요청)
            Product->>Product: 재고차감 스킵(return)
        else 기록없음 
            Product->>Redis: 재고차감시도 기록(멱등처리)
            Product->>DB: 재고 확인 요청
            DB->>Product: 재고 확인
            Product->>Product: 재고 부족 판정
            Product->>DLQ: product.stock.decrease-dlt 이벤트 발행
            Product->>Kafka: product.stock.decreased(success=fail) 이벤트 발행
        end
    end

    Kafka->>Order: product.stock.decreased(success=fail) 이벤트 수신
    Order->>DB: 주문상태 변경 요청
    DB->>Order: 주문상태 변경(DECREASE_STOCK_FAILED)

    Frontend->>Order: 주문상태 폴링
    Order->>Frontend: 주문상태 응답(DECREASE_STOCK_FAILED)
    
    Note over Frontend: 사용자에게 "재고 부족으로 주문이 취소되었습니다" 메시지 표시
```

#### 3-2. 잔액부족

```mermaid
sequenceDiagram autonumber
    participant Frontend
    participant Order
    participant Product
    participant Payment
    participant Redis
    participant DB

    participant Kafka
    participant DLQ as Dead Letter Queue
    participant TossPayments

    Note over Frontend,TossPayments: 장애상황2. 잔액부족
    Note over Frontend,Order: ※ 모든 요청은 API Gateway(8000)를 통해 라우팅됨


    Frontend->>TossPayments: 결제 요청
    TossPayments->>Frontend: 결제 준비 완료


    Frontend ->> Order: POST /api/orders/confirm
    Order ->> DB: 주문상태 변경
    DB ->> Order: 주문상태 변경 요청(DECREASE_STOCK)
    Order ->> Redis: PaymentKey 저장
    Order ->> Kafka: product.stock.decrease 이벤트 발행
    Order ->> Frontend: 주문번호 응답
    
    loop 폴링 지속
        Frontend->>Order: 주문상태 폴링
        Order->>Frontend: 주문상태 응답(DECREASE_STOCK)
    end


    Kafka ->> Product: product.stock.decrease 이벤트 수신
    Product->>DB: 재고차감 요청
    DB->>Product: 재고차감
    Product->>Kafka: product.stock.decreased 이벤트 발행(success = true)
    
    
    Kafka ->> Order: product.stock.decreased 이벤트 수신
    Order->>Redis: PaymentKey 조회
    Redis->>Order: PaymentKey 응답
    Order->>DB: 주문상태 변경 요청
    DB->>Order: 주문상태 변경(PAYMENT_PENDING)
    Order->>Kafka: payment.pending 이벤트 발행


    Kafka->>Payment: payment.pending 이벤트 수신
    rect rgba(255, 182, 193, 0.3)
      alt 잔액부족으로 결재실패
          Payment->>TossPayments: 결제승인 API 호출
          TossPayments->>Payment: 결제실패 응답
          Payment->>DB: 장애로그 저장 요청
          DB->>Payment: 장애로그 저장
          Payment->>DLQ:payment.pending-dlt 이벤트 발행
          DLQ->>Payment:payment.pending-dlt 이벤트 처리
          Payment->>DB: 결제상태 상태 변경 및 실패로그 저장 요청
          DB->>Payment: 결제상태 상태 변경 및 실패로그 저장
          Payment->>Kafka: payment.confirmed.fail 이벤트 발행
      end
    end

    Kafka->>Order: payment.confirmed.fail 이벤트 수신
    Order->>DB: 주문상태 변경 요청
    DB->>Order: 주문상태 변경(PAYMENT_FAILED)


    Frontend->>Order: 주문상태 폴링
    Order->>Frontend: 주문상태 응답(PAYMENT_FAILED)
    Note over Frontend: 사용자에게 "잔액이 부족합니다" 메시지 표시

    Note over Frontend,TossPayments: 보상트랜잭션 - 재고롤백 이벤트
    Order->>Kafka: product.stock.rollback 이벤트 발행
    Kafka->>Product: product.stock.rollback 이벤트 수신
    Product->>Redis: 재고복구시도 기록 조회
    Redis->>Product: 재고복구시도 기록 응답
    
    rect rgba(205, 237, 151, 0.3)
        alt 기록 존재함 (이미 처리된 요청)
            Product->>Product: 재고복구 스킵(return)
        else 기록없음
            Product->>Redis: 재고복구시도 기록(중복 재고 복구를 위한 멱등처리)
            Product->>DB: 재고 복구 요청
            DB->>Product: 재고 복구
            Product->>Redis: 재고복구시도 기록 삭제
            alt 재고 복구 실패
                Product->>DLQ: 실패이벤트 저장(로깅)
            end
        end
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


## 🎬 프로젝트 시연영상

- **youtube**: https://www.youtube.com/watch?v=N6IL4091ePg
