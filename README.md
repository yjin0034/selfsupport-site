# 📘 Self-Support Platform - 게시판 + 결제·후원 통합 서비스 (Spring Boot 3)

> **자립청년 지원 플랫폼 - 게시판 + 결제/후원 기능 통합 프로젝트**
> 
> Spring Boot 3 기반으로 CRUD 게시판, 댓글, Security 로그인,  
> Toss 결제 기반 충전/후원 시스템을 포함한 통합 웹 서비스입니다.

---

# 🏛 1. 전체 서비스 개요

### 📌 프로젝트명  
**Self-Support Platform (게시판 + 결제·후원)**

### 📌 주요 기능  
- 게시글 CRUD / 검색 / 페이징  
- 댓글 작성/삭제  
- Spring Security 로그인  
- 지갑 충전  
- 포인트 후원 / 직접 결제 후원  
- Toss Payments 연동  
- 멱등성·재시도 기반 복합 결제 처리  

---

# 🧱 2. 기술 스택

| 레이어 | 사용 기술 |
|--------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.3.x |
| Build | Gradle 8.x |
| ORM | Spring Data JPA + QueryDSL |
| DB | H2 / MySQL |
| View | Thymeleaf |
| Security | Spring Security 6 |
| Payment | Toss Payments API |
| Test | JUnit5, Mockito, SpringBootTest |

---

# 📁 3. 전체 아키텍처 구조

## 📂 전체 패키지 구조

```text
src/main/java/
├─ board/                  # 게시판 서비스
│  ├─ article
│  ├─ comment
│  ├─ user
│  ├─ controller
│  ├─ service
│  ├─ repository
│  └─ dto
│
└─ payment/                # 결제·후원 서비스
   ├─ wallet
   ├─ order
   ├─ donation
   ├─ transaction
   ├─ checkout
   ├─ external
   ├─ processing
   └─ retry
```
➡️ 복잡한 결제 흐름을 도메인 / 애플리케이션 / 인프라 계층으로 분리한 구조입니다.

---

## 📝 4. 주요 기능

### 📄 4-1. 게시판 서비스

#### **✏️ 1) Article - 게시글 CRUD**
- 게시글 목록 조회 (페이징)
- 게시글 검색 (제목, 본문, 작성자, 해시태그)
- 게시글 상세 보기
- 게시글 작성 / 수정 / 삭제  
- 해시태그 기반 검색 기능

#### **💬 2) ArticleComment - 댓글 기능**
- 게시글 상세 페이지에서 댓글 목록 확인
- 댓글 등록 / 수정 / 삭제
- 본인이 작성한 댓글만 수정 및 삭제 가능

#### **🔐 3) 로그인/로그아웃**
- Spring Security 6 기반
- 커스텀 UserDetails (`BoardPrincipal`)
- Thymeleaf Security Extras 적용 

#### **📄 4) Pagination 기능**
- Pageable 기반 자동 페이징
- PaginationService에서 페이지 네비게이션 계산 (현재 페이지 기준 앞/뒤 5칸 유지)
- 테스트 기반으로 구현하여, 엣지 케이스에 대한 계산 정확성 검증 완료.

---

### 💳 4-2. 결제·후원 서비스

#### **💰 1) Wallet — 지갑 관리**
- 사용자별 단 하나의 지갑 유지 (UserId Unique)
- 잔액 충전 / 잔액 차감
- 음수 잔액 방지
- 지갑 조회 (walletId, userId)

---

#### **🧾 2) Transaction — 입출금 트랜잭션 기록**
- 충전 / 후원 / PG 결제 각각에 대해 기록 남김
- orderId / donationId 기반 멱등성 보장
- 트랜잭션 타입:
  - `CHARGE` (충전)
  - `PAYMENT` (포인트 후원)
  - `PG_PAYMENT` (PG 결제 승인 기록)

---

#### **🧺 3) Order — 결제 요청(주문)**
- “결제를 시도하려는 의사”를 표현하는 도메인
- Toss Payments 위젯에서 사용되는 orderId(requestId) 관리
- 상태 기반 흐름 관리  
  - `WAIT → APPROVED → COMPLETED`

---

#### **🎁 4) Donation — 후원 도메인**
- 후원 품목(DonationItem) + 후원 금액 저장
- 포인트 후원 / 직접 결제(DIRECT) 후원 지원
- 후원 상태 관리: `PENDING → COMPLETED`
- Donation + Transaction 연동

---

#### **🪙 5) 충전 기능**
- 지갑 잔액 충전
- Toss 위젯 기반 결제
- PG 승인 이후 Wallet + Transaction 반영

---

#### **🎉 6) 후원 기능**
- 포인트 후원 (지갑 잔액 차감)
- 직접 결제 후원 (PG 결제 → 승인 → 후원 완료)
- Donation + Transaction 자동 연동

---

#### **🪄 7) 결제 위젯 (Checkout)**
- Toss 결제 위젯 렌더링
- 결제 화면:  
  - `/wallets/charge`  
  - `/donations/{id}/checkout`
- orderId, customerKey, 금액 자동 바인딩

---

#### **🌐 8) External PG 연동**
- Toss `/payments/confirm` 호출
- 승인 결과 검증
- 예외 / 타임아웃 대응
- PG 실패/성공 로직 분리

