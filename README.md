# Flasher Builder (Android)

**OnePlus / OPPO / Realme cihazlar için firmware flasher paketini doğrudan telefonunuzda oluşturan Android uygulaması.**

Bilgisayara ihtiyaç duymadan, indirdiğiniz tam OTA ZIP'inden (ya da bağlantısından) cihazınıza flaş edilebilecek paketi üretir.
Üretilen ZIP, bilgisayardan kurulum yapan flasher paketidir; uygulama cihazı flaşlamaz.

## Ne işe yarar?
- Tam OTA'dan partition'ları çıkarır, süper imajı oluşturur ve hazır flasher paketini (ZIP + SHA-256) verir.
- Cihaz, flasher türü ve bölgeyi (EU, IN, GLO, CN…) listeden seçersiniz; geçerli kombinasyonlar hazırdır.
- OTA'yı bağlantıyla indirebilir ya da telefonunuzdaki ZIP dosyasını seçebilirsiniz.
- **Özel `odm.img`:** İsterseniz paketteki ODM yerine kendi seçtiğiniz `odm.img` konur (ör. başka bir ROM'dan alınan ODM ile kamera sorununu gidermek için).
- Build bildirimde ilerler, ekran kapansa da sürer. Bitince paket **İndirilenler** klasörüne kaydedilir.
- Türkçe ve İngilizce arayüz (Android 13+ için sağ üstte dil seçimi).

## Kullanım
1. Cihaz, tür ve bölgeyi seçin.
2. Tam OTA ZIP bağlantısını yapıştırın veya dosyayı seçin.
3. *Build başlat* → bitince *İndirilenler'e kaydet*.

**Boş alan:** OTA boyutunun yaklaşık 4 katı gerekir (8 GB OTA ≈ 32 GB). Günlük, *Günlüğü göster* anahtarıyla açılır.

> Üretilen paketler resmî değildir. Flaş işlemi kendi sorumluluğunuzdadır; önce yedek alın.

## Kullanılan proje ve teşekkür
Paket oluşturma mantığı, flasher şablonları ve cihaz kataloğu **Flasher_Builder** projesine aittir. Bu uygulama o projenin
Python kodunu değiştirmeden telefonda çalıştırır.

- **Flasher_Builder** — geliştirici **koaaN**: https://github.com/koaaN/Flasher_Builder

Emeği için koaaN'a çok teşekkürler. 🙏

Ayrıca: [otaripper](https://github.com/syedinsaf/otaripper) (OTA çıkarma, syedinsaf), `lpmake` (AOSP; arm64 derlemesi
[MIO-KITCHEN-SOURCE](https://github.com/ColdWindScholar/MIO-KITCHEN-SOURCE) deposundan) ve [Chaquopy](https://chaquo.com/chaquopy/).

---

# English

**An Android app that builds firmware flasher packages for OnePlus / OPPO / Realme devices right on your phone — no PC needed.**
Give it a full OTA ZIP (link or file), pick device / flasher type / region, and it produces the flasher package (ZIP + SHA-256).

- Optional **custom `odm.img`** replaces the ODM inside the package (e.g. to fix the camera with an ODM from another ROM).
- Runs in the background with a progress notification; the result is saved to Downloads.
- Needs about 4× the OTA size in free space. English and Turkish UI.

Packages are unofficial; flashing is at your own risk.

**Credits:** the build logic, flasher templates and device catalog come from [Flasher_Builder](https://github.com/koaaN/Flasher_Builder) by **koaaN** — thank you!
Also: [otaripper](https://github.com/syedinsaf/otaripper), `lpmake` (AOSP, arm64 build from [MIO-KITCHEN-SOURCE](https://github.com/ColdWindScholar/MIO-KITCHEN-SOURCE)) and [Chaquopy](https://chaquo.com/chaquopy/).
