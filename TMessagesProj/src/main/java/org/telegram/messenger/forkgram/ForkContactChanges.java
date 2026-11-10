package org.telegram.messenger.forkgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Tele iOS: Contact Change log.
 *
 * Records changes that contacts make to their own profile – name, username,
 * bio, phone number and profile photo – as reported by the server updates.
 * The log is shown in Tele iOS Settings → Contact Change.
 */
public final class ForkContactChanges {

    private static final String PREF_NAME = "fork_contact_changes";
    private static final String KEY_LOG = "log";
    private static final String KEY_BASE = "base_";
    private static final int MAX_ENTRIES = 300;

    private ForkContactChanges() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static TLRPC.User user(int account, int uid) {
        try {
            return MessagesController.getInstance(account).getUser(uid);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean eligible(TLRPC.User user) {
        return user != null && !UserObject.isUserSelf(user) && UserObject.isContact(user);
    }

    private static String displayName(TLRPC.User user) {
        if (user == null) {
            return "";
        }
        String name = (TextUtils.isEmpty(user.first_name) ? "" : user.first_name)
                + (TextUtils.isEmpty(user.last_name) ? "" : " " + user.last_name);
        if (TextUtils.isEmpty(name.trim()) && !TextUtils.isEmpty(user.username)) {
            name = "@" + user.username;
        }
        return name.trim();
    }

    private static JSONObject readBase(int account, int uid) {
        try {
            String raw = prefs().getString(KEY_BASE + account + "_" + uid, null);
            if (!TextUtils.isEmpty(raw)) {
                return new JSONObject(raw);
            }
        } catch (JSONException e) {
            FileLog.e(e);
        }
        return new JSONObject();
    }

    private static void writeBase(int account, int uid, JSONObject base) {
        prefs().edit().putString(KEY_BASE + account + "_" + uid, base.toString()).apply();
    }

    private static void addEntry(int account, int uid, String field, String from, String to) {
        try {
            TLRPC.User user = user(account, uid);
            if (!eligible(user)) {
                return;
            }
            JSONObject entry = new JSONObject();
            entry.put("t", System.currentTimeMillis() / 1000L);
            entry.put("name", displayName(user));
            entry.put("field", field);
            entry.put("from", from == null ? "" : from);
            entry.put("to", to == null ? "" : to);
            JSONArray log = getLog();
            JSONArray updated = new JSONArray();
            updated.put(entry);
            for (int i = 0; i < log.length() && i < MAX_ENTRIES - 1; i++) {
                updated.put(log.get(i));
            }
            prefs().edit().putString(KEY_LOG, updated.toString()).apply();
        } catch (JSONException e) {
            FileLog.e(e);
        }
    }

    /** Server reported a name (and/or username) update for a user. */
    public static void checkName(int account, int uid, String newFirst, String newLast, String newUsername) {
        try {
            TLRPC.User user = user(account, uid);
            if (!eligible(user)) {
                return;
            }
            JSONObject base = readBase(account, uid);
            if (!base.has("fn")) {
                base.put("fn", nz(newFirst)).put("ln", nz(newLast)).put("un", nz(newUsername));
                writeBase(account, uid, base);
                return;
            }
            boolean changed = false;
            String oldFirst = base.optString("fn"), oldLast = base.optString("ln"), oldUsername = base.optString("un");
            if (!TextUtils.equals(oldFirst, nz(newFirst)) || !TextUtils.equals(oldLast, nz(newLast))) {
                String oldName = (oldFirst + " " + oldLast).trim();
                String newName = (nz(newFirst) + " " + nz(newLast)).trim();
                addEntry(account, uid, "Name", oldName, newName);
                changed = true;
            }
            if (!TextUtils.equals(oldUsername, nz(newUsername))) {
                String from = TextUtils.isEmpty(oldUsername) ? "—" : "@" + oldUsername;
                String to = TextUtils.isEmpty(nz(newUsername)) ? "—" : "@" + newUsername;
                addEntry(account, uid, "Username", from, to);
                changed = true;
            }
            if (changed || !base.has("fn")) {
                base.put("fn", nz(newFirst)).put("ln", nz(newLast)).put("un", nz(newUsername));
                writeBase(account, uid, base);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /** Server reported a phone number update for a user. */
    public static void checkPhone(int account, int uid, String newPhone) {
        try {
            TLRPC.User user = user(account, uid);
            if (!eligible(user)) {
                return;
            }
            JSONObject base = readBase(account, uid);
            String old = base.has("ph") ? base.optString("ph") : null;
            if (!base.has("ph")) {
                base.put("ph", nz(newPhone));
                writeBase(account, uid, base);
                return;
            }
            if (!TextUtils.equals(old, nz(newPhone))) {
                addEntry(account, uid, "Phone", formatPhone(old), formatPhone(newPhone));
                base.put("ph", nz(newPhone));
                writeBase(account, uid, base);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /** Server reported a profile photo update for a user (photoId 0 = removed). */
    public static void checkPhoto(int account, int uid, long newPhotoId) {
        try {
            TLRPC.User user = user(account, uid);
            if (!eligible(user)) {
                return;
            }
            JSONObject base = readBase(account, uid);
            if (!base.has("pi")) {
                base.put("pi", newPhotoId);
                writeBase(account, uid, base);
                return;
            }
            long old = base.optLong("pi", 0);
            if (old != newPhotoId) {
                addEntry(account, uid, "Photo", old == 0 ? "—" : "changed", newPhotoId == 0 ? "—" : "changed");
                base.put("pi", newPhotoId);
                writeBase(account, uid, base);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /** Full user info arrived – track the bio ("about") field. */
    public static void checkBio(int account, int uid, String newAbout) {
        try {
            TLRPC.User user = user(account, uid);
            if (!eligible(user)) {
                return;
            }
            JSONObject base = readBase(account, uid);
            String old = base.has("ab") ? base.optString("ab") : null;
            if (!base.has("ab")) {
                base.put("ab", nz(newAbout));
                writeBase(account, uid, base);
                return;
            }
            if (!TextUtils.equals(old, nz(newAbout))) {
                String from = TextUtils.isEmpty(old) ? "—" : old;
                String to = TextUtils.isEmpty(nz(newAbout)) ? "—" : newAbout;
                addEntry(account, uid, "Bio", from, to);
                base.put("ab", nz(newAbout));
                writeBase(account, uid, base);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String formatPhone(String phone) {
        if (TextUtils.isEmpty(phone)) {
            return "—";
        }
        return "+" + phone;
    }

    /** Persisted log, newest entry first. */
    public static JSONArray getLog() {
        try {
            String raw = prefs().getString(KEY_LOG, null);
            if (!TextUtils.isEmpty(raw)) {
                return new JSONArray(raw);
            }
        } catch (JSONException e) {
            FileLog.e(e);
        }
        return new JSONArray();
    }

    public static int getLogCount() {
        return getLog().length();
    }

    /** Short subtitle shown under the Contact Change row in settings. */
    public static String summaryText() {
        int count = getLogCount();
        if (count == 0) {
            return LocaleController.getString(R.string.ContactChangesNone);
        }
        return LocaleController.formatString(R.string.ContactChangesCount, count);
    }

    public static void clearLog() {
        prefs().edit().putString(KEY_LOG, null).apply();
    }

    public static String formatTimestamp(long seconds) {
        return new SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.US).format(new Date(seconds * 1000L));
    }
}
