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

## 2026-10-18 — League of Legends takibi (branch `feature/lol-tracker`)

- PR #13 (makrolar + 315 yemek) ve #14 (spor stili) birleştirildi; canlıda doğrulandı.
- Behlül "sadece ben ve arkadaşlarım kullanacak" dedi → Riot'un **kişisel API anahtarı** buna uygun.
  Seçim: LiftLog'un içinde 5. sekme ("Oyun").
- **Backend V9:** `lol_players` (paylaşılan önbellek), `lol_tracked_players` (kim kimi takip ediyor),
  `lol_match_players` (maç başına oyuncu satırı). `RiotClient` arayüzü: gerçek HTTP (anahtar sadece
  sunucuda), sahte veri (`RIOT_FAKE=true`, yerel geliştirme), "ayarlı değil". Hız sınırlayıcı 18/sn ve
  90/2dk; oyuncu 10 dakikada bir yenileniyor, Riot cevap veremezse kayıtlı veri gösteriliyor.
- **Web:** Oyun sekmesi = arkadaş sıralaması (tier/kademe/LP), oyuncu ekleme (Riot ID + sunucu), oyuncu
  sayfası (kazanma oranı, KDA, CS/dk, son 10 maç şeridi, en çok oynanan 3 şampiyon, son maçlar). Resimler
  Riot'un Data Dragon CDN'inden; ulaşılamazsa baş harfler.
- Testlerin yakaladığı iki hata: yeni oyuncu kaydedilmeden maç satırları yazılıyordu (PUUID dışarıdan
  geldiği için `save()` merge yapıyordu → `Persistable`), Master+ LP 100'ü geçince sıralama karışıyordu.
- Doğrulama: backend 56 test, web 58 test, lint; yerelde sahte veriyle ekleme, bulunamayan ID, sıralama,
  oyuncu sayfası, yenileme; anahtarsız modda "anahtar eklenmedi" mesajı ve önbellekten liste.
- **Sıradaki adım (Behlül):** [docs/RIOT.md](docs/RIOT.md)'deki adımlarla kişisel anahtarı alıp Render'da
  `RIOT_API_KEY` olarak eklemek. Nick: Pesimist Fidan#2003.

## 2026-10-19 — Hesap kurtarma kodu (branch `feature/recovery-code`)

- Kalıcı Riot kişisel anahtarı başvurusu yapıldı ve anında onaylandı (Approved). Behlül Render'da
  `RIOT_API_KEY`'i değiştirdi; yeni deploy olmadığı için anahtar bir sonraki deploy'da devreye girecek.
- Behlül'ün sırası: kurtarma kodu → uyuma sorunu → League grup davet kodu. Tasarım iş akışında her özellik
  için üç bağımsız tasarım ve bir hakem kullanıldı; uyuma için ayrı araştırma yapıldı.
- **Kurtarma kodu:** kod, cihaz hesabının kendisi (`uuid.parola`, 76 karakter); geri yükleme sıradan bir giriş.
  "Yeni kod" = parola değişikliği (`PUT /api/v1/profile/password`, Android /auth altındaki çağrılarda token'ı
  sildiği için /profile altında). V10 `users.token_version`; her JWT `ver` taşıyor, eski sürümlü token'lar
  bir sonraki istekte 401 alıyor (claim'i olmayan eski token'lar 0 sayılıyor, deploy kimseyi atmadı).
- **Web:** hesabı olmayan cihazda karşılama ekranı (Başla / Kurtarma kodum var), kodu başka yerde değişen
  cihazda "Bu cihazdan çıkış yapıldı" ekranı (eskiden 409 hatasında takılıyordu), Profil'de kod kartı
  (göster, kopyala, paylaş, yeni kod, başka kod kullan), Antrenmanlar'da "kodunu kaydet" hatırlatıcısı.
  Yanıtı kaybolan "yeni kod" bekleyen parola olarak saklanıyor; geç gelen 401 yeni token'ı silmiyor.
- **İnceleme iş akışı** (4 açı + her bulguya şüpheci doğrulayıcı, 23 ajan): 8 bulgu doğrulandı, 11'i
  çürütüldü. Düzeltilenler: sunucuya ulaşıp yanıtı kaybolan yeni kodun sessizce benimsenmesi, sekmeler
  arası kontrolün hiç çalışmaması, boş kayıt başlatınca eski verinin görünmesi, çıkış ekranında yanlış
  mesaj. Ayrıca sayfa dili artık Türkçe: büyük harf başlıklar "İ"yi doğru yazıyor ("PROFİL").
