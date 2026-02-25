# Seat Hold Ticketing — v0.1 정리(내부 노트)

## 목표(0.1v)
- 기능 구현이 아니라 **규칙(불변식/원자성/멱등)을 테스트로 증명**한 상태로 “마감”
- 과한 확장(큐/알림/대기열/환불 등)은 0.2+로 미룸

---

## 현재 모델(0.1v) — 핵심 설계 선택
### 좌석 점유 표현
- `seats` 테이블은 `AVAILABLE / SOLD`만 사용
- HOLD는 `hold_groups` + `hold_group_seats`로만 표현(유효기간은 expiresAt)
- 좌석 경합 방지는 `hold_group_seats`의 유니크 키로 처리:
  - (event_id, seat_id) unique → 같은 좌석을 동시에 HOLD하려 하면 한쪽은 DB 제약 위반으로 실패

### Confirm 흐름(핵심)
- Confirm은 “현재 유효한 Hold(만료 전)”만 SOLD로 바꿔야 함
- Confirm 멱등:
  - 1순위: `paymentTxId`
  - 2순위: `(userId, confirmKey)`
- 결제는 stub이며 상태(APPROVED/DECLINED/TIMEOUT)는 결정 후 불변

---

## 0.1v에서 증명해야 하는 불변식(체크리스트)
### HOLD
- All-or-Nothing: seatIds 중 하나라도 못 잡으면 전체 실패
- 멱등:
  - 같은 (userId,eventId,idempotencyKey) 재호출 → **동일 결과**
  - 같은 key인데 seatIds가 다르면 → **IDEMPOTENCY_CONFLICT**
- OnSale 조건 위반 시 HOLD 거절
- 유저 HOLD limit(<=4) 위반 시 HOLD 거절

### EXPIRE(SWEEP)
- 만료된 hold_group_seats / hold_groups는 조건부로 정리됨
- sweep vs confirm 경합 시 데이터가 깨지지 않아야 함(최소한 “더블 성공”은 없어야 함)

### CONFIRM
- OnSale 조건 위반 시 confirm 거절
- 결제 승인(APPROVED)만 confirm 가능
- amount는 서버 산정, 불일치 시 거절
- 멱등:
  - paymentTxId 기반 멱등 반환
  - (userId, confirmKey) 기반 멱등 반환
  - 충돌 시 IDEMPOTENCY_CONFLICT

---

## 0.1v에서 수정/보완한 것(최근 반영)
### 1) deleteSoldHolds() 삭제 개수 mismatch 처리
- 기존: mismatch면 로그만 찍고 계속 진행 가능(=confirm이 성공할 여지)
- 변경: mismatch면 예외 throw → 트랜잭션 롤백(0.1v 규칙 증명에 맞춤)
- 테스트: mismatch 상황에서 예외 + holdGroup delete 호출 안됨 검증

### 2) 테스트 커버리지 보강(필수 구멍 메움)
- HoldService:
  - EVENT_NOT_ON_SALE 거절
  - HOLD_LIMIT_EXCEEDED(기존 hold + 신규 요청, 요청 자체 > 4)
- ConfirmService:
  - PAYMENT_TIMEOUT 거절
  - paymentTxId 없음 → IDEMPOTENCY_CONFLICT
  - paymentTxId 없이 (userId, confirmKey)로 idempotency hit

### 3) HOLD_LIMIT 동시성 레이스는 0.2로 넘김
- 조회 기반으로 limit 판단하는 구조는 동시 요청에서 레이스 가능
- 0.1v는 Known limitation으로 명시 + 코드 TODO로 남김

---

## Known limitation(0.1v에서 의도적으로 남긴 것)
### HOLD_LIMIT의 원자성 보장 부족
- 현재 방식: (기존 hold 좌석 수 조회) + (요청 seat 수)로 판단
- 동시에 2개의 HOLD 요청이 들어오면 둘 다 통과할 수 있음(레이스)
- 0.2에서 “원자적으로 보장”하는 방식으로 개선 예정

---

## 0.1v 마감 조건(내 기준)
- 위 불변식에 대응하는 테스트가 모두 통과
- confirm/hold/expire 경계 케이스에서 “성공하면 안 되는 성공”이 없음
- Known limitation을 정확히 문서화하고 0.2 백로그로 분리

