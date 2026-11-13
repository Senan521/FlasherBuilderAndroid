# Flasher Builder (Android)

**OnePlus / OPPO / Realme cihazlar için firmware flasher paketini doğrudan telefonunuzda oluşturan Android uygulaması.**

Bilgisayara ihtiyaç duymadan, indirdiğiniz tam OTA ZIP'inden (ya da bağlantısından) cihazınıza flaş edilebilecek paketi üretir.
Üretilen ZIP, bilgisayardan kurulum yapan flasher paketidir; uygulama cihazı flaşlamaz.

## Ne işe yarar?
- Tam OTA'dan partition'ları çıkarır, süper imajı oluşturur ve hazır flasher paketini (ZIP + SHA-256) verir.
- Cihaz, flasher türü ve bölgeyi (EU, IN, GLO, CN…) listeden seçersiniz; geçerli kombinasyonlar hazırdır.
- OTA'yı bağlantıyla indirebilir ya da telefonunuzdaki ZIP dosyasını seçebilirsiniz.
- **Özel `odm.img` (yalnızca OnePlus 13R seçiliyken):** OnePlus Ace 5 üzerinde 13R ROM kullananlar için; paketteki ODM yerine Ace 5 CN ROM'undan aldığınız `odm.img` konur (kamera sorununu gidermek için). Ace 5 CN OTA bağlantısını yapıştırırsanız yalnızca ODM bölümü indirilip çıkarılır (OTA'nın tamamı inmez), ya da dosyayı kendiniz seçersiniz. Gerçek 13R'de kullanmayın.
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

- Optional **custom `odm.img`** (shown for OnePlus 13R only): for OnePlus Ace 5 users running the 13R ROM — replaces the ODM in the package with the odm.img from your Ace 5 CN ROM (camera fix). Paste the Ace 5 CN OTA link and only its ODM partition is downloaded and extracted (not the whole OTA), or pick the file yourself. Not for a real 13R.
- Runs in the background with a progress notification; the result is saved to Downloads.
- Needs about 4× the OTA size in free space. English and Turkish UI.

Packages are unofficial; flashing is at your own risk.

**Credits:** the build logic, flasher templates and device catalog come from [Flasher_Builder](https://github.com/koaaN/Flasher_Builder) by **koaaN** — thank you!
Also: [otaripper](https://github.com/syedinsaf/otaripper), `lpmake` (AOSP, arm64 build from [MIO-KITCHEN-SOURCE](https://github.com/ColdWindScholar/MIO-KITCHEN-SOURCE)) and [Chaquopy](https://chaquo.com/chaquopy/).
