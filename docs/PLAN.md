# Antrenman Takip Uygulaması — Proje Planı

17 Ekim 2026 · Behlül Akdağ

## Proje özeti ve hedefler

Android için bir antrenman takip uygulaması yapıyoruz: kullanıcı antrenmanlarını, hareketlerini ve setlerini kaydeder, PR'larını ve ilerlemesini grafiklerle görür. Uygulama kendi yazdığımız bir Spring Boot API'sine bağlanır, veriler PostgreSQL'de durur, backend Docker ile paketlenip internete yayınlanır.

**Çalışma adı:** LiftLog (istediğin zaman değiştirilebilir)

**Hedefler:**

1. **CV projesi:** Staj başvurularında gösterilecek, çalışan, yayınlanmış bir full-stack mobil proje.
2. **Öğrenme:** Kotlin, Android, REST API, veritabanı tasarımı, Docker ve Git'i gerçek bir projede kullanmak.
3. **Anlatabilmek:** Her parçayı kendin yazacaksın; mülakatta "neden böyle yaptın?" sorusuna cevap verebileceksin.
4. **Gerçek kullanım:** Kendi antrenmanlarında kullanacağın bir uygulama.

**Çalışma kuralı:** Claude adımı ve nedenini anlatır, kodu sen yazarsın; takıldığın yerde birlikte çözeriz. Vizeler kasım başında başladığı için o dönem tempo düşer, plan buna göre ayarlandı.

## Teknoloji seçimleri ve gerekçeleri

Mobil taraf Kotlin + Jetpack Compose ile native Android, backend Java + Spring Boot. Java bildiğin için Kotlin'e geçiş kolay, Spring Boot ise stajlarda en çok aranan Java teknolojisi.

| Katman | Teknoloji | Neden |
| --- | --- | --- |
| Mobil dil | Kotlin | Google'ın resmi Android dili; Java'ya çok yakın |
| Mobil arayüz | Jetpack Compose | Modern Android arayüz yöntemi, yeni ilanlarda isteniyor |
| Mobil mimari | MVVM + ViewModel + StateFlow | Android'in önerdiği standart yapı |
| Ağ istekleri | Retrofit + OkHttp | API'ye bağlanmak için standart kütüphane |
| Yerel veri (Faz 4) | Room | İnternet yokken de çalışabilmek için |
| Bağımlılık enjeksiyonu | Hilt | Kodun test edilebilir ve düzenli olması için |
| Grafikler | Vico | Compose ile uyumlu grafik kütüphanesi |
| Backend | Java 21 + Spring Boot 3 | Bildiğin dil, sektör standardı |
| Veri erişimi | Spring Data JPA + Hibernate | Tabloları Java sınıflarıyla yönetmek |
| Veritabanı | PostgreSQL | Zaten kullandığın veritabanı |
| Şema yönetimi | Flyway | Tablo değişikliklerini sürüm sürüm takip etmek |
| Güvenlik | Spring Security + JWT | Kayıt / giriş ve kullanıcıya özel veri |
| API dokümantasyonu | Springdoc OpenAPI (Swagger) | API'yi tarayıcıdan görüp test etmek |
| Paketleme | Docker + Docker Compose | Yarım kalan Docker işini tamamlamak |
| Sürüm kontrolü | Git + GitHub | CV'de link verilecek yer |
| Otomatik test | JUnit 5, Mockito, GitHub Actions | Her push'ta testlerin çalışması |

**Neden Flutter veya React Native değil?** İkisi de hem Android hem iOS'a çıkar ama Dart veya JavaScript öğrenmeyi gerektirir. Senin Java temelin native Android'e doğrudan aktarılıyor. iOS sürümü için zaten Mac gerekir.

## Özellikler: MVP ve sonraki sürümler

İlk sürüm (MVP) bilerek küçük tutuldu: antrenman ekle, set ekle, geçmişi gör. Diğer her şey MVP çalıştıktan sonra eklenecek.

