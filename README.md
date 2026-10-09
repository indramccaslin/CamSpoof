# CamSpoof

An **Xposed/LSPosed (Vector)** module to spoof the *device identity* written into **photo EXIF** metadata by camera apps. Photos look as if they were taken on another device (e.g. "Xiaomi 18 Pro Max") without changing the device identity globally.

## Features

- Changes EXIF **Make / Model / Brand** of photos (MIUI Camera, GCam, AGC, and others).
- Ready-made **identity presets**, selected right from the app:
  - `Default` — no spoofing (real device identity)
  - `Xiaomi 18 Pro Max`
  - `Xiaomi 17 Ultra`
  - `Xiaomi 15 Ultra`
- **Settings UI** built into the APK. Pick a preset → applied automatically and camera apps are restarted.
- Active only inside **camera app processes** — system `ro.product.*` stays untouched, so other apps / the system fingerprint are unaffected.
- Handles the `(device)` codename suffix that GCam / Google Camera appends to the model name (`ExifInterface` hook).

## Requirements

- **Rooted** phone: [KernelSU](https://kernelsu.org/) (or Magisk / KernelSU) installed.
- **Vector** framework (an LSPosed fork): typically `/data/adb/zgisk_vector` + `/data/adb/lspd`. (Stock LSPosed should also work.)
- No other dependencies.

## Installation

### 1. Download the APK

Grab `CamSpoof.apk` from the **[Releases](https://github.com/indramccaslin/CamSpoof/releases/latest)** page.

### 2. Install the APK

```sh
adb install -r -g CamSpoof.apk
# or install it via a file manager on the phone
```

### 3. Enable the module in Vector

- Open **Vector** (or LSPosed).
- Select the **CamSpoof** module.
- Toggle **Enable**.
- Set its **scope** to the camera apps you use (pick "Cameras" / "All apps").

Built-in camera packages the module already knows (full list in `src/.../CameraSpoof.java`):

```
com.android.camera          (MIUI camera)
com.android.MGC             (MCG/BSG GCam)
com.agc.gcam84              (AGC)
com.google.android.GoogleCamera
org.codeaurora.snapcam
com.sec.android.app.camera  (Samsung)
... and more
```

> You can also set the scope manually from a (rooted) terminal:
> ```sh
> su -c '/data/adb/lspd/cli scope set com.camspoof \
>   com.android.camera/0 com.android.MGC/0 com.agc.gcam84/0'
> ```

### 4. Reboot / restart the camera apps

Restart your camera apps (in case they were not injected yet). A reboot is optional but safest.

## Usage (Switching Identity)

1. Open the **CamSpoof** app from your launcher.
2. Pick the identity you want:
   - `Default` → spoofing **off** (real device identity).
   - `Xiaomi 18 Pro Max` / `Xiaomi 17 Ultra` / `Xiaomi 15 Ultra` → spoofing on.
3. Grant **root** the first time (KernelSU / root manager) so the app can write the config and restart the camera apps automatically.
4. Open a camera app and take a photo. Check the EXIF → `Make` / `Model` now match your selection.

### Verify EXIF

```sh
exiftool -Make -Model /sdcard/DCIM/Camera/<photo>.jpg
# Make  : Xiaomi
# Model : Xiaomi 18 Pro Max
```

## Advanced Configuration

The module reads `/data/local/tmp/camspoof_conf` (`key=value`, one per line):

```
make=Xiaomi
model=Xiaomi 18 Pro Max
brand=Xiaomi
```

- An empty or `default` `model` → spoofing off.
- This file is written automatically by the app UI whenever you change the preset. You can edit it manually (root needed), then restart the camera apps.

## How It Works

- `CameraSpoof` (the Xposed entry) runs only inside **camera app processes** (whitelist `CAMERA_PKGS`), so the global identity is never changed.
- Inside that process it:
  1. Overrides the `android.os.Build` static fields (`MODEL`, `MANUFACTURER`, `BRAND`).
  2. Hooks `SystemProperties.get/getString` for `ro.product.*.model/manufacturer/brand` keys.
  3. Hooks `ExifInterface.p(int,Object)` to strip the `(device)` codename suffix added by GCam / Google Camera.
- The identity values are read from `/data/local/tmp/camspoof_conf` by every camera process when it starts.

## Building from Source

Build requirements: OpenJDK, Android `build-tools` (`aapt2`, `d8`, `zipalign`, `apksigner`), `android.jar`, and the Xposed API (`api-82.jar`).

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

## Project Structure

```
CamSpoof.apk                              → module APK (release)
src/com/camspoof/CameraSpoof.java          → Xposed entry (spoof + Exif hook)
src/com/camspoof/SettingsActivity.java     → preset picker UI
AndroidManifest.xml                        → module manifest + activity
res/values/                                → label & xposedscope
assets/xposed_init                         → Xposed entry class list
```

## Notes

- This is a **personal project** for research/learning. The device identity belongs to the vendor; use it responsibly.
- Only **photo EXIF metadata** is affected — never the system `ro.product.*` or benchmark results.