- Elle bulunan hata: çıkış ekranında aynı hesabın yeni kodu "zaten bu cihazda" diye reddediliyordu
  (sadece e-posta karşılaştırılıyordu) → e-posta ve parola birlikte karşılaştırılıyor.
- Doğrulama: backend 66 test, web 87 test, lint, build; yerel Docker (V10) ile telefon boyutunda: eski
  token çalışıyor, karşılama → kodla geri yükleme, yeni kod → eski cihaz çıkış ekranı → yeni kodla dönüş.
- Kurtarma için yönetici yedeği (kod da cihaz da kaybolursa): Neon SQL'de pgcrypto ile yeni BCrypt parola
  ve `token_version + 1`, sonra arkadaşa `<uuid tiresiz>.<parola>` gönderilir.

## 2026-10-19 — Açılış hızı ve kalan iş (branch `feature/faster-startup`, PR #17)

- Behlül uyuma sorunu için "sadece hızlandır"ı seçti (ping yok: Render ekibi uyandırma isteklerini
  ücretsiz planın ruhuna aykırı buluyor, ayrıca sürekli açık API Neon'un aylık ücretsiz süresini bitirir).
- 0.1 CPU / 512 MB'ta ölçüm: açılış **178 sn → 96 sn** (`-XX:TieredStopAtLevel=1`) **→ 51 sn** (CDS
  arşivi, imaj oluşturulurken veritabanısız eğitim açılışıyla). Ayrıntılar `docs/DEPLOY.md`'de.
- League grup davet kodu tasarımı hazır, henüz yapılmadı: `docs/design/lol-groups.md` (PR #17'de).
  Kararlar: sadece üyeler, kişi başı tek grup.

**Sıradaki adımlar:**
1. PR #16 (kurtarma kodu) ve #17 (açılış hızı) CI yeşilse birleştir; ikisi de `README.md`'ye farklı
   satırlar ekliyor, çakışma beklenmiyor. Birleşince canlıda kontrol: V10 Neon'da, eski token'lar çalışıyor,
   Render loglarında açılış ~1 dakikanın altında, League sekmesi kalıcı Riot anahtarıyla çalışıyor.
2. iPhone'da: Profil → Kurtarma kodu → Kopyala, Notlar'a kaydet.
3. League grupları: `docs/design/lol-groups.md`'ye göre (V11, önce HttpRiotClient 404 düzeltmesi).

## 2026-10-19 — League arkadaş grupları (branch `feature/lol-groups`)

- PR #16 (kurtarma kodu) ve #17 (açılış hızı) Behlül'ün onayıyla birleştirildi ve canlıya çıktı. Canlı API
  uykudan **64 sn**'de uyandı (önceden ~3 dk), sonraki istekler ~0,15 sn.
- **Riot 404 düzeltmesi:** yanlış sunucuda seçilen Riot ID (TR1 yerine EUW1) 500 yerine "bulunamadı" (404);
  sessiz yenilemede kayıtlı veri gösteriliyor, elle yenilemede 503 RIOT_ERROR.
- **Yenileme kuralları:** oyuncu satırı `FOR NO KEY UPDATE SKIP LOCKED` ile kilitlenip yenileniyor (aynı oyuncu
  aynı anda iki kez yenilenmez), sayfa başına en fazla 4 oyuncu, takip/bağlama için 14 isteklik yedek.
- **V11 `lol_groups` + `lol_group_members`:** kişi başı tek grup (user_id birincil anahtar), sahip = en eski
  üye, 10 karakterlik davet kodu (49,5 bit, deneme sınırı gerekmiyor).
- **API** `/api/v1/lol/group`: oluştur, koda katıl, yeniden adlandır, yeni kod, kendi Riot hesabını bağla,
  ayrıl, üye çıkar (kod değişir). Kural ihlalleri 409 + `fieldErrors.group`, sahiplik 403.
- **Web:** League sekmesinde Grup / Takip geçişi (`?view=`), grup kartı + kenara taşan davet çipi (Paylaş →
  mesajın tamamı, yoksa kopyala), mesajın tamamını yapıştırarak katılma, "SEN" etiketli satır, ayarlar
  sayfası (ad, yeni kod, üye çıkar, ayrıl). Türkçe sayfada marka "LİFTLOG" görünüyordu → düzeltildi.
- İlk doğrulama (inceleme öncesi): backend 95 test (66'dan), web 102 test (87'den), lint, build.
  Yerel Docker + `RIOT_FAKE=true` ile telefon boyutunda: kur → bağla → ikinci kullanıcı küçük harf ve tireyle
  katılır → aynı tahta → arkadaşın sayfası grup üzerinden açılır → çıkar (kod değişir, eski kod 404) →
  yönetici ayrılır (yönetim geçer) → son üye ayrılır (grup silinir). Açık ve koyu tema kontrol edildi.
- **İnceleme iş akışı** (3 açı + her açı için şüpheci doğrulayıcı): 20 bulgu, 15'i doğrulandı, 5'i çürütüldü.
  Düzeltmeler ikinci bir iş akışında yapıldı (backend ve web paralel, her biri ayrıca şüpheciyle kontrol, 5 ek
  bulgu daha düzeltildi). Önemlileri: tek bozuk maç bütün tahtanın yenilenmesini durduruyordu; sunucu değiştiren
  oyuncu bir daha bağlanamıyordu; yeniden adlandırma eşzamanlı yenilemenin üstüne eski veriyi yazabiliyordu;
  web'de geç gelen eski istek yenilenmiş davet kodunu geri getirebiliyordu; ayarlar açıkken başarısız yenileme
  sayfayı siliyordu. Yeni testler mutasyonla denendi (düzeltme kaldırılınca ilgili test kırılıyor).
- Son durum: backend **116 test**, web **113 test**, lint, build; her ara commit tek başına derleniyor ve birim
  testlerini geçiyor.

**Sıradaki adımlar:**
1. PR'ı CI yeşil olunca birleştir (Behlül'ün onayıyla). Canlıda V11 Neon'a uygulanacak.
2. iPhone'da: League → Grup → Grup kur, kodu arkadaşlara WhatsApp'tan gönder; arkadaşlar LiftLog'u ana
   ekrandan açıp "Koda katıl"a mesajın tamamını yapıştırsın.
