# 배포

`master` 에 push 하면 GitHub Actions 가 테스트 → 이미지 빌드 → 서버 배포까지 한다.

```
master push
   │
   ├─ ① Test           백엔드 mvn test · 프런트 npm ci && build   (PR 도 여기까지)
   ├─ ② Build & Push   backend / web 이미지를 Docker Hub 에 올린다
   └─ ③ Deploy         compose 전송 → pull → up -d → edge 를 거쳐 헬스체크
```

서버에서는 앞단 [edge](https://github.com/hunesu1114/EdgeProxy) 프록시가 TLS 를 끝내고
도메인을 보고 이 앱으로 넘긴다. 이 앱 컨테이너는 **호스트 포트를 열지 않는다.**

```
인터넷 :443
   │
edge-proxy                     TLS 종단 + 도메인 라우팅
   │  weekly-report-mik.duckdns.org
weekly-report-web              정적 파일 + /api 프록시
   │
weekly-report-backend          Spring Boot
   │
weekly-report-postgres
```

---

## 최초 설정

**아래 순서를 지켜야 한다.** 인증서가 없으면 edge 배포가 `nginx -t` 에서 막히고,
edge 설정이 없으면 앱 배포가 헬스체크에서 막힌다.

### 1. 서버 · 배포용 SSH 키

edge · SecretManager 저장소에 이미 등록해 둔 키가 있으면 **같은 값을 그대로 쓰면 된다.**
이 단계는 건너뛰고 3번으로 간다.

없다면 서버에서 만든다.

```bash
ssh-keygen -t ed25519 -C "github-actions" -f ~/.ssh/gh_deploy -N ""
```

```bash
cat ~/.ssh/gh_deploy.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys
```

```bash
cat ~/.ssh/gh_deploy
```

마지막 명령이 출력한 **개인키 전문**(`-----BEGIN` ~ `-----END` 줄 포함)을
뒤에서 `SERVER_SSH_KEY` 로 넣는다.

### 2. 서버 · 인증서 발급

DuckDNS 는 HTTP-01 이 공유기 때문에 막히는 경우가 많아 **DNS-01** 을 쓴다.

```bash
export DuckDNS_Token="발급받은_토큰"
```

```bash
acme.sh --issue --dns dns_duckdns -d weekly-report-mik.duckdns.org
```

```bash
acme.sh --install-cert -d weekly-report-mik.duckdns.org \
  --key-file       /etc/edge/certs/weekly-report/privkey.pem \
  --fullchain-file /etc/edge/certs/weekly-report/fullchain.pem \
  --reloadcmd      "docker exec edge-proxy nginx -s reload"
```

`--install-cert` 는 대상 디렉터리를 만들어 주지 않는 경우가 있다. 미리 만들어 둔다.

```bash
sudo mkdir -p /etc/edge/certs/weekly-report
```

`--reloadcmd` 덕분에 갱신될 때마다 nginx 가 알아서 다시 읽는다.
이게 없으면 인증서는 갱신됐는데 nginx 는 옛것을 물고 있어 어느 날 만료된다.

확인:

```bash
ls -l /etc/edge/certs/weekly-report/
```

`fullchain.pem` 과 `privkey.pem` 이 둘 다 보여야 다음으로 넘어간다.

### 3. 서버 · DB 비밀번호 배치

```bash
mkdir -p ~/weekly-report && cd ~/weekly-report
```

```bash
umask 077 && printf 'POSTGRES_DB=weekly_report\nPOSTGRES_USER=weekly\nPOSTGRES_PASSWORD=%s\n' "$(openssl rand -base64 24)" > .env.secrets
```

```bash
chmod 600 .env.secrets && cat .env.secrets
```

> **이 파일은 배포마다 새로 만들면 안 된다.**
> postgres 는 볼륨을 처음 만들 때의 비밀번호로 계정을 굳힌다. 이후 환경변수만 바꿔도
> DB 안의 비밀번호는 그대로라, 다음 배포에서 인증 실패로 기동하지 못한다.
> 그래서 CI 가 만들지 않고 서버에 한 번만 둔다. 템플릿은 `deploy/env.secrets.example`.

### 4. GitHub Secrets 등록

이 저장소 → Settings → Secrets and variables → Actions → **Secrets** 탭.

| 이름 | 설명 |
|---|---|
| `DOCKERHUB_USERNAME` | Docker Hub 계정 |
| `DOCKERHUB_TOKEN` | Docker Hub Access Token (비밀번호 아님) |
| `SERVER_HOST` | `119.67.28.20` |
| `SERVER_USER` | SSH 사용자 |
| `SERVER_SSH_KEY` | SSH 개인키 전문 |
| `SERVER_PORT` | SSH 포트 (22 면 생략 가능) |

edge · SecretManager 저장소에 등록한 것과 같은 값이다.
**Secret 은 저장소마다 따로다.** 다른 저장소에 넣은 값은 넘어오지 않는다.

Docker Hub 토큰은 Docker Hub → Account Settings → Personal access tokens 에서
`Read & Write` 권한으로 만든다.

### 5. edge 에 도메인 추가

`deploy/edge/weekly-report.conf` 를 edge 저장소의 `conf.d/` 에 넣고 push 하면
edge 의 CI 가 반영한다. (이미 반영되어 있다면 건너뛴다.)

```bash
cp deploy/edge/weekly-report.conf ~/edge/conf.d/weekly-report.conf
```

edge 저장소에서 커밋 후 `master` 에 push.

### 6. 배포

이 저장소 `master` 에 push 하거나, Actions 탭에서 `CI/CD` 를 수동 실행한다.

```
https://weekly-report-mik.duckdns.org
```

---

## 평소 배포

`master` 에 push 하면 끝이다. 서버에서 할 일은 없다.

이미지 태그는 커밋 SHA 로 붙고, `.env.deploy` 의 `IMAGE_TAG` 가 그 값으로 덮어써진다.
`latest` 도 같이 올라가지만 배포는 SHA 태그를 쓴다 — `latest` 만 쓰면
어떤 커밋이 돌고 있는지 서버에서 알 수 없다.

---

## 손으로 배포해야 할 때

```bash
cd ~/weekly-report && docker compose -f docker-compose.prod.yml --env-file .env.secrets --env-file .env.deploy pull
```

```bash
cd ~/weekly-report && docker compose -f docker-compose.prod.yml --env-file .env.secrets --env-file .env.deploy up -d
```

특정 커밋으로 되돌리려면 `.env.deploy` 의 `IMAGE_TAG` 를 그 커밋 SHA 로 바꾸고 위를 다시 실행한다.

---

## 문제가 생겼을 때

| 증상 | 확인 |
|---|---|
| 배포가 `.env.secrets 가 없습니다` 로 멈춤 | 위 3번을 안 했다 |
| 헬스체크 실패 · 502 | edge 에 `weekly-report.conf` 가 반영됐는지, 인증서가 있는지 |
| edge 배포가 `nginx -t` 에서 실패 | `/etc/edge/certs/weekly-report/` 에 인증서가 없다 (위 2번) |
| 백엔드가 DB 인증 실패로 재시작 | `.env.secrets` 를 다시 만들었다. 아래 "DB 비밀번호를 잃어버렸을 때" |
| 엉뚱한 사이트가 뜬다 | 해당 도메인의 server 블록이 없어 edge 기본 서버로 갔다 |

```bash
cd ~/weekly-report && docker compose -f docker-compose.prod.yml --env-file .env.secrets --env-file .env.deploy logs -f backend
```

```bash
docker network inspect edge-net | grep -A2 weekly-report
```

`weekly-report-web` 이 `edge-net` 에 없으면 edge 가 찾아가지 못해 502 가 난다.

### DB 비밀번호를 잃어버렸을 때

주간보고 데이터는 되살릴 수 없으므로 먼저 덤프를 시도한다.

```bash
docker exec weekly-report-postgres pg_dumpall -U weekly > ~/weekly-report-backup.sql
```

컨테이너가 이미 죽어 붙지 못하면, 볼륨을 지우고 처음부터 다시 시작하는 수밖에 없다.

```bash
docker volume rm weekly-report-prod_postgres-data
```
