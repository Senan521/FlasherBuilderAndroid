# Flasher Builder (Android)

Material 3 Expressive arayüzlü Android uygulaması.
- **Bulutta Build** (çalışır durumda tasarlandı): GitHub Actions'ta derler, bitmiş paketi telefona indirir.
- **Cihazda Build** (temel hazır): arm64 `otaripper` + `lpmake` APK'ya gömülü, "Yerel araçları denetle" ile
  cihazda çalıştıkları doğrulanır. Python build hattının cihaza taşınması sonraki aşamadır.

## 1) APK'yı GitHub'da derleme (Termux)
```bash
pkg update && pkg install git gh unzip
gh auth login                                   # GitHub hesabınla giriş
unzip FlasherBuilderAndroid.zip && cd FlasherBuilderAndroid
git init -b main && git add . && git commit -m "ilk sürüm"
gh repo create flasher-builder-android --private --source=. --push
gh workflow run android.yml                     # APK derlemesini başlat
gh run watch                                    # ilerlemeyi izle
```
İndirme: `gh run download -n FlasherBuilder-apk` → APK'yı telefona kurun (bilinmeyen kaynak izni gerekir).
Kalıcı sürüm için: `git tag v0.1.0 && git push --tags` → APK "Releases" sayfasına yüklenir.

Derleme hata verirse: `gh run view --log-failed` çıktısını (son ~60 satır) bana yapıştırın.

## 2) Bulut modu için kurulum
1. `koaaN/Flasher_Builder` reposunu **fork** edin.
2. `repo-side/cloud_build.yml` dosyasını fork'ta `.github/workflows/cloud_build.yml` olarak ekleyin:
   ```bash
   gh repo fork koaaN/Flasher_Builder --clone && cd Flasher_Builder
   mkdir -p .github/workflows && cp /yol/repo-side/cloud_build.yml .github/workflows/
   git add . && git commit -m "cloud build" && git push
   ```
3. GitHub → Settings → Developer settings → **Fine-grained token**: yalnızca o fork, izin **Actions: Read and write**.
4. Uygulama → Bulutta Build → ⚙ Ayarlar: sahip, repo, token. Sonra cihaz/tür/bölge seçip OTA bağlantısını girin.

Not: Public fork'ta Actions dakikaları ücretsizdir; private'ta aylık ücretsiz kota geçerlidir.
Disk: backend ≈ 4× OTA boyutu boş alan ister (ZIP'siz 3×).

## 3) Kalıcı imza (isteğe bağlı)
Secret yoksa APK debug anahtarıyla imzalanır (kurulabilir ama güncellemede anahtar değişirse kaldırıp kurmak gerekir).
```bash
pkg install openjdk-17
keytool -genkeypair -v -keystore release.jks -alias key -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks | gh secret set KEYSTORE_B64
gh secret set KEYSTORE_PASSWORD; gh secret set KEY_ALIAS; gh secret set KEY_PASSWORD
```

## Arka plan çalışma
Takip/indirme `BuildService` (foreground, dataSync) içinde yürür; bildirimde ilerleme ve "İptal" vardır.
Android 15+'ta dataSync ~6 saatle sınırlıdır; süre dolarsa GitHub'daki build sürer, uygulamadan
"Önceki build'e devam et" ile indirmeye devam edilir.

## Katalog
`app/src/main/assets/catalog.json` (17 cihaz, 86 kombinasyon) fork'taki `build_tools.yml`'den üretilir.
Repo yeni cihaz ekleyince fork klasöründe: `python3 /yol/tools/export_catalog.py > catalog.json`.

## Yerel araçlar (arm64)
`tools/fetch_arm64_tools.sh` (CI'da otomatik çalışır) şunları SHA-256 doğrulamasıyla indirir:
- otaripper v3.5.0 resmi Android arm64 sürümü (Apache-2.0)
- lpmake (AOSP, Apache-2.0) — MIO-KITCHEN deposundaki sabit commit'ten statik arm64 binary
Binary'ler Git'e eklenmez (`.gitignore`), APK içine `lib*.so` adıyla girer.

## Cihazda Build için kalan iş (Faz 2)
Python worker'ın Android'e taşınması: Chaquopy; `sys.executable` ile alt süreç başlatan yardımcıların
süreç içi çalıştırılması; `aria2c/curl` yerine saf Python indirme; `x86_64` platform kontrolünün esnetilmesi;
araç yollarının `NativeTools`'a yönlendirilmesi.
