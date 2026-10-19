# LiftLog — Kod Rehberi (mülakat hazırlığı)

Bu belge projeyi baştan sona okuman için bir yol haritası. Her bölümde **ne var**, **neden öyle
yapıldı** ve **mülakatta nasıl anlatırsın** yazıyor. Önerim: dosyaları burada verilen sırayla aç,
her birini okuduktan sonra kendine "bunu silsem ne bozulur?" diye sor.

---

## 1. Büyük resim

İki ayrı uygulama var ve tek bağları JSON taşıyan bir REST API:

```
Telefon (Kotlin/Compose)  ──HTTPS + JSON + JWT──▶  Sunucu (Spring Boot)  ──▶  PostgreSQL
```

Her iki tarafta da aynı fikir var: **katmanlar**. Her katman yalnızca bir alttakini bilir.
Böylece bir katmanı değiştirdiğinde (ör. veritabanı, ağ kütüphanesi) diğerleri etkilenmez.

| Backend | Android | Görevi |
| --- | --- | --- |
| Controller | Screen (Composable) | Dış dünyayla konuşur (HTTP isteği / kullanıcı dokunuşu) |
| Service | ViewModel | Mantık ve durum |
| Repository | Repository | Veriyi nereden alacağını bilir |
| Entity + DB | Retrofit + API | Asıl veri kaynağı |

---

## 2. Backend — okuma sırası

### 2.1 Veritabanı: `backend/src/main/resources/db/migration/`
- `V1__init.sql`: dört tablo + index'ler. **Flyway** bu dosyaları sırayla bir kez çalıştırır ve
  hangisini çalıştırdığını `flyway_schema_history` tablosunda tutar. Şemayı kodla birlikte
  sürümlemek böyle olur. Hibernate'e tablo yaptırmıyoruz (`ddl-auto: validate`); o yalnızca
  Entity'lerin şemaya uyduğunu kontrol ediyor.
- `CHECK (reps > 0)`, `CHECK (weight_kg >= 0)`: kötü veri uygulamayı atlasa bile veritabanı reddeder.
- `NUMERIC(6,2)`: 2.5 kg gibi değerler `FLOAT`'ta tam saklanamaz (ikili sistemde yuvarlanır).
- `V2`: hazır hareketler (`user_id = NULL` = herkese açık). `V3`: MVP'deki demo kullanıcı.

**Mülakat sorusu:** "Şemayı neden Hibernate'e oluşturtmadın?" → Üretimde şemanın ne zaman,
nasıl değiştiğini kontrol etmek isterim; Flyway ile her değişiklik bir dosya, git'te görünür ve
her ortamda aynı sırayla uygulanır.

### 2.2 Entity'ler: `entity/`
Tabloların Java karşılığı. Dikkat edilecekler:
- `@ManyToOne(fetch = LAZY)`: ilişkili nesne ancak gerektiğinde yüklenir.
- `Workout.addSet()` ve `removeSet()`: ilişkinin iki ucunu birlikte günceller. `orphanRemoval = true`
  sayesinde listeden çıkarılan set veritabanından da silinir.
- `nextSetOrder()`: set sırası iş kuralı olduğu için Entity'de.

### 2.3 Repository'ler: `repository/`
Spring Data JPA, metot isminden sorgu üretir (`findByIdAndUserId`). Karmaşık olanlar `@Query`:
- `findDetailByIdAndUserId`: **`join fetch`** ile antrenman + setler + hareketler **tek sorguda**
  gelir. Yoksa her set için ayrı sorgu atılırdı (**N+1 problemi**: mülakatta çok sorulur).
- `findSummariesByUserId`: liste kartlarındaki toplamları (set sayısı, hacim) veritabanında
  hesaplar ve doğrudan DTO üretir (JPQL constructor expression).
- `StatsRepository.findWeeklyVolume`: PostgreSQL'e özel `date_trunc('week', ...)` kullandığı
  için **native query**. Haftalar Pazartesi başlar.

### 2.4 DTO'lar: `dto/`
API'ye giren ve çıkan veri. Java `record` kullandık (değiştirilemez, kısa).
**Neden Entity'yi doğrudan döndürmüyoruz?** (1) Veritabanını değiştirince API sözleşmesi
bozulmasın. (2) `password_hash` gibi alanlar yanlışlıkla dışarı sızmasın. (3) Lazy ilişkiler
JSON'a çevrilirken istenmeyen sorgular veya hatalar çıkmasın.

`SetRequest`'teki `@Min`, `@DecimalMax` gibi notasyonlar **Bean Validation**. Controller'da
`@Valid` olunca kurala uymayan istek service'e hiç ulaşmaz ve 400 döner.

