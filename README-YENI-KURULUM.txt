VALEKAPIMDA GÜNCEL PAKET

Bu paket Java/Kotlin hedef uyumsuzluğu düzeltilmiş yeni sürümdür.
Android uygulamalarında Java ve Kotlin hedefi 17 olarak eşitlenmiştir.
Gradle, Android Studio'nun gömülü JDK 21'i ile çalışabilir.

KURULUM
1. Eski CMD pencerelerini kapatmayabilirsiniz.
2. ZIP dosyasını masaüstüne çıkarın.
3. Android Studio'da File > Close Project yapın.
4. Open ile şu klasörü açın:
   ValeKapimda-Guncel\ValeKapimda\apps\customer-android
5. Gradle JDK olarak Embedded JDK / jbr-21 seçili kalsın.
6. Sync bitince gerçek Android telefonu veya emülatörü seçip Run'a basın.

ÖNEMLİ
- Eski customer-android klasörünün üstüne kopyalamayın.
- Yeni klasörü ayrı proje olarak açın.
- Emülatör için C diskinde en az 15 GB boş alan önerilir.
- Disk alanı yetersizse gerçek Android telefon kullanın.

PAKET İÇERİĞİ
- apps/customer-android: müşteri uygulaması
- apps/driver-android: vale uygulaması
- apps/admin-web: yönetim paneli
- services/api: backend
- database: PostgreSQL şeması
