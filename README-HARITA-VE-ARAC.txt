VALEKAPIMDA - FOTOĞRAFSIZ ARAÇ FORMU + GOOGLE MAPS ALTYAPISI

Bu sürümde:
- Araç fotoğrafı kaldırıldı.
- Ruhsat fotoğrafı kaldırıldı.
- Araç formunda yalnızca plaka, marka, model, renk, model yılı ve vites vardır.
- Ana ekrana Google Maps Compose haritası eklendi.
- Alım ve gidilecek yer işaretçileri hazırlandı.
- Konum izinleri AndroidManifest.xml dosyasına eklendi.
- KM ve fiyat kartı demo değerle çalışır.

GOOGLE MAPS ANAHTARI EKLEME
1) apps/customer-android/app/src/main/AndroidManifest.xml dosyasını açın.
2) BURAYA_GOOGLE_MAPS_API_ANAHTARI yazısını Google Cloud API anahtarınızla değiştirin.
3) File > Sync Project with Gradle Files yapın.
4) Run tuşuna basın.

Not: Gerçek rota, trafik, yol mesafesi ve adres araması sonraki entegrasyonda Routes API / Places API ile bağlanacaktır.
