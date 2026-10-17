CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100),
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE exercises (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    muscle_group  VARCHAR(50),
    -- NULL = built-in exercise visible to everyone
    user_id       BIGINT REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE workouts (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workout_date  DATE NOT NULL,
    notes         TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE workout_sets (
    id            BIGSERIAL PRIMARY KEY,
    workout_id    BIGINT NOT NULL REFERENCES workouts(id) ON DELETE CASCADE,
    exercise_id   BIGINT NOT NULL REFERENCES exercises(id),
    set_order     INT NOT NULL,
    reps          INT NOT NULL CHECK (reps > 0),
    -- NUMERIC instead of FLOAT so values like 2.5 kg are stored exactly
    weight_kg     NUMERIC(6,2) NOT NULL CHECK (weight_kg >= 0),
    rpe           NUMERIC(3,1) CHECK (rpe BETWEEN 1 AND 10)
);

CREATE INDEX idx_workout_sets_workout_id ON workout_sets(workout_id);
CREATE INDEX idx_workout_sets_exercise_id ON workout_sets(exercise_id);
CREATE INDEX idx_workouts_user_date ON workouts(user_id, workout_date DESC);
CREATE INDEX idx_exercises_user_id ON exercises(user_id);