---

#### **🔄 9) Processing — 승인 결과 처리 오케스트레이션**
1. PG 승인 성공
2. Order 상태 `APPROVED`
3. Wallet 잔액 충전 또는 차감
4. Transaction 생성
5. Donation 후원 상태 `COMPLETED`

---

#### **🔁 10) Retry · 보상 처리**
- 승인 성공했으나 서버에서 실패 시 자동 회복
- 실패한 Confirm Request 저장 후 재시도
- 멱등성 기반이므로 중복 처리 없이 안전

---

## 🧱 5. 도메인 & ERD 구조

"게시판 서비스, 결제/후원 서비스의 핵심 도메인 구조와  
DB 필드(스키마), 그리고 ERD 관계를 한 섹션으로 통합하여 정리했습니다."

---

### 📄 5-1. 게시판 서비스

#### **📰 1) Article (게시글)**

**📌 도메인 개요**
- ID (Long)
- title / content / hashtag
- 작성자(UserAccount) 연관관계 (N:1)
- ArticleComment 와 1:N 관계
- createdAt / createdBy 자동 생성(Auditing)
- CRUD + 검색 + 해시태그 기반 조회 기능 제공

**📌 DB 필드 구조**

| 필드 | 설명 |
|------|------|
| id | PK |
| title | 제목 |
| content | 본문 |
| hashtag | 해시태그 |
| user_id | 작성자 FK |
| createdAt / createdBy | Auditing 필드 |

---

#### **💬 2) ArticleComment (댓글)**

**📌 도메인 개요**
- ID (Long)
- content
- Article (N:1)
- UserAccount (작성자) (N:1)
- createdAt / createdBy 자동 생성
- 작성자 본인만 수정/삭제 가능

**📌 DB 필드 구조**

| 필드 | 설명 |
|------|------|
| id | PK |
| article_id | FK |
| content | 댓글 내용 |
| user_id | 댓글 작성자 |

---

#### **👤 3) UserAccount (사용자)**

**📌 도메인 개요**
- userId (PK), password, nickname, email
- 모든 게시글/댓글 작성자 정보와 연결
- Spring Security 인증(UserDetails)과 직접 연동

**📌 DB 필드 구조**

| 필드 | 설명 |
|------|------|
| user_id | PK |
| email | 이메일 |
| password | 암호화된 비밀번호 |
| nickname | 사용자명 |
| createdAt | 생성일 |
| createdBy | 생성자 |

---

#### **🔗 4) ERD 관계 구조**
```yaml
USER_ACCOUNT (1) —— (N) ARTICLE —— (N) ARTICLE_COMMENT
```

```yaml
USER
├─ id (PK)
├─ email
├─ password
└─ nickname
    │
    │
    ├── (1:N) ARTICLE
    │        ├─ id (PK)
    │        ├─ title
    │        ├─ content
    │        ├─ hashtag
    │        ├─ user_id (FK)               # USER.id
    │        ├─ created_at / created_by
    │        └─ ...
    │
    └── (1:N) ARTICLE_COMMENT
             ├─ id (PK)
             ├─ article_id (FK)            # ARTICLE.id
             ├─ user_id (FK)               # USER.id
             ├─ content
             ├─ created_at / created_by
             └─ ...
```
			
---

## 5-2. 💳 결제/후원 서비스

### **1) Wallet (지갑)**

#### 📌 도메인 설명
- 사용자별 포인트 잔액 관리  
- 1 user = 1 wallet  
- 잔액 변동은 Transaction을 통해서만 발생  

#### 📌 DB 필드 구조
| 필드 | 설명 |
|------|------|
| id | PK |
| user_id | 사용자 식별자 |
| balance | 현재 잔액 |
| created_at | 생성일 |
| updated_at | 수정일 |

---

### **2) Transaction (입출금 기록)**

#### 📌 도메인 설명
- 지갑 잔액 변화의 근거가 되는 "장부"
- orderId/donationId 기반 멱등성 보장

#### 📌 DB 필드 구조
| 필드 | 설명 |
|------|------|
| id | PK |
| user_id | 사용자 |
| wallet_id | 지갑 FK |
| type | CHARGE / PAYMENT / PG_PAYMENT |
| amount | 입출금 금액 |
| order_id | 주문 ID |
| donation_id | 후원 ID |
| payment_key | PG 결제 key |
| created_at | 생성일 |

---

### **3) Order (주문)**

#### 📌 도메인 설명
- 결제 시도 자체를 표현하는 모델
- Toss 위젯에서 사용하는 requestId 포함
- 상태 기반 결제 플로우 관리 (`WAIT`, `APPROVED`, `FAILED`)

#### 📌 DB 필드 구조
| 필드 | 설명 |
|------|------|
| id | PK |
| user_id | 사용자 |
| amount | 결제 금액 |
| request_id | Toss 위젯에서 사용하는 ID |
| status | WAIT / APPROVED / FAILED |
| created_at | 생성일 |

---

### **4) Donation (후원)**

#### 📌 도메인 설명
- 어떤 후원 아이템을 누구에게 후원하는지 저장
- 포인트 후원 & 직접 결제 후원 모두 관리