3. Canlıda League sekmesinin kalıcı Riot anahtarıyla çalıştığını telefondan kontrol et (bu oturumda
   tarayıcıda canlı hesap açılmadı).

## 2026-10-19 — Face ID ile yedek (passkey) (branch `feature/passkey`)

- PR #18 (League grupları) Behlül'ün onayıyla birleştirildi; canlıda 6 grup adresi açık, V11 uygulandı.
- Behlül kurtarma kodunu uğraştırıcı buldu; "veriler kaybolmasın" isteğiyle passkey seçildi (a seçeneği: dönen
  cihaz kendi gizli girişini alır, bir daha Face ID istenmez). Tasarım: `docs/design/passkey.md`.
- **Backend:** Yubico `webauthn-server-core` 2.10.1; V12 (passkeys, ceremonies, device_secrets, users.passkey_handle),
  V13 (device_secrets.last_used_at). Yeni kod passkey'leri ve cihaz anahtarlarını siler. Giriş uç noktalarında
  dakikada 30 deneme sınırı.
- **Web:** Profil'de Yedek kartı (Face ID ile yedekle / Yedeklendi), kurtarma kodu altında katlı; Antrenmanlar'da
  tek dokunuşla yedekleme bandı; açılışta "Face ID ile geri dön". Face ID seçenekleri dokunuştan önce hazırlanıyor
  (Safari şartı).
- İnceleme (kod inceleme ajanı): başka hesaba girme yolu bulunmadı; 4 orta, 5 düşük bulgu düzeltildi.
- Doğrulama: backend 144 test, web 159 test, lint, build. Yerel Docker'da tarayıcıda yazılım anahtar cihazıyla uçtan
  uca: yedekle → veriyi sil → Face ID ile dön (aynı hesap, League grubu dahil) → token silinince sessiz giriş →
  Face ID ile dönen cihazda kod yerine açıklama → Yeni kod passkey'i siler, kart sıfırlanır.

**Sıradaki adımlar:**
1. PR'ı CI yeşil olunca birleştir; canlıda V12/V13 ve passkey adresleri.
2. iPhone'da gerçek Face ID ile dene: Profil → Face ID ile yedekle; sonra Safari'de açıp "Face ID ile geri dön".
3. League: daha ayrıntılı istatistikler. 4. Antrenman: rutinler, tik atarak antrenman modu, ilerleme önerisi.
## 2026-10-19 — Ayrıntılı League istatistikleri (branch `feature/lol-stats`)

