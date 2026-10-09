package com.camspoof;

import android.os.Build;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Spoof identitas device HANYA di dalam proses aplikasi kamera, sehingga EXIF
 * foto menunjukkan device yang dipilih di UI (mis. "Xiaomi 18 Pro Max").
 * Pilihan dibaca dari /data/local/tmp/camspoof_conf; jika kosong -> tanpa spoof.
 */
public class CameraSpoof implements IXposedHookLoadPackage {

    private static final String CONF = "/data/local/tmp/camspoof_conf";

    private static final String[] CAMERA_PKGS = {
            "com.android.camera", "com.android.camera2", "com.miui.camera",
            "com.xiaomi.camera", "com.google.android.GoogleCamera",
            "com.google.android.GoogleCameraGo", "com.android.MGC",
            "org.codeaurora.snapcam", "net.sourceforge.opencamera",
            "com.sec.android.app.camera", "com.samsung.android.camera",
            "com.oppo.camera", "com.oplus.camera", "com.oneplus.camera",
            "com.huawei.camera", "com.motorola.camera", "com.motorola.camera2",
            "com.sonyericsson.android.camera", "com.asus.camera",
            "com.nothing.camera", "com.hmdglobal.app.camera",
            "com.agc.gcam84", "com.agc.gcam"
    };

    private static boolean isCamera(String pkg) {
        if (pkg == null) return false;
        for (String p : CAMERA_PKGS) {
            if (p.equals(pkg)) return true;
        }
        String lp = pkg.toLowerCase();
        return lp.contains("camera") || lp.contains("gcam");
    }

    /** @return {make, model, brand} atau null kalau spoof dimatikan. */
    private static String[] readDevice() {
        try {
            java.io.File f = new java.io.File(CONF);
            if (!f.exists() || !f.canRead()) return null;
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(
                            new java.io.FileInputStream(f), "UTF-8"));
            String make = "", model = "", brand = "";
            String l;
            while ((l = r.readLine()) != null) {
                int eq = l.indexOf('=');
                if (eq < 0) continue;
                String k = l.substring(0, eq).trim();
                String v = l.substring(eq + 1).trim();
                if (k.equals("make")) make = v;
                else if (k.equals("model")) model = v;
                else if (k.equals("brand")) brand = v;
            }
            r.close();
            if (model.isEmpty() || model.equalsIgnoreCase("default")) return null;
            if (make.isEmpty()) make = "Xiaomi";
            if (brand.isEmpty()) brand = make;
            return new String[]{make, model, brand};
        } catch (Throwable t) {
            XposedBridge.log("CamSpoof[cfg]: " + t);
            return null;
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null || lpparam.packageName == null) return;
        if (!isCamera(lpparam.packageName)) return;

        final String[] dev = readDevice();
        if (dev == null) {
            XposedBridge.log("CamSpoof: default (tanpa spoof) di " + lpparam.packageName);
            return;
        }
        final String make = dev[0];
        final String model = dev[1];
        final String brand = dev[2];

        ClassLoader cl = lpparam.classLoader;

        // 1) Timpa field statis android.os.Build di proses kamera ini saja
        try {
            XposedHelpers.setStaticObjectField(Build.class, "MODEL", model);
            XposedHelpers.setStaticObjectField(Build.class, "MANUFACTURER", make);
            XposedHelpers.setStaticObjectField(Build.class, "BRAND", brand);
        } catch (Throwable t) {
            XposedBridge.log("CamSpoof[Build]: " + t);
            try {
                setFieldFallback(cl, "MODEL", model);
                setFieldFallback(cl, "MANUFACTURER", make);
                setFieldFallback(cl, "BRAND", brand);
            } catch (Throwable t2) {
                XposedBridge.log("CamSpoof[Build-fallback]: " + t2);
            }
        }

        // 2) Hook SystemProperties.get() untuk namespace model/manufacturer/brand
        PropHook ph = new PropHook(make, model, brand);
        for (String sig : new String[]{"get", "getString"}) {
            try {
                XposedHelpers.findAndHookMethod("android.os.SystemProperties", cl,
                        sig, String.class, ph);
            } catch (Throwable ignored) {
            }
            try {
                XposedHelpers.findAndHookMethod("android.os.SystemProperties", cl,
                        sig, String.class, String.class, ph);
            } catch (Throwable ignored) {
            }
        }

        // 3) Util kamera Google membangun Model = Build.MODEL + " (" + Build.DEVICE + ")"
        //    sebelum disimpan via ExifInterface.p(int,Object). Bersihkan sufiks itu.
        hookExif(cl, model);

        XposedBridge.log("CamSpoof: aktif di " + lpparam.packageName
                + " -> " + make + " " + model);
    }

    private static final class PropHook extends XC_MethodHook {
        private final String make, model, brand;
        PropHook(String make, String model, String brand) {
            this.make = make; this.model = model; this.brand = brand;
        }
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (param.args == null || param.args.length == 0) return;
            Object k = param.args[0];
            if (!(k instanceof String)) return;
            String v = overrideFor((String) k, make, model, brand);
            if (v != null) param.setResult(v);
        }
    }

    private static String overrideFor(String key, String make, String model, String brand) {
        if (key == null) return null;
        switch (key) {
            case "ro.product.model":
            case "ro.product.system.model":
            case "ro.product.vendor.model":
            case "ro.product.odm.model":
            case "ro.product.product.model":
            case "ro.product.system_ext.model":
            case "ro.product.vendor_dlkm.model":
            case "ro.product.marketname":
            case "ro.product.vendor.marketname":
            case "ro.product.system.marketname":
                return model;
            case "ro.product.manufacturer":
            case "ro.product.system.manufacturer":
            case "ro.product.vendor.manufacturer":
            case "ro.product.odm.manufacturer":
                return make;
            case "ro.product.brand":
            case "ro.product.system.brand":
            case "ro.product.vendor.brand":
                return brand;
            default:
                return null;
        }
    }

    private static void setFieldFallback(ClassLoader cl, String name, String value) throws Throwable {
        Class<?> build = XposedHelpers.findClass("android.os.Build", cl);
        java.lang.reflect.Field f = build.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    private static void hookExif(ClassLoader cl, final String model) {
        try {
            Class<?> exif = XposedHelpers.findClass(
                    "com.google.android.libraries.camera.exif.ExifInterface", cl);
            int hooked = 0;
            for (java.lang.reflect.Method m : exif.getDeclaredMethods()) {
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length == 2 && pt[0] == int.class && pt[1] == Object.class
                        && m.getReturnType() != void.class) {
                    XposedBridge.hookMethod(m, new ExifModelHook(model));
                    hooked++;
                }
            }
            XposedBridge.log("CamSpoof[Exif]: hooked " + hooked + " builder(s)");
        } catch (Throwable t) {
            XposedBridge.log("CamSpoof[Exif]: " + t);
        }
    }

    private static final class ExifModelHook extends XC_MethodHook {
        private final String model;
        ExifModelHook(String model) { this.model = model; }
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (param.args == null || param.args.length < 2) return;
            Object v = param.args[1];
            if (!(v instanceof String)) return;
            String s = (String) v;
            String prefix = model + " (";
            if (s.startsWith(prefix) && s.endsWith(")")) {
                param.args[1] = model;
            }
        }
    }
}
