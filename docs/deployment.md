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
도메인을 보고 이 앱으로 넘긴다. 이 앱 컨테이너는 **공개 포트를 열지 않는다.**
예외는 postgres 하나이고, 그것도 서버 루프백 전용이다 (아래 "운영 DB 조회").

```
인터넷 :443
   │
edge-proxy                     TLS 종단 + 도메인 라우팅
   │  weekly-report-mik.duckdns.org
weekly-report-web              정적 파일 + /api 프록시
   │
weekly-report-backend          Spring Boot
   │
weekly-report-postgres         127.0.0.1:15434 (루프백 전용 · 조회용)
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

### 3. 서버 · JWT 서명 키 배치

DB 비밀번호는 여기 없다. GitHub Secret `POSTGRES_PASSWORD` 가 원본이고 CI 가
배포마다 `.env.deploy` 에 실어 내린다(다음 단계). 이 파일에는 JWT 키만 둔다.

```bash
mkdir -p ~/weekly-report && cd ~/weekly-report
```

```bash
umask 077 && printf 'POSTGRES_DB=weekly_report\nPOSTGRES_USER=weekly\nAPP_JWT_SECRET=%s\n' "$(openssl rand -base64 32)" > .env.secrets
```

```bash
chmod 600 .env.secrets && cat .env.secrets
```

> **이 파일은 배포마다 새로 만들면 안 된다.** `APP_JWT_SECRET` 이 바뀌면 발급해둔
> 로그인 토큰이 전부 무효가 되어 모두 튕긴다. 그래서 CI 가 만들지 않고 서버에
> 한 번만 둔다. 템플릿은 `deploy/env.secrets.example`.

> **이미 `.env.secrets` 가 있는 서버라면** `APP_JWT_SECRET` 이 있는지만 본다.
> 없으면 앱은 뜨지만 기동할 때마다 임시 키가 만들어져, 재배포·재시작 때마다
> 로그인이 전부 풀린다(로그에 경고가 크게 찍힌다).
>
> ```bash
> cd ~/weekly-report && printf 'APP_JWT_SECRET=%s\n' "$(openssl rand -base64 32)" >> .env.secrets
> ```
>
> 예전 구성에서 넘어온 서버라면 `POSTGRES_PASSWORD` 줄이 남아 있다.
> **그 값을 먼저 GitHub Secret 에 넣고** 줄을 지운다. 순서를 바꾸면 다음 배포에서
> 백엔드가 인증 실패로 재시작한다. `.env.deploy` 가 뒤에 와서 이기므로 남겨 두어도
> 동작은 하지만, 값만 어긋난 채 남아 다음 사람을 헷갈리게 한다.
>
> ```bash
> cd ~/weekly-report && grep POSTGRES_PASSWORD .env.secrets
> ```

### 4. GitHub Secrets 등록

이 저장소 → Settings → Secrets and variables → Actions → **Secrets** 탭.

| 이름 | 설명 |
|---|---|
| `DOCKERHUB_USERNAME` | Docker Hub 계정 |
| `DOCKERHUB_TOKEN` | Docker Hub Access Token (비밀번호 아님) |
| `SERVER_HOST` | 서버 주소 |
| `SERVER_USER` | SSH 사용자 |
| `SERVER_SSH_KEY` | SSH 개인키 전문 |
| `SERVER_PORT` | SSH 포트 (22 면 생략 가능) |
| `POSTGRES_PASSWORD` | 운영 DB 비밀번호 |

> **`POSTGRES_PASSWORD` 는 한 번 넣으면 Secret 만 고쳐서 바꿀 수 없다.**
> postgres 는 볼륨을 처음 만들 때의 비밀번호로 계정을 굳힌다. Secret 을 바꾸면
> 컨테이너 환경변수만 바뀌고 DB 안의 계정은 그대로라, 백엔드가 인증 실패로
> 재시작을 반복한다. 바꿀 때는 아래 "DB 비밀번호 바꾸기"를 따른다.
>
> **GitHub Secrets 는 등록한 값을 다시 볼 수 없다.** 백업이 되지 않는다는 뜻이다.
> 비밀번호 관리자에 사본을 따로 둔다. 서버에서 읽는 방법은 아래 "운영 DB 조회".

edge · SecretManager 저장소에 등록한 것과 같은 값이다.
**Secret 은 저장소마다 따로다.** 다른 저장소에 넣은 값은 넘어오지 않는다.

> 서버 주소와 SSH 포트의 실제 값은 **이 문서에 적지 않는다.** Secrets 로 관리하는
> 값을 문서에 박아두면 그 방침이 무의미해진다. 모르면 서버를 함께 쓰는 사람에게 묻는다.

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

## 운영 DB 조회

postgres 는 **서버 루프백에만** 게시되어 있다 — `127.0.0.1:15434`.
서버에 들어온 사람만 닿고, 밖에서는 SSH 터널을 거쳐야 한다.
컨테이너를 다시 만들어도, 네트워크를 다시 만들어도 이 주소는 그대로다.

### DataGrip

1. **New → Data Source → PostgreSQL**
2. **SSH/SSL** 탭 → `Use SSH tunnel` 체크 → 서버 주소 · SSH 포트 · 계정 · 개인키 등록
3. **General** 탭
   - Host `127.0.0.1`
   - Port `15434`
   - Database `weekly_report`
   - User `weekly`
   - Password — 아래 "비밀번호를 어디서 읽나"
4. **Options → Read-only 를 켠다**

세 가지를 빠뜨리면 안 된다.

- **Host 는 SSH 서버 기준으로 해석된다.** 내 PC 의 루프백이 아니라 서버의 루프백이다.
  터널이 뚫린 뒤 그 안에서 `127.0.0.1:15434` 로 붙는다.
- **개발 DB 와 데이터 소스 이름을 구분한다.** 개발용 compose 의 DB 도 `127.0.0.1` 로
  보이니 `weeklyReport (prod)` 처럼 적어둔다. 섞이면 운영 DB 에 개발용 쿼리를 던진다.
- **Read-only 를 켠다.** DataGrip 은 스키마 인트로스펙션을 자동으로 돌리고,
  실수로 실행한 UPDATE 를 되돌려주지 않는다.

### 비밀번호를 어디서 읽나

원본은 GitHub Secret `POSTGRES_PASSWORD` 인데 **Secrets 는 다시 볼 수 없다.**
CI 가 배포마다 서버에 내려놓은 사본에서 읽는다.

```bash
grep POSTGRES_PASSWORD ~/weekly-report/.env.deploy
```

이 파일은 배포마다 덮어써지지만 값은 Secret 그대로라 바뀌지 않는다.

### 터널 없이 — 서버에서 직접

```bash
docker exec -it weekly-report-postgres psql -U weekly -d weekly_report
```

이 경로는 호스트 포트를 거치지 않는다. 포트가 막혀 있어도 되고,
**비밀번호도 필요 없다** — 유닉스 소켓 접속은 `trust` 다.

---

## DB 비밀번호 바꾸기

**Secret 만 고치면 서비스가 죽는다.** postgres 는 볼륨을 처음 만들 때의 비밀번호로
계정을 굳힌다. Secret 을 바꾸면 백엔드에 새 값이 들어가는데 DB 안의 계정은 옛
비밀번호 그대로라, 백엔드가 인증 실패로 재시작을 반복한다.
postgres 자신은 멀쩡히 뜨고 헬스체크도 통과한다(`pg_isready` 는 인증을 보지 않는다).

두 곳을 같은 값으로 맞춰야 한다. 데이터는 건드리지 않으므로 볼륨을 지울 일은 없다.

새 비밀번호에는 `$`, `#`, 따옴표, 공백을 쓰지 않는다. compose 가 env 파일을 읽을 때
`$` 는 변수로, `#` 는 주석으로 먹는다. 영문·숫자·`-`·`_` 면 안전하다.

