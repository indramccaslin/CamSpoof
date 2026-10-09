# CamSpoof

Modul **Xposed/LSPosed (Vector)** untuk melewati identitas *device* di **EXIF foto** yang diambil dari aplikasi kamera. Foto hasil jepret akan terlihat seperti diambil oleh device lain (contoh: "Xiaomi 18 Pro Max") tanpa mengubah identitas device secara global di sistem.

## Fitur

- Ubah **Make / Model / Brand** (EXIF) foto kamera (MIUI camera, GCam, AGC, dan lainnya).
- **Preset identitas** siap pakai, dipilih langsung dari app:
  - `Default (Xiaomi Mi 9)` — tanpa spoof (identitas asli)
  - `Xiaomi 18 Pro Max`
  - `Xiaomi 17 Ultra`
  - `Xiaomi 15 Ultra`
- **UI settings** di dalam APK (bukan lewat manager). Pilih preset → otomatis diterapkan & app kamera di-restart.
- Hanya aktif di **proses aplikasi kamera** — tidak merusak `ro.product.*` di sistem, jadi app lain / SafetyNet fingerprint sistem tidak terpengaruh.
- Menangani modifikasi otomatis GCam/Google Camera yang menambahkan codename `(device)` di belakang nama model (hook `ExifInterface`).

## Persyaratan

- HP **root**: [KernelSU](https://kernelsu.org/) (atau Magisk/KernelSU) terpasang.
- Framework **Vector** (fork LSPosed): jalur umum `/data/adb/zgisk_vector` + `/data/adb/lspd`. (LSPosed biasa juga seharusnya berfungsi.)
- Tidak ada dependensi tambahan.

## Cara Pasang

### 1. Download APK

Ambil `CamSpoof.apk` dari halaman **[Releases](https://github.com/indramccaslin/CamSpoof/releases/latest)** → download asset `CamSpoof.apk`.

### 2. Install APK

```sh
adb install -r -g CamSpoof.apk
# atau lewat file manager di HP
```

### 3. Aktifkan modul di Vector

- Buka app **Vector** (atau LSPosed).
- Pilih modul **CamSpoof**.
- Centang **Aktifkan / Enable**.
- Set **scope** ke aplikasi kamera yang dipakai (tampilkan daftar "Kamera" / "All apps").

Scope bawaan yang sudah dikenal modul (daftar lengkap ada di `src/.../CameraSpoof.java`):

```
com.android.camera          (MIUI camera)
com.android.MGC             (MCG/BSG GCam)
com.agc.gcam84              (AGC)
com.google.android.GoogleCamera
org.codeaurora.snapcam
com.sec.android.app.camera  (Samsung)
... dan lainnya
```

> Lewat terminal (root), scope bisa diset manual:
> ```sh
> su -c '/data/adb/lspd/cli scope set com.camspoof \
>   com.android.camera/0 com.android.MGC/0 com.agc.gcam84/0'
> ```

### 4. Reboot / restart kamera

Restart aplikasi kamera (jika belum ter-inject lagi). Reboot HP opsional namun paling aman.

## Cara Pakai (Ganti Identitas)

1. Buka app **CamSpoof** dari launcher.
2. Pilih identitas yang diinginkan:
   - `Default (Xiaomi Mi 9)` → spoof **mati**.
   - `Xiaomi 18 Pro Max` / `Xiaomi 17 Ultra` / `Xiaomi 15 Ultra` → spoof aktif.
3. Izin **root** diminta **sekali** (KernelSU/manager root) agar app bisa menulis konfigurasi & me-restart kamera otomatis.
4. Buka aplikasi kamera, jepret. Cek EXIF foto → `Make`/`Model` sudah sesuai pilihan.

### Verifikasi EXIF

```sh
exiftool -Make -Model /sdcard/DCIM/Camera/<foto>.jpg
# Make  : Xiaomi
# Model : Xiaomi 18 Pro Max
```

## Konfigurasi (Lanjutan)

Modul membaca file `/data/local/tmp/camspoof_conf` (format `key=value`, 1 baris per key):

```
make=Xiaomi
model=Xiaomi 18 Pro Max
brand=Xiaomi
```

- `model` kosong atau `default` → spoof mati.
- File ini ditulis otomatis oleh UI app ketika mengganti preset. Edit manual juga bisa (perlu root), lalu restart kamera.

## Cara Kerja

- `CameraSpoof` (entry Xposed) hanya berjalan di proses **aplikasi kamera** (whitelist `CAMERA_PKGS`), sehingga tidak mengubah identitas global.
- Di proses tersebut:
  1. Field statis `android.os.Build` (`MODEL`, `MANUFACTURER`, `BRAND`) ditimpa.
  2. `SystemProperties.get/getString` untuk key `ro.product.*.model/manufacturer/brand` di-hook.
  3. `ExifInterface.p(int,Object)` di-hook untuk menghapus sufiks codename `(device)` yang ditambahkan GCam/Google Camera.
- Nilai identitas dibaca dari `/data/local/tmp/camspoof_conf` oleh tiap proses kamera saat di-load.

## Build dari Source

Persyaratan build: OpenJDK, Android `build-tools` (`aapt2`, `d8`, `zipalign`, `apksigner`), `android.jar`, dan Xposed API (`api-82.jar`).

```sh
AJ=<path>/android.jar
APJ=<path>/api-82.jar

aapt2 compile --dir res -o build/res.zip
aapt2 link -o build/base.apk -I "$AJ" --manifest AndroidManifest.xml \
  build/res.zip --min-sdk-version 21 --target-sdk-version 34 --auto-add-overlay

javac -source 8 -target 8 -bootclasspath "$AJ" -classpath "$AJ:$APJ" \
  -d build/classes $(find src -name '*.java')

d8 --lib "$AJ" --lib "$APJ" --min-api 21 --output build/dex $(find build/classes -name '*.class')

cp build/base.apk build/unsigned.apk
zip -qj build/unsigned.apk build/dex/classes.dex
zip -q build/unsigned.apk assets/xposed_init

zipalign -f -p 4 build/unsigned.apk build/aligned.apk
apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out CamSpoof.apk build/aligned.apk
```

## Struktur Project

```
CamSpoof.apk                              → APK modul (release)
src/com/opencode/camspoof/CameraSpoof.java   → entry Xposed (spoof + Exif hook)
src/com/opencode/camspoof/SettingsActivity.java → UI pilih preset
AndroidManifest.xml                        → manifest modul + activity
res/values/                                → label & xposedscope
assets/xposed_init                         → daftar entry class Xposed
```

## Catatan

- Modul ini adalah **proyek pribadi** untuk riset/pembelajaran. Identitas device tetap milik vendor; gunakan dengan bijak.
- Perubahan identitas hanya terjadi pada **metadata EXIF** foto, bukan pada `ro.product.*` sistem atau hasil benchmark.