#### 📌 DB 필드 구조
| 필드 | 설명 |
|------|------|
| id | PK |
| user_id | 후원자 |
| donation_item | 후원 품목 |
| amount | 금액 |
| order_id | 직접 결제 시 연결되는 주문 ID |
| status | PENDING / COMPLETED |
| created_at | 생성일 |

---

### **5) ERD 전체 구조**

```yaml
USER (1) — (1) WALLET — (N) TRANSACTION
│
└— (N) ORDER — (1) DONATION
```

```yaml
USER
├─ id (PK)
├─ email
├─ password
└─ nickname
      │
      ├── (1:1) WALLET
      │        ├─ id (PK)                 # USER.id
      │        ├─ user_id (FK)          
      │        ├─ balance                 # 현재 잔액
      │        └─ ...
      │
      ├── (1:N) TRANSACTION
      │        ├─ id (PK)                    # USER.id
      │        ├─ wallet_id (FK)             # WALLET.id
      │        ├─ type                       # CHARGE / PAYMENT / PG_PAYMENT
      │        ├─ amount
      │        ├─ order_id (FK, nullable)    # ORDER.id
      │        ├─ donation_id (FK, nullable) # DONATION.id
      │        ├─ payment_key (nullable)
      │        └─ ...
      │
      ├── (1:N) ORDER
      │        ├─ id (PK)
      │        ├─ user_id (FK)            # USER.id
      │        ├─ amount
      │        ├─ request_id              # Toss 위젯 requestId
      │        ├─ status                  # WAIT / APPROVED / FAILED
      │        └─ ...
      │
      └── (1:N) DONATION
               ├─ id (PK)
               ├─ user_id (FK)            # USER.id (후원자)
               ├─ donation_item
               ├─ amount
               ├─ order_id (FK, nullable) # 직접 결제의 경우 ORDER.id
               ├─ status                  # PENDING / COMPLETED
               └─ created_at
```

---

## 6. 📄 API 주요 엔드포인트

### 📄 6-1. 게시판 서비스

#### **📝 1) 게시글 (Articles)**

| Method | Endpoint                | Description                |
|--------|-------------------------|----------------------------|
| GET    | /articles               | 게시글 목록 (검색 + 페이징) |
| GET    | /articles/{id}          | 게시글 상세보기             |
| GET    | /articles/form          | 게시글 작성폼               |
| POST   | /articles/form          | 게시글 작성                 |
| GET    | /articles/{id}/form     | 게시글 수정폼               |
| POST   | /articles/{id}/form     | 게시글 수정                 |
| POST   | /articles/{id}/delete   | 게시글 삭제                 |

---

#### **💬 2) 댓글 (Comments)**

| Method | Endpoint                   | Description     |
|--------|----------------------------|-----------------|
| POST   | /comments                  | 댓글 작성        |
| POST   | /comments/{id}/delete      | 댓글 삭제        |

---

### 💳 6-2. 결제/후원 서비스

#### **1) 💰 Wallet / Charge**

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /wallets/charge | 충전 페이지 UI |
| POST | /api/wallets/orders | 충전용 Order 생성 |
| GET | /checkout/{requestId} | Toss 결제 위젯 페이지 |
| POST | /payments/confirm | PG 승인 요청 |
| GET | /order-requested | 승인 성공 후 리디렉션 |

---

#### **2) 🎁 Donation / 후원**

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /donations | 후원 메인 페이지 |
| POST | /api/donations | 후원 생성(PENDING) |
| GET | /donations/{id}/checkout | 후원 결제 위젯 |
| POST | /payments/confirm | PG 승인 |
| POST | /api/donations/{id}/point | 포인트 후원 |

---

## ⚙️ 7. 아키텍처 계층 구조
"어떤 레이어가 어떤 역할을 하는가"
기술적인 레이어 구조 자체만 설명

### 📄 6-1. 게시판 서비스

```yaml
🔹 Article 흐름
ArticleController → ArticleService → ArticleRepository → JPA

🔹 Comment 흐름
ArticleCommentController → ArticleCommentService → ArticleCommentRepository

🔹 Security 흐름
CustomUserDetailsService → BoardPrincipal → Spring Security FilterChain
```

### 💳 6-2. 결제/후원 서비스

```yaml
CheckoutController
       ↓ (orderId, amount)
PaymentWidget(Toss)
       ↓ (successUrl)
External(PG confirm)
       ↓ (승인 결과)
PaymentProcessingService
 → OrderService
 → WalletService
 → TransactionService
 → DonationService
```

---

## 🔄 8. 기능별 시나리오 동작 흐름

"사용자가 어떤 기능을 실행하면 시스템이 어떻게 동작하는가"
시나리오 기반 흐름 설명

---

### 📄 8-1. 게시판 서비스

#### **📰 1) 게시글 목록 조회 (+ 검색)**

**🔁 전체 흐름**
1) 사용자가 `/articles` 접속  
2) `SearchType` + `searchKeyword` 파라미터 전달 (optional)  
3) `ArticleService.searchArticles()` 호출  
   - 3-1) 검색어 없음 → `articleRepository.findAll(pageable)`  
   - 3-2) 검색어 있음 → SearchType 별 분기  
     - title  
     - content  
     - userId  
     - nickname  
     - hashtag  
