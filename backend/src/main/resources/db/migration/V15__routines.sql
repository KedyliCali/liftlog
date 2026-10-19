-- Saved routines: a named list of exercises with planned sets x target reps, to start a workout from.
CREATE TABLE routines (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(60) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_routines_user_id ON routines(user_id);

CREATE TABLE routine_exercises (
    id          BIGSERIAL PRIMARY KEY,
    routine_id  BIGINT NOT NULL REFERENCES routines(id) ON DELETE CASCADE,
    -- 0-based order of the exercise inside the routine
    position    INT    NOT NULL,
    exercise_id BIGINT NOT NULL REFERENCES exercises(id),
    sets        INT    NOT NULL CHECK (sets BETWEEN 1 AND 10),
    target_reps INT    NOT NULL CHECK (target_reps BETWEEN 1 AND 100),
    UNIQUE (routine_id, position)
);

-- A workout started from a routine. Deleting the routine keeps the workout and its sets; it just loses the plan.
ALTER TABLE workouts ADD COLUMN routine_id BIGINT REFERENCES routines(id) ON DELETE SET NULL;

CREATE INDEX idx_workouts_routine_id ON workouts(routine_id);