### 2.5 Hata yönetimi: `exception/GlobalExceptionHandler.java`
`@RestControllerAdvice` bütün controller'ların hatalarını tek yerde yakalar ve hep aynı JSON
şeklini (`ApiError`) döner. Android tarafı bu şekli bildiği için hatayı düzgün gösterebiliyor.

### 2.6 Service'ler: `service/`
- `WorkoutService`: her sorgu **mevcut kullanıcıyla sınırlı**. Başkasının antrenmanını
  istersen "bulunamadı" (404) dönüyor, "yasak" (403) değil. Böylece o ID'nin var olduğu bile anlaşılmıyor.
- `@Transactional`: metot içindeki tüm veritabanı işleri ya hep birlikte olur ya hiç olmaz.
  `readOnly = true` okuma metotlarında Hibernate'in gereksiz kontrolleri atlamasını sağlar.
- `OneRepMax`: Epley formülü `ağırlık × (1 + tekrar / 30)`. Saf bir fonksiyon olduğu için test etmesi çok kolay.
- `StatsService`: PR'lar **tabloda saklanmıyor, her istekte setlerden hesaplanıyor**. Bir seti
  silince eski bir PR yanlış biçimde kalamaz (veri tekrarı yok).
- `addSet` yeni setin önceki en iyi tahmini 1RM'i geçip geçmediğine bakıyor ve `newPersonalRecord` döndürüyor.

### 2.7 Güvenlik: `security/`
Akış:
1. `POST /auth/register` → şifre **BCrypt** ile hash'lenir (geri çevrilemez, her seferinde farklı "salt").
2. `JwtService` bir **JWT** üretir. Üç parçası var: başlık, içerik (`sub` = kullanıcı id, `exp`
   = bitiş zamanı) ve imza. İmza `JWT_SECRET` ile HMAC-SHA256 hesaplanıyor.
3. Telefon sonraki her istekte `Authorization: Bearer <token>` gönderir.
4. Spring Security'nin **resource server** filtresi imzayı ve süreyi doğrular, kullanıcıyı
   `SecurityContext`'e koyar. Biz filtre yazmadık; Spring'in hazır ve test edilmiş filtresini kullandık.
5. `JwtCurrentUserProvider` oradan kullanıcı id'sini okur.

Önemli tasarım: service'ler `CurrentUserProvider` **arayüzüne** bağlı. MVP'de bu arayüzün
"hep demo kullanıcı" diyen bir uygulaması vardı; JWT eklenince yalnızca o sınıf değişti, hiçbir
service'e dokunulmadı. Bu, **dependency inversion** ilkesine iyi bir örnek.

Ayrıntılar:
- CSRF kapalı ve oturum `STATELESS`: cookie kullanmıyoruz, her istek kendi token'ını taşıyor.
- Login, "e-posta yok" ile "şifre yanlış" için aynı mesajı veriyor. E-posta yoksa bile sahte bir
  BCrypt karşılaştırması yapılıyor; böylece cevap süresine bakarak hangi e-postaların kayıtlı
  olduğu anlaşılamıyor (**timing attack**).
- Token 30 gün geçerli. Refresh token yok; bu bilinçli bir sadeleştirme. Mülakatta "geliştirmek
  istesem refresh token eklerdim" diyebilirsin.

### 2.8 Testler: `src/test/`
- `service/*Test`: **Mockito** ile repository'leri taklit edip yalnızca iş mantığını test ediyor (hızlı).
- `controller/*Test`: `@WebMvcTest` yalnızca web katmanını başlatır; doğrulama ve hata formatını test eder.
- `ApiIntegrationTest`: **Testcontainers** Docker'da gerçek bir PostgreSQL açar. Flyway şemayı
  kurar, testler gerçek HTTP akışını (kayıt → giriş → antrenman → set → istatistik) çalıştırır.
  Native SQL sorgusu da ancak böyle doğru test edilebilir.

---

## 3. Android — okuma sırası

### 3.1 Giriş noktaları
- `LiftLogApplication` (`@HiltAndroidApp`): Hilt'in bağımlılık grafiğini başlatır.
- `MainActivity`: tek Activity. Ekranların hepsi Compose ile çiziliyor.
- `ui/LiftLogApp.kt`: **kök karar**. Oturum durumu `Loading` ise yükleniyor ekranı,
  `LoggedOut` ise giriş ekranı, `LoggedIn` ise ana uygulama. Çıkış yapınca veya token süresi
  dolunca (401) navigasyon kodu yazmaya gerek kalmıyor; durum değişince ekran kendiliğinden değişiyor.

### 3.2 Veri katmanı: `data/`
- `remote/dto/ApiDtos.kt`: backend JSON'unun birebir karşılığı (`@Serializable`).
- `remote/LiftLogApi.kt`: **Retrofit** arayüzü. Metotlar `suspend`, yani coroutine içinde çağrılıyor.
- `remote/AuthInterceptor.kt`: her isteğe token ekleyen OkHttp **interceptor**'ı. 401 gelirse oturumu siler.
- `local/SessionStore.kt`: token'ı **DataStore**'da (uygulamaya özel depolama) tutar ve
  `StateFlow` olarak yayınlar.