4) `Page<ArticleDto>` 반환  
5) `Page<ArticleResponse>` 로 변환  
6) `index.html` 렌더링  

**🔍 검색 가능 항목**
- 제목  
- 본문  
- 작성자 ID  
- 작성자 닉네임  
- 해시태그 (#spring 등)

---

#### **✏️ 2) 게시글 작성**

**(1) 작성폼 조회**
1) `GET /articles/form` 요청  
2) 빈 `ArticleFormResponse` 생성  
3) `form.html` 렌더링  

**(2) 게시글 저장**
1) `POST /articles/form` 요청  
2) 로그인 사용자 인증 (`BoardPrincipal`)  
3) `ArticleRequest → ArticleDto` 변환  
4) `ArticleService.saveArticle()`  
   - 4-1) `UserAccount` 프록시 조회  
   - 4-2) `dto.toEntity(userAccount)`  
   - 4-3) `articleRepository.save()`  
5) `/articles` 로 리다이렉트  

---

#### **📄 3) 게시글 상세 보기**

1) `GET /articles/{articleId}` 요청  
2) `ArticleService.getArticleWithComments()`  
   - 2-1) 게시글 조회  
   - 2-2) 댓글 목록 조회  
   - 2-3) `ArticleWithCommentsDto` 생성  
3) `ArticleWithCommentsResponse` 로 매핑  
4) `detail.html` 렌더링  

---

#### **🛠 4) 게시글 수정**

**(1) 수정폼 조회**
1) `GET /articles/{id}/form` 요청  
2) `Article` 조회  
3) 작성자 동일성 검사 (`article.userId == principal.userId`)  
4) `ArticleResponse` 로 데이터 바인딩  
5) `form.html` 렌더링  

**(2) 수정 처리**
1) `POST /articles/{id}/form` 요청  
2) `ArticleRequest → ArticleDto` 변환  
3) `ArticleService.updateArticle()`  
   - 3-1) `Article` 프록시 조회  
   - 3-2) `UserAccount` 프록시 조회  
   - 3-3) 작성자 동일성 검사  
   - 3-4) Dirty Checking 기반 업데이트  
     - title 변경  
     - content 변경  
     - hashtag 변경  
4) `/articles/{id}` 로 이동  

---

#### **🗑 5) 게시글 삭제**

1) `POST /articles/{id}/delete` 요청  
2) 로그인 사용자 확인  
3) `ArticleService.deleteArticle()`  
   - 3-1) `articleRepository.deleteByIdAndUserAccount_UserId()`  
4) `/articles` 로 이동  

---

#### **💬 6) 댓글 작성**

1) `POST /comments` 요청  
2) `ArticleCommentRequest` DTO 수신  
3) `ArticleCommentService.saveArticleComment()`  
   - 3-1) `Article` 프록시 조회  
   - 3-2) `UserAccount` 프록시 조회  
   - 3-3) `dto.toEntity(article, userAccount)`  
   - 3-4) 댓글 저장  
4) `/articles/{articleId}` 로 이동  

---

#### **🗑 7) 댓글 삭제**

1) `POST /comments/{commentId}/delete` 요청  
2) 인증 사용자 검증  
3) `ArticleCommentService.deleteArticleComment()`  
   - 3-1) `articleCommentRepository.deleteByIdAndUserAccount_UserId()`  
4) `/articles/{articleId}` 로 이동  

---

### 💳 8-2. 결제/후원 서비스

> Self-Support 결제 시스템은 **지갑(Wallet)**, **주문(Order)**, **후원(Donation)**,  
그리고 **PG 결제(Toss Payments)** 를 중심으로 동작합니다.

아래는 각 기능별 전체 흐름(Use Case Flow)입니다.

---

#### **💰 1) 지갑 충전(Charge) 흐름**

> 사용자가 포인트 충전을 완료하기까지의 전체 단계 흐름입니다.

**(1) 충전 페이지 조회**
1. `GET /wallets/charge` 요청  
2. 로그인 사용자 ID 기반 `WalletService.findWalletByUserId()`  
    - 지갑 없으면 자동 생성  
3. 잔액 + userId 모델에 바인딩  
4. `charge-page.html` 렌더링 (충전 금액 선택 UI)

**(2) Order 생성 → 결제 위젯 이동**
1. 사용자가 충전 금액 선택 후 submit  
2. `GET /api/wallets/charge?userId&amount` 호출  
3. Order 엔티티 생성  
    - requestId(UUID)  
    - amount  
    - userId  
    - status = WAIT  
4. 저장 후 `payment/charge-order.html` 렌더링  
5. Toss Payments 결제 위젯 초기화  
    - requestId  
    - amount  
    - customerKey = “customerKey-{userId}”

**(3) Toss 결제 요청 → 성공 Redirect**
1. 사용자가 “충전하기” 버튼 클릭  
2. JS 위젯에서 Toss로 결제 요청  
3. 성공 시 PG가 브라우저를 /charge-order-requested?orderId=xx 로 리다이렉트

**(4) 결제 승인 처리 (confirm)**
1. 프론트엔드가 POST /api/wallets/charge-confirm 요청
    - body: { paymentKey, orderId, amount }