**MVP (v1.0)**

- Hareket listesi: Squat, Bench Press, Deadlift, Overhead Press gibi hazır hareketler + kendi hareketini ekleme
- Yeni antrenman başlatma (tarih, not)
- Antrenmana set ekleme: hareket, tekrar, ağırlık (kg), isteğe bağlı RPE
- Geçmiş antrenmanları listeleme ve detayını görme
- Antrenman ve set silme / düzenleme

**v1.1 — Kullanıcı hesapları**

- Kayıt olma ve giriş (e-posta + şifre, JWT)
- Her kullanıcı yalnızca kendi verisini görür

**v1.2 — İlerleme ve istatistik**

- Hareket başına PR takibi (en ağır set, tahmini 1RM)
- Ağırlık ilerleme grafiği (hareket seçerek)
- Haftalık toplam hacim (set × tekrar × ağırlık)
- Yeni PR kırıldığında uygulamada bildirim

**v1.3 — Kullanım kolaylığı**

- Setler arası dinlenme zamanlayıcısı
- Antrenman şablonları (örneğin "Push günü" — hazır hareket listesi)
- Son antrenmandaki ağırlıkları otomatik önerme
- Karanlık tema

**v2.0 — Çevrimdışı mod (isteğe bağlı, ileri seviye)**

- Room ile yerel kayıt; internet gelince sunucuyla senkronizasyon

**Kapsam dışı (şimdilik):** sosyal özellikler, iOS sürümü, beslenme takibi, Play Store'da ücretli yayın.

## Sistem mimarisi

İki taraf var: telefondaki Android uygulaması ve sunucuda Docker içinde çalışan backend + veritabanı. Aralarındaki tek bağ HTTPS üzerinden JSON taşıyan REST API.

```
[ TELEFON: Android uygulaması ]              [ SUNUCU: Docker Compose ]
 Ekranlar (Jetpack Compose)                    Controller (Spring Boot)
        ↓                                             ↓
 ViewModel (UiState)                           Service (PR, 1RM, hacim)
        ↓                                             ↓
 Repository                                    Repository (Spring Data JPA)
        ↓                                             ↓
 Retrofit servisi  ── HTTPS + JSON + JWT ──→   PostgreSQL (Flyway şeması)
```

Bir "set ekle" dokunuşu soldan aşağı iner, API'den geçer, sağda yukarıdan aşağı inip PostgreSQL'e yazılır; cevap aynı yoldan geri döner.

## Veritabanı tasarımı

Beş tablo yeterli: bir kullanıcının antrenmanları var, her antrenmanda birden fazla set var, her set bir harekete ait. MVP'de tablolar baştan kurulur ama tek bir test kullanıcısıyla çalışılır; giriş sistemi v1.1'de eklenir.

```sql
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
    muscle_group  VARCHAR(50),           -- bacak, göğüs, sırt...
    user_id       BIGINT REFERENCES users(id) -- NULL = herkese açık hazır hareket
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
    weight_kg     NUMERIC(6,2) NOT NULL CHECK (weight_kg >= 0),
    rpe           NUMERIC(3,1) CHECK (rpe BETWEEN 1 AND 10)
);

CREATE TABLE templates (            -- v1.3
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name          VARCHAR(100) NOT NULL
);
```

**İlişkiler:**

- `users` 1 → N `workouts`
- `workouts` 1 → N `workout_sets`
- `exercises` 1 → N `workout_sets`

**Mülakatta konuşacağın tasarım kararları:**

- `ON DELETE CASCADE`: antrenman silinince setleri de silinir.
- `CHECK` kısıtları: negatif ağırlık veya 0 tekrar veritabanı seviyesinde engellenir.
- `NUMERIC(6,2)`: ağırlık için `FLOAT` yerine; 2.5 kg gibi değerlerde yuvarlama hatası olmaz.
- PR ayrı tabloda tutulmaz; setlerden sorguyla hesaplanır (veri tekrarı yok).
- `workout_sets(workout_id)` ve `workouts(user_id, workout_date)` üzerine index.

## REST API tasarımı

Mobil uygulama backend'le yalnızca JSON üzerinden konuşur. Tüm adresler `/api/v1` ile başlar; v1.1'den sonra `auth` dışındaki her istek `Authorization: Bearer <token>` başlığı taşır.

| Metot | Adres | Ne yapar | Sürüm |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | Yeni kullanıcı oluşturur | v1.1 |
| POST | `/api/v1/auth/login` | Giriş yapar, JWT döner | v1.1 |
| GET | `/api/v1/exercises` | Hareket listesini döner | MVP |
| POST | `/api/v1/exercises` | Yeni hareket ekler | MVP |
| GET | `/api/v1/workouts?page=0&size=20` | Antrenmanları sayfalı listeler (en yeni önce) | MVP |
| GET | `/api/v1/workouts/{id}` | Tek antrenmanı setleriyle döner | MVP |
| POST | `/api/v1/workouts` | Yeni antrenman oluşturur | MVP |
| PUT | `/api/v1/workouts/{id}` | Antrenmanı günceller | MVP |
| DELETE | `/api/v1/workouts/{id}` | Antrenmanı siler | MVP |
| POST | `/api/v1/workouts/{id}/sets` | Antrenmana set ekler | MVP |
| PUT | `/api/v1/sets/{id}` | Seti günceller | MVP |
| DELETE | `/api/v1/sets/{id}` | Seti siler | MVP |
| GET | `/api/v1/stats/prs` | Hareket başına PR'ları döner | v1.2 |
| GET | `/api/v1/stats/progress?exerciseId=1` | Bir hareketin zaman içindeki ilerlemesi | v1.2 |
| GET | `/api/v1/stats/volume?weeks=8` | Haftalık toplam hacim | v1.2 |

**Örnek istek** — `POST /api/v1/workouts/42/sets`:

```json
{ "exerciseId": 1, "reps": 5, "weightKg": 140.0, "rpe": 8 }
```

**Örnek cevap** — `201 Created`:

```json
{ "id": 318, "exerciseName": "Squat", "setOrder": 3, "reps": 5, "weightKg": 140.0, "rpe": 8 }
```

**Kurallar:**

- Veritabanı sınıfları (Entity) dışarı açılmaz; giriş-çıkış için ayrı DTO sınıfları kullanılır.
- Gelen veri `@Valid` ile kontrol edilir; hatalı istek `400` ve açıklayıcı bir mesaj döner.
- Hatalar tek bir `@RestControllerAdvice` sınıfında ortak formatta döner.
- Başkasının antrenmanına erişim `404` döner (varlığı bile belli edilmez).

## Mobil uygulama ekranları ve yapısı

Uygulama alt menüde üç sekmeyle açılır: **Antrenmanlar**, **İlerleme**, **Profil**. Ekranlar arası geçiş Navigation Compose ile yapılır.

| Ekran | Ne gösterir | Sürüm |
| --- | --- | --- |
| Giriş / Kayıt | E-posta ve şifre formu | v1.1 |
| Antrenman listesi | Geçmiş antrenmanlar kart olarak, sağ altta "+" butonu | MVP |
| Antrenman detayı | Hareketlere göre gruplanmış setler, "Set ekle" butonu | MVP |
| Set ekleme (alt sayfa) | Hareket seçimi, tekrar, ağırlık, RPE alanları | MVP |
| Hareket seçimi | Aranabilir hareket listesi + "Yeni hareket" | MVP |
| İlerleme | Hareket seçici, ağırlık grafiği, PR kartları | v1.2 |
| Dinlenme zamanlayıcısı | Set sonrası geri sayım | v1.3 |
| Profil / Ayarlar | Kullanıcı bilgisi, tema, çıkış | v1.1 |

**Her ekranın iç yapısı (MVVM):**

1. **Screen (Composable):** Sadece görüntüler, kullanıcı hareketini ViewModel'e iletir.
2. **ViewModel:** Ekranın durumunu (`UiState`: yükleniyor / veri / hata) tutar.
3. **Repository:** Veriyi nereden alacağını bilir (API, ileride Room).
4. **Retrofit servisi:** HTTP isteğini atar.

Bu ayrım sayesinde ekran kodu ağ koduyla karışmaz; mülakatta "MVVM nedir, neden kullandın?" sorusunun cevabı bu dört katman.

## Proje klasör yapısı

Tek bir GitHub deposunda iki ana klasör olur: `backend` ve `android`. Böylece CV'de tek link verirsin.

```
liftlog/
├── README.md                 # proje vitrini: ekran görüntüleri, mimari, kurulum
├── docker-compose.yml        # backend + PostgreSQL tek komutla
├── .github/workflows/        # otomatik testler (CI)
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/behlul/liftlog/
│       ├── controller/       # HTTP isteklerini karşılar
│       ├── service/          # iş mantığı (PR hesabı vb.)
│       ├── repository/       # veritabanı erişimi (Spring Data JPA)
│       ├── entity/           # tablo karşılığı sınıflar
│       ├── dto/              # API'ye giren / çıkan veri
│       ├── security/         # JWT ve Spring Security ayarları
│       └── exception/        # ortak hata yönetimi
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/     # Flyway SQL dosyaları (V1__init.sql ...)
└── android/
    └── app/src/main/java/com/behlul/liftlog/
        ├── data/
        │   ├── remote/       # Retrofit servisleri ve DTO'lar
        │   ├── local/        # Room (v2.0)
        │   └── repository/
        ├── domain/model/     # uygulamanın kendi veri sınıfları
        ├── ui/
        │   ├── workouts/     # liste + detay ekranları ve ViewModel'leri
        │   ├── progress/
        │   ├── auth/
        │   ├── components/   # tekrar kullanılan arayüz parçaları
        │   └── theme/
        └── di/               # Hilt modülleri
```

## Fazlara bölünmüş yol haritası ve takvim

Toplam yaklaşık 16 hafta: çalışan MVP 21 Aralık'a, CV'ye eklenecek sürüm Şubat başına hedefleniyor. Vize ve final dönemlerinde tempo düşer; tarihler buna göre kayabilir.

| Faz | Tarih | İçerik |
| --- | --- | --- |
| Faz 0 · Kurulum | 18–26 Eki (1 hafta) | Araçlar, GitHub deposu, Spring Boot iskeleti, Docker'da PostgreSQL |
| Faz 1 · Backend MVP | 27 Eki – 23 Kas (4 hafta, vize dönemi) | Tablolar, antrenman ve set API'leri, hata yönetimi, ilk testler |
| Faz 2 · Android MVP | 24 Kas – 21 Ara (4 hafta) | Compose ekranları, MVVM, Retrofit ile API'ye bağlanma |
| **Kapı** | 21 Ara | Telefonda antrenman eklenip listelenebiliyor |
| Faz 3 · Hesaplar ve ilerleme | 22 Ara – 18 Oca (4 hafta) | JWT ile giriş, PR takibi, ilerleme grafiği, haftalık hacim |
| Faz 4 · Yayın ve vitrin | 19 Oca – 8 Şub (3 hafta) | Deploy, CI, README, APK; zaman kalırsa zamanlayıcı ve şablonlar |
| **Kapı** | 8 Şub | CV'ye eklenir — APK indirilebilir, README hazır |

Her kapı geçilmeden bir sonraki faza geçilmez; böylece yarım kalan çok özellik yerine çalışan az özellik olur.

**Faz 0 · Kurulum**

- [x] Araçları kur (bkz. son bölüm)
- [x] GitHub deposu ve klasör yapısı
- [x] Spring Boot projesi + Docker'da PostgreSQL
- [x] `GET /api/v1/health` çalışıyor

