package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Forkgram: hidden chats storage and PIN management.
 * A hidden dialog disappears from every chat list but stays fully functional
 * (messages keep arriving). Hidden dialogs are reachable by long-pressing the
 * Edit capsule in the chats header and entering the 4-digit PIN.
 */
public class ForkHiddenChats {

    private static SharedPreferences prefs(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences("fork_hiddenchats_" + account, Context.MODE_PRIVATE);
    }

    private static final android.util.SparseArray<Set<Long>> hiddenCache = new android.util.SparseArray<>();

    public static Set<Long> getHiddenIds(int account) {
        Set<Long> cached = hiddenCache.get(account);
        if (cached != null) {
            return cached;
        }
        Set<String> stored = prefs(account).getStringSet("ids", null);
        Set<Long> ids = new HashSet<>();
        if (stored != null) {
            for (String s : stored) {
                try {
                    ids.add(Long.parseLong(s));
                } catch (Exception ignore) {}
            }
        }
        hiddenCache.put(account, ids);
        return ids;
    }

    public static void saveIds(int account, Set<Long> ids) {
        Set<String> stored = new HashSet<>();
        for (long id : ids) {
            stored.add(String.valueOf(id));
        }
        prefs(account).edit().putStringSet("ids", stored).apply();
        hiddenCache.put(account, new HashSet<>(ids));
    }

    public static boolean isHidden(int account, long dialogId) {
        return getHiddenIds(account).contains(dialogId);
    }

    public static boolean hasPin(int account) {
        return prefs(account).contains("pin_hash");
    }

    public static ArrayList<TLRPC.Dialog> filterHidden(int account, ArrayList<TLRPC.Dialog> array) {
        Set<Long> hidden = getHiddenIds(account);
        if (hidden.isEmpty() || array == null || array.isEmpty()) {
            return array;
        }
        ArrayList<TLRPC.Dialog> filtered = new ArrayList<>(array.size());
        for (int a = 0, N = array.size(); a < N; a++) {
            TLRPC.Dialog dialog = array.get(a);
            if (dialog != null && hidden.contains(dialog.id)) {
                continue;
            }
            filtered.add(dialog);
        }
        return filtered;
    }

    /**
     * Tele iOS: lightweight breadcrumb trail for the hide flow, appended to
     * fork_debug.txt in the app's external files dir; lets a "silent" crash be
     * pinpointed to the exact step even when no Java stack trace is produced.
     */
    private static void trace(String what) {
        try {
            android.content.Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) {
                return;
            }
            java.io.File dir = ctx.getExternalFilesDir(null);
            if (dir == null) {
                return;
            }
            java.io.File f = new java.io.File(dir, "fork_debug.txt");
            if (f.length() > 256 * 1024) {
                f.delete();
            }
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            w.write(new java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(new java.util.Date()) + " " + what + "\n");
            w.close();
        } catch (Throwable ignored) {}
    }

    public static void hideDialogs(BaseFragment fragment, ArrayList<Long> dialogIds, Runnable onDone) {
        if (fragment == null || dialogIds == null || dialogIds.isEmpty()) {
            return;
        }
        int account = fragment.getCurrentAccount();
        trace("hideDialogs count=" + dialogIds.size() + " hasPin=" + hasPin(account));
        if (!hasPin(account)) {
            // Tele iOS: first hide sets the PIN on a full-screen code page, not a dialog
            fragment.presentFragment(new ForkCodeEntryActivity(
                    LocaleController.getString(R.string.ForkgramSetPinTitle),
                    LocaleController.getString(R.string.ForkgramSetPinText),
                    (java.util.function.Consumer<String>) pin -> {
                        prefs(account).edit().putString("pin_hash", hashPin(pin)).apply();
                        trace("pinSaved");
                        doHide(fragment, dialogIds, onDone);
                    }));
        } else {
            doHide(fragment, dialogIds, onDone);
        }
    }

    private static void doHide(BaseFragment fragment, ArrayList<Long> dialogIds, Runnable onDone) {
        int account = fragment.getCurrentAccount();
        trace("doHide ids=" + dialogIds.size());
        Set<Long> ids = new HashSet<>(getHiddenIds(account));
        ids.addAll(dialogIds);
        saveIds(account, ids);
        trace("doHideSaved");
        if (onDone != null) {
            trace("onDoneStart");
            onDone.run();
            trace("onDoneEnd");
        }
        CharSequence text = dialogIds.size() == 1
                ? LocaleController.getString(R.string.ForkgramChatHidden)
                : LocaleController.formatString("ForkgramChatsHidden", R.string.ForkgramChatsHidden, dialogIds.size());
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.done, text).show();
        trace("bulletinShown");
    }

    /**
     * Long-press on the Edit capsule: gate the hidden chats list behind the PIN
     * on a full-screen code page (never a dialog).
     */
    public static void openHiddenChats(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        int account = fragment.getCurrentAccount();
        trace("openHiddenChats hasPin=" + hasPin(account));
        if (!hasPin(account)) {
            if (getHiddenIds(account).isEmpty()) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.ForkgramNoHiddenChatsYet)).show();
            } else {
                presentSetPin(fragment, account);
            }
            return;
        }
        fragment.presentFragment(new ForkCodeEntryActivity(
                LocaleController.getString(R.string.ForkgramEnterPinTitle),
                LocaleController.getString(R.string.ForkgramEnterPinText),
                pin -> {
                    String storedHash = prefs(account).getString("pin_hash", null);
                    return storedHash != null && hashPin(pin).equals(storedHash);
                },
                () -> {
                    trace("pinVerified");
                    presentHiddenChats(fragment);
                }));
    }

    private static void presentSetPin(BaseFragment fragment, int account) {
        fragment.presentFragment(new ForkCodeEntryActivity(
                LocaleController.getString(R.string.ForkgramSetPinTitle),
                LocaleController.getString(R.string.ForkgramSetPinText),
                (java.util.function.Consumer<String>) pin -> {
                    prefs(account).edit().putString("pin_hash", hashPin(pin)).apply();
                    trace("pinSaved");
                    presentHiddenChats(fragment);
                }));
    }

    private static void presentHiddenChats(BaseFragment fragment) {
        fragment.presentFragment(new ForkHiddenChatsActivity());
    }

    private static String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(("forkgram::" + pin).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(pin.hashCode());
        }
    }
}
