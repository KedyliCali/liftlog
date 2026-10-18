# Yayınlama rehberi: Render + Neon (+ iPhone)

Amaç: API'yi internette, kart bilgisi vermeden ve yönetilecek bir sunucu olmadan çalıştırmak.

- **Neon:** ücretsiz PostgreSQL. Süresi dolmuyor. Boşta kalınca uyuyor, ilk sorguda saniyeler içinde uyanıyor.
- **Render:** `backend/Dockerfile` dosyasından API'yi derleyip çalıştırıyor. `main`'e gelen her
  backend değişikliğinde kendiliğinden yeniden yayınlanıyor. Ayarları depodaki `render.yaml` dosyasında.

**Ücretsiz planın sınırı:** 15 dakika istek gelmezse API uyuyor. Sonraki ilk istek, sunucu açılana
kadar bekliyor. 0.1 CPU ile ölçtüğümüzde bu ~2,5 dakika sürdü. Uygulama bu süre boyunca
"Sunucu uyanıyor…" yazıyor ve zaman aşımına düşmüyor.

---

## 1. Neon: veritabanını oluştur (~3 dk)

1. <https://neon.tech> adresine gir, **Sign up**'a bas ve **GitHub ile devam et**.
2. İlk projeyi oluştur:
   - **Project name:** `liftlog`
   - **Postgres version:** 17
   - **Region:** **AWS Europe Central 1 (Frankfurt)**. Render servisi de Frankfurt'ta; aynı bölge olunca istekler hızlı gider.
3. Proje panelinde **Connect**'e bas.
   - **Connection pooling** seçeneğini **kapat**. Flyway doğrudan bağlantı istiyor.
   - Bağlantı adresi şu biçimde görünür:
     ```
     postgresql://KULLANICI:SIFRE@ep-xxxx.eu-central-1.aws.neon.tech/neondb?sslmode=require
     ```
4. Bu adresten Render'a gireceğin üç değeri çıkar:

   | Render'daki alan | Ne yazılacak | Örnek |
   | --- | --- | --- |
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://` + `@`'den sonraki kısmın tamamı | `jdbc:postgresql://ep-xxxx.eu-central-1.aws.neon.tech/neondb?sslmode=require` |
   | `DB_USER` | `://` ile `:` arasındaki kısım | `neondb_owner` |
   | `DB_PASSWORD` | `:` ile `@` arasındaki kısım | (şifre) |

   Şifreyi kimseyle paylaşma ve bir yere commit'leme. Sadece Render'daki ilgili kutuya yapıştırılacak.

## 2. Render: API'yi yayınla (~10 dk, çoğu bekleme)

> Render `main` dalını okuyor. Bu yüzden önce bu değişikliklerin olduğu PR `main`'e birleştirilmiş olmalı.

1. <https://render.com> adresine gir ve **GitHub ile giriş yap**.
2. Sağ üstteki **New +** menüsünden **Blueprint**'i seç.
3. **Connect a repository** ekranında GitHub'a erişim izni iste ve yalnızca `KedyliCali/liftlog` deposunu seç.
4. Render depodaki `render.yaml`'ı okur ve iki servis gösterir: `liftlog-api` (backend) ve
   `liftlog-web` (iPhone'da açacağın web uygulaması). Senden şu değerleri ister:

   | Servis | Alan | Ne yazılacak |
   | --- | --- | --- |
   | liftlog-api | `SPRING_DATASOURCE_URL`, `DB_USER`, `DB_PASSWORD` | 1. adımdaki tablodan |
   | liftlog-api | `CORS_ALLOWED_ORIGINS` | `https://liftlog-web.onrender.com` |
   | liftlog-web | `VITE_API_URL` | `https://liftlog-api.onrender.com` |

   `JWT_SECRET` alanı boş görünebilir; onu Render kendisi rastgele üretecek.
5. **Apply** (Deploy Blueprint) butonuna bas.
6. İlk derleme 5–10 dakika sürer. API'nin **Logs** sekmesinde `Started LiftlogApplication` yazısını görünce hazırdır.
7. **Adresleri kontrol et.** İki servisin sayfasının üst kısmında gerçek adresleri yazıyor. Ad doluysa
   Render sonuna ek koyar (ör. `liftlog-web-x1y2.onrender.com`). 4. adımdaki tahminden farklıysa:
   - API → **Environment** → `CORS_ALLOWED_ORIGINS` = web'in gerçek adresi → **Save**
   - Web → **Environment** → `VITE_API_URL` = API'nin gerçek adresi → **Save, rebuild, and deploy**
8. İki adresi de Claude'a yaz.

Kontrol: tarayıcıda `https://<api-adresi>/api/v1/health` açılınca `{"status":"UP"}` görünmeli.
İlk açılış birkaç dakika sürebilir.

## 3. iPhone'a kur (~1 dk)

1. iPhone'da **Safari** ile web adresini aç. Uygulama ilk açılışta kendi hesabını otomatik oluşturur.
2. Alttaki **Paylaş** (kare ve yukarı ok) → **Ana Ekrana Ekle** → **Ekle**.
3. Artık ana ekrandaki LiftLog simgesiyle tam ekran, uygulama gibi açılır.

Verilerin bu iPhone'daki Safari'ye bağlı bir gizli hesapta durur. Safari'nin site verilerini silersen
uygulama yeni ve boş bir hesapla başlar.

## 4. Sonrasında (Claude)

1. Canlı API'ye ve web uygulamasına karşı uçtan uca deneme.
2. `./gradlew assembleRelease -PliftlogApiUrl=https://<adres>/` ile imzalı APK'yı üretip emülatörde denemek.
3. Telefonuna kurulum: APK'yı telefona atıp **bilinmeyen kaynaklara izin ver** ile kurarsın.
   İstersen GitHub Releases'a da konur.

## Sorun çıkarsa

| Belirti | Sebep / çözüm |
| --- | --- |
| Render logunda `password authentication failed` | `DB_USER` / `DB_PASSWORD` yanlış kopyalanmış. Servis → **Environment** → düzelt → **Save** |
| `Connection refused` ya da SSL hatası | URL'nin başında `jdbc:postgresql://`, sonunda `?sslmode=require` olmalı |
| Logda `liftlog.jwt.secret ... at least 32 bytes` | `JWT_SECRET` üretilmemiş. Environment'ta **Generate**'e bas |
| Web açılıyor ama "Sunucuya ulaşılamıyor" diyor | `VITE_API_URL` yanlış, ya da API'nin `CORS_ALLOWED_ORIGINS` değeri web'in adresiyle birebir aynı değil (başında `https://`, sonunda `/` olmadan) |
| Derleme `out of memory` | Ücretsiz planın 512 MB'ı derlemede nadiren yetmeyebiliyor. **Manual Deploy → Clear build cache & deploy** ile tekrar dene |
