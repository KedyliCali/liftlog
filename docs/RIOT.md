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

## Yerelde anahtarsız geliştirme

`.env` dosyasına `RIOT_FAKE=true` yaz (veya `RIOT_FAKE=true docker compose up -d api`). Her Riot ID için
tutarlı, uydurma rank ve maçlar üretilir; `notfound#TR1` bulunamayan oyuncuyu taklit eder. Canlı
sunucuda asla açılmaz: anahtar ve `RIOT_FAKE` birlikte verilirse uygulama başlamaz.
