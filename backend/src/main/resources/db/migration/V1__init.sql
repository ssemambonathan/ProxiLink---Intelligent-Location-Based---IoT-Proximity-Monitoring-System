CREATE TABLE roles (
    id UUID PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    organization VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE devices (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    organization VARCHAR(120) NOT NULL,
    name VARCHAR(120) NOT NULL,
    device_type VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    hardware_id VARCHAR(128),
    ble_mac VARCHAR(32),
    service_uuid VARCHAR(64),
    status VARCHAR(24) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    last_rssi INTEGER,
    last_proximity VARCHAR(24),
    last_latitude DOUBLE PRECISION,
    last_longitude DOUBLE PRECISION,
    last_accuracy_m DOUBLE PRECISION,
    last_location_at TIMESTAMP WITH TIME ZONE,
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_devices_org ON devices (organization);
CREATE INDEX idx_devices_status ON devices (status);
CREATE INDEX idx_devices_owner ON devices (owner_id);
CREATE UNIQUE INDEX uq_devices_org_hardware ON devices (organization, hardware_id);

CREATE TABLE device_registrations (
    id UUID PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    registered_by UUID REFERENCES users(id),
    method VARCHAR(32) NOT NULL,
    notes TEXT,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE device_status_history (
    id UUID PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    status VARCHAR(24) NOT NULL,
    reason VARCHAR(255),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_device_status_history_device ON device_status_history (device_id, changed_at);

CREATE TABLE proximity_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    organization VARCHAR(120) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    user_display_name VARCHAR(120) NOT NULL,
    device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
    device_name VARCHAR(120),
    ble_mac VARCHAR(32),
    rssi INTEGER NOT NULL,
    smoothed_rssi DOUBLE PRECISION,
    proximity_level VARCHAR(24) NOT NULL,
    estimated_distance_m DOUBLE PRECISION,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    accuracy_m DOUBLE PRECISION,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    metadata TEXT
);

CREATE INDEX idx_prox_org_time ON proximity_events (organization, recorded_at);
CREATE INDEX idx_prox_device_time ON proximity_events (device_id, recorded_at);
CREATE INDEX idx_prox_level_time ON proximity_events (proximity_level, recorded_at);

CREATE TABLE location_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    organization VARCHAR(120) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    user_display_name VARCHAR(120) NOT NULL,
    device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy_m DOUBLE PRECISION,
    speed_mps DOUBLE PRECISION,
    altitude_m DOUBLE PRECISION,
    source VARCHAR(24) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    metadata TEXT
);

CREATE INDEX idx_loc_org_time ON location_events (organization, recorded_at);
CREATE INDEX idx_loc_user_time ON location_events (user_id, recorded_at);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(500) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    metadata TEXT
);

CREATE INDEX idx_notifications_user_time ON notifications (user_id, created_at);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(64) NOT NULL,
    category VARCHAR(24) NOT NULL,
    entity_type VARCHAR(64),
    entity_id VARCHAR(64),
    ip VARCHAR(64),
    user_agent VARCHAR(255),
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_audit_time ON audit_logs (created_at);
CREATE INDEX idx_audit_action ON audit_logs (action);
CREATE INDEX idx_audit_category ON audit_logs (category);

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(64) NOT NULL UNIQUE,
    device_label VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_sessions_user ON user_sessions (user_id);

CREATE TABLE system_settings (
    setting_key VARCHAR(64) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
