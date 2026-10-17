# LiftLog — Claude Code talimatları

## Proje
Android antrenman takip uygulaması (Kotlin + Jetpack Compose) ve Spring Boot + PostgreSQL backend.
Tüm plan, tasarım ve yol haritası `docs/PLAN.md` dosyasında. Her oturumun başında onu oku.

## En önemli kural: öğretme modu
Bu bir CV ve öğrenme projesi. Geliştirici (Behlül) her parçayı mülakatta anlatabilmeli.
- Kodu tek seferde büyük bloklar hâlinde yazma. Adım adım ilerle.
- Her adımda önce NE yapılacağını ve NEDEN yapıldığını kısaca anlat, sonra küçük bir parça kod yaz ya da Behlül'ün yazmasını iste.
- Yeni bir kavram geçtiğinde (DTO, JPA, ViewModel, JWT vb.) 2-3 cümleyle açıkla.
- Behlül açıkça "sen yaz" demedikçe, uzun kodu onun yerine yazma; ipucu ve iskelet ver.
- Açıklamaları Türkçe yap. Kod, değişken adları ve commit mesajları İngilizce olsun.
- Arayüzde bir yeri tarif ederken "sol, sağ, üst, alt" gibi yön ifadeleri kullan.

## Çalışma düzeni
- `docs/PLAN.md` içindeki fazları sırayla takip et; bir fazın kapısı geçilmeden sonrakine geçme.
- Her oturumun sonunda `PROGRESS.md` dosyasını güncelle: ne yapıldı, hangi görev bitti, sıradaki adım ne.
- Her özellik ayrı branch'te (`feature/...`), küçük commit'lerle (`feat: ...`, `fix: ...`).
- Gizli bilgiler (şifre, JWT anahtarı) asla koda yazılmaz; `.env` kullan, `.gitignore`'a ekle.

## Teknoloji
- Backend: Java 21, Spring Boot 3, Spring Data JPA, PostgreSQL, Flyway, Spring Security + JWT, JUnit 5, Testcontainers
- Android: Kotlin, Jetpack Compose, MVVM, Hilt, Retrofit, Vico (grafik), ileride Room
- Altyapı: Docker, Docker Compose, GitHub Actions
