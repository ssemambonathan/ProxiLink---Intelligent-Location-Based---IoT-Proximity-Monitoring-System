# ProxiLink — Deployment guide

This guide covers local development, the containerized stack, TLS, Android release
builds and operational concerns. Commands assume the repository root.

## 1. Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 21 | Backend toolchain; Gradle fails on newer JDKs not supported by the chosen plugin versions |
| Android Studio | Recent (AGP 8.13) | SDK 36, build tools |
| Node.js | 22+ | Dashboard |
| Docker + Compose | Compose v2 | Optional; Docker was unavailable in the development environment |
| PostgreSQL | 16 | Production database |
| Python | 3.10+ | Only for the event simulator (standard library) |

## 2. Local development (H2, no Docker)

### Backend

```bash
cd backend
export JAVA_HOME=/path/to/jdk-21
./gradlew bootRun
# or build a jar
./gradlew bootJar
java -jar build/libs/proxilink-backend-1.0.0.jar
```

The default `dev` profile:

- listens on `http://localhost:8080`;
- stores data in `backend/data/proxilink.mv.db` (H2, PostgreSQL compatibility mode);
- runs Flyway migrations on startup;
- seeds `admin@proxilink.dev` / `ProxiLink!Demo2026` (ADMIN) and
  `demo@proxilink.dev` / `ProxiLink!Demo2026` (USER) when the user table is empty;
- enables the H2 console at `/h2-console` and Swagger UI at `/swagger-ui.html`;
- generates an ephemeral JWT key if `JWT_SECRET` is unset (tokens die on restart).

Reset the database by stopping the backend and deleting `backend/data/`.

### Dashboard

```bash
cd admin-dashboard
npm install
npm run dev        # http://localhost:5173, proxies /api and /ws to :8080
npm run build      # static bundle in dist/
```

### Android

```bash
cd android-app
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

The debug build defaults to `http://10.0.2.2:8080/api/v1/` (emulator loopback).
For a physical phone on the same Wi-Fi, change `DEFAULT_API_BASE_URL` in
`app/build.gradle.kts` to `http://<your-lan-ip>:8080/api/v1/` and add that host to
the debug `network_security_config.xml` if it is not already covered. Release
builds never allow cleartext, so point them at an HTTPS endpoint.

### Simulator

```bash
python tools/simulator.py --email demo@proxilink.dev --password 'ProxiLink!Demo2026' \
  --interval 3 --count 30
```

Every event is tagged `metadata.simulated=true`; see the banner printed at start-up.

## 3. Containerized deployment

`docker-compose.yml` starts PostgreSQL, Redis, the backend and the dashboard
(nginx). The dashboard is served on port 8081 and proxies `/api` and `/ws` to the
backend, so the browser never needs CORS.

```bash
cp .env.example .env
# edit .env: strong POSTGRES_PASSWORD, JWT_SECRET (openssl rand -base64 48),
# admin email/password and your real origins
docker compose build
docker compose up -d
docker compose ps
```

URLs:

| Service | URL |
| --- | --- |
| Dashboard | http://localhost:8081 |
| Backend API | http://localhost:8080/api/v1 |
| Health | http://localhost:8080/actuator/health |
| OpenAPI | http://localhost:8080/v3/api-docs |

The backend container runs the `prod` profile:

- `ddl-auto=validate`; Flyway is the only schema writer;
- H2 console and demo seeding are off;
- an ephemeral JWT key is impossible because `ProductionSafetyCheck` refuses to
  start without `JWT_SECRET` (≥ 32 bytes) and explicit CORS/WS origins;
- Redis is enabled for the health/status surface; settings caching remains
  Caffeine (`CACHE_TYPE=caffeine`);
- the first administrator is created only when the database is empty, using
  `BOOTSTRAP_ADMIN_*`. After the first login, change the password and set
  `BOOTSTRAP_ADMIN_ENABLED=false`.

> **Verification caveat:** Docker was not running in the development environment,
> so the compose stack was reviewed but not executed. The backend and dashboard
> were verified natively (Gradle tests, Vite build, local HTTP/WS runs).

## 4. Environment variables

