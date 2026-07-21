# ValeKapımda Profesyonel Sürüm – Aşama 1

Bu paket demo oturum altyapısını üretim yaklaşımına taşıyan backend temelini içerir.

## Eklenenler

- SMS OTP talep/doğrulama endpointleri
- Kısa ömürlü access token + döndürülen refresh token
- Refresh token yenileme ve çıkış
- Demo login varsayılan olarak kapalı
- Profil okuma/güncelleme
- Silinebilir araç kayıtları ve tekil plaka kontrolü
- Zenginleştirilmiş müşteri işlem geçmişi
- İşlem ayrıntısı
- Favori adresler
- Tamamlanan işlem için puanlama/yorum
- Vale canlı konum endpointi
- Merkezi hata yönetimi ve üretimde ayrıntı gizleme

## Veritabanı

Mevcut veritabanında bir kez çalıştırın:

```sql
\i database/002_professional_foundation.sql
```

Render/Supabase/Neon panelinde dosyanın içeriğini SQL editörüne yapıştırarak da çalıştırabilirsiniz.

## Backend ortam değişkenleri

`services/api/.env.example` dosyasını `.env` olarak kopyalayın ve gerçek değerleri girin.

Üretimde mutlaka:

- `NODE_ENV=production`
- güçlü `JWT_SECRET`
- güçlü `OTP_PEPPER`
- `ENABLE_DEMO_LOGIN=false`
- gerçek SMS sağlayıcısı

kullanılmalıdır.

## Yeni auth akışı

1. `POST /auth/request-otp`
2. `POST /auth/verify-otp`
3. API çağrılarında `Authorization: Bearer <accessToken>`
4. Süre dolunca `POST /auth/refresh`
5. Çıkışta `POST /auth/logout`

Geliştirme ortamında OTP kodu API cevabındaki `developmentCode` alanında ve terminalde görünür. Üretimde kod dönmez.

## Kontrol

Backend TypeScript derlemesi bu paket hazırlanırken başarıyla tamamlandı:

```text
npm run build
```

## Sonraki uygulama aşaması

Android müşteri uygulamasında yeni auth akışının bağlanması, güvenli token saklama, Ana Sayfa / Geçmiş / Profil alt menüsü ve gerçek araç listesinin API’den yüklenmesi yapılacaktır.
