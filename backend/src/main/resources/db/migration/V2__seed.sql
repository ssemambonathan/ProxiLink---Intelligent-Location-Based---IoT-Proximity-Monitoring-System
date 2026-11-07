INSERT INTO roles (id, name) VALUES
    ('11111111-1111-1111-1111-111111111111', 'USER'),
    ('22222222-2222-2222-2222-222222222222', 'ADMIN');

INSERT INTO system_settings (setting_key, setting_value, updated_by, updated_at) VALUES
    ('proximity.thresholds', '{"immediate":-50,"veryClose":-65,"nearby":-80,"far":-95}', NULL, CURRENT_TIMESTAMP),
    ('location.intervalSeconds', '30', NULL, CURRENT_TIMESTAMP),
    ('scan.intervalSeconds', '10', NULL, CURRENT_TIMESTAMP),
    ('device.offlineTimeoutSeconds', '300', NULL, CURRENT_TIMESTAMP),
    ('data.retentionDays', '90', NULL, CURRENT_TIMESTAMP);
