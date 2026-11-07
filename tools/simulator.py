#!/usr/bin/env python3
"""
ProxiLink event simulator.

Generates synthetic BLE proximity and GPS events and pushes them through the same
REST contract the Android client uses. Use it to demonstrate the end-to-end
pipeline (device -> event -> database -> WebSocket -> dashboard) when no physical
BLE hardware is available.

SIMULATION ONLY: every event is tagged metadata.simulated=true and
metadata.generator="tools/simulator.py". Nothing here reads a radio.

Standard library only:
    python tools/simulator.py --email demo@proxilink.dev --password 'ProxiLink!Demo2026'

Options:
    --base-url       Backend base URL (default http://localhost:8080)
    --email          Account email (env PROXILINK_EMAIL)
    --password       Account password (env PROXILINK_PASSWORD)
    --hardware-id    Stable identifier of the simulated beacon
    --name           Device name shown on the dashboard
    --interval       Seconds between RSSI samples (default 3)
    --duration       Stop after N seconds; 0 = run until Ctrl+C
    --count          Stop after N samples; 0 = unlimited
    --lat/--lng      Base coordinates for location events
    --batch-size     Events per REST call (1-50, default 5)
"""

from __future__ import annotations

import argparse
import json
import math
import os
import random
import signal
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone

SIM_HEADERS = {"Content-Type": "application/json", "Accept": "application/json"}


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def iso(moment: datetime) -> str:
    return moment.astimezone(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


class ApiError(RuntimeError):
    def __init__(self, status: int, payload: str):
        super().__init__(f"HTTP {status}: {payload[:400]}")
        self.status = status
        self.payload = payload


class ProxiLinkClient:
    def __init__(self, base_url: str, email: str, password: str):
        self.base_url = base_url.rstrip("/")
        self.email = email
        self.password = password
        self.access_token: str | None = None
        self.user: dict | None = None

    def _request(self, method: str, path: str, body: dict | None = None, auth: bool = True) -> dict:
        data = json.dumps(body).encode() if body is not None else None
        headers = dict(SIM_HEADERS)
        if auth and self.access_token:
            headers["Authorization"] = f"Bearer {self.access_token}"
        request = urllib.request.Request(self.base_url + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                raw = response.read().decode()
                return json.loads(raw) if raw else {}
        except urllib.error.HTTPError as exc:
            raise ApiError(exc.code, exc.read().decode(errors="replace")) from exc
        except urllib.error.URLError as exc:
            raise ConnectionError(str(exc.reason)) from exc

    def login(self) -> None:
        payload = self._request(
            "POST",
            "/api/v1/auth/login",
            {"email": self.email, "password": self.password, "deviceLabel": "event-simulator"},
            auth=False,
        )
        self.access_token = payload["accessToken"]
        self.user = payload["user"]

    def identify(self, hardware_id: str) -> dict | None:
        result = self._request("POST", "/api/v1/devices/identify", {"hardwareId": hardware_id})
        return result.get("device") if result.get("recognized") else None

    def register_device(self, hardware_id: str, name: str) -> dict:
        return self._request(
            "POST",
            "/api/v1/devices",
            {
                "name": name,
                "deviceType": "ESP32 BLE Beacon (simulated)",
                "category": "SENSOR",
                "hardwareId": hardware_id,
                "bleMac": "02:00:00:00:00:01",
                "serviceUuid": "6e400001-b5a3-f393-e0a9-e50e24dcca9e",
                "metadata": {
                    "simulated": True,
                    "generator": "tools/simulator.py",
                    "note": "Synthetic device without physical hardware",
                },
            },
        )

    def post_proximity(self, events: list[dict]) -> dict:
        return self._request("POST", "/api/v1/events/proximity", {"events": events})

    def post_location(self, events: list[dict]) -> dict:
        return self._request("POST", "/api/v1/events/location", {"events": events})


def classify(rssi: float) -> str:
    if rssi >= -50:
        return "IMMEDIATE"
    if rssi >= -65:
        return "VERY_CLOSE"
    if rssi >= -80:
        return "NEARBY"
    if rssi >= -95:
        return "FAR"
    return "OUT_OF_RANGE"


class RssiWalk:
    """Mean-reverting random walk so the demo produces believable transitions."""

    def __init__(self, start: float = -62.0, target: float | None = None):
        self.value = start
        self.smoothed = start
        self.target = target if target is not None else start

    def next(self) -> tuple[float, float]:
        drift = (self.target - self.value) * 0.08
        self.value = max(-100.0, min(-35.0, self.value + drift + random.uniform(-3.5, 3.5)))
        self.smoothed = 0.7 * self.smoothed + 0.3 * self.value
        return round(self.value, 1), round(self.smoothed, 2)

    def move_target(self) -> None:
        self.target = random.uniform(-92.0, -42.0)


def build_proximity_event(device_id: str, ble_mac: str, rssi: float, smoothed: float, lat: float, lng: float) -> dict:
    return {
        "eventId": str(uuid.uuid4()),
        "deviceId": device_id,
        "bleMac": ble_mac,
        "rssi": int(round(rssi)),
        "smoothedRssi": smoothed,
        "latitude": round(lat, 6),
        "longitude": round(lng, 6),
        "accuracyM": round(random.uniform(4.0, 12.0), 1),
        "recordedAt": iso(utc_now()),
        "metadata": {
            "simulated": True,
            "generator": "tools/simulator.py",
            "pathLossModel": "log-distance, txPower=-59dBm, n=2.0",
        },
    }


def build_location_event(lat: float, lng: float) -> dict:
    return {
        "eventId": str(uuid.uuid4()),
        "latitude": round(lat, 6),
        "longitude": round(lng, 6),
        "accuracyM": round(random.uniform(3.0, 9.0), 1),
        "speedMps": round(random.uniform(0.0, 1.6), 2),
        "altitudeM": round(random.uniform(105.0, 118.0), 1),
        "recordedAt": iso(utc_now()),
        "metadata": {"simulated": True, "generator": "tools/simulator.py"},
    }


def flush(client: ProxiLinkClient, kind: str, queue: list[dict], stop: bool = False) -> list[dict]:
    if not queue:
        return queue
    try:
        response = client.post_proximity(queue) if kind == "proximity" else client.post_location(queue)
        rejected = response.get("rejected") or []
        print(
            f"  [{kind}] accepted={response.get('accepted', 0)} "
            f"duplicates={response.get('duplicates', 0)} rejected={len(rejected)}"
        )
        for item in rejected:
            print(f"    rejected {item.get('eventId')}: {item.get('reason')}")
        return []
    except ApiError as exc:
        if exc.status in (401, 403):
            print(f"  [{kind}] auth rejected ({exc.status}); refreshing token")
            client.login()
            return queue
        print(f"  [{kind}] server rejected batch: {exc}; keeping {len(queue)} event(s) for retry")
        return queue
    except ConnectionError as exc:
        print(f"  [{kind}] backend unreachable ({exc}); buffering {len(queue)} event(s)")
        if stop:
            raise
        return queue


def main() -> int:
    parser = argparse.ArgumentParser(description="ProxiLink synthetic BLE/GPS event simulator")
    parser.add_argument("--base-url", default=os.environ.get("PROXILINK_URL", "http://localhost:8080"))
    parser.add_argument("--email", default=os.environ.get("PROXILINK_EMAIL", "demo@proxilink.dev"))
    parser.add_argument("--password", default=os.environ.get("PROXILINK_PASSWORD", "ProxiLink!Demo2026"))
    parser.add_argument("--hardware-id", default="esp32-sim-001")
    parser.add_argument("--name", default="Simulated ESP32 Beacon")
    parser.add_argument("--interval", type=float, default=3.0)
    parser.add_argument("--duration", type=int, default=0, help="seconds; 0 = until Ctrl+C")
    parser.add_argument("--count", type=int, default=0, help="samples; 0 = unlimited")
    parser.add_argument("--lat", type=float, default=52.2297)
    parser.add_argument("--lng", type=float, default=21.0122)
    parser.add_argument("--batch-size", type=int, default=5)
    args = parser.parse_args()
    args.batch_size = max(1, min(50, args.batch_size))

    print("=" * 78)
    print(" ProxiLink EVENT SIMULATOR - SIMULATION MODE")
    print(" No Bluetooth adapter or GPS is used. Events are synthetic and tagged")
    print(' metadata.simulated=true. Do not present these as real hardware readings.')
    print("=" * 78)

    client = ProxiLinkClient(args.base_url, args.email, args.password)
    try:
        client.login()
    except (ApiError, ConnectionError) as exc:
        print(f"Cannot sign in to {args.base_url}: {exc}")
        print("Is the backend running? Demo login: demo@proxilink.dev / ProxiLink!Demo2026")
        return 2

    print(f"Signed in as {client.user['email']} ({client.user['organization']})")

    try:
        device = client.identify(args.hardware_id)
        if device is None:
            device = client.register_device(args.hardware_id, args.name)
            print(f"Registered simulated device {device['name']} ({device['id']})")
        else:
            print(f"Using existing device {device['name']} ({device['id']})")
    except (ApiError, ConnectionError) as exc:
        print(f"Device setup failed: {exc}")
        return 3

    device_id = device["id"]
    ble_mac = device.get("bleMac") or "02:00:00:00:00:01"
    walk = RssiWalk()
    proximity_queue: list[dict] = []
    location_queue: list[dict] = []
    started = time.time()
    samples = 0
    failures = 0
    running = True

    def stop_handler(_signum, _frame):
        nonlocal running
        running = False

    signal.signal(signal.SIGINT, stop_handler)
    signal.signal(signal.SIGTERM, stop_handler)

    while running:
        if 0 < args.duration <= time.time() - started:
            break
        if 0 < args.count <= samples:
            break
        samples += 1

        if samples % 25 == 0:
            walk.move_target()

        rssi, smoothed = walk.next()
        jitter = math.sin(samples / 9.0) * 0.00035
        lat = args.lat + jitter + random.uniform(-0.00008, 0.00008)
        lng = args.lng + jitter + random.uniform(-0.00008, 0.00008)

        proximity_queue.append(build_proximity_event(device_id, ble_mac, rssi, smoothed, lat, lng))
        if samples % 5 == 0:
            location_queue.append(build_location_event(lat, lng))

        print(f"[{samples:04d}] rssi={rssi:6.1f} smoothed={smoothed:6.2f} -> {classify(smoothed):12s} "
              f"queued={len(proximity_queue)}/{len(location_queue)}")

        if len(proximity_queue) >= args.batch_size:
            proximity_queue = flush(client, "proximity", proximity_queue)
        if len(location_queue) >= args.batch_size:
            location_queue = flush(client, "location", location_queue)

        try:
            time.sleep(max(0.2, args.interval))
        except KeyboardInterrupt:
            break

    print("Flushing buffered events...")
    for kind, queue in (("proximity", proximity_queue), ("location", location_queue)):
        attempts = 0
        while queue and attempts < 5:
            attempts += 1
            before = len(queue)
            queue = flush(client, kind, queue, stop=True)
            if len(queue) == before and queue:
                time.sleep(min(8, 2 ** attempts))
        if queue:
            failures += len(queue)
            print(f"  [{kind}] {len(queue)} event(s) could not be delivered")

    print(f"Done. samples={samples} undelivered={failures}")
    return 0 if failures == 0 else 4


if __name__ == "__main__":
    sys.exit(main())
