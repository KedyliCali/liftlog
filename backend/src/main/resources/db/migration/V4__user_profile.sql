ALTER TABLE users
    ADD COLUMN height_cm      NUMERIC(4,1) CHECK (height_cm BETWEEN 50 AND 272),
    ADD COLUMN body_weight_kg NUMERIC(5,2) CHECK (body_weight_kg BETWEEN 20 AND 500);

-- Separate table so loading a user never drags the image bytes along
CREATE TABLE user_avatars (
    user_id      BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    content_type VARCHAR(50) NOT NULL,
    data         BYTEA NOT NULL,
    updated_at   TIMESTAMP NOT NULL DEFAULT now()
);
