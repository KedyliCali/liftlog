# LiftLog — İlerleme Günlüğü

## 2026-10-17 — Faz 0 · Kurulum

**Yapılanlar**
- Kurulu araçlar: JDK 21 (Temurin 21.0.12), Git 2.55, Docker 29.6, GitHub CLI, yerel PostgreSQL 18.
- winget ile kurulum başlatıldı: IntelliJ IDEA, DBeaver Community, Bruno, Android Studio.
- Git deposu açıldı, GitHub'a bağlandı: <https://github.com/KedyliCali/liftlog> (**private**).
- `feature/backend-setup` branch'i:
  - start.spring.io ile backend iskeleti (`backend/`). Plan "Spring Boot 3" diyordu ama Initializr artık
    3.x sunmuyor (açık kaynak desteği bitti) → **Spring Boot 4.1.1** seçildi.
  - `docker-compose.yml`: PostgreSQL 17, host portu **5433** (yerel PostgreSQL 5432 ile çakışmasın diye).
  - `.env` (git'e girmez) + `.env.example`; `application.yml` şifreyi `.env`'den okur.
  - `GET /api/v1/health` → `{"status":"UP"}`, `@WebMvcTest` ile test edildi.
- Doğrulama: `./mvnw test` → 2 test geçti; uygulama çalıştırılıp tarayıcıda 200 + `{"status":"UP"}` görüldü.

**Faz 0 durumu**
- [x] Araçlar (telefon tarafı hariç)
- [x] GitHub deposu ve klasör yapısı
- [x] Spring Boot projesi + Docker'da PostgreSQL
- [x] `GET /api/v1/health` çalışıyor

**Kalan / Behlül'ün yapması gerekenler**
- PR'ı incele ve `main`'e birleştir.
- Telefonda Geliştirici Seçenekleri + USB hata ayıklama (Faz 2'ye kadar gerekmez).
- Android Studio'yu ilk açışta SDK kurulum sihirbazını tamamla.

**Sıradaki adım:** Faz 1 · Flyway ile ilk şema (`V1__init.sql`) ve hazır hareketler.