**Faz 1 · Backend MVP**

- [x] Flyway ile ilk şema (`V1__init.sql`) ve hazır hareketler
- [x] Entity, Repository, DTO sınıfları
- [x] Hareket, antrenman ve set endpoint'leri
- [x] Doğrulama ve ortak hata formatı
- [x] Swagger sayfası
- [x] Service katmanı birim testleri

**Faz 2 · Android MVP**

- [x] Android Studio'da Compose projesi, tema, alt menü
- [x] Retrofit + Hilt kurulumu
- [x] Antrenman listesi ekranı
- [x] Antrenman detayı ve set ekleme
- [x] Yükleniyor / hata durumları
- [ ] Gerçek telefonda deneme (şimdilik yalnızca emülatörde denendi)

**Faz 3 · Hesaplar ve ilerleme**

- [x] Spring Security + JWT, kayıt ve giriş
- [x] Android'de giriş ekranı, token'ı güvenli saklama (DataStore)
- [x] PR ve 1RM hesabı, `stats` endpoint'leri
- [x] İlerleme ekranı ve grafik
- [x] Testcontainers ile entegrasyon testleri

**Faz 4 · Yayın ve vitrin**

- [ ] Dockerfile ve sunucuya kurulum, HTTPS (Dockerfile + Caddy'li prod compose hazır; sunucu yok)
- [x] GitHub Actions ile CI
- [ ] İmzalı APK ve GitHub Release (imzalama hazır; yayın canlı API adresini bekliyor)
- [x] README, ekran görüntüleri (demo GIF henüz yok)
- [ ] İsteğe bağlı: dinlenme zamanlayıcısı ve şablonlar

## Test, Git ve kod kalitesi

Her özellik kendi branch'inde yazılır, testleri geçince `main`'e birleştirilir. GitHub'daki düzenli commit geçmişi, işverenin ilk baktığı şeylerden biri.

**Git düzeni:**

- `main` her zaman çalışan sürüm
- Her özellik için ayrı branch: `feature/add-sets`, `feature/jwt-auth`
- Küçük ve anlamlı commit'ler: `feat: add set creation endpoint`, `fix: reject negative weight`
- Her branch bir Pull Request ile birleştirilir (tek başına çalışsan da; PR açıklaması ne yaptığını anlatır)

**Backend testleri:**

- Birim testleri (JUnit 5 + Mockito): service katmanındaki hesaplar, örneğin PR ve 1RM
- Entegrasyon testleri (`@SpringBootTest` + Testcontainers): gerçek bir PostgreSQL ile API uçtan uca
- Hedef: service katmanında en az %70 test kapsamı

**Android testleri:**

- ViewModel birim testleri (sahte repository ile)
- En az bir Compose UI testi (antrenman ekleme akışı)

**Otomasyon (GitHub Actions):** Her push'ta backend testleri ve Android derlemesi otomatik çalışır; README'deki yeşil rozet bunu gösterir.

**Kod kalitesi:** Backend'de tutarlı biçim için Spotless, Android'de ktlint. Gizli bilgiler (veritabanı şifresi, JWT anahtarı) koda yazılmaz; ortam değişkeninden okunur ve `.env` dosyası `.gitignore`'a eklenir.

## Yayınlama (deploy)

Backend ve veritabanı bir bulut sunucusunda Docker ile çalışır; Android uygulaması APK olarak GitHub Releases'ta paylaşılır. Böylece işveren uygulamayı telefonuna indirip gerçek sunucuya bağlı hâlde deneyebilir.

**Backend için seçenekler** (fiyat ve ücretsiz kotalar değişebildiği için o gün tekrar kontrol edilecek):

| Seçenek | Artısı | Eksisi |
| --- | --- | --- |
| GitHub Student Developer Pack kredileriyle bir VPS | Öğrenci olarak ücretsiz kredi; tam kontrol, Linux pratiği | Sunucuyu kendin yönetirsin |
| Render / Railway gibi platformlar | Docker imajını verip çalıştırmak çok kolay | Ücretsiz planlar uyku moduna girebilir |
| Oracle Cloud ücretsiz katmanı | Sürekli ücretsiz sanal sunucu | Hesap açma süreci zahmetli olabilir |

**Yayınlama adımları:**

1. Backend için `Dockerfile` (çok aşamalı derleme: önce Maven ile derle, sonra küçük bir Java imajında çalıştır)
2. `docker-compose.yml` ile backend + PostgreSQL
3. Sunucuya kurulum, ortam değişkenleri, HTTPS (Caddy ile otomatik sertifika)
4. Android'de sunucu adresini `BuildConfig` ile ayarlama (geliştirme: `10.0.2.2`, yayın: gerçek adres)
5. İmzalı release APK oluşturup GitHub Releases'a yükleme
6. İsteğe bağlı: Google Play Console'da dahili test (tek seferlik geliştirici hesabı ücreti var)

## CV, GitHub ve mülakat hazırlığı

Proje bittiğinde CV'de iki satır, GitHub'da iyi bir README ve mülakatta anlatabileceğin beş karar olacak.

**CV satırı (İngilizce, örnek):**

> **LiftLog — Workout Tracking App** | Kotlin, Jetpack Compose, Spring Boot, PostgreSQL, Docker  
> Built a full-stack Android app for logging workouts and tracking strength progress, backed by a JWT-secured REST API deployed with Docker; added automated tests and CI with GitHub Actions.

**README'de olacaklar:**

- Kısa tanım + 3-4 ekran görüntüsü veya kısa bir GIF
- Mimari çizimi ve kullanılan teknolojiler
- Kurulum: `docker compose up` ile backend, Android Studio ile uygulama
- API dokümantasyonu linki (Swagger)
- APK indirme linki

**Mülakatta hazır olman gereken sorular:**

- [ ] Neden MVVM kullandın, her katman ne iş yapıyor?
- [ ] JWT nasıl çalışıyor, token nerede saklanıyor?
- [ ] Veritabanında neden `NUMERIC` kullandın, neden PR'ı ayrı tabloda tutmadın?
- [ ] Entity ve DTO neden ayrı?
- [ ] Docker ne işe yaradı, Docker olmadan nasıl çalıştırırdın?
- [ ] En çok nerede zorlandın, nasıl çözdün?
- [ ] Tekrar yapsan neyi farklı yapardın?

**Ek görünürlük:** Bitince LinkedIn'de kısa bir paylaşım ve Fiverr/Upwork profillerinde "mobil + backend" yeteneği olarak gösterme.

## Kurulum kontrol listesi ve ilk adım

İlk oturumda araçları kurup backend'den bir "merhaba" cevabı almayı hedefliyoruz.

**Kurulacaklar:**

- [x] JDK 21 (backend için)
- [x] IntelliJ IDEA Community (backend)
- [x] Android Studio (Kotlin ve emülatör dahil)
- [x] PostgreSQL (kuruluysa sürümünü kontrol et) + pgAdmin veya DBeaver
- [x] Docker Desktop
- [x] Git + GitHub hesabı
- [x] Postman veya Bruno (API denemek için)
- [ ] Telefonunda Geliştirici Seçenekleri ve USB hata ayıklama (gerçek cihazda denemek için)

**İlk oturumun adımları:**

1. GitHub'da `liftlog` deposunu oluştur, bilgisayarına klonla
2. start.spring.io'dan backend projesini oluştur (Web, Data JPA, PostgreSQL, Validation, Flyway)
3. `docker-compose.yml` ile sadece PostgreSQL'i ayağa kaldır
4. `GET /api/v1/health` yazıp tarayıcıda cevabı gör
5. İlk commit: `chore: initial backend setup`
