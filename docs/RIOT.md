# League takibi: Riot API anahtarı kurulumu

"Oyun" sekmesi verileri Riot Games API'sinden alır. Anahtar olmadan uygulama çalışır, sadece bu sekme
"Riot anahtarı henüz eklenmedi" der.

## 1. Kişisel anahtar al (bir kez)

1. <https://developer.riotgames.com> adresine git, Riot hesabınla giriş yap.
2. Üstte **Register Product** → **Personal API Key** seç.
   - Kişisel anahtar, sadece senin ve küçük bir arkadaş grubunun kullandığı, herkese açık olmayan
     araçlar içindir. LiftLog tam olarak bu.
3. Formu doldur. Örnek açıklama:
   > LiftLog: a private workout app used by me and a few friends. A tab shows our own ranked stats
   > (rank, last 20 matches, top champions). Data is cached on the server, refreshed at most every
   > 10 minutes per player.
4. Onaylanınca **APPS** sayfasında `RGAPI-...` ile başlayan anahtar görünür. Kişisel anahtarın süresi
   dolmaz (dashboard'daki 24 saatlik geliştirme anahtarı değil, bunu kullan).

Anahtar bir şifre gibidir: koda, sohbete ya da ekran görüntüsüne koyma.

## 2. Render'a ekle

1. Render → **liftlog-api** servisi → **Environment**.
2. `RIOT_API_KEY` satırına anahtarı yapıştır → **Save Changes**.
3. Render servisi yeniden başlatır (~2 dakika). Sonra uygulamada **Oyun → Oyuncu ekle**.

## Sınırlar ve nasıl uyulduğu

- Kişisel anahtar: saniyede 20, iki dakikada 100 istek.
- Sunucu bunun altında kalır (18 / 90): her oyuncu en fazla 10 dakikada bir yenilenir, bir yenileme en
  fazla ~13 istek harcar, bütçe azalınca kayıtlı veri gösterilir.
- Elle yenileme (sağ üstteki ↻) dakikada bir kereden sık çalışmaz.
- Bir sayfa açılışı en fazla **4 yenileme denemesi** yapar (en eski veriden başlayarak); kalanlar kayıtlı
  veriyle görünür ve sıradaki açılışta yenilenir. Bir oyuncuda çıkan Riot hatası sıradakini durdurmaz; sadece
  anahtar sorunu ya da biten bütçe durdurur.
- Riot'un artık vermediği (404) ya da o an veremediği bir maç atlanır; oyuncu yine yenilenmiş sayılır.
- Arka plan yenilemeleri bütçenin son **14 isteğine** dokunmaz: bu, bir takip ya da grup bağlantısının
  (1 hesap araması + 1 yenileme) her zaman yer bulması için ayrılır.
- Aynı oyuncu aynı anda iki kez yenilenmez: yenilemeden önce oyuncunun satırı
  `SELECT ... FOR NO KEY UPDATE SKIP LOCKED` ile kilitlenir. Başka bir istek o oyuncuyu yeniliyorsa bu istek
  beklemeden sıradakine geçer. Böylece 5 arkadaş tahtayı aynı anda açsa da her oyuncu 10 dakikada en fazla
  bir kez Riot'a sorulur.

## Arkadaş grupları

- League sekmesinde **Grup / Takip** geçişi var. Grup: davet koduyla katılan LiftLog kullanıcıları; tahta,
  her üyenin bağladığı kendi Riot hesabından oluşur ve herkes aynı sıralamayı görür. Takip: kişisel liste,
  eskisi gibi.
- Kişi başı tek grup, en fazla 20 üye. Grubu en önce katılan üye yönetir; o ayrılınca yönetim sıradakine geçer.
- Davet kodu 10 karakter (`K7QXM-2PFD9`), 0/O ve 1/I/L içermez. Yönetici kodu yenileyebilir; biri gruptan
  çıkarılınca kod kendiliğinden değişir. Son üye ayrılınca grup ve kodu silinir.
- Riot ID başka bir sunucuda seçildiyse (TR1 yerine EUW1 gibi) artık 500 değil "bulunamadı" (404) döner.
- Sunucu değiştiren oyuncu (PUUID aynı kalır) yeni sunucuyla takip edilince ya da bağlanınca oraya taşınır.

## Yerelde anahtarsız geliştirme

`.env` dosyasına `RIOT_FAKE=true` yaz (veya `RIOT_FAKE=true docker compose up -d api`). Her Riot ID için
tutarlı, uydurma rank ve maçlar üretilir; `notfound#TR1` bulunamayan oyuncuyu taklit eder. Canlı
sunucuda asla açılmaz: anahtar ve `RIOT_FAKE` birlikte verilirse uygulama başlamaz.
