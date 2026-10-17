# LiftLog — İlerleme Günlüğü

## 2026-10-17 — Faz 0 → Faz 4 (tek oturum)

Behlül "her şeyi kur, GitHub'a bağla, komple bitir" dediği için bu oturumda kodu Claude yazdı.
Öğrenme açığını kapatmak için [docs/WALKTHROUGH.md](docs/WALKTHROUGH.md) yazıldı: her katmanı,
her kararın nedenini ve mülakat cevaplarını anlatıyor. **Sıradaki ilk iş bu dosyayı okumak.**

### Kurulum
- Kurulu araçlar: JDK 21, Git, Docker, GitHub CLI, yerel PostgreSQL 18. Bu oturumda winget ile
  IntelliJ IDEA, DBeaver, Bruno ve Android Studio kuruldu.
- Android SDK komut satırından `%LOCALAPPDATA%\Android\Sdk` altına kuruldu: platform 36 ve 37,
  build-tools 36.1, emulator, platform-tools ve `medium_phone` AVD'si (Android 36). Android Studio
  ilk açılışta bu SDK'yı bulmalı.
- Depo: <https://github.com/KedyliCali/liftlog> (**public**).

### Faz 0 · Kurulum: tamam ([#1](https://github.com/KedyliCali/liftlog/pull/1), birleştirildi)
- Spring Boot **4.1.1** iskeleti (Initializr artık 3.x sunmuyor). PostgreSQL 17 Docker'da, host
  portu 5433. `GET /api/v1/health`.

### Faz 1 · Backend MVP: tamam ([#2](https://github.com/KedyliCali/liftlog/pull/2))
- Flyway V1–V3, Entity/Repository/DTO, hareket, antrenman ve set uçları, `@Valid`, ortak `ApiError`, Swagger.
- Bulunan ve düzeltilen hata: doğrulama mesajları işletim sisteminin diline göre Türkçe dönüyordu → `locale: en`.

### Faz 2 · Android MVP: tamam ([#3](https://github.com/KedyliCali/liftlog/pull/3))
- Compose + MVVM + Hilt + Retrofit. Liste, detay ve set ekleme sayfası, hareket seçici, yükleniyor/hata/boş durumları.
- Bulunan ve düzeltilen hata: detaydan dönünce liste yenilenmiyordu → `onScreenResumed()`.

### Faz 3 · Hesaplar ve ilerleme: tamam ([#4](https://github.com/KedyliCali/liftlog/pull/4))
- JWT (Spring resource server + Nimbus), kayıt, giriş ve `me`. PR, ilerleme ve haftalık hacim
  istatistikleri, yeni PR bildirimi.
- Android'de giriş ekranı, DataStore'da oturum, İlerleme sekmesi (Vico grafikleri) ve Profil sekmesi.
- Testcontainers entegrasyon testleri.

### Faz 4 · Yayın ve vitrin: kısmen ([#5](https://github.com/KedyliCali/liftlog/pull/5))
- Çok aşamalı Dockerfile, `docker compose up` ile tam yığın, Caddy'li üretim compose dosyası.
- GitHub Actions CI. İlk çalışmada backend işi geçti; Android işinin sonucu PR'da.
- İmzalı release APK: anahtar deposu `android/keystore/` içinde, şifreler `android/keystore.properties`
  dosyasında. İkisi de git'e girmiyor. **İkisinin yedeğini al**: kaybolursa aynı uygulamaya güncelleme imzalanamaz.
- README (İngilizce, ekran görüntüleriyle) ve WALKTHROUGH.md (Türkçe).

### Doğrulama
- Backend: 37 test geçti (`./mvnw verify`); CI'da da geçti.
- Android: 20 ViewModel testi geçti, lint temiz (yalnızca `targetSdk` uyarısı bilerek bırakıldı).
- Emülatörde elle denendi: kayıt, giriş, çıkış, antrenman ve set ekleme, ilerleme grafikleri,
  koyu tema, R8 ile küçültülmüş release APK.
- Demo verisi: `python scripts/seed_demo_data.py`. Hesap bilgileri betiğin içinde (yalnızca yerel test hesabı).

### Yapılmayanlar / Behlül'ün kararı
- **PR'ları birleştir:** #2 → #3 → #4 → #5 sırayla. Her biri bir öncekinin üstüne kurulu.
  Otomatik onay sistemi incelemesiz birleştirmeye izin vermedi.
- **Sunucu:** hesap açma ve ödeme bilgisi gerektiği için Claude yapamaz. Seçenekler PLAN.md'nin
  "Yayınlama" bölümünde. Sunucu hazır olunca: `docker compose -f deploy/docker-compose.prod.yml up -d`,
  ardından `./gradlew assembleRelease -PliftlogApiUrl=https://<alan-adı>/` ve GitHub Release.
- Gerçek telefonda deneme (Geliştirici Seçenekleri + USB hata ayıklama telefonda açılmalı).
- En az bir Compose UI testi (plan istiyor, henüz yok). Demo GIF yok.
- v1.3 (dinlenme zamanlayıcısı, şablonlar) ve v2.0 (Room) başlanmadı.

**Sıradaki adım:** WALKTHROUGH.md'yi oku → PR'ları sırayla incele ve birleştir → sunucu seçimi.
