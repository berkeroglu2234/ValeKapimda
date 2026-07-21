# ValeKapımda – Kolay Adres Seçimi ve Canlı Rota

Bu paket üç parçayı birlikte günceller:

1. Müşteri uygulaması: otomatik mevcut konum, yazıyla adres arama, öneri seçme, gerçek yol rotası, süre/mesafe/fiyat ve canlı vale işareti.
2. Vale uygulaması: aktif görev sırasında 5 saniyede bir konum gönderimi.
3. Backend: adres arama, ters adres çözümleme ve yol rotası endpointleri.

## Kurulum sırası

### 1. GitHub ve Render backend

Güncellenmiş proje dosyalarını mevcut ValeKapimda GitHub deposuna kopyalayın ve gönderin:

```powershell
cd C:\Users\I3LacK\Desktop\ValeKapimda
git add .
git commit -m "Kolay adres secimi ve canli rota"
git push
```

Render otomatik deploy başlatır. Render Build Command:

```text
npm install && npm run build
```

Start Command:

```text
npm run start
```

Deploy tamamlanınca aşağıdaki adresleri tarayıcıda test edin:

```text
https://valekapimda-api.onrender.com/health
https://valekapimda-api.onrender.com/places/search?q=Marmara%20Park
```

### 2. Müşteri uygulaması

Android Studio ile açın:

```text
apps/customer-android
```

Sync Project, ardından Build > Rebuild Project ve APK oluşturun.

### 3. Vale uygulaması

Android Studio ile açın:

```text
apps/driver-android
```

Sync Project, ardından Build > Rebuild Project ve APK oluşturun. İlk görevde konum izni istendiğinde izin verin.

## Kullanım

- Müşteri uygulaması açılınca alım konumunu otomatik alır.
- Müşteri “Marmara Park AVM” gibi bir yer yazar ve Adresi ara düğmesine basar.
- Sonuçlardan birini seçince gerçek yol rotası, km, süre ve fiyat hesaplanır.
- Harita normalde kapalıdır; “Haritada kontrol et / düzelt” ile açılır.
- Vale talebi kabul edip aktif göreve geçtiğinde konumu 5 saniyede bir müşteriye iletilir.

## Önemli üretim notu

Bu sürüm ilk çalışan kurulum için OpenStreetMap Nominatim ve OSRM genel servislerini kullanır. Bunlar test ve düşük trafik için uygundur. Google Play’de yüksek kullanıcı sayısına geçmeden önce Google Places + Routes, Mapbox veya kurumsal bir rota sağlayıcısına geçilmelidir. Backend proxy yapısı bu değişimi kolaylaştırır.
