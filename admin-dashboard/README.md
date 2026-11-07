# ProxiLink Console

Web administration dashboard for ProxiLink — a BLE proximity and location tracking platform.
Built against `documentation/api-contract.md` and `documentation/architecture.md`; the backend
remains the source of truth for authorization, classification and data.

## Stack

| Layer | Choice |
| --- | --- |
| Build | Vite 7, TypeScript 5.9 (strict) |
| UI | React 19, React Router 7, Tailwind CSS 3.4 |
| Charts | Recharts 2.15 |
| Maps | Leaflet 1.9.4 + react-leaflet 5, OpenStreetMap tiles (no API key) |
| Icons / dates / classes | lucide-react, date-fns, clsx |

Route-level code splitting keeps the initial shell small; Leaflet and Recharts load only when a
map or analytics route is opened.

## Getting started

Prerequisites: Node.js 20+ (verified on Node 24) and npm.

```bash
cd admin-dashboard
npm install
npm run dev          # http://localhost:5173
```

The dev server proxies `/api` and `/ws` to `http://localhost:8080`, so the app is origin-relative.
Start the Spring Boot backend (and optionally `tools/simulator.py`) in another terminal for live data.
The console starts and renders normally even when the backend is not running: it shows an
"API unreachable" banner and per-view error states rather than failing or inventing data.

```bash
npm run build        # tsc -b && vite build
npm run preview      # serve dist/
npm run typecheck
```

### Demo credentials

| Role | Email | Password |
| --- | --- | --- |
| ADMIN | `admin@proxilink.dev` | `ProxiLink!Demo2026` |
| USER | `demo@proxilink.dev` | `ProxiLink!Demo2026` |

USER accounts can use Overview, Live monitoring, Map, Devices, Analytics and Profile, but not the
Administration routes (Users, Audit log, Settings). Hiding those routes is UX only; the API enforces
roles and organization scoping.

## Environment variables

Both are optional for local development.

| Variable | Default | Purpose |
| --- | --- | --- |
| `VITE_API_URL` | `/api/v1` | API base. Accepts a full base (`https://host/api/v1`) or an origin (`https://api.example.com`, `/api/v1` is appended). |
| `VITE_WS_URL` | derived | WebSocket URL. When unset the client uses `ws(s)://<window.location.host>/ws?token=…`. Set it when the API lives on another host. |

See `.env.example`. Vite only exposes `VITE_*` values to the browser — never put secrets there.

## Pages

- **Overview** — stat cards, 7-day event trend, category donut, recent proximity/location events,
  map preview, system status strip.
- **Live monitoring** — device table with latest RSSI/location, realtime proximity and location
  feeds, WebSocket state with reconnect/backoff, pause/resume.
- **Interactive map** — status-coloured, category-icon markers, RSSI/last-seen popups, filters,
  legend, fit-to-data, optional location trail, click-through to a device drawer.
- **Devices** — searchable/filterable/paginated registry, register, edit, activate/deactivate, soft
  delete, details drawer with recent events.
- **Analytics** — 7/14/30-day ranges, event trends, proximity distribution, online ratio, most
  active devices, category breakdown.
- **Profile** — identity, session expiry, API/WebSocket endpoints, sign out, notification access.
- **Administration (ADMIN)** — Users (enable/disable, roles), Audit log (action/category/actor/date
  filters, expandable context), Settings (proximity thresholds, intervals, offline timeout,
  retention).

## Auth and security notes

- Access and refresh tokens, plus the last known user profile, are stored in `localStorage`
  (`proxilink.session.v1`). This is a deliberate trade-off: it survives reloads without a backend
  round trip, but **any XSS on this origin can read the tokens**. The console renders no third-party
  HTML, uses React escaping, and never logs tokens or the token-bearing WebSocket URL. For a
  high-assurance deployment, move refresh tokens to `HttpOnly; Secure; SameSite` cookies and keep
  only the short-lived access token in memory.
- A 401 triggers one single-flight `POST /auth/refresh` (refresh tokens rotate), then the original
  request is retried. A failed refresh clears the session and redirects to `/login`.
- Logout calls `POST /auth/logout` with the refresh token and clears local state even if the call
  fails.
- Roles are decoded from the access token for navigation gating only.
- The backend is expected behind HTTPS/WSS in production; the WebSocket handshake token is part of
  the URL and can appear in proxy logs, so terminate TLS responsibly and avoid verbose URL logging.

## API assumptions

The client follows the documented v1 contract. Where the contract leaves room, these choices were made:

- **Base path** — `/api/v1`; every request is origin-relative by default so the Vite proxy (dev) or
  reverse proxy (prod) can route it.
- **WebSocket** — `ws(s)://<host>/ws?token=<accessToken>`; JSON frames `hello`, `proximity.event`,
  `location.event`, `device.status`, `notification`, `pong`. The client sends `{"type":"ping"}` every
  25 s, reconnects with exponential backoff (1–30 s, jitter), treats 70 s of silence as stale, and
  refreshes the access token before reconnecting when needed.
- **Settings** — `GET/PATCH /settings` with
  `proximityThresholds {immediate, veryClose, nearby, far}`, `locationIntervalSeconds`,
  `offlineTimeoutSeconds`, `dataRetentionDays`. `scanIntervalSeconds` is read from
  `GET /config/public`; it is only included in a PATCH when an admin changes it, because the
  contract's settings example does not persist it. `PATCH` may return the updated object or `204`.
  Defaults in the UI are `-50/-65/-80/-95 dBm`, 30 s location, 15 s scan, 300 s offline, 90 days.
- **List endpoints** — `GET /devices`, `/events/proximity`, `/events/location`, `/users`,
  `/audit-logs`, `/notifications` return
  `{items, page, size, totalElements, totalPages}`. Audit filters used: `action`, `category`,
  `actorId`, `from`, `to` (ISO-8601 instants), `page`, `size`. Notifications use
  `unreadOnly`, `page`, `size`.
- **Errors** — `{code, message, violations}` (violations mapped to form fields). Network failure is a
  distinct state, never a fabricated success.
- **Empty/partial payloads** — optional device fields (`lastRssi`, `lastLocation`, `metadata`) are
  treated as unknown and rendered as `—`.

## Data honesty

There are no fixtures or mock data in production code paths. When the API is unavailable, views show
skeletons followed by explicit error/empty states and the global connectivity banner. Map tiles are
the only third-party runtime asset (OSM, attribution retained) and are styled with CSS for the dark
theme; no mapping API key is used.

## Layout

```
src/
├── components/     UI primitives, layout, map, charts, drawers
├── context/        toast, connectivity, auth, realtime, notifications
├── hooks/          useApi (polling/cancellation), debounce, recent-id highlights
├── lib/            typed API client, session/JWT, WebSocket manager, formatters, metadata
└── pages/          one file per route; admin pages under pages/admin
```

Polling and WebSocket updates never replace existing rows with skeletons; only first loads do.