2. PaymentProcessingService.createCharge() 호출
	- 2-1) Order 조회
    - 2-2) 외부 PG 승인 API 호출
    - 2-3) Order 상태 APPROVED
    - 2-4) Wallet 잔액 증가
    - 2-5) Transaction(CHARGE) 생성
   
**(5) 후처리 및 페이지 이동**
1. confirm 성공 → 302 Redirect: /donations
(충전 후 자연스럽게 후원 페이지로 이동)

**📌 최종 요약**
```yaml
[사용자] → 충전 페이지 → 금액 선택
 → Order 생성 → Toss 위젯 결제
 → 결제 성공 리다이렉트
 → 서버에서 confirm 처리
 → Wallet 증가 + Transaction 생성
 → donations 페이지 이동
```

---

#### **🎁 2) 직접 결제(카드) 후원 흐름**

> 포인트를 사용하지 않고 **"바로 결제(카드)"** 로 후원하는 흐름입니다.

**(1) Donation 생성 (PENDING)**
1. 사용자가 후원 페이지에서 “직접 결제(DIRECT)” 선택  
2. `POST /api/donations/direct` 호출  
3. Donation(PENDING) 생성  
    - amount = 후원 아이템 금액  
    - status = PENDING  
4. Donation 기반 Order 생성  
    - status = WAIT  
5. `payment/order.html` 렌더링 (Toss 위젯)

**(2) Toss 결제 위젯 → 카드 결제**
1. “결제하기” 버튼 클릭  
2. Toss 결제 요청
3. 결제 성공 시 브라우저를 /order-requested?orderId=xx 로 리디렉트

**(3) Toss Confirm 승인 처리**
1. POST /payments/confirm
    - paymentKey
    - orderId
2. PaymentProcessingService.createDirectDonation() 호출
    2-1) Order 조회 → APPROVED
    2-2) Donation 조회 → COMPLETED
    2-3) PG Payment Transaction 생성 (donation 기록 저장)

**(4) 후처리**
1. Donation 완료 페이지로 이동
2. 후원 내역은 Donation + Transaction 테이블로 관리

**📌 최종 요약**
```yaml
[사용자] → 후원 페이지 → DIRECT 선택
 → Donation(PENDING) + Order 생성
 → Toss 결제
 → confirm 처리
 → Order APPROVED
 → Donation COMPLETED
 → Transaction 생성
```

---

####🎯 **3) 포인트 후원(POINT Donation) 흐름**

> 포인트(지갑 잔액)으로 후원하는 경우, external PG 없이 서버 내부만으로 처리되는 흐름입니다.
> Transaction(PAYMENT) + Wallet 차감 + Donation COMPLETED 까지 한 번에 처리됩니다.

**(1) 후원 요청 – POINT 방식 선택**
1. 후원 페이지(/donations)에서 후원 물품 선택 (BOOK / FOOD / SUPPLY)
2. "포인트 후원" 선택
3. POST /api/donations/point 요청
    - form-data: userId, item
4. DonationService에서 해당 후원 아이템 가격 조회
    - BOOK → 10,000원
    - FOOD → 30,000원
    - SUPPLY → 50,000원

**(2) Donation(PENDING → COMPLETED) 생성 및 처리**
**내부 처리 흐름**
1. 지갑 조회
    - walletService.findWalletByUserId(userId)
2. 잔액 부족 여부 확인
    - 부족 → 400 에러 "잔액 부족"
3. Donation 생성 (PENDING)
    - amount
    - item
    - userId
    - status = PENDING
4. Wallet 잔액 차감
    - walletService.addBalance(amount.negate())
5. Transaction 생성
    - type = PAYMENT
    - donationId
    - amount
    - walletId
6. Donation 상태 COMPLETED 로 변경

**(3) 처리 완료 후 응답**
1. 성공 시 응답 JSON 또는 redirect
2. 화면에서는 "포인트 후원 성공" 알림

**📌 최종 요약**
```yaml
[사용자] → 후원 페이지 → POINT 선택
 → Donation(PENDING) 생성
 → Wallet 잔액 차감
 → Transaction(PAYMENT) 생성
 → Donation COMPLETED
 → 성공 메시지
```

---

## 9. 기능별 핵심 동작 코드 (요약)

### 📄 9-1. 게시판 서비스

#### **🔹 게시글 저장 — Dirty Checking + DTO 변환 기반 저장**

```java
// ArticleService.java
@Transactional
public Long saveArticle(ArticleDto dto) {
    UserAccount user = userAccountRepository.getReferenceById(dto.userAccountDto().userId());

    Article article = Article.of(
            dto.title(),
            dto.content(),
            dto.hashtag(),
            user
    );

    return articleRepository.save(article).getId();
}
```

📌 설명
- DTO → Entity 변환 후 저장
- 작성자(UserAccount)를 프록시(getReferenceById)로 조회하여 불필요한 SELECT 방지
- 저장 후 게시글 ID 반환

#### **🔹 Spring Security 인증 — UserDetailsService + Custom Principal**

```java
// SecurityConfig.java
@Bean
public UserDetailsService userDetailsService(UserAccountRepository repo) {
    return username -> repo.findById(username)
            .map(UserAccountDto::from)
            .map(BoardPrincipal::from)
            .orElseThrow(() -> new UsernameNotFoundException("유저 없음: " + username));
}
```