**1. DB 안에서 바꾼다** (옛 비밀번호가 없어도 된다 — 소켓 접속은 `trust`)

```bash
docker exec -it weekly-report-postgres psql -U weekly -d weekly_report -c "ALTER USER weekly WITH PASSWORD '새비밀번호';"
```

**2. GitHub Secret `POSTGRES_PASSWORD` 를 같은 값으로 고친다**

**3. 재배포한다.** Actions 탭에서 `CI/CD` 수동 실행. 손으로 할 거라면:

```bash
cd ~/weekly-report && sed -i "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=새비밀번호|" .env.deploy
```

```bash
cd ~/weekly-report && docker compose -f docker-compose.prod.yml --env-file .env.secrets --env-file .env.deploy up -d
```

**4. 확인**

```bash
cd ~/weekly-report && docker compose -f docker-compose.prod.yml --env-file .env.secrets --env-file .env.deploy logs --tail 30 backend
```

`Started WeeklyReportApplication` 이 보이면 끝이다. `password authentication failed`
가 보이면 두 곳의 값이 다르다.

> 3번을 빠뜨리면 다음 배포까지 백엔드가 옛 비밀번호로 돌다가, 그때 가서 죽는다.
> 바꾼 자리에서 끝내는 편이 낫다.

---

## 문제가 생겼을 때

