-- Body weight over time: at most one entry per user per calendar day (the client's date), so a second
-- weigh-in on the same day replaces the first. users.body_weight_kg stays as "the current weight" and is
-- kept equal to the newest entry by WeightService, so stats that read it keep working unchanged.
CREATE TABLE weight_entries (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entry_date DATE         NOT NULL,
    weight_kg  NUMERIC(5,2) NOT NULL CHECK (weight_kg BETWEEN 20 AND 400),
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_weight_entries_user_date UNIQUE (user_id, entry_date)
);

-- Seed the history with the weight users already entered in their profile, dated the day they joined
-- (the best guess we have). Profile weights above 400 kg are allowed by users' own CHECK but not here.
INSERT INTO weight_entries (user_id, entry_date, weight_kg)
SELECT id, COALESCE(created_at::date, CURRENT_DATE), body_weight_kg
FROM users
WHERE body_weight_kg BETWEEN 20 AND 400;
