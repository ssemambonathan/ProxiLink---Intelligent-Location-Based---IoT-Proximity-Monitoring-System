/**
 * Domain types mirroring documentation/api-contract.md (v1).
 * Optional fields are modelled as nullable where the backend may omit them.
 */

export type Role = 'USER' | 'ADMIN';

export interface User {
  id: string;
  email: string;
  displayName: string;
  organization: string;
  roles: Role[] | string[];
  enabled: boolean;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: User;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  tokenType?: string;
  expiresInSeconds: number;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type DeviceStatus =
  | 'ONLINE'
  | 'OFFLINE'
  | 'NEARBY'
  | 'OUT_OF_RANGE'
  | 'UNREGISTERED'
  | 'INACTIVE';

export type DeviceCategory =
  | 'ASSET'
  | 'SENSOR'
  | 'WEARABLE'
  | 'INFRASTRUCTURE'
  | 'GATEWAY'
  | 'OTHER';

export type ProximityLevel =
  | 'IMMEDIATE'
  | 'VERY_CLOSE'
  | 'NEARBY'
  | 'FAR'
  | 'OUT_OF_RANGE';

export interface DeviceLocation {
  latitude: number;
  longitude: number;
  accuracyM?: number | null;
  recordedAt?: string | null;
}

export interface Device {
  id: string;
  name: string;
  deviceType?: string | null;
  category: DeviceCategory | string;
  hardwareId?: string | null;
  bleMac?: string | null;
  serviceUuid?: string | null;
  ownerId?: string | null;
  ownerName?: string | null;
  organization?: string | null;
  status: DeviceStatus | string;
  active: boolean;
  lastSeenAt?: string | null;
  lastRssi?: number | null;
  lastProximity?: ProximityLevel | string | null;
  lastLocation?: DeviceLocation | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  metadata?: Record<string, unknown> | null;
}

export interface ProximityEvent {
  id: string;
  eventId?: string | null;
  deviceId?: string | null;
  deviceName?: string | null;
  userId?: string | null;
  userDisplayName?: string | null;
  bleMac?: string | null;
  rssi?: number | null;
  smoothedRssi?: number | null;
  proximityLevel?: ProximityLevel | string | null;
  estimatedDistanceM?: number | null;
  latitude?: number | null;
  longitude?: number | null;
  accuracyM?: number | null;
  recordedAt: string;
  receivedAt?: string | null;
  metadata?: Record<string, unknown> | null;
}

export interface LocationEvent {
  id: string;
  eventId?: string | null;
  deviceId?: string | null;
  userId?: string | null;
  userDisplayName?: string | null;
  latitude: number;
  longitude: number;
  accuracyM?: number | null;
  speedMps?: number | null;
  altitudeM?: number | null;
  source?: string | null;
  recordedAt: string;
  receivedAt?: string | null;
  metadata?: Record<string, unknown> | null;
}

export interface AppNotification {
  id: string;
  type: string;
  title: string;
  body?: string | null;
  read: boolean;
  createdAt: string;
}

export interface CategoryCount {
  category: DeviceCategory | string;
  count: number;
}

export interface Overview {
  totalDevices: number;
  onlineDevices: number;
  offlineDevices: number;
  activeProximityEvents: number;
  recentLocationEvents24h: number;
  unreadNotifications: number;
  devicesByCategory: CategoryCount[];
  discoveredToday: number;
}

export interface LiveSnapshot {
  devices: Device[];
  recentProximity: ProximityEvent[];
  recentLocations: LocationEvent[];
  serverTime: string;
}

export interface TrendPoint {
  date: string;
  proximityEvents: number;
  locationEvents: number;
  uniqueDevices: number;
}

export interface TrendsResponse {
  points: TrendPoint[];
}

export interface MostActiveDevice {
  deviceId: string;
  name: string;
  eventCount: number;
}

export interface LevelCount {
  level: ProximityLevel | string;
  count: number;
}

export interface AnalyticsDevices {
  mostActive: MostActiveDevice[];
  proximityDistribution: LevelCount[];
  onlineRatio: { online: number; offline: number };
}

export interface SystemStatus {
  status: string;
  database: string;
  redis: string;
  websocketSessions: number;
  uptimeSeconds: number;
  version: string;
}

export interface AuditLog {
  id: string;
  action: string;
  category: string;
  actorId?: string | null;
  actorName?: string | null;
  actorEmail?: string | null;
  entityType?: string | null;
  entityId?: string | null;
  ipAddress?: string | null;
  userAgent?: string | null;
  details?: Record<string, unknown> | null;
  metadata?: Record<string, unknown> | null;
  createdAt: string;
}

export interface ProximityThresholds {
  immediate: number;
  veryClose: number;
  nearby: number;
  far: number;
}

export interface Settings {
  proximityThresholds: ProximityThresholds;
  locationIntervalSeconds: number;
  /** Present in /config/public; persisted through settings when supported. */
  scanIntervalSeconds?: number;
  offlineTimeoutSeconds: number;
  dataRetentionDays: number;
}

export interface PublicConfig {
  proximityThresholds: ProximityThresholds;
  locationIntervalSeconds: number;
  scanIntervalSeconds: number;
}

export interface DeviceStatusFrame {
  deviceId: string;
  status: DeviceStatus | string;
  lastSeenAt?: string | null;
  lastRssi?: number | null;
  receivedAt: number;
}

export type WsFrame =
  | { type: 'hello'; payload: { sessionId: string; userId: string; serverTime: string } }
  | { type: 'proximity.event'; payload: ProximityEvent }
  | { type: 'location.event'; payload: LocationEvent }
  | { type: 'device.status'; payload: { deviceId: string; status: string; lastSeenAt?: string | null; lastRssi?: number | null } }
  | { type: 'notification'; payload: AppNotification }
  | { type: 'pong'; payload?: unknown };
