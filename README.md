# LiftLog

Workout tracking app for Android: log workouts, exercises and sets, and follow your strength progress.

- **Android:** Kotlin, Jetpack Compose, MVVM, Hilt, Retrofit
- **Backend:** Java 21, Spring Boot 3, Spring Data JPA, PostgreSQL, Flyway
- **Infra:** Docker Compose, GitHub Actions

> Work in progress. See [`docs/PLAN.md`](docs/PLAN.md) for the roadmap and [`PROGRESS.md`](PROGRESS.md) for the current status.

## Running the backend locally

```bash
cp .env.example .env        # then set your own database password
docker compose up -d        # starts PostgreSQL
cd backend && ./mvnw spring-boot:run
```

Health check: <http://localhost:8080/api/v1/health>
