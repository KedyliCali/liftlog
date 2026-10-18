-- Bodyweight exercises store only the ADDED load in workout_sets.weight_kg (0 = bodyweight only).
-- Statistics add the user's body weight on top, so a weighted pull-up and a plain one compare fairly.
ALTER TABLE exercises ADD COLUMN bodyweight BOOLEAN NOT NULL DEFAULT false;

UPDATE exercises SET bodyweight = true WHERE user_id IS NULL AND name IN ('Pull-up', 'Dip');

INSERT INTO exercises (name, muscle_group, bodyweight) VALUES
    ('Chin-up',            'Back',      true),
    ('Muscle-up',          'Back',      true),
    ('Inverted Row',       'Back',      true),
    ('Push-up',            'Chest',     true),
    ('Pike Push-up',       'Shoulders', true),
    ('Handstand Push-up',  'Shoulders', true),
    ('Pistol Squat',       'Legs',      true),
    ('Bulgarian Split Squat', 'Legs',   true),
    ('Hanging Leg Raise',  'Core',      true),
    ('L-sit',              'Core',      true),
    ('Weighted Pull-up',   'Back',      true),
    ('Weighted Chin-up',   'Back',      true),
    ('Weighted Dip',       'Chest',     true),
    ('Weighted Push-up',   'Chest',     true);