- Behlül "daha çok bilgi, tablo, istatistik" istedi. Tasarım: `docs/design/lol-stats.md`.
- **Backend:** V14 maç ayrıntıları (altın, görüş, seviye, alınan hasar, kill katılımı, hasar payı, en büyük seri,
  totem, eşyalar, büyüler, oyun modu) ve `lol_rank_history`. Eski maçlar yenilemede bütçe içinde tamamlanıyor.
  Özet: dakika başı ortalamalar, seri, roller, kuyruklar, tüm şampiyonlar, rekorlar; `GET .../rank-history`.
- **Web:** oyuncu sayfasında seri çipi, LP grafiği, 8 istatistik kutusu, roller, sıralanabilir şampiyon tablosu,
  kuyruklar, rekorlar, eşya/büyü ikonlu maç satırları; grup tahtasında "Tablo" karşılaştırması (`?board=table`).
- Doğrulama: backend 157 test, web 140 test (passkey ile birleşik halde 186); yerel Docker + sahte Riot ile
  tarayıcıda: yenileme sonrası 8 kutu, rekorlar, rozetler, eşya ikonları, grup tablosu.
- **Not:** passkey PR'ı (#19, V12/V13) önce birleşmeli; sonra bu dala main birleştirilip `endpoints.ts` import
  satırındaki küçük çakışma çözülecek.

## 2026-10-19 — Daha kolay antrenman girişi (branch `feature/workout-flow`)

- Behlül "antrenman girişi nasıl kolaylaşır" diye sordu; rutinler + tik atarak antrenman modu + ilerleme önerisi
  seçildi. Ardından "makine seçenekleri yok" dedi: hareket listesine makine/kablo hareketleri eklendi.
- **Backend:** V15 rutinler (`routines`, `routine_exercises`, `workouts.routine_id`), `/routines` CRUD, antrenmandan
  rutin, rutinle başlatma, `GET /workouts/{id}/plan` (Progression: tüm setler hedefe ulaştıysa +2,5 kg, vücut
  ağırlığında en düşük tekrar + 1, yoksa aynı ağırlık, ilk kez boş). V16 ekipman kolonu ve 46 yeni hareket.
- **Web:** Antrenmanlar'da "Rutinle başla" şeridi ve Rutinler sayfası; rutinli antrenmanda hazır doldurulmuş
  planlı setler, ± ayarlayıcılar, ✓ ile tek dokunuşta kayıt ve otomatik dinlenme sayacı; öneri etiketleri; "Rutin
  olarak kaydet"; seçicide "Makine" filtresi. Rutin telefonun kendi tarihiyle başlıyor (sunucu UTC).
- Doğrulama: backend 182 test, web 226 test, build. Yerel Docker ile tarayıcıda: antrenmandan rutin → şeritten başlat →
  "↑ 2,5 kg" ve 62,5 kg × 8 hazır satırlar → ✓ ile set + rekor bildirimi + dinlenme sayacı; Makine filtresi.

**Sıradaki adımlar:**
1. PR'ları sırayla birleştir: #19 (passkey, V12/V13) → #20 (League, V14) → #21 (antrenman, V15/V16). Dallar üst üste
   kurulu; her biri bir öncekini içeriyor.
2. Canlıda kontrol: migration'lar, Face ID, League sayfası, rutinler.

## 2026-10-19 — Barkodla ekleme ve dünü kopyala (branch `feature/nutrition-quick`)

- **Backend:** `POST /api/v1/nutrition/days/{date}/copy-from/{sourceDate}` önceki günün kayıtlarını tek işlemde
  (hepsi ya da hiçbiri) kopyalar ve yeni kayıtları döner; aynı gün 400. Migration yok: barkod sunucuda saklanmıyor,
  kayıt modeli (ad + kcal + makrolar) değişmedi.
