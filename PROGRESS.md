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

## 2026-10-17 (akşam) — Yayın ve cila (yarım kaldı)

**Yayın:** Oracle kartı reddetti → **Render + Neon** (kart yok) seçildi.
- `render.yaml`, PORT/512 MB uyumu, uyanma ipucu ve uzun zaman aşımı → PR [#6](https://github.com/KedyliCali/liftlog/pull/6) **birleşti**.
- Behlül'de kalan: Neon projesi (Frankfurt, yalnızca Postgres açık) → Render Blueprint. Adımlar `docs/DEPLOY.md`'de.
  Neon'dan **sunucu adı + kullanıcı adı** gelecek (şifre değil), sonra Render'daki 3 alan doldurulacak.

**Cila + iPhone** (branch `feature/app-polish`):
- [x] Otomatik cihaz hesabı: giriş ekranı yok (Android + web)
- [x] Profil: isim, boy, kilo, VKİ, fotoğraf (backend V4, Android ekranı, web sayfası)
- [x] **iPhone için web uygulaması (PWA)**, `web/`: React + TypeScript + Vite. Safari → Ana Ekrana Ekle
- [x] Dinlenme zamanlayıcısı, kaydırarak silme + geri al, şablon (eski antrenmanı tekrarla), PR konfetisi (web)
- [x] Backend CORS (`CORS_ALLOWED_ORIGINS`), Render statik sitesi (`render.yaml`), CI'da web işi
- Doğrulama: backend 41 test, Android 21 test, web 17 test geçti; web tarayıcıda telefon boyutunda
  uçtan uca denendi (oluşturma, set, PR, zamanlayıcı, kaydırma/geri al, şablon, profil, fotoğraf).
  Android profil düzenleme emülatörde denendi; Android'de fotoğraf seçici denenmedi.
- Android'e zamanlayıcı/kaydırma/şablon **eklenmedi** (iPhone kullanıldığı için web'e öncelik verildi).
- Not: Docker Desktop açılışta çöküyordu (kalmış soket dosyaları). `%LOCALAPPDATA%\Docker\run` ve
  `docker-secrets-engine` klasörlerinin adı `.bozuk*` yapılarak çözüldü; bu klasörler silinebilir.

## 2026-10-18 — Canlıya çıktı

- PR #7 birleşti. Neon projesi `liftlog` (Frankfurt, PostgreSQL 18), Render Blueprint `liftlog`.
- **Web (iPhone):** <https://liftlog-web.onrender.com>
- **API:** <https://liftlog-api-evn4.onrender.com> (Swagger: `/swagger-ui.html`). Render, `liftlog-api`
  adı dolu olduğu için `-evn4` ekledi; web'in `VITE_API_URL` değeri buna göre düzeltildi.
- Canlıda doğrulandı: sağlık 200, CORS yalnızca web adresine izin veriyor, cihaz hesabı, antrenman ve set
  yazma, PR tespiti, profil, doğrudan alt sayfa açma, service worker.
- Neon araçları: `neon` + `neon-postgres` skill'leri ve proje düzeyinde OAuth'lu MCP (`.mcp.json`).
- Testten kalan veri: canlıda bir test cihaz hesabı (1 antrenman, 2 set) ve `smoke-…@liftlog.test` hesabı var; zararsız.
- Küçük kusur: `manifest.webmanifest` `binary/octet-stream` türüyle sunuluyor (iPhone kurulumunu etkilemiyor).

**Sıradaki adım:** iPhone'da Safari ile web adresini aç → Paylaş → Ana Ekrana Ekle. Android tarafında
zamanlayıcı/kaydırma/şablon eksik; release APK'yı canlı API adresiyle derlemek.

## 2026-10-18 — Kalistenik + web cilası (branch `feature/calisthenics-polish`)

- **Backend V5:** `exercises.bodyweight` sütunu; Pull-up ve Dip işaretlendi, 14 yeni hareket eklendi
  (Chin-up, Muscle-up, Inverted Row, Push-up, Pike/Handstand Push-up, Pistol/Bulgarian Split Squat,
  Hanging Leg Raise, L-sit, Weighted Pull-up/Chin-up/Dip/Push-up).
- Kural: sette yalnızca **ek ağırlık** saklanır; hacim, PR ve e1RM sorgularında profildeki vücut ağırlığı
  eklenir (`weight + case when bodyweight then body_weight_kg`). Kilo değişirse eski setler yeni kiloyla
  hesaplanır (bilinçli sadeleştirme).
- **Web:** kas grubuna göre gruplu seçici + "Kalistenik" filtresi, "Ek ağırlık (kg)" alanı ve ipucu,
  satırlarda "VA + 10 kg", sunucunun `totalVolumeKg` değeri, PR kartında ek yük + toplam.
- **Cila:** View Transitions (kart → detay morfu), yay animasyonlu alt menü hapı, içerik şeklinde iskelet
  yükleyiciler, sayarak artan toplamlar, kas grubu renkleri, başlık parıltısı, basınca yaylanan düğmeler,
  set kaydında çizilen onay işareti, PR köşe etiketi.
- Doğrulama: backend 42 test, web 27 test, lint ve build temiz; yerel Docker API (V5 uygulandı) ile telefon
  boyutunda uçtan uca denendi (ağırlıklı barfiks, hacim 1.279,5 → 2.815,5 kg elle doğrulandı, PR, iskeletler,
  açık/koyu tema).
- Android bu alanları yok sayıyor (bilinmeyen JSON alanları); vücut ağırlığı gösterimi Android'e eklenmedi.
