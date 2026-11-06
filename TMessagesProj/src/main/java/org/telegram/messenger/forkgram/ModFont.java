package org.telegram.messenger.forkgram;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextPaint;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Map;

public class ModFont {

    private static final String PREF_NAME = "fork_mod_font";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_NAME = "font_name";
    private static final File FONT_FILE = new File(ApplicationLoader.getFilesDirFixed(), "modfont_custom.ttf");

    public static volatile boolean enabled;
    public static volatile String fontName;

    private static volatile Typeface regularTypeface;
    private static volatile Typeface boldTypeface;
    private static volatile Typeface italicTypeface;
    private static volatile Typeface boldItalicTypeface;
    private static volatile boolean initialized;
    private static volatile boolean systemApplied;
    private static volatile boolean callbackRegistered;

    private static Field[] themePaintFields;
    private static long lastSweep;

    private ModFont() {
    }

    // =========================================================
    // Global init — called once from ApplicationLoader.onCreate()
    // =========================================================

    public static void init() {
        try {
            ensureInitialized();
            if (enabled && regularTypeface != null) {
                applySystemFonts();
                registerActivityCallback();
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (ModFont.class) {
            if (initialized) {
                return;
            }
            try {
                Context context = ApplicationLoader.applicationContext;
                if (context == null) {
                    return;
                }
                fontName = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_NAME, null);
                enabled = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false) && FONT_FILE.exists();
                if (enabled) {
                    Typeface tf = Typeface.createFromFile(FONT_FILE);
                    if (tf == null) {
                        enabled = false;
                    } else {
                        setFamilyInternal(tf, false);
                    }
                }
                initialized = true;
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }

    private static void setFamilyInternal(Typeface tf, boolean applySystem) {
        regularTypeface = tf;
        try {
            boldTypeface = Typeface.create(tf, Typeface.BOLD);
            italicTypeface = Typeface.create(tf, Typeface.ITALIC);
            boldItalicTypeface = Typeface.create(tf, Typeface.BOLD_ITALIC);
        } catch (Throwable t) {
            FileLog.e(t);
            boldTypeface = tf;
            italicTypeface = tf;
            boldItalicTypeface = tf;
        }
        enabled = true;
        if (applySystem) {
            applySystemFonts();
        }
    }

    // =========================================================
    // System-wide font replacement (only inside our own process)
    // =========================================================

    @SuppressWarnings("unchecked")
    private static void applySystemFonts() {
        if (systemApplied || regularTypeface == null) {
            return;
        }
        synchronized (ModFont.class) {
            if (systemApplied) {
                return;
            }
            try {
                exemptHiddenApi();
            } catch (Throwable ignore) {
            }
            // 1) Replace Typeface.DEFAULT / DEFAULT_BOLD / SANS_SERIF / SERIF / MONOSPACE
            try {
                Field f = Typeface.class.getDeclaredField("sDefaults");
                f.setAccessible(true);
                Typeface[] old = (Typeface[]) f.get(null);
                Typeface[] arr = new Typeface[Math.max(5, old == null ? 5 : old.length)];
                arr[0] = regularTypeface;
                arr[1] = boldTypeface;
                arr[2] = italicTypeface;
                arr[3] = boldItalicTypeface;
                for (int i = 4; i < arr.length; i++) {
                    arr[i] = regularTypeface;
                }
                f.set(null, arr);
            } catch (Throwable t) {
                FileLog.e(t);
            }
            // 2) Replace system font families ("sans-serif", "serif", "monospace", ...)
            try {
                Field f = Typeface.class.getDeclaredField("sSystemFontMap");
                f.setAccessible(true);
                Map<String, Typeface> map = (Map<String, Typeface>) f.get(null);
                if (map != null) {
                    String[] regularFamilies = {
                        "sans-serif", "sans-serif-light", "sans-serif-thin", "sans-serif-condensed",
                        "sans-serif-condensed-light", "sans-serif-narrow", "sans-serif-smallcaps",
                        "serif", "monospace"
                    };
                    for (String name : regularFamilies) {
                        map.put(name, regularTypeface);
                    }
                    map.put("sans-serif-medium", boldTypeface);
                    map.put("sans-serif-black", boldTypeface);
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
            systemApplied = true;
        }
    }

    private static void exemptHiddenApi() {
        try {
            Method forName = Class.class.getDeclaredMethod("forName", String.class);
            Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
            Class<?> vmRuntimeClass = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
            if (vmRuntimeClass == null) {
                return;
            }
            Method getRuntime = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null);
            Method setHiddenApiExemptions = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "setHiddenApiExemptions", new Class[]{String[].class});
            Object vmRuntime = getRuntime.invoke(null);
            setHiddenApiExemptions.invoke(vmRuntime, new Object[]{new String[]{"L"}});
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    // =========================================================
    // Theme sweep — every TextPaint in Theme.java that still has
    // no font gets ours. Runs when an activity resumes.
    // =========================================================

    private static void registerActivityCallback() {
        if (callbackRegistered) {
            return;
        }
        try {
            Application app = (Application) ApplicationLoader.applicationContext;
            if (app == null) {
                return;
            }
            callbackRegistered = true;
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                }

                @Override
                public void onActivityStarted(Activity activity) {
                }

                @Override
                public void onActivityResumed(Activity activity) {
                    sweepThemePaints();
                }

                @Override
                public void onActivityPaused(Activity activity) {
                }

                @Override
                public void onActivityStopped(Activity activity) {
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                }
            });
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static void sweepThemePaints() {
        if (!enabled || regularTypeface == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSweep < 2000) {
            return;
        }
        lastSweep = now;
        try {
            if (themePaintFields == null) {
                Class<?> themeClass = Class.forName("org.telegram.ui.ActionBar.Theme");
                Field[] all = themeClass.getDeclaredFields();
                ArrayList<Field> paints = new ArrayList<>();
                for (Field f : all) {
                    if (f.getType() == TextPaint.class && Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        paints.add(f);
                    }
                }
                themePaintFields = paints.toArray(new Field[0]);
            }
            for (Field f : themePaintFields) {
                try {
                    TextPaint p = (TextPaint) f.get(null);
                    if (p != null && p.getTypeface() == null && !f.getName().contains("Emoji")) {
                        p.setTypeface(regularTypeface);
                    }
                } catch (Throwable ignore) {
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    // =========================================================
    // Typeface providers (hooks used by AndroidUtilities / Theme)
    // =========================================================

    /** Regular weight. Returns null when the custom font is off. */
    public static Typeface override(Typeface original) {
        ensureInitialized();
        return enabled ? regularTypeface : null;
    }

    /** Bold weight — used by AndroidUtilities.bold(). */
    public static Typeface overrideBold() {
        ensureInitialized();
        return enabled ? boldTypeface : null;
    }

    /** Weight picked from the asset name — used by AndroidUtilities.getTypeface(). */
    public static Typeface overridePath(String assetPath) {
        ensureInitialized();
        if (!enabled || regularTypeface == null) {
            return null;
        }
        if (assetPath == null) {
            return regularTypeface;
        }
        String p = assetPath.toLowerCase();
        boolean italic = p.contains("italic");
        boolean bold = p.contains("bold") || p.contains("medium") || p.contains("black") || p.contains("extrabold");
        if (bold && italic) {
            return boldItalicTypeface;
        }
        if (bold) {
            return boldTypeface;
        }
        if (italic) {
            return italicTypeface;
        }
        return regularTypeface;
    }

    // =========================================================
    // UI helpers (used by ForkSettingsActivity)
    // =========================================================

    public static String currentStateText() {
        ensureInitialized();
        if (enabled && fontName != null) {
            return fontName;
        }
        return "Default";
    }

    public static boolean applyFromUri(Context context, Uri uri) {
        try {
            File tmp = new File(ApplicationLoader.getFilesDirFixed(), "modfont_tmp.ttf");
            InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) {
                return false;
            }
            OutputStream out = new FileOutputStream(tmp);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            out.close();
            in.close();
            Typeface typeface = Typeface.createFromFile(tmp);
            if (typeface == null) {
                tmp.delete();
                return false;
            }
            if (FONT_FILE.exists()) {
                FONT_FILE.delete();
            }
            if (!tmp.renameTo(FONT_FILE)) {
                tmp.renameTo(FONT_FILE);
            }
            initialized = true;
            fontName = queryName(context, uri);
            Context app = ApplicationLoader.applicationContext;
            if (app != null) {
                app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_ENABLED, true)
                    .putString(KEY_NAME, fontName)
                    .apply();
            }
            setFamilyInternal(typeface, true);
            registerActivityCallback();
            return true;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    public static void reset() {
        enabled = false;
        regularTypeface = null;
        boldTypeface = null;
        italicTypeface = null;
        boldItalicTypeface = null;
        systemApplied = false;
        initialized = true;
        fontName = null;
        Context app = ApplicationLoader.applicationContext;
        if (app != null) {
            app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply();
        }
        if (FONT_FILE.exists()) {
            FONT_FILE.delete();
        }
    }

    public static void restartApp(Context context) {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(intent);
            }
            Runtime.getRuntime().exit(0);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static String queryName(Context context, Uri uri) {
        try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    return cursor.getString(idx);
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return "custom.ttf";
    }
}