### Backend

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | HTTP port |
| `SPRING_PROFILES_ACTIVE` | `dev` | Use `prod` in production |
| `DATABASE_URL` | H2 file URL | JDBC URL |
| `DATABASE_USER` / `DATABASE_PASSWORD` | `sa` / empty | Credentials |
| `DATABASE_DRIVER` | H2 driver | `org.postgresql.Driver` in prod |
| `JPA_DDL_AUTO` | `none` | `validate` in prod |
| `JWT_SECRET` | empty (ephemeral in dev) | HS256 key, ≥ 32 bytes, required in prod |
| `JWT_ACCESS_TTL_SECONDS` | `1800` | Access token lifetime |
| `JWT_REFRESH_TTL_DAYS` | `14` | Refresh token lifetime |
| `CORS_ALLOWED_ORIGINS` | localhost origins | Comma-separated browser origins |
| `WS_ALLOWED_ORIGINS` | `*` | Comma-separated WebSocket origins; wildcard rejected in prod |
| `RATE_LIMIT_GENERAL` / `RATE_LIMIT_LOGIN` | `240` / `10` | Requests per minute per IP |
| `DEMO_SEED_ENABLED` | `true` in dev, `false` in prod | Demo accounts; opt in explicitly for a staging demo |
| `BOOTSTRAP_ADMIN_ENABLED` | `false` | Create first admin if the DB is empty (prod) |
| `BOOTSTRAP_ADMIN_EMAIL` | — | Required when bootstrap is enabled |
| `BOOTSTRAP_ADMIN_PASSWORD` | — | ≥ 12 chars, letter + digit, not `CHANGE_ME*` |
| `BOOTSTRAP_ORGANIZATION` | `ProxiLink` | Organization of the first admin |
| `REDIS_ENABLED` | `false` | Enables Redis status/health integration |
| `CACHE_TYPE` | `caffeine` | Spring cache provider |
| `RETENTION_CLEANUP_ENABLED` | `true` | Daily event/session purge |

### Dashboard (build-time)

| Variable | Default | Purpose |
| --- | --- | --- |
| `VITE_API_URL` | origin-relative `/api/v1` | Override for split deployments |
| `VITE_WS_URL` | derived from page origin | Override the WebSocket URL |

## 5. TLS and reverse proxy

Terminate TLS in front of the compose stack (cloud load balancer, Caddy, Traefik
or another nginx). Requirements:

- HTTPS for all REST traffic; WSS for `/ws`.
- Forward `X-Forwarded-For` only from trusted hops (the rate limiter trusts the
  first entry).
- Do not log query strings for `/ws`; the access token is part of the handshake.
  The bundled nginx config disables access logging for that location.
- Set `CORS_ALLOWED_ORIGINS` and `WS_ALLOWED_ORIGINS` to the public HTTPS origin.
- Add HSTS at the edge (`Strict-Transport-Security: max-age=31536000`).
- For Android release builds, use the public HTTPS base URL; the release network
  security config rejects cleartext.

## 6. Android release build

```bash
cd android-app
./gradlew lint assembleRelease
```

A signing config is intentionally not committed. Create a keystore outside the
repository and configure it locally (or via CI secrets):

```bash
keytool -genkeypair -v -keystore ~/proxilink-release.jks -alias proxilink \
  -keyalg RSA -keysize 4096 -validity 10000
```

Add `signingConfigs.release` in `app/build.gradle.kts` reading from environment
variables, set `DEFAULT_API_BASE_URL` to the HTTPS endpoint, and keep the keystore
and passwords out of git (`.gitignore` already excludes `*.jks`, `*.keystore` and
`.env`).

## 7. Operations

### Health and monitoring

- `GET /actuator/health` → `{"status":"UP"}` (Redis health is disabled when
  `REDIS_HEALTH_ENABLED=false`).
- `GET /actuator/metrics` for JVM/HTTP metrics.
- `GET /api/v1/system/status` (authenticated) for DB status, WebSocket session
  count, uptime and version.
- Container healthchecks are defined in `docker-compose.yml`.

### Backups

- `pg_dump -Fc proxilink > proxilink-$(date +%F).dump` nightly.
- Test restores periodically; Flyway history must be included.
- The H2 dev database is disposable; do not treat it as production storage.

### Retention

The retention job deletes events older than `data.retentionDays` (default 90) and
sessions expired for more than 30 days, writing an audit entry. Adjust the setting
from the dashboard admin page or the `system_settings` table.

### Scaling limitations

- Rate limiting and the WebSocket session registry are in-process. Running more
  than one backend replica requires a Redis-backed rate limiter and a pub/sub
  bridge for WebSocket fan-out.
- Device staleness monitoring is scheduled per instance; with replicas, make the
  job idempotent or move it to a single scheduler.
- Caffeine caches are per instance; settings changes propagate on cache eviction
  in the instance that handled the update. Use Redis cache mode if strict
  cross-instance consistency is required.

## 8. Production checklist

- [ ] `JWT_SECRET` generated with a CSPRNG and stored in a secret manager.
- [ ] Database password unique and not the example value.
- [ ] `CORS_ALLOWED_ORIGINS` and `WS_ALLOWED_ORIGINS` restricted to real origins.
- [ ] HTTPS/WSS enforced at the edge; HSTS enabled.
- [ ] Bootstrap admin created, password rotated, `BOOTSTRAP_ADMIN_ENABLED=false`.
- [ ] Demo seed disabled (default in prod).
- [ ] Backups scheduled and restore tested.
- [ ] Retention period approved by the data owner.
- [ ] Log aggregation configured; `/ws` query strings excluded.
- [ ] Android release signed with a protected keystore and pointed at HTTPS.
- [ ] Rate limits reviewed for expected traffic.
- [ ] `docker compose config` validated and images pinned/scanned in CI.
