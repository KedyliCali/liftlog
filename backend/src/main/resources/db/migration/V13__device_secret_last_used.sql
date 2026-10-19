-- Device secrets are evicted by least recent use instead of age: a device that keeps signing in keeps its
-- secret, while one that has not been seen for longest is the one that must use its passkey again.
-- Nullable so the column is added instantly; the app sets it on insert and on every login that uses it,
-- and a NULL counts as the oldest.
ALTER TABLE device_secrets ADD COLUMN last_used_at TIMESTAMP;

UPDATE device_secrets SET last_used_at = created_at;

DROP INDEX idx_device_secrets_user;
CREATE INDEX idx_device_secrets_user ON device_secrets(user_id, last_used_at);
