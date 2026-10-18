-- Raised on every password change (on the web: "New code"). Each JWT carries the value it was issued
-- with in the "ver" claim, so tokens from before the change stop working on their next request.
-- A constant default is metadata-only on PostgreSQL 11+, so this is instant even on a large table.
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
