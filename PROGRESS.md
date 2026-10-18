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

## 2026-10-18 — Sekmeler arası kaydırma + toplu set (branch `feature/tab-swipe`)

- Sekme sayfalarında sola/sağa kaydırınca yan sekmeye geçiliyor; geçiş yön bilgili kayma animasyonu
  (menüye dokununca da aynı). Kaydırarak silme satırları, çip satırları, grafikler, formlar ve açık
  sayfalar hareketi kendisi kullanıyor, sekme değiştirmiyor. Eşik mantığı `lib/tabSwipe.ts` (testli).
- Set ekle sayfasında "Set sayısı" (1–10): aynı ağırlık ve tekrarla N set tek seferde kaydediliyor
  (sırayla N istek; PR kontrolü elle eklemekle aynı).
- Not: tarayıcı paneli gizliyken animasyon kareleri durduğu için view transition'lar takılıyor; test
  ederken panel görünür olmalı. Gerçek telefonda sorun yok.
- Doğrulama: web 33 test, lint, tsc; telefon boyutunda kaydırma (iki uç, kart üstü, çapraz) ve 4×8 şınav denendi.

## 2026-10-18 — Beslenme günlüğü (branch `feature/nutrition-log`)

- PR #10 (kaydırma + toplu set) CI yeşil olunca birleştirildi.
- **Backend V6:** `food_entries` tablosu (gün, ad, kcal, protein) ve `users` tablosuna günlük kalori/protein
  hedefi. Uçlar: gün özeti, yemek ekle/sil, sık yenenler, hedefler. Sık yenenler ayrı tabloda tutulmuyor;
  son 60 günün kayıtlarından `GROUP BY` ile hesaplanıyor (PR'lar gibi).
- **Web:** yeni "Beslenme" sekmesi (alt menüde 2. sırada, kaydırmayla da açılıyor). Büyük kalori halkası ve
  onun sağ altına binen protein halkası; hedef aşılınca kırmızı. Gün değiştirici (geleceğe gidilmiyor),
  "Yine ye" çipleri (tek dokunuş + geri al), kaydırarak silme + geri al, hedef sayfasında kiloya göre
  protein önerisi (1,8 g/kg).
- Doğrulama: backend 44 test, web 41 test, lint, build; yerel Docker (V6 uygulandı) ile telefon boyutunda
  hedef belirleme, yemek ekleme, hızlı ekleme + geri al, önceki gün, açık/koyu tema denendi.
- Android beslenme ekranı yok.

## 2026-10-18 — Hazır yemek listesi (branch `feature/food-catalog`)

- PR #11 (beslenme günlüğü) CI yeşil olunca birleştirildi; canlıda Beslenme sekmesi açıldı, Neon'da V6 uygulandı.
- **Backend V7:** `foods` tablosu, 105 yaygın Türk yemeği ve temel besin (9 kategori). Her biri için 100 g
  başına kcal/protein ve tipik porsiyon (örn. "1 kase = 250 g"). Değerler yaklaşık; yaygın besin tablolarından
  yuvarlandı. `GET /api/v1/foods` hepsini döndürüyor (1 gün önbellek).
- **Web:** "Yemek ekle" artık önce arama: Türkçe harf duyarsız ("corba" → "çorba"), İngilizce adla da
  bulunuyor, boş aramada kategorilere göre liste. Seçince porsiyon (½ adım) veya gram; kcal/protein canlı
  hesaplanıyor. Kayıt adı miktarı içeriyor ("Mercimek çorbası (1,5 × 1 kase)"). "Elle gir" eski formu açıyor.
- Doğrulama: backend 45 test, web 49 test, lint, build; yerel Docker (V7) ile telefon boyutunda arama,
  porsiyon/gram hesapları ve kayıt elle doğrulandı.

## 2026-10-18 — Daha çok yemek + karbonhidrat/yağ (branch `feature/macros-more-foods`)

- PR #12 (hazır yemek listesi) birleştirildi; canlıda arama çalışıyor, Neon'da V7 uygulandı.
- **Backend V8:** `foods` ve `food_entries` tablolarına karbonhidrat ve yağ. Mevcut 105 yemeğe değerler
  eklendi, 210 yeni yemek: kahvaltılıklar, ev yemekleri, sokak lezzetleri/fast food, balıklar, süt ürünleri,
  tahıllar, sebze, meyve, kuruyemiş, tatlı/atıştırmalık, içecekler (toplam 315, 11 kategori).
  Entegrasyon testi her yemeğin kalorisini makrolarla karşılaştırıyor (4/4/9 kcal/g, %20 tolerans).
- **Web:** yemek satırlarında P/K/Y, halkaların altında kalorinin kaynağını gösteren çubuk, porsiyon
  önizlemesinde K/Y, elle girişte isteğe bağlı karbonhidrat ve yağ. Eski kayıtlarda bu değerler boş kalıyor.
- Bulunan hata: katalog 1 gün tarayıcı önbelleğinde tutulduğu için yeni yemekler görünmüyordu → istek
  adresine uygulama sürümü eklendi (`/foods?v=1.3.0`), her sürümde liste tazeleniyor.
- Doğrulama: backend 45 test, web 51 test, lint; yerel Docker (V8) ile telefon boyutunda arama, kokoreç
  ekleme, makro çubuğu yüzdeleri elle doğrulandı.

## 2026-10-18 — Yeni stil: "spor ekipmanı" (branch `feature/sport-style`, #13'ün üstünde)

- Behlül "AI gibi durmasın" dedi; seçenekler arasından öneri istedi → Strava/Whoop tarzı önerildi ve yapıldı.
- Neredeyse siyah zemin, tek sinyal rengi lime (#C8FF2E; açık temada okunması için zeytin yeşili),
  ince çerçeveli düz paneller, köşeler 4–12 px, başlık ve sayılarda Barlow Condensed (büyük harf),
  gövdede Barlow. Fontlar npm paketiyle uygulamaya gömülü (PWA çevrimdışı çalışsın, Google'a istek gitmesin);
  kullanılmayan kalınlıklar ve Latin dışı alt kümeler önbelleğe alınmıyor.
- Kaldırılanlar: başlık arkasındaki parıltı, turuncu blok, sıcak gradyanlı PR etiketi, tüm emojiler
  (yerine tek tip çizgi SVG ikon seti `components/Icons.tsx`).
- Alt menüde aktif sekmeyi gösteren hap yerine üst kenarda kayan lime çizgi. Avatar yuvarlatılmış kare.
- Düzeltilen iki stil hatası: `.ring-label span` ve `.stat-tile span` kuralları CountUp sayısını da
  küçültüyordu → doğrudan çocukla sınırlandı.
- Doğrulama: web 51 test, lint, build; telefon boyutunda dört sekme + detay, açık ve koyu tema ekran
  görüntüsüyle kontrol edildi.
- Not: Android uygulamasının teması hâlâ eski turuncu tasarımda.