- **Web:** Yiyecek ekle'de "Barkod okut": arka kamera, çerçeveli vizör, destekleniyorsa fener, kapat; kamera izni
  yoksa ya da kamera yoksa kodu elle yazma. iOS Safari'de BarcodeDetector olmadığı için çözümleme ZXing
  (`@zxing/browser` 0.2.1, `@zxing/library` 0.23.0) ile JavaScript'te; kütüphane yalnızca tarayıcı açılınca yükleniyor.
  Kod Open Food Facts'te aranıyor (kcal yoksa kJ/4,184), bulunan ürün katalog yiyeceği gibi porsiyon/gram adımına
  gidiyor ve localStorage'da önbelleğe alınıyor (tekrar okutma çevrimdışı çalışır). Bulunamazsa "Bu ürün bulunamadı;
  elle ekleyebilirsin" ile elle girişe, varsa adla.
- Beslenme sayfası: gün boşken "Dünü kopyala" kartı, ayrıca sağ üstteki ⋮ menüsünde; bildirimde sayı ve
  "Geri al" (yalnızca kopyalanan kayıtları siler).
- Doğrulama: backend 183 test (`./mvnw verify`), web 255 test, tsc, lint, build. Tarayıcıda görsel kontrol yalnızca
  stiller için yapıldı (kamera, önizleme panelinde engelli).

**Sıradaki adımlar:**
1. iPhone'da ana ekrandaki uygulamadan gerçek bir paket okut: izin istemi, odak ve fener davranışını gör.
2. İstenirse barkodu kayıtla birlikte saklamak için V18 migration'ı (`food_entries.barcode`).

## 2026-10-19 — Antrenman özeti + haftalık seri (branch `feature/workout-summary`)

- **Backend:** `GET /api/v1/workouts/{id}/summary` (sahibi değilse 404): toplam hacim, set, hareket, bu antrenmanda
  kırılan rekorlar (o ana kadarki en ağır set ya da en iyi tahmini 1RM; hareketin ilk kez yapılışı rekor sayılmaz,
  hareket başına en güçlü set), karşılaştırma (aynı rutinin önceki antrenmanı, yoksa birebir aynı hareketlerle
  yapılan önceki antrenman) ve haftalık seri. `GET /api/v1/stats/streak` → `{weeks, thisWeekWorkouts, longestWeeks}`:
  ISO haftası (pazartesi başlar); bu hafta antrenman yoksa sayım geçen haftadan başlar, pazartesi sabahı 0'a düşmez.
  Saf mantık `WeekStreak` ve `WorkoutRecap` sınıflarında, birim testli. Migration gerekmedi (V17 yok).
- **Web:** Antrenman sayfasında "Antrenmanı bitir" (⋮ menüsünde ve set varsa listenin altında) → özet sayfası:
  "Bugün 4.015 kg kaldırdın", "geçen seferden +1.330 kg" etiketi, set/hareket kutuları, seri çipi, rekor listesi,
  Paylaş (navigator.share, yoksa panoya kopyalama). Antrenmanlar başlığında katmanlı "4 HAFTA SERİ" çipi (alevli SVG
  ikon, "bu hafta 1×" etiketi). Not: League sayfasında zaten `.streak-chip` sınıfı vardı; yeni çip `.week-streak`.
- Süre gösterilmiyor: antrenmanın bitiş zamanı tutulmuyor.
- Doğrulama: backend 203 test (`mvnw verify`), web 242 test, tsc, lint, build. Yerel Postgres + backend + Vite ile
  tarayıcıda (375 px): seri çipi başlıkta, bitir butonu, özet sayfası gerçek API verisiyle.

**Sıradaki adımlar:**
1. PR aç ve birleştir; canlıda özet ve seri uçlarını kontrol et.
2. Android'e aynı özet ve seri (isteğe bağlı).

## 2026-10-19 — Arkadaşlarla antrenman karşılaştırma (branch `feature/friend-training`)
- **Backend:** V20 `users.share_training` (varsayılan kapalı). `PUT /api/v1/profile/share-training {enabled}`,
  profil yanıtında `shareTraining`. `GET /api/v1/group/training?days=7|30[&today=]`: League grubunun (aynı grup)
  paylaşan üyeleri için antrenman sayısı, hacim, set, haftalık seri ve 4 büyük hareketin tüm zamanların en iyi
  tahmini 1RM'i. Çağıran kendini her zaman görür (`shares` bayrağıyla); paylaşmayanlar yalnızca
  `{displayName, shares:false}`. Grupta değilse 204. Yalnızca hazır (built-in) hareketler sayılır.
