# Hopes Kotlin Server

Notion 기능명세서와 API 명세서를 기준으로 만든 Kotlin/Spring Boot REST API입니다.

## 준비

- JDK 21
- Maven 3.9+
- MySQL 8 이상

MySQL을 실행한 뒤 프로젝트 루트의 `.env`에서 아래 두 값을 자신의 MySQL 계정에 맞게 입력합니다.

```properties
DATABASE_USERNAME=root
DATABASE_PASSWORD=MySQL을_설치할_때_정한_비밀번호
```

기본 접속 주소는 다음과 같습니다. 해당 계정에 데이터베이스 생성 권한이 있으면 `hopes` 데이터베이스가 자동 생성됩니다.

```properties
DATABASE_URL=jdbc:mysql://localhost:3306/hopes?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul
```

## 실행

```bash
mvn spring-boot:run
```

서버 기본 주소는 `http://localhost:8080`입니다. 테이블은 JPA가 자동 생성·갱신합니다.

## Gmail 인증메일

발신자는 `team.native.official@gmail.com`으로 설정되어 있습니다. `.env`의 `MAIL_PASSWORD=` 뒤에 공백 없이 Google 앱 비밀번호를 입력하면 매 요청마다 생성되는 랜덤 6자리 인증번호가 전송됩니다.

## 주요 API

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/signup/email-verifications` | 학교 이메일 인증번호 요청 |
| POST | `/api/signup/email-verifications/confirm` | 인증번호 확인 |
| POST | `/api/signup` | 회원가입 |
| POST | `/api/login` | 로그인 및 Bearer 토큰 발급 |
| GET | `/api/main?searchKeyword=` | 대화 목록/검색 |
| POST | `/api/chats` | 새 대화 생성 |
| GET | `/api/chats/{id}` | 대화 불러오기 |
| POST | `/api/chats/{id}/messages` | 사용자 질문 저장 |
| PATCH | `/api/general` | 다크/라이트 테마 변경 |
| GET/PATCH | `/api/mypage` | 사용자 정보 조회/수정 |
| GET | `/api/setting/main` | 설정 조회 |
| PATCH | `/api/setting` | 커스텀 프롬프트 저장/대화 전체 삭제 |
| POST | `/api/password/request` | 비밀번호 재설정 인증 요청 |
| POST | `/api/password/reset` | 비밀번호 변경 |

로그인 이후 API에는 `Authorization: Bearer {accessToken}` 헤더가 필요합니다.

AI 모델 연동은 AI 담당자가 별도로 제공하는 영역이므로 이 서버는 현재 사용자 질문과 대화 내역 저장을 담당합니다.
