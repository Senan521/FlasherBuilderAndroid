# Flasher Builder (Android)

Material 3 Expressive arayüzlü, **tamamen cihazda çalışan** firmware flasher paketi oluşturucu (TR / EN).
Flasher_Builder'ın Python backend'i Chaquopy ile uygulamanın içinde çalışır; `otaripper` ve `lpmake`
APK'daki arm64 binary'leridir. GitHub hesabı ya da bulut gerekmez.

## Kullanım
1. Cihaz, flasher türü ve bölgeyi seçin.
2. Tam OTA ZIP için bağlantı yapıştırın **veya** "cihazdan seç" ile ZIP dosyasını seçin
   (seçilen dosya bir kez kopyalanır, build sırasında kopyalanmadan taşınır; Ace 6 COS için BASE da aynı şekilde).
3. **Build başlat** → bildirimden ilerleme izlenir, ekran kapansa da sürer.
4. Bitince **İndirilenler'e kaydet** ile ZIP `Download/FlasherBuilder` klasörüne kopyalanır.

- Disk: OTA boyutunun yaklaşık 4 katı boş alan (8 GB OTA ≈ 32 GB). Eksikse backend net bir hata verir.
- **Günlüğü göster** anahtarı (varsayılan kapalı) kaydırılabilir günlüğü açar; "Kopyala" ile paylaşılabilir.
- Dil: sistem diline uyar (TR/EN). Android 13+'ta sağ üstteki dil simgesinden değiştirilir.
- "Yerel araçları denetle": otaripper ve lpmake bu cihazda çalışıyor mu kontrol eder.
- "Çalışma alanını temizle": bloat önbelleği, bitmiş build'ler ve seçilen dosyaları siler.

## APK'yı GitHub'da derleme (Termux)
```bash
pkg update && pkg install git gh unzip
gh auth login
unzip FlasherBuilderAndroid.zip && cd FlasherBuilderAndroid
git init -b main && git add . && git commit -m "ilk sürüm"
gh repo create flasher-builder-android --private --source=. --push
gh workflow run android.yml && gh run watch
cd ~/storage/downloads && gh run download -R <kullanici>/flasher-builder-android -n FlasherBuilder-apk
```
Derleme hata verirse: `gh run view --log-failed | grep -E "^e: |Could not resolve|requires" | head -30`.
Kalıcı imza (isteğe bağlı): `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` secret'ları; yoksa debug anahtarıyla imzalanır.

## Nasıl çalışıyor
- CI, `tools/bundle_repo.py` ile Flasher_Builder'ın gerekli kısmını `repo.zip` olarak APK'ya gömer
  (varsayılan: koaaN/Flasher_Builder `main`; fork için Actions → Run workflow → `source_repo`).
- `tools/fetch_arm64_tools.sh` otaripper (v3.5.0 resmi Android arm64) ve lpmake'i SHA-256 doğrulamasıyla indirir.
- `app/src/main/python/android_entry.py` upstream kodunu değiştirmeden uyarlar: yerel araçlar APK'dan,
  yardımcı süreçler uygulama içinde, indirmeler urllib ile (kaldığı yerden devam eder), seçilen OTA taşınır.
- Katalog (`assets/catalog.json`, 17 cihaz / 86 kombinasyon): `python3 tools/export_catalog.py > catalog.json` (fork klasöründe).

## Günlükteki "Invalid sparse file format at header magic" satırı
Zararsızdır. lpmake, süper imaja eklediği her partition dosyasını önce sparse formatında mı diye dener; ham (sparse
olmayan) imajlarda bu satırı yazar ve ham olarak devam eder (ham imaj başına bir satır). Doğrulandı: build çıkış kodu 0,
çıktı geçerli Android sparse imajı. Ekrandaki günlükte gizlenir; backend'in build.log dosyasında kalır.
