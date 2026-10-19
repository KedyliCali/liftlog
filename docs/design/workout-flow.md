# Faster workout logging: routines, workout mode, progression: design

Behlül asked (2026-10-19) how logging could be easier and chose "whatever fits best": saved routines, a
workout mode where planned sets are ticked off, and a progression suggestion. Merge order: after #19 and #20
(this branch is cut from feature/lol-stats, which already contains the passkey branch); the migration is V15.

## What the user does
1. **Save a routine once:** on a finished workout, "Rutin olarak kaydet" turns it into a routine (each exercise
   with its number of sets and the reps it was done for; name defaults to the workout's note). Routines can also
   be built from scratch on a Routines sheet (add exercise, sets × reps), renamed, reordered, deleted.
2. **Start from a routine:** the Workouts page shows a "Rutinle başla" strip of routine chips above the list
   (and the New workout sheet lists them first). One tap creates today's workout linked to the routine and opens
   it in workout mode.
3. **Workout mode** (workout detail page of a workout that has a routine): every exercise of the routine shows
   its planned sets as rows prefilled with the suggested weight and the target reps. Each row has a big ✓:
   - tap ✓ → the set is logged exactly as shown (one request), the row turns into a normal logged set, and the
     rest timer starts by itself;
   - tap the weight or reps → large ± steppers (2.5 kg / 1 rep; bodyweight exercises step the added load) without
     the keyboard, then ✓;
   - planned rows are not stored: they are derived from the routine minus the sets already logged for that
     exercise, so closing the app mid-workout loses nothing;
   - "Hareket ekle" and the existing manual set entry still work for anything extra.
4. **Progression suggestion** (per exercise, shown as a small tag on the first planned row):
   - last time every planned set reached the target reps → **+2.5 kg** ("↑ 2,5 kg"); for bodyweight exercises
     with no added load → **+1 rep** on the target;
   - any set fell short → same weight ("Aynı ağırlık");
   - never done before → no weight (empty, the user types it once) ("İlk kez").
   "Last time" = the most recent earlier workout that contains this exercise.

## Backend
Migration V15:
- `routines (id BIGSERIAL PK, user_id → users ON DELETE CASCADE, name VARCHAR(60) NOT NULL, created_at)`.
- `routine_exercises (id BIGSERIAL PK, routine_id → routines ON DELETE CASCADE, position INT NOT NULL,
  exercise_id → exercises, sets INT CHECK 1..10, target_reps INT CHECK 1..100, UNIQUE (routine_id, position))`.
- `workouts.routine_id BIGINT NULL → routines ON DELETE SET NULL`.

Endpoints (JWT; a user only sees their own routines; another user's id is a 404):
- `GET /api/v1/routines` → `[{id, name, exercises: [{exerciseId, name, bodyweight, sets, targetReps}]}]`
- `POST /api/v1/routines` `{name, exercises: [{exerciseId, sets, targetReps}]}` → 201; `PUT /api/v1/routines/{id}`
  (same body, replaces the list); `DELETE /api/v1/routines/{id}` → 204.
- `POST /api/v1/routines/from-workout/{workoutId}` `{name?}` → 201 routine: exercises in first-logged order, sets =
  number of logged sets, targetReps = the most common reps of those sets.
- `POST /api/v1/routines/{id}/start` `{workoutDate?}` → 201 `WorkoutDetail` (empty, `routineId` set, notes = name).
- `GET /api/v1/workouts/{id}/plan` → `[{exerciseId, name, bodyweight, plannedSets, loggedSets, targetReps,
  suggestedWeightKg|null, lastWeightKg|null, lastReps: int[], suggestion: 'INCREASE'|'SAME'|'FIRST',
  increaseKg|null}]` for a workout with a routine (404 otherwise); `remaining = plannedSets - loggedSets`.
- `WorkoutDetail` += `routineId|null`.
Validation: name 1–60 trimmed, 1–20 exercises, exercises must be visible to the user (built-in or own).
The suggestion is a pure function (`Progression`) with unit tests.

## Web
- Workouts page: "Rutinle başla" chip strip (only when routines exist) and a "Rutinler" entry to manage them.
- NewWorkoutSheet: routines listed first.
- WorkoutDetailPage: when `routineId` is set, a workout-mode section per exercise with planned rows (prefilled,
  ± steppers, ✓) and the suggestion tag; ticking posts the set (`POST /workouts/{id}/sets`), refetches the plan
  and starts the rest timer (`useRestTimer`). A "Rutin olarak kaydet" action on any workout with sets.
- Routines sheet: list, create, edit (exercise picker reused from the set editor), delete with confirm.
- All strings TR + EN; sport style; layered elements (no flat single blocks).