- `repository/`: DTO'yu uygulamanın kendi modeline (`domain/model`) çevirir ve ağ hatalarını
  `AppError`'a dönüştürür (`ApiCall.kt`). ViewModel'ler HTTP kodlarını hiç görmez.

**Neden ayrı domain modeli?** Ekran kodu sunucunun JSON şekline bağlı kalmasın. Ör. tarih
sunucudan `String` geliyor ama uygulamada `LocalDate` olarak kullanılıyor.

### 3.3 Bağımlılık enjeksiyonu: `di/`
**Hilt** sınıfların ihtiyaç duyduğu nesneleri (Retrofit, repository...) kendisi oluşturup veriyor.
`RepositoryModule`'deki `@Binds`, "`WorkoutRepository` istendiğinde `RemoteWorkoutRepository` ver"
demek. Testlerde aynı arayüzün sahte (fake) bir sürümünü veriyoruz; kodu test edilebilir yapan bu.

### 3.4 Ekranlar: `ui/`
Her ekranda aynı desen:
- **`XxxViewModel`**: `MutableStateFlow<XxxUiState>` tutar. Ekran yalnızca bu durumu okur. Kullanıcı
  bir şey yapınca ViewModel'deki bir fonksiyon çağrılır (**tek yönlü veri akışı**).
- **`XxxScreen`**: ViewModel'i alır ve durumu `XxxContent`'e verir. `XxxContent` ViewModel'den
  bağımsız olduğu için ileride önizlenebilir ve test edilebilir.
- `UiState`: `Loading` / `Success` / `Error`. Her ekran bu üç durumu çiziyor.
- Tek seferlik olaylar (ör. "antrenman oluşturuldu, detaya git") `Channel` ile gönderiliyor.
  `StateFlow` kullanılsaydı ekran dönünce olay tekrar tetiklenebilirdi.

İlginç yerler:
- `WorkoutListViewModel.onScreenResumed()`: detaydan dönünce liste yenileniyor. Bunu önce
  ekranda bir bayrakla yapmıştım ama ekran kompozisyondan çıkınca bayrak sıfırlandığı için
  çalışmadı. Emülatörde test ederken yakaladım ve kararı ViewModel'e taşıdım. **"En çok nerede
  zorlandın?" sorusu için iyi bir hikâye.**
- `WorkoutDetailViewModel.validateSetInput()`: backend'deki limitlerin aynısı. Kötü veri ağa hiç gitmiyor.
- `openAddSet()`: yeni seti bir önceki setin değerleriyle dolduruyor (kullanım kolaylığı).
- `ProgressScreen`: **Vico** grafikleri. 1RM grafiğinin Y ekseni 0'dan değil verinin etrafından
  başlıyor; yoksa 80'den 94 kg'a çıkış düz bir çizgi gibi görünüyordu.

### 3.5 Testler: `android/app/src/test/`
`testing/Fakes.kt` içindeki sahte repository'lerle ViewModel'ler ağ olmadan test ediliyor.
`MainDispatcherRule`, `viewModelScope`'un kullandığı `Dispatchers.Main`'i test dispatcher'ıyla
değiştiriyor. `advanceUntilIdle()` bekleyen bütün coroutine'leri bitiriyor. `Turbine` ise
Flow'dan gelen olayları test etmeye yarıyor.

---

## 4. Altyapı

- `backend/Dockerfile`: **çok aşamalı**. 1. aşama JDK ile derler, 2. aşama yalnızca JRE ve jar
  içerir; imaj küçük kalır, derleme araçları üretime gitmez. Uygulama root olmayan bir kullanıcıyla çalışır.
- `docker-compose.yml`: yerelde `db` (+ istersen `api`). `deploy/docker-compose.prod.yml`: sunucuda
  `db` + `api` + `caddy`. Dışarıya yalnızca Caddy açık; HTTPS sertifikasını Let's Encrypt'ten otomatik alır.
- `.github/workflows/ci.yml`: her push ve PR'da backend testleri (Testcontainers dahil), Docker
  imajı derlemesi, Android testleri, lint ve debug APK.
- Gizli bilgiler: `.env` (DB şifresi, `JWT_SECRET`) ve `android/keystore.properties` (APK imza
  şifreleri) git'e girmiyor. Örnek değerler `.env.example` dosyasında.

---

## 5. Mülakat soruları — kısa cevaplar

**Neden MVVM?** Ekran kodu yalnızca çizer, mantık ViewModel'de. ViewModel ekran döndürmede
yaşamaya devam eder ve Android'e bağımlı olmadığı için birim testi yazılabilir.