- **Web:** Grup kartının altında League / Spor anahtarı (`?view=sport`). 7/30 gün tablosu, sütuna dokununca sıralama,
  her sütunun lideri lime. Paylaşmayanlar gri "paylaşmıyor" çipleri. Paylaşmadıysan neyin paylaşıldığını anlatan
  kart + Paylaş anahtarı; Profil'de de anahtar.
- Doğrulama: backend `mvnw verify` 223 test, web 338 test, tsc, lint, build. Tarayıcıda görsel kontrol yapılmadı.

**Sıradaki adımlar:**
1. Tarayıcıda (375 px) Spor görünümünü gerçek veriyle kontrol et, PR aç.
2. Android'e aynı görünüm (isteğe bağlı).

## 2026-10-19 — Kilo takibi (branch `feature/weight-log`)

- **Backend:** V18 `weight_entries` (kullanıcı başına günde bir kayıt, `UNIQUE(user_id, entry_date)`, 20–400 kg
  CHECK, kullanıcı silinince CASCADE). Profilde kilosu olan her kullanıcı için katılım tarihli bir kayıt aktarıldı.
  `GET /api/v1/weight?days=30|90|365&today=` → `{entries, latest, change7d, change30d, average7d}`;
  `PUT /api/v1/weight/{date}` `{weightKg}` (aynı gün tekrar kaydedilirse üzerine yazar; yarından sonrası 400);
  `DELETE /api/v1/weight/{date}` (yoksa 404). Trend saf `WeightTrend` sınıfında, birim testli: değişimler bugünden
  değil son tartıdan ölçülür; 7 günlük değişim 7–14 gün önceki en yeni kayıtla, 30 günlük 30–60 gün öncekiyle
  karşılaştırılır (daha eskisi yanıltıcı olur, null döner).
- **Profil senkronu:** en yeni kayıt değişince `users.body_weight_kg` da ona eşitlenir (BMI ve vücut ağırlığı
  hareketleri bozulmasın). Son kayıt silinirse profil kilosu olduğu gibi kalır. Profilde kilo *değişirse* bugünün
  kaydı yazılır (aynı kiloyla kaydetmek bugünkü tartıyı ezmez). 400 kg üstü (profil eski istemciler için 500'e izin
  veriyor) profile yazılır ama kayda geçmez.
- **Web:** Profil'de "Kilo" kartı: son kilo (lime blok, "BUGÜN" etiketi), "7 günde −0,5 kg" / "30 günde" çipleri,
  7 gün ortalaması, ±0,1 / ±0,5 adımlı hızlı giriş, Kaydet/Güncelle, bugünkü kaydı silme, 30/90/365 gün çizgi
  grafiği (günlük tartı gri, 7 günlük hareketli ortalama lime). Beslenme başlığında "KİLO 81,3 kg" çipi
  `/profile#weight`'e götürür ve kartı görünür yapar.
- Doğrulama: backend 230 test (`mvnw verify`, 5 yeni entegrasyon + 6 birim), web 348 test, tsc, lint, build.
  Geçici Postgres + backend + Vite ile tarayıcıda (375 px): kaydetme, profil kilosu/VKİ güncellemesi, çip linki.

**Sıradaki adımlar:**
1. PR aç ve birleştir; canlıda V18'in backfill'ini kontrol et.
2. Android'e kilo kaydı (isteğe bağlı); Android profil doğrulaması hâlâ 500 kg'a izin veriyor.

## 2026-10-19 — Verilerini indir (branch `feature/data-export`)

- **Backend:** `GET /api/v1/export/workouts.csv` (set başına bir satır: tarih, not, hareket, set, ağırlık, tekrar, RPE,
  vücut ağırlığı bayrağı, tahmini 1RM), `/nutrition.csv` (tarih, yemek, kcal, protein, karbonhidrat, yağ) ve `/all.zip`
  (iki CSV + `profile.json`: ad, boy, kilo, hedefler; e-posta/şifre/token asla). `CsvWriter`: UTF-8 BOM, `;` ayraç,
  ondalık virgül, RFC 4180 tırnaklama, `=+-@` ile başlayan metne `'` (CSV injection). Satırlar `Stream` + fetch size ile
  doğrudan yanıta akıyor; kullanıcı kimliği token'dan. CORS `Content-Disposition` başlığını açıyor. Migration yok.