---

# v0.2

## 0.2 목표
- 0.1v에서 남긴 “원자성/정합성/관측성” 구멍을 **DB-중심으로 메우기**
- 기능 확장보다 “동시성/운영” 레벨을 한 단계 올리는 버전

---

## (A) HOLD_LIMIT 원자 보장(최우선)
### 문제
- 동시 HOLD 2개가 같은 유저로 들어오면 limit 우회 가능

### 옵션 1: 유저 단위 serialize(가장 단순/안전)
- (userId,eventId) 단위로 **DB row lock**을 잡고 hold 진행
- 구현 후보:
  - `users`(또는 `user_event_hold_limit`) 테이블에 (userId,eventId) row를 만들고 `SELECT ... FOR UPDATE`
- 장점: 이해/구현/증명 쉬움, 테스트로 재현 가능
- 단점: 유저 단위로 HOLD가 직렬화됨(하지만 limit 규칙 자체가 유저 단위라 합리적)

### 옵션 2: 카운터 테이블 + 조건부 업데이트(가장 “스펙스럽게”)
- `user_event_hold_counter(user_id,event_id,held_count,version/updated_at)`
- HOLD 시:
  - `UPDATE ... SET held_count = held_count + :n WHERE held_count + :n <= 4`
  - 업데이트 성공(1 row)일 때만 seat hold 진행
- 실패 시 즉시 HOLD_LIMIT_EXCEEDED
- seat 확보 실패 시 counter 롤백(같은 트랜잭션에서 보장)

### 0.2 테스트(필수)
- 같은 user로 동시에 HOLD 2번 쏴서 limit이 절대 깨지지 않는지(멀티스레드)

---

## (B) 좌석 조회에서 HELD 상태 노출(UX/정합성)
### 문제
- 현재 HOLD는 hold_group_seats에만 있으므로 seats 조회만 하면 “모두 AVAILABLE처럼 보일” 수 있음

### 구현 방향
- seats 조회 시:
  - `LEFT JOIN hold_group_seats` where `expires_at > now`
  - joined row 존재하면 상태를 `HELD`로 계산해서 응답 DTO에 내려줌
- seats 테이블을 바꾸지 않고도 “조회 응답”은 HELD를 가질 수 있음

### 0.2 테스트
- HOLD 생성 후 조회 시 해당 seat이 HELD로 보이는지
- 만료 sweep 후 조회 시 다시 AVAILABLE로 보이는지

---

## (C) 통합 테스트를 “진짜 DB 경합”으로 끌어올리기(Testcontainers)
### 목표
- Mockito 단위 테스트만으로는 유니크 제약/락/트랜잭션 경합을 ‘증명’하기 부족
- 0.2에서 Testcontainers + 멀티스레드로 “진짜 레이스”를 재현

### 필수 시나리오
- 같은 좌석 동시 HOLD 경쟁(한쪽만 성공)
- HOLD 직후 만료 sweep vs confirm 경합(더블 성공 없어야 함)
- confirm idempotency 충돌(paymentTxId vs confirmKey)

---

## (D) 관측성(운영 로그/메트릭) 최소 추가
- 어떤 이벤트에서:
  - HOLD 성공/실패 사유 분포
  - confirm 실패 사유 분포
  - sweep 삭제 건수
- “장애/이상징후를 알 수 있는 수준”까지만(과한 APM/대시보드X)

---

## (E) 스키마/인덱스 점검(필요 최소)
- confirm 쿼리(hold_group_seats/hold_groups 조인)에서 필수 인덱스 확인
- expires_at 조건으로 sweep/delete를 자주 하므로:
  - `hold_group_seats(expires_at)`
  - `hold_groups(expires_at)`
  - 조인 키들(event_id, hold_group_id 등) 확인

---

# 다음 액션(내가 할 일)
- 0.2에서 (A) 옵션 1 vs 옵션 2 중 하나를 선택하고,
- 선택한 방식 기준으로:
  - 테이블/쿼리/트랜잭션 경계
  - 테스트 시나리오(멀티스레드 + Testcontainers)
  를 설계/구현한다.
