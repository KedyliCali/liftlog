-- What an exercise is done with, so the picker can offer a "machine" filter. NULL for custom exercises the
-- user made without choosing (they still show under "all").
ALTER TABLE exercises ADD COLUMN equipment VARCHAR(16)
    CHECK (equipment IN ('BARBELL', 'DUMBBELL', 'MACHINE', 'CABLE', 'BODYWEIGHT'));

UPDATE exercises SET equipment = 'BODYWEIGHT' WHERE user_id IS NULL AND bodyweight;
UPDATE exercises SET equipment = 'BARBELL' WHERE user_id IS NULL AND name IN
    ('Squat', 'Front Squat', 'Romanian Deadlift', 'Deadlift', 'Barbell Row', 'Bench Press', 'Incline Bench Press',
     'Overhead Press', 'Barbell Curl');
UPDATE exercises SET equipment = 'DUMBBELL' WHERE user_id IS NULL AND name IN ('Dumbbell Bench Press', 'Lateral Raise');
UPDATE exercises SET equipment = 'MACHINE' WHERE user_id IS NULL AND name = 'Leg Press';
UPDATE exercises SET equipment = 'CABLE' WHERE user_id IS NULL AND name IN ('Lat Pulldown', 'Triceps Pushdown');

-- Gym machines and cable stations most people use, plus the dumbbell basics that were missing
INSERT INTO exercises (name, muscle_group, equipment) VALUES
    ('Chest Press Machine',            'Chest',     'MACHINE'),
    ('Incline Chest Press Machine',    'Chest',     'MACHINE'),
    ('Pec Deck',                       'Chest',     'MACHINE'),
    ('Smith Machine Bench Press',      'Chest',     'MACHINE'),
    ('Shoulder Press Machine',         'Shoulders', 'MACHINE'),
    ('Reverse Pec Deck',               'Shoulders', 'MACHINE'),
    ('Seated Row Machine',             'Back',      'MACHINE'),
    ('T-Bar Row',                      'Back',      'MACHINE'),
    ('Assisted Pull-up Machine',       'Back',      'MACHINE'),
    ('Back Extension',                 'Back',      'MACHINE'),
    ('Leg Extension',                  'Legs',      'MACHINE'),
    ('Seated Leg Curl',                'Legs',      'MACHINE'),
    ('Lying Leg Curl',                 'Legs',      'MACHINE'),
    ('Hack Squat',                     'Legs',      'MACHINE'),
    ('Smith Machine Squat',            'Legs',      'MACHINE'),
    ('Hip Thrust Machine',             'Legs',      'MACHINE'),
    ('Hip Abduction Machine',          'Legs',      'MACHINE'),
    ('Hip Adduction Machine',          'Legs',      'MACHINE'),
    ('Seated Calf Raise',              'Legs',      'MACHINE'),
    ('Standing Calf Raise Machine',    'Legs',      'MACHINE'),
    ('Preacher Curl Machine',          'Arms',      'MACHINE'),
    ('Triceps Dip Machine',            'Arms',      'MACHINE'),
    ('Ab Crunch Machine',              'Core',      'MACHINE'),
    ('Seated Cable Row',               'Back',      'CABLE'),
    ('Single-Arm Cable Row',           'Back',      'CABLE'),
    ('Straight-Arm Pulldown',          'Back',      'CABLE'),
    ('Close-Grip Lat Pulldown',        'Back',      'CABLE'),
    ('Cable Fly',                      'Chest',     'CABLE'),
    ('Low-to-High Cable Fly',          'Chest',     'CABLE'),
    ('Face Pull',                      'Shoulders', 'CABLE'),
    ('Cable Lateral Raise',            'Shoulders', 'CABLE'),
    ('Cable Curl',                     'Arms',      'CABLE'),
    ('Overhead Cable Triceps Extension', 'Arms',    'CABLE'),
    ('Rope Triceps Pushdown',          'Arms',      'CABLE'),
    ('Cable Crunch',                   'Core',      'CABLE'),
    ('Cable Pull-Through',             'Legs',      'CABLE'),
    ('Cable Kickback',                 'Legs',      'CABLE'),
    ('Dumbbell Shoulder Press',        'Shoulders', 'DUMBBELL'),
    ('Dumbbell Row',                   'Back',      'DUMBBELL'),
    ('Incline Dumbbell Press',         'Chest',     'DUMBBELL'),
    ('Dumbbell Fly',                   'Chest',     'DUMBBELL'),
    ('Dumbbell Curl',                  'Arms',      'DUMBBELL'),
    ('Hammer Curl',                    'Arms',      'DUMBBELL'),
    ('Goblet Squat',                   'Legs',      'DUMBBELL'),
    ('Dumbbell Lunge',                 'Legs',      'DUMBBELL'),
    ('Skull Crusher',                  'Arms',      'BARBELL');