- **Web:** Profil → "Verilerini indir" kartı (Antrenmanlar CSV, Beslenme CSV, Hepsi ZIP). `requestFile` token'la indirir;
  `saveFile` iPhone/iPad'de `navigator.share({files})`, diğerlerinde `<a download>`. Safari dokunma süresi dolarsa
  (`NotAllowedError`) kartta "Kaydet" butonu çıkar.
- Beslenme CSV'sinde **gram sütunu yok**: `food_entries` porsiyon gramını saklamıyor (migration istenmedi).
- Doğrulama: `mvnw verify` (CsvWriterTest 13, ExportIntegrationTest 6), web 344 test, tsc, lint, build. Yerel Postgres +
  backend + Vite ile tarayıcıda (375 px): kart, CSV içeriği ve ZIP indirme gerçek API ile.

**Sıradaki adımlar:**
1. PR aç; gerçek iPhone'da (Safari + ana ekran uygulaması) paylaşım sayfasını dene.
2. Gram isteniyorsa `food_entries.grams` için migration (ayrı iş).

## 2026-10-19 — Dört özellik canlıda (PR #27–#30)

- #27 internetsiz set kaydı, #28 CSV/ZIP dışa aktarma, #29 kilo günlüğü (V18), #30 grup arkadaşlarıyla antrenman
  karşılaştırma (V20, isteğe bağlı) sırayla main'e birleşti. Zincir birlikte doğrulandı: backend 253 test, web 396
  test, lint, tsc, build; Docker'da V20'ye geçiş, tarayıcıda kilo kaydı, ZIP indirme, Spor görünümü.
- Canlı: Render'da API ve web yeni sürümde; yeni uç noktalar api-docs'ta ve oturumsuz 401 dönüyor (Neon V20'de).
- İptal edilenler: Türkçe hareket isimleri, hatırlatma bildirimi. Android kullanılmıyor.

**Sıradaki adımlar:**
1. LoL istatistiklerini detaylandırma (maç detayı, erken oyun farkları, rakip analizi, örüntüler, grafikler):
   Behlül'ün seçimini bekliyor.
2. Kullanıcı işleri: Render ücretli plan (uyku gecikmesi); Safari ve ana ekran hesabını Face ID ile birleştirme.

## 2026-10-19 — LoL detaylı istatistikler (branch `feature/lol-deep-stats`)

- **Backend:** V21 ile maçlar artık bütün olarak saklanıyor: `lol_matches`, `lol_match_teams` (hedefler, banlar),
  `lol_match_participants` (10 oyuncu) ve `lol_match_frames` (zaman çizelgesi; maç sayfası ilk açılınca Riot'tan bir
  kez çekilir). Oyuncu başına 100 maç tutuluyor (yenileme başına hâlâ en fazla 10 indirme). Eski maçlar boş kalan
  bütçeyle yeniden indiriliyor. Yeni uç noktalar: `GET /lol/players/{puuid}/stats?queue=&games=&tzOffsetMinutes=`,
  `GET /lol/matches/{id}`, `GET /lol/matches/{id}/timeline`, `GET /lol/group/duos`.
- **Web:** oyuncu sayfası sekmeli (Genel, Şampiyonlar, Duo, Rakipler, Build, Maçlar), kuyruk ve 20/50/100 filtreleri,
  duo tablosu ve "Takip et", koridor rakipleri, karşılaşılan/yanında oynanan şampiyonlar, eşya/rün/büyü/yama,
  taraf/süre/saat/gün kazanma oranları, eğilim grafiği. Maç sayfası: iki takımın skor tablosu, hedefler, banlar,
  altın farkı grafiği, 10. ve 15. dakika koridor farkları.
- Duo, Riot'un hazır duo bilgisi değil: en az 2 maçta aynı takımda olunan kişiler.
- Doğrulama: backend 284 test (`mvnw verify`), web 424 test, tsc, lint, build. Docker'da V21, sahte Riot verisiyle
  375 px'te tüm sekmeler ve maç sayfası.
- Docker Desktop bu bilgisayarda kapanınca silinemeyen soket dosyaları bırakıyor (`Docker\run`,
  `docker-secrets-engine`); çözüm iki klasörü yeniden adlandırıp Docker'ı başlatmak.

**Sıradaki adımlar:**
1. PR'ı birleştir; canlıda V21'i ve gerçek Riot verisiyle duo/maç sayfasını kontrol et.
2. #31 (silme onayı) birleştirme onayı bekliyor.