| 증상 | 확인 |
|---|---|
| 배포가 `.env.secrets 가 없습니다` 로 멈춤 | 위 3번을 안 했다 |
| 헬스체크 실패 · 502 | edge 에 `weekly-report.conf` 가 반영됐는지, 인증서가 있는지 |
| edge 배포가 `nginx -t` 에서 실패 | `/etc/edge/certs/weekly-report/` 에 인증서가 없다 (위 2번) |
| `--install-cert` 가 `Permission denied` | 인증서 디렉터리가 root 소유다. 위 2번의 주석 참고 |
| 60~90일 뒤 갑자기 인증서 만료 | 갱신 cron 이 인증서 디렉터리에 못 쓰고 있다. `acme.sh --list` 와 소유자 확인 |
| 백엔드가 DB 인증 실패로 재시작 | Secret 만 바꾸고 `ALTER USER` 를 안 했다. 위 "DB 비밀번호 바꾸기" |
| 재배포·재시작할 때마다 로그아웃됨 | `.env.secrets` 에 `APP_JWT_SECRET` 이 없다. 위 3번 참고 |
| API 가 전부 401 | 토큰이 만료됐거나 없다. 화면이 로그인으로 돌려보낸다 |
| 엉뚱한 사이트가 뜬다 | 해당 도메인의 server 블록이 없어 edge 기본 서버로 갔다 |
| DataGrip 이 `connection refused` | 터널은 붙었는데 포트가 없다. 아래 확인 |
| DataGrip 이 인증 실패 | 서버 `.env.deploy` 의 `POSTGRES_PASSWORD` 와 다르다 |

`connection refused` 면 서버에서 포트가 실제로 떠 있는지 본다.
`127.0.0.1:15434` 가 보여야 한다. `0.0.0.0` 이면 잘못된 것이니 즉시 고친다.

```bash
ss -ltnp | grep 15434
```

오래된 배포가 서버에 남아 있으면 포트가 없다. 이 게시는 compose 파일에 있고
CI 가 배포마다 덮어쓰므로, 한 번은 재배포해야 반영된다.

```bash
cd ~/weekly-report && docker compose -f docker-compose.prod.yml --env-file .env.secrets --env-file .env.deploy logs -f backend
```

```bash
docker network inspect edge-net | grep -A2 weekly-report
```

`weekly-report-web` 이 `edge-net` 에 없으면 edge 가 찾아가지 못해 502 가 난다.

### DB 비밀번호를 잃어버렸을 때

**볼륨을 지울 일이 아니다.** postgres 컨테이너가 떠 있기만 하면 되찾을 수 있다.
유닉스 소켓 접속은 `trust` 라, 옛 비밀번호 없이 `docker exec` 로 들어가 새로 정하면 된다.
그대로 위 "DB 비밀번호 바꾸기"를 따른다.

서버에 내려와 있는 사본이 남아 있을 수도 있으니 먼저 본다.

```bash
grep POSTGRES_PASSWORD ~/weekly-report/.env.deploy
```

무엇을 하든 그 전에 덤프를 떠 둔다. 주간보고 데이터는 되살릴 수 없다.

```bash
docker exec weekly-report-postgres pg_dumpall -U weekly > ~/weekly-report-backup.sql
```

볼륨을 지우는 것은 비밀번호 문제로는 할 일이 아니다. 데이터 파일 자체가 깨져
컨테이너가 뜨지 못할 때, 덤프를 확보한 뒤의 마지막 수단이다.

```bash
docker volume rm weekly-report-prod_postgres-data
```
