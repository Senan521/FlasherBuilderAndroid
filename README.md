# Flasher Builder (Android)

🇬🇧 English · [🇹🇷 Türkçe](#türkçe)

## What is it?

An Android app that **builds firmware flasher packages for OnePlus / OPPO / Realme devices directly on your phone** — no PC needed.
You give it a full OTA ZIP (a link or a file), choose your device, flasher type and region, and it produces the flasher package (ZIP + SHA-256).
Then you flash that package from a computer as usual; the app itself does not flash your device.

## How to use

1. Install the APK from the [Releases](../../releases) page (allow "install unknown apps" if asked).
2. Pick **Device**, **Flasher type** and **Region**.
3. Paste the **full OTA ZIP link**, or choose the ZIP file from your phone.
4. Tap **Start build**. Progress is shown in a notification and continues with the screen off.
5. When it finishes, tap **Save to Downloads**. The package is saved to `Download/FlasherBuilder`.

### Good to know

- **Free space:** about **4× the OTA size** (an 8 GB OTA needs ≈ 32 GB). If space is short, the build stops with a clear message.
- **Two OTAs:** a few selections (for example Ace 6 SuperHybrid IN) ask for an additional BASE OTA and need about 5× the combined size.
- **Change image (optional switch):** replace images in the package with images from another ROM.
  Paste an OTA link and tap *Scan images*, tick the images you want (odm, boot, abl…) — only those are downloaded —
  or pick an image file from your phone (the file name must be the partition name, e.g. `odm.img`).
  Use only images compatible with your device; `vbmeta*` cannot be replaced.
- **Show log:** a switch that reveals a scrollable, copyable log (hidden by default).
- **Clear workspace** frees the space used by cached files and finished builds. **Check native tools** verifies the built-in tools run on your phone.
- **Language:** English and Turkish follow your system language; on Android 13+ you can switch with the globe icon at the top right.
- **Requirements:** Android 10+, 64-bit ARM (arm64) phone, internet connection.
- **Updates:** release APKs are debug-signed. If an update refuses to install over an older version, uninstall the old one first.

> Packages are unofficial. Flashing is at your own risk — back up your data first.

## Credits & thanks

The build logic, flasher templates and device catalog come from **[Flasher_Builder](https://github.com/koaaN/Flasher_Builder) by [koaaN](https://github.com/koaaN)**.
This app runs that project's code on your phone. Thank you, koaaN, for the great work! 🙏

Also built on: [otaripper](https://github.com/syedinsaf/otaripper) by syedinsaf (OTA extraction), `lpmake` from AOSP
(arm64 build from [MIO-KITCHEN-SOURCE](https://github.com/ColdWindScholar/MIO-KITCHEN-SOURCE)) and [Chaquopy](https://chaquo.com/chaquopy/).

---

# Türkçe

## Nedir?

**OnePlus / OPPO / Realme cihazlar için firmware flasher paketini doğrudan telefonunuzda oluşturan** Android uygulaması — bilgisayar gerekmez.
Tam OTA ZIP'ini (bağlantı ya da dosya) verirsiniz, cihaz, flasher türü ve bölgeyi seçersiniz; uygulama flasher paketini (ZIP + SHA-256) üretir.
Paketi her zamanki gibi bilgisayardan flaşlarsınız; uygulamanın kendisi cihazı flaşlamaz.

## Nasıl kullanılır?

1. APK'yı [Releases](../../releases) sayfasından indirip kurun (istenirse "bilinmeyen kaynaklardan yüklemeye" izin verin).
2. **Cihaz**, **Flasher türü** ve **Bölge**yi seçin.
3. **Tam OTA ZIP bağlantısını** yapıştırın ya da ZIP dosyasını telefonunuzdan seçin.
4. **Build başlat**'a basın. İlerleme bildirimde görünür, ekran kapansa da sürer.
5. Bitince **İndirilenler'e kaydet**'e basın. Paket `Download/FlasherBuilder` klasörüne kaydedilir.

### Bilmeniz gerekenler

- **Boş alan:** OTA boyutunun yaklaşık **4 katı** (8 GB OTA ≈ 32 GB). Yetersizse build net bir mesajla durur.
- **İki OTA:** bazı seçimler (örn. Ace 6 SuperHybrid IN) ek bir BASE OTA ister ve iki OTA'nın toplamının yaklaşık 5 katı alan gerektirir.
- **İmaj değiştir (isteğe bağlı anahtar):** paketteki imajların yerine başka bir ROM'dan imaj koyar.
  OTA bağlantısını yapıştırıp *İmajları tara*ya basın, istediğiniz imajları (odm, boot, abl…) işaretleyin — yalnızca onlar indirilir —
  ya da telefonunuzdan bir imaj dosyası seçin (dosya adı bölüm adı olmalı, ör. `odm.img`).
  Yalnızca cihazınızla uyumlu imajlar kullanın; `vbmeta*` değiştirilemez.
- **Günlüğü göster:** kaydırılabilir ve kopyalanabilir günlüğü açan anahtar (varsayılan kapalı).
- **Çalışma alanını temizle** önbellek ve biten build'lerin yerini boşaltır. **Yerel araçları denetle** yerleşik araçların telefonunuzda çalıştığını doğrular.
- **Dil:** Türkçe ve İngilizce sistem dilini izler; Android 13+'ta sağ üstteki dünya simgesiyle değiştirilir.
- **Gereksinimler:** Android 10+, 64-bit ARM (arm64) telefon, internet bağlantısı.
- **Güncelleme:** sürüm APK'ları debug imzalıdır. Eski sürümün üzerine kurulmazsa önce eskisini kaldırın.

> Paketler resmî değildir. Flaş işlemi kendi sorumluluğunuzdadır — önce verilerinizi yedekleyin.

## Kaynaklar ve teşekkür

Paket oluşturma mantığı, flasher şablonları ve cihaz kataloğu **[koaaN](https://github.com/koaaN)**'ın **[Flasher_Builder](https://github.com/koaaN/Flasher_Builder)** projesine aittir.
Bu uygulama o projenin kodunu telefonunuzda çalıştırır. Emeği için koaaN'a çok teşekkürler! 🙏

Ayrıca: syedinsaf'ın [otaripper](https://github.com/syedinsaf/otaripper) (OTA çıkarma), AOSP `lpmake`
(arm64 derlemesi [MIO-KITCHEN-SOURCE](https://github.com/ColdWindScholar/MIO-KITCHEN-SOURCE) deposundan) ve [Chaquopy](https://chaquo.com/chaquopy/).
