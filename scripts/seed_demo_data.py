"""Fill a LiftLog backend with eight weeks of realistic training for the demo account.

Used for screenshots and for trying the Progress tab without logging weeks of workouts by hand.
The demo account is local/test data only; never reuse this password anywhere real.

    python scripts/seed_demo_data.py                          # http://localhost:8080
    python scripts/seed_demo_data.py https://api.example.com  # another server
"""
import json
import sys
import urllib.error
import urllib.request
from datetime import date, timedelta

DEMO_EMAIL = "demo.lifter@liftlog.test"
DEMO_PASSWORD = "liftlog-demo-2026"
DEMO_NAME = "Demo Lifter"

# Built-in exercise ids from V2__seed_exercises.sql
SQUAT, BENCH, DEADLIFT, OHP, ROW = 1, 9, 5, 13, 6

# (weekday offset, notes, [(exercise, start kg, weekly increase kg, reps, sets)])
TEMPLATE = [
    (0, "Squat + Bench", [(SQUAT, 100, 2.5, 5, 3), (BENCH, 70, 1.25, 5, 3), (ROW, 60, 1.25, 8, 3)]),
    (2, "Deadlift + OHP", [(DEADLIFT, 130, 2.5, 5, 1), (OHP, 42.5, 0.5, 5, 3), (ROW, 60, 1.25, 8, 2)]),
    (4, "Squat + Bench (heavy)", [(SQUAT, 110, 2.5, 3, 3), (BENCH, 77.5, 1.25, 3, 3)]),
]
WEEKS = 8


def call(base, method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(base + path, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(request) as response:
        raw = response.read()
        return json.loads(raw) if raw else None


def get_token(base):
    credentials = {"email": DEMO_EMAIL, "password": DEMO_PASSWORD}
    try:
        return call(base, "POST", "/api/v1/auth/login", credentials)["token"]
    except urllib.error.HTTPError as error:
        if error.code != 401:
            raise
    return call(base, "POST", "/api/v1/auth/register", {**credentials, "displayName": DEMO_NAME})["token"]


def main():
    base = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")
    token = get_token(base)

    existing = call(base, "GET", "/api/v1/workouts?size=1", token=token)["totalElements"]
    if existing:
        print(f"Demo account already has {existing} workouts; nothing to do.")
        return

    this_monday = date.today() - timedelta(days=date.today().weekday())
    first_monday = this_monday - timedelta(weeks=WEEKS - 1)
    created = 0
    for week in range(WEEKS):
        for day_offset, notes, exercises in TEMPLATE:
            day = first_monday + timedelta(weeks=week, days=day_offset)
            if day > date.today():
                continue
            workout = call(base, "POST", "/api/v1/workouts", {"workoutDate": day.isoformat(), "notes": notes}, token)
            for exercise_id, start, step, reps, sets in exercises:
                # Deload every fourth week, like a real program, so the chart is not a perfect line
                weight = start + step * week - (5 if week % 4 == 3 else 0)
                for _ in range(sets):
                    body = {"exerciseId": exercise_id, "reps": reps, "weightKg": weight, "rpe": 8}
                    call(base, "POST", f"/api/v1/workouts/{workout['id']}/sets", body, token)
            created += 1
    print(f"Created {created} workouts for {DEMO_EMAIL}")


if __name__ == "__main__":
    main()
