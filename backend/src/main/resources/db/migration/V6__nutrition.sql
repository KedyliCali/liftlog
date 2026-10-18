-- Daily nutrition goals live on the user: one pair per person, null until they set one.
ALTER TABLE users
    ADD COLUMN calorie_goal_kcal INTEGER CHECK (calorie_goal_kcal BETWEEN 500 AND 10000),
    ADD COLUMN protein_goal_g    INTEGER CHECK (protein_goal_g BETWEEN 10 AND 500);

-- One row per thing eaten. "Frequent foods" are computed from these rows, not stored separately,
-- so they can never drift from what was actually logged.
CREATE TABLE food_entries (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entry_date    DATE         NOT NULL,
    name          VARCHAR(100) NOT NULL,
    calories_kcal INTEGER      NOT NULL CHECK (calories_kcal BETWEEN 0 AND 10000),
    protein_g     NUMERIC(5,1) NOT NULL CHECK (protein_g BETWEEN 0 AND 1000),
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

-- Every query filters by user and day (or a recent date range)
CREATE INDEX idx_food_entries_user_date ON food_entries (user_id, entry_date);