📌 설명
- username 기반 DB 조회
- UserAccount → UserAccountDto → BoardPrincipal 로 변환
- 존재하지 않으면 Spring Security가 로그인 실패 처리

---

### 💳 9-2. 결제/후원 서비스

#### **🔹 (1) 지갑 충전(Charge) — 멱등성 보장 + 트랜잭션 삽입 원자성**

```java
// TransactionService.java
public ChargeTransactionResponse charge(ChargeTransactionRequest request) {
    // 금액 유효성 검증
    if (request.amount() == null || request.amount().signum() <= 0) {
        throw new IllegalArgumentException("결제 금액은 양수여야 합니다.");
    }

    try {
        // 1) 지갑 조회
        FindWalletResponse wallet = walletService.findWalletByUserId(request.walletId());

        // 2) 잔액 충전
        AddBalanceWalletResponse updated =
                walletService.addBalance(new AddBalanceWalletRequest(wallet.id(), request.amount()));

        // 3) 충전 트랜잭션 생성 및 저장
        Transaction tx = Transaction.createChargeTransaction(
                wallet.userId(), wallet.id(), request.orderId(), request.amount()
        );

        transactionRepository.save(tx);

        return new ChargeTransactionResponse(updated.walletId(), updated.balance());
    }
    catch (DataIntegrityViolationException e) {
        // 4) 멱등성 처리 — 이미 처리된 orderId
        Transaction existing = transactionRepository
                .findTransactionByOrderId(request.orderId())
                .orElseThrow();
        return new ChargeTransactionResponse(existing.getWalletId(), existing.getAmount());
    }
}
```

📌 설명
- orderId 에 UNIQUE 제약 조건을 둬 동일 요청이 여러 번 들어와도 1회만 반영
- 충전과 트랜잭션 생성이 원자적
- 중복 요청 시 DB 예외를 캐치해 기존 트랜잭션 반환 → 멱등성(Idempotency) 보장
- 실패 시 rollback 보장되는 구조

#### **🔹 (2) PG 결제 승인 처리 — Order 승인 → Wallet 반영 → Transaction 생성**
```java
// PaymentProcessingService.java
@Transactional
public void createCharge(ConfirmRequest request, boolean isRetry) {
    // 1) 주문 조회
    Order order = orderRepository.findByRequestId(request.orderId())
            .orElseThrow(() -> new RuntimeException("Order not found"));

    // 2) PG 결제 승인 API 호출
    PgConfirmResponse pgResponse = externalPgClient.confirmPayment(request);

    // 3) 주문 상태 업데이트
    order.approve(pgResponse.paymentKey());

    // 4) 지갑 잔액 반영
    AddBalanceWalletResponse wallet =
            walletService.addBalance(new AddBalanceWalletRequest(order.getUserId(), order.getAmount()));

    // 5) 충전 트랜잭션 생성
    transactionService.pgPayment(order.getUserId(), order.getRequestId(), order.getAmount(), pgResponse.paymentKey());
}
```

📌 설명
- PG 결제 승인 → 주문 승인 → 지갑 잔액 증가 → 트랜잭션 생성 전체 플로우 처리
- 외부 API 실패 시 RetryRequest 저장 → 재시도 가능
- 전체 로직이 하나의 트랜잭션으로 관리됨 (@Transactional)
- 승인 성공 후 서버 에러가 나도 retry 로 복구 가능

### 🔍 핵심 동작 코드 (상세)

더 많은 세부 구현 코드는 아래 문서에 정리해 두었습니다.

