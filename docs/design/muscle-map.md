# Muscle map: design

Behlül asked (2026-10-19) for a body map where each muscle is coloured by how much it was trained: little =
yellow, a lot = red, so a glance shows which muscles a week (or one workout) hit.

## Muscles
16 regions, ids used by both sides: `CHEST, FRONT_DELTS, SIDE_DELTS, REAR_DELTS, BICEPS, TRICEPS, FOREARMS,
ABS, OBLIQUES, TRAPS, LATS, LOWER_BACK, GLUTES, QUADS, HAMSTRINGS, CALVES` (plus `ADDUCTORS`, `ABDUCTORS` folded
into QUADS / GLUTES on the map).

## Measure
Sets per muscle: a set counts **1** for each primary muscle of its exercise and **0.5** for each secondary
muscle. Colour bands (per 7 days; for 30 days the thresholds are multiplied by 30/7 and rounded):
- 0 → grey (not trained), (0, 5) → yellow, [5, 10) → orange, ≥ 10 → red.
For a single workout: (0, 2) yellow, [2, 4) orange, ≥ 4 red.

## Backend
- Migration **V17**: `exercise_muscles (exercise_id → exercises ON DELETE CASCADE, muscle VARCHAR(16), role
  VARCHAR(10) CHECK IN ('PRIMARY','SECONDARY'), PRIMARY KEY (exercise_id, muscle))`, seeded for every built-in
  exercise (V2, V5, V16 names) by name lookup.
- Custom exercises (no rows) map from `muscle_group`:
  - Chest → CHEST primary;
  - Back → LATS primary, TRAPS secondary;
  - Legs → QUADS primary, HAMSTRINGS and GLUTES secondary;
  - Shoulders → SIDE_DELTS primary, FRONT_DELTS secondary;
  - Arms → BICEPS and TRICEPS secondary (0.5 each, since it is unknown which one);
  - Core → ABS primary.
- `GET /api/v1/stats/muscles?days=7|30` → `{days, from, to, muscles: [{muscle, sets (number, 1 decimal),
  exercises: [{name, sets}]}]}` for muscles with sets > 0, over the user's workouts dated in the last `days`
  days (inclusive of today, phone date passed as `today` query param optional, default server date).
- `WorkoutRecapResponse` += `muscles: [{muscle, sets}]` for that workout.
- `GET /api/v1/exercises` += `muscles: [{muscle, role}]` per exercise (built-ins from the table, custom from the
  fallback), so the exercise picker can show them later.

## Web
- `components/BodyMap.tsx`: front and back silhouettes in SVG drawn by hand (layered: a darker offset outline
  behind the body, muscle paths on top with a thin separator stroke; no flat single blob). Each muscle path
  carries `data-muscle`, is coloured by band (CSS variables, readable in light and dark), and is tappable.
- Progress page: a "Kas haritası" card with Ön / Arka toggle and 7 gün / 30 gün; a legend (Az / Orta / Çok);
  tapping a muscle shows a small panel: Turkish muscle name, sets, and the exercises with their sets.
- Workout summary sheet: a small front+back map of that workout.
- Turkish muscle names: Göğüs, Ön omuz, Yan omuz, Arka omuz, Pazı (biceps), Arka kol (triceps), Ön kol, Karın,
  Yan karın, Trapez, Kanat (lats), Bel, Kalça, Ön bacak (quadriceps), Arka bacak (hamstring), Baldır.