**JWT nasıl çalışıyor, token nerede?** Sunucu imzalı bir token verir. Telefon onu DataStore'da
saklar ve her istekte header'a koyar. Sunucu imzayı doğrular; oturum tutmaz (stateless). İmza
gizli anahtarla atıldığı için token değiştirilirse doğrulama başarısız olur.

**Neden `NUMERIC`, neden PR tablosu yok?** Kesin ondalık değer için `NUMERIC`. PR'ı setlerden
hesaplamak veri tekrarını ve tutarsızlık riskini ortadan kaldırır.

**Entity ve DTO neden ayrı?** API sözleşmesi ile veritabanı yapısı bağımsız değişebilsin; hassas
alanlar sızmasın; lazy ilişkiler serileştirme sırasında sorun çıkarmasın.

**Docker ne işe yaradı?** "Benim bilgisayarımda çalışıyordu" sorununu bitirir. Veritabanı ve API
her yerde aynı sürümle, tek komutla kalkar. Testcontainers da testlerde gerçek PostgreSQL açmak için Docker kullanır.

**League grubunun davet kodu neden 10 karakter, neden deneme sınırı yok?** 31 harfli alfabeyle 31^10 ≈ 8,2e14
kod, yaklaşık 49,5 bit. Saniyede 300 deneme bir yıl sürse bile 10 canlı koddan birini bulma ihtimali ~1/10 000.
8 karakter (40 bit) bir deneme sınırlayıcısı gerektirirdi; sınırlayıcı ise bedava hesaplarla herkesin
katılmasını engellemeye açık ve bellekte durum tutar. Kod zaten yapıştırıldığı için iki karakter fazlası ucuz.

**Davet kodu neden düz metin, kurtarma kodu neden BCrypt'li?** Davet kodu paylaşılmak için var: her üye onu
tekrar görüp gönderebilmeli, yenilenince eskisi ölür ve sadece grup verisini açar. Kurtarma kodu ise bütün
hesabı açar, bu yüzden parola gibi hash'lenir.

**Gruba katılırken neden grup satırını kilitliyorsun (`FOR NO KEY UPDATE`)?** Üye sayısı kontrolü ("20'den az mı?")
ile ekleme arasında başka biri de katılabilir. Grubu değiştiren her işlem önce grup satırını kilitlediği
için aynı grupta işlemler sırayla çalışır; 20. koltuğa aynı anda iki kişi oturamaz, son kişi çıkınca grup
kesin silinir. (Hibernate `PESSIMISTIC_WRITE`'ı PostgreSQL'de `FOR NO KEY UPDATE` olarak yazar; bu kilit
kendisiyle çakıştığı için işlemleri yine sıraya sokar. Kilit kaldırılınca başarısız olan bir entegrasyon
testi bunu kanıtlıyor.)

**Riot bir maçı vermezse ne oluyor?** O maç atlanır, oyuncu yine "yenilendi" sayılır; maç kaydedilmediği için
bir sonraki yenilemede tekrar istenir. Eskiden tek bozuk maç oyuncuyu hep "en eski" bırakıyor ve her sayfa
açılışı onda takılıp grubun geri kalanını hiç yenilemiyordu.

**Grup sahibi neden bir kolon değil?** Sahip, en önce katılan üye (`joined_at`, sonra `user_id`). Ayrılan ya
da silinen birini gösteren bir `owner_id` hiç oluşmaz, devretme kodu da gerekmez.

**`SKIP LOCKED` ve `NO KEY UPDATE` ne işe yarıyor?** Yenilemeden önce oyuncunun satırı işlem bitene kadar
kilitlenir. `SKIP LOCKED`: satırı başka bir istek tutuyorsa beklemeden atla (o zaten yeniliyor).
`NO KEY UPDATE`: anahtar değişmeyeceği için, o oyuncuyu takip etmek gibi yabancı anahtar kontrolleri
(`KEY SHARE`) engellenmez. Sonuç: aynı oyuncu aynı anda iki kez Riot'a sorulmaz ve aynı maç satırı iki kez
eklenmeye çalışılmaz. İki iş parçacıklı entegrasyon testi bunu kanıtlıyor.

**`LolGroupMember` neden `Persistable`?** Kimliği kullanıcının kendi id'si, veritabanı üretmiyor. Spring Data
id'si dolu bir varlığı "var olan" sanıp `merge` eder; iki eşzamanlı katılmada biri sessizce diğer gruba
taşınabilirdi. `Persistable` ile her zaman `INSERT` olur ve birincil anahtar çakışması hata verir.

**Tekrar yapsan neyi farklı yapardın?** Refresh token, çevrimdışı çalışma için Room, ekranlar
için Compose UI testleri, eşzamanlı set düzenlemede optimistic locking (`@Version`).