👉 (https://www.notion.so/ssencoding/2b271f1e0e6c8011ba89c540f4254b9a?source=copy_link)

---

## 🧪 10. 테스트 전략

### 📄 10-1. 게시판 서비스

#### **✅ 단위 테스트**

**🔸 ArticleServiceTest**
- 검색/저장/수정/삭제 로직 테스트

**🔸 ArticleCommentServiceTest**
- 댓글 저장/수정/삭제
- 존재하지 않는 ID 접근 테스트 포함

#### **✅ MVC 테스트**

**🔸 ArticleControllerTest**
- HTTP 요청 → Model 값 → View 이름 검증
- Security 연동 테스트 포함

**🔸 ArticleCommentControllerTest**
- 댓글 등록/삭제 요청 테스트
- CSRF 적용 검증

#### **✅ 통합 테스트**
- BoardProjectApplicationTests
- 전체 Bean 로딩 테스트
- Spring Boot 부팅 검증

---

### 💳 10-2. 결제/후원 서비스

#### **✅ 단위 테스트(Unit Test)**

**🔸 WalletServiceTest**
- 지갑 생성(userId unique) 멱등성 검증  
- 잔액 충전 / 차감 로직 테스트  
- 잔액 부족, 존재하지 않는 지갑 등 예외 처리 검증  

**🔸 TransactionServiceTest**
- 충전/후원 트랜잭션 생성 정상 동작 검증  
- orderId / donationId 기반 멱등성 처리 테스트  
- 중복 요청 시 `DataIntegrityViolationException` 처리 확인  

**🔸 OrderServiceTest**
- 주문 생성(WAIT 상태) 정상 생성 테스트  
- 주문 상태 업데이트(approve / fail) 로직 검증  
- requestId 기반 Order 조회 테스트  

**🔸 DonationServiceTest**
- **포인트 후원:** 잔액 차감 → Donation 상태 COMPLETED 전환 전체 흐름 검증  
- **직접 결제 기반 후원:** Order 승인 → Donation 완료 처리 테스트  
- Donation 상태(PENDING → COMPLETED) 전이 검증  

#### **✅ MVC 테스트 (Controller / Web Layer)**

**🔸 CheckoutControllerTest**
- 충전/후원 결제 진입 시 필수 모델(requestId, amount, customerKey) 바인딩 검증  
- `/checkout/{requestId}` 위젯 페이지 렌더링 테스트  
- 존재하지 않는 주문/후원 접근 시 예외 처리 검증  

#### **✅ 통합 테스트(Integration Test)**

**🔸 WalletServiceIntgTest**
*(동시성 + 멱등성 핵심 테스트)*  
- 다중 스레드 환경에서 `createWallet` 동시 요청 → 단 하나만 생성되는지 검증  
- `addBalance` 동시성 테스트 (잔액이 정확히 1회만 증가하는지 확인)  
- DB 격리 수준에서 멱등성(Idempotency) 보장 확인  

**🔸 PaymentProcessingServiceTest** 
*(PG 승인 → 내부 도메인 처리까지 전체 오케스트레이션 검증)*  
- Toss PG 승인 → Order 승인 → Wallet 업데이트 → Transaction 생성 전체 플로우 테스트  
- 포인트 후원 / 직접 결제 후원 흐름 모두 검증  
- 승인 성공 후 서버 단계에서 fail 발생 → retry 기반 정상 복구 여부 확인  
- 잘못된 승인 요청(paymentKey / orderId 불일치) 시 예외 처리 검증  

---

## 🛠 11. 트러블 슈팅 (문제 해결)

### 📄 11-1. 게시판 서비스

#### **✔ Issue 1 — Spring 6 환경에서 컨트롤러 파라미터 적용 오류**

**🔻 에러 메시지**
```yaml
Name for argument ... Ensure that the compiler uses the '-parameters' flag.
```

**🔍 원인**
Gradle Java 컴파일러 설정에서 -parameters 옵션이 적용되지 않음

**🛠 해결**
build.gradle에서 JavaCompile에 명시적으로 옵션 추가

```gradle
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs.add("-parameters")
}
```

#### **✔ Issue 2 — Thymeleaf Decoupled Logic 바인딩 실패**

**🔍 원인**
Lombok + @ConfigurationProperties + @ConstructorBinding 조합에서
Spring Boot 3 환경에서 동작하지 않음

**🛠 해결**
record 기반으로 변경

```java
@ConfigurationProperties("spring.thymeleaf3")
public record Thymeleaf3Properties(boolean decoupledLogic) {}
```

---

### 💳 11-2. 결제/후원 서비스

#### **✔️ Issue — Spring Data JPA 동시성 테스트에서 `expected: X but was: Y` 발생**

**🔻 에러 메시지**

```yaml
AssertionFailedError:
expected: 35L
but was: 1L
```

**🔍 원인**
- 테스트에서 생성된 **Wallet 엔티티가 DB에 즉시 반영되지 않음 (flush 미실행)**  
- 여러 스레드가 동시에 `findWalletByWalletId()` 호출 시  
  → 아직 DB에 존재하지 않아 **조회 실패** 발생  
- JPA는 기본적으로 **트랜잭션 커밋 시점에 flush**하므로  
  멀티스레드 테스트에서는 DB 상태가 보장되지 않음  
- 테스트 메서드에 `@Transactional`을 사용하지 않았기 때문에  
  flush 타이밍을 명시적으로 잡아줘야 함

**🛠 해결 방법**
- 지갑 생성 후 즉시 DB에 반영하도록 flush() 추가

```java
CreatedWalletResponse wallet = walletService.createWallet(new CreateWalletRequest(1L));
Long walletId = wallet.id();
// 💡 동시성 테스트에서는 Wallet을 반드시 DB에 즉시 반영해야 한다
walletRepository.flush();
```

**🎯 해결 효과**
- 다른 스레드에서 Wallet 조회 안정화
- 충전/결제 멱등성 테스트 정상 통과
- 트랜잭션 1건만 생성
- expected != actual 에러 완전 해결

### 🔍 문제 해결 내용 (상세)

더 상세한 문제 해결 내용은 아래 문서에서 정리해 두었습니다.

👉 (https://www.notion.so/ssencoding/2b271f1e0e6c8011ba89c540f4254b9a?source=copy_link)

---

## 🚦 12. 실행 방법
```bash
git clone https://github.com/{username}/selfsupport-site.git
cd selfsupport-site

./gradlew clean build

java -jar build/libs/board-project-0.0.1-SNAPSHOT.jar
```

---

## 🌟 13. 서비스 화면

"아래는 게시판 및 결제·후원 기능의 주요 화면들입니다."

### 📄 13-1. 게시판 서비스

- **로그인 화면**
<img width="400p" alt="로그인" src="https://github.com/yjin0034/selfsupport-site/blob/main/document/images/%EB%A1%9C%EA%B7%B8%EC%9D%B8.png" />
<br>

- **게시글 목록**
<img width="400p" height="893" alt="게시글 목록" src="https://github.com/yjin0034/selfsupport-site/blob/main/document/images/%EA%B2%8C%EC%8B%9C%EA%B8%80%20%EB%AA%A9%EB%A1%9D.png" />
<br>

- **게시글 상세**
<img width="400" alt="게시글 상세" src="https://github.com/yjin0034/selfsupport-site/blob/main/document/images/%EA%B2%8C%EC%8B%9C%EA%B8%80%20%EC%83%81%EC%84%B8.png" />
<br>

- **게시글 작성**
<img width="400" alt="게시글 작성" src="https://github.com/yjin0034/selfsupport-site/blob/main/document/images/%EA%B2%8C%EC%8B%9C%EA%B8%80-%EC%9E%91%EC%84%B1.gif" />
<br>

- **게시글 수정**
<img width="400" alt="게시글 수정" src="https://github.com/yjin0034/selfsupport-site/blob/main/document/images/%EA%B2%8C%EC%8B%9C%EA%B8%80-%EC%88%98%EC%A0%95.gif" />
<br>

- **댓글 작성**
<img width="400" alt="댓글 작성" src="https://github.com/yjin0034/selfsupport-site/blob/main/document/images/%EB%8C%93%EA%B8%80-%EC%9E%91%EC%84%B1.gif" />
<br>

- **게시글 검색**
<img width="400" alt="게시글 검색" src="https://github.com/yjin0034/selfsupport-site/blob/main/document/images/%EA%B2%8C%EC%8B%9C%EA%B8%80-%EA%B2%80%EC%83%89.gif" />
<br>

- **해시태그 검색**
<img width="400" alt="해시태그 검색" src="https://github.com/yjin0034/selfsupport-site/blob/main/document/images/%ED%95%B4%EC%8B%9C%ED%83%9C%EA%B7%B8-%EA%B2%80%EC%83%89.gif" />
<br>

---

### 💳 13-2. 결제/후원 서비스

- **충전 페이지**
<img width="400" alt="충전 페이지" src="https://github.com/yjin0034/selfsupport-site/blob/main/document/images/%EC%B6%A9%EC%A0%84%20%ED%8E%98%EC%9D%B4%EC%A7%80.png" />
<br>

- **후원 페이지**
<img width="400" alt="후원 페이지" src="https://github.com/yjin0034/selfsupport-site/blob/main/document/images/%ED%9B%84%EC%9B%90%20%ED%8E%98%EC%9D%B4%EC%A7%80.png" />
<br>

- **Toss 결제 위젯**
<img width="400" alt="Toss 결제 위젯" src="https://github.com/yjin0034/selfsupport-site/blob/main/document/images/Toss%20%EA%B2%B0%EC%A0%9C%20%EC%9C%84%EC%A0%AF.png" />
<br>

- **결제 성공 리디렉션 페이지**
<img width="400" alt="결제 성공 리다이렉션" src="https://github.com/yjin0034/selfsupport-site/blob/main/document/images/%EA%B2%B0%EC%A0%9C%20%EC%84%B1%EA%B3%B5%20%EB%A6%AC%EB%8B%A4%EC%9D%B4%EB%A0%89%EC%85%98.png" />
<br>

- **지갑 충전**
<img width="400" alt="지갑 충전" src="https://github.com/yjin0034/selfsupport-site/blob/main/document/images/%EC%A7%80%EA%B0%91-%EC%B6%A9%EC%A0%84.gif" />
<br>

- **직접 결제 후원**
<img width="400" alt="직접 결제 후원" src="https://github.com/yjin0034/selfsupport-site/blob/main/document/images/%EC%A7%81%EC%A0%91-%EA%B2%B0%EC%A0%9C-%ED%9B%84%EC%9B%90_1.gif" />
<br>

- **포인트 후원**
<img width="400" alt="포인트 후원" src="https://github.com/yjin0034/selfsupport-site/blob/main/document/images/%ED%8F%AC%EC%9D%B8%ED%8A%B8-%ED%9B%84%EC%9B%90_1.gif" />
<br>

---

## 🧩 14. 프로젝트 구조 요약
이 프로젝트를 통해 익힌 것:

### 📄 14-1. 게시판 서비스
- Spring Boot MVC 전 과정 (Controller → Service → Repository)
- JPA 도메인 설계 및 연관관계 매핑
- QueryDSL 기반 동적 검색
- Spring Security 인증/인가 구조
- Thymeleaf Decoupled Logic 적용
- 테스트 기반 개발 (단위, MVC, 통합)
- 페이징, 검색, 정렬 기능까지 포함한 실제 웹서비스 개발 경험

### 💳 14-2. 결제/후원 서비스
- 실제 서비스 수준의 **복합 결제 아키텍처 구현 능력**
- **Wallet / Order / Transaction / Donation** 핵심 도메인 설계
- **Toss Payments API 연동** 경험
- 멱등성 기반의 **안전한 결제 처리 및 장애 대응**
- **Retry 기반 보상 트랜잭션 구조 설계**
- 도메인 기반 설계 + 계층형 아키텍처 구성 능력
- **테스트 중심의 안정성 확보**

--- 
