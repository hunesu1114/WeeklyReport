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

> **GitHub Secrets 는 등록한 값을 다시 볼 수 없다.** 쓰기 전용이라 다른 저장소에서
> 복사해 올 수 없고, 화면에도 표시되지 않는다. 값은 서버의 개인키 파일에서 다시 꺼낸다.

서버에 있는 개인키를 찾는다. `authorized_keys` 에 등록된 공개키와 짝이 맞는 것을 고른다.

```bash
ls -l ~/.ssh/
```

```bash
for k in ~/.ssh/id_* ~/.ssh/*deploy*; do case "$k" in *.pub) continue;; esac; [ -f "$k" ] || continue; echo "== $k"; ssh-keygen -y -f "$k" 2>/dev/null; done
```

```bash
cat ~/.ssh/authorized_keys
```

위 두 출력에서 `ssh-ed25519 AAAA...` 부분이 일치하는 개인키가 배포에 쓰는 키다.
`cat` 으로 그 파일 전문을 꺼내 `SERVER_SSH_KEY` 에 넣는다.

짝이 맞는 게 없거나 어떤 키인지 모르겠으면 **새로 만들어 추가하면 된다.**
`authorized_keys` 는 여러 줄을 허용하므로 기존 키는 그대로 살아 있는다.

```bash
ssh-keygen -t ed25519 -C "github-actions-weekly-report" -f ~/.ssh/gh_deploy_weekly -N ""
```

```bash
cat ~/.ssh/gh_deploy_weekly.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys
```

```bash
cat ~/.ssh/gh_deploy_weekly
```

마지막 명령이 출력한 **개인키 전문**(`-----BEGIN` ~ `-----END` 줄 포함)을
뒤에서 `SERVER_SSH_KEY` 로 넣는다. 줄바꿈까지 그대로 붙여야 한다.

### 2. 서버 · 인증서 발급

DuckDNS 는 HTTP-01 이 공유기 때문에 막히는 경우가 많아 **DNS-01** 을 쓴다.

```bash
export DuckDNS_Token="발급받은_토큰"
```

```bash
acme.sh --issue --dns dns_duckdns -d weekly-report-mik.duckdns.org
```

`--install-cert` 는 대상 디렉터리를 만들어 주지 않는다. 먼저 만들어 둔다.
**소유자를 acme.sh 를 돌리는 계정으로 맞춘다.**

```bash
sudo install -d -o "$USER" -g "$USER" /etc/edge/certs/weekly-report
```

> `sudo mkdir` 로 만들면 디렉터리 주인이 root 가 되어 `--install-cert` 가
> `Permission denied` 로 실패한다. 여기서 `sudo acme.sh` 로 우회하면 안 된다.
> acme.sh 는 `~/.acme.sh` 에 설치되어 **갱신 cron 도 같은 사용자로 돌기 때문에**,
> root 로 한 번 넣어두면 60일 뒤 자동 갱신에서 똑같이 실패하고 인증서가
> 조용히 만료된다. 디렉터리를 acme.sh 를 돌리는 사용자 소유로 두어야 한다.
>
> 이미 root 소유로 만들었다면:
>
> ```bash
> sudo chown -R "$USER:$USER" /etc/edge/certs/weekly-report
> ```

이제 설치한다.

```bash
acme.sh --install-cert -d weekly-report-mik.duckdns.org \
  --key-file       /etc/edge/certs/weekly-report/privkey.pem \
  --fullchain-file /etc/edge/certs/weekly-report/fullchain.pem \
  --reloadcmd      "docker exec edge-proxy nginx -s reload"
```

`--reloadcmd` 덕분에 갱신될 때마다 nginx 가 알아서 다시 읽는다.
이 명령도 같은 사용자로 실행되므로, 그 계정이 docker 그룹에 있어야 한다.
이게 없으면 인증서는 갱신됐는데 nginx 는 옛것을 물고 있어 어느 날 만료된다.

확인:

```bash
ls -l /etc/edge/certs/weekly-report/
```

`fullchain.pem` 과 `privkey.pem` 이 둘 다 보여야 다음으로 넘어간다.
소유자가 acme.sh 를 돌리는 계정인지도 함께 본다. 기존 도메인들과 맞춰 두면 된다.

```bash
ls -ld /etc/edge/certs/*/
```

### 3. 서버 · DB 비밀번호 배치

```bash
mkdir -p ~/weekly-report && cd ~/weekly-report
```

```bash
umask 077 && printf 'POSTGRES_DB=weekly_report\nPOSTGRES_USER=weekly\nPOSTGRES_PASSWORD=%s\nAPP_JWT_SECRET=%s\n' "$(openssl rand -base64 24)" "$(openssl rand -base64 32)" > .env.secrets
```

```bash
chmod 600 .env.secrets && cat .env.secrets
```

> **이 파일은 배포마다 새로 만들면 안 된다.**
> postgres 는 볼륨을 처음 만들 때의 비밀번호로 계정을 굳힌다. 이후 환경변수만 바꿔도
> DB 안의 비밀번호는 그대로라, 다음 배포에서 인증 실패로 기동하지 못한다.
> `APP_JWT_SECRET` 도 같다. 값이 바뀌면 발급해둔 로그인 토큰이 전부 무효가 된다.
> 그래서 CI 가 만들지 않고 서버에 한 번만 둔다. 템플릿은 `deploy/env.secrets.example`.

> **이미 `.env.secrets` 가 있는 서버라면** `APP_JWT_SECRET` 한 줄만 덧붙인다.
> 없어도 앱은 뜨지만 기동할 때마다 임시 키가 만들어져, 재배포·재시작 때마다
> 로그인이 전부 풀린다(로그에 경고가 크게 찍힌다).
>
> ```bash
> cd ~/weekly-report && printf 'APP_JWT_SECRET=%s\n' "$(openssl rand -base64 32)" >> .env.secrets
> ```

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
| `--install-cert` 가 `Permission denied` | 인증서 디렉터리가 root 소유다. 위 2번의 주석 참고 |
| 60~90일 뒤 갑자기 인증서 만료 | 갱신 cron 이 인증서 디렉터리에 못 쓰고 있다. `acme.sh --list` 와 소유자 확인 |
| 백엔드가 DB 인증 실패로 재시작 | `.env.secrets` 를 다시 만들었다. 아래 "DB 비밀번호를 잃어버렸을 때" |
| 재배포·재시작할 때마다 로그아웃됨 | `.env.secrets` 에 `APP_JWT_SECRET` 이 없다. 위 3번 참고 |
| API 가 전부 401 | 토큰이 만료됐거나 없다. 화면이 로그인으로 돌려보낸다 |
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
