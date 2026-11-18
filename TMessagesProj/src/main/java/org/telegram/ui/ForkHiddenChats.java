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

    public static void hideDialogs(BaseFragment fragment, ArrayList<Long> dialogIds, Runnable onDone) {
        if (fragment == null || dialogIds == null || dialogIds.isEmpty()) {
            return;
        }
        int account = fragment.getCurrentAccount();
        if (!hasPin(account)) {
            showSetPinDialog(fragment, () -> doHide(fragment, dialogIds, onDone));
        } else {
            doHide(fragment, dialogIds, onDone);
        }
    }

    private static void doHide(BaseFragment fragment, ArrayList<Long> dialogIds, Runnable onDone) {
        int account = fragment.getCurrentAccount();
        Set<Long> ids = new HashSet<>(getHiddenIds(account));
        ids.addAll(dialogIds);
        saveIds(account, ids);
        if (onDone != null) {
            onDone.run();
        }
        CharSequence text = dialogIds.size() == 1
                ? LocaleController.getString(R.string.ForkgramChatHidden)
                : LocaleController.formatString("ForkgramChatsHidden", R.string.ForkgramChatsHidden, dialogIds.size());
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.done, text).show();
    }

    /**
     * Long-press on the Edit capsule: gate the hidden chats list behind the PIN.
     */
    public static void openHiddenChats(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        int account = fragment.getCurrentAccount();
        if (!hasPin(account)) {
            if (getHiddenIds(account).isEmpty()) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.ForkgramNoHiddenChatsYet)).show();
            } else {
                showSetPinDialog(fragment, () -> presentHiddenChats(fragment));
            }
            return;
        }
        showPinInputDialog(fragment,
                LocaleController.getString(R.string.ForkgramEnterPinTitle),
                LocaleController.getString(R.string.ForkgramEnterPinText),
                pin -> {
                    String storedHash = prefs(account).getString("pin_hash", null);
                    if (storedHash != null && hashPin(pin).equals(storedHash)) {
                        presentHiddenChats(fragment);
                    } else {
                        showWrongPinAlert(fragment);
                    }
                });
    }

    private static void presentHiddenChats(BaseFragment fragment) {
        fragment.presentFragment(new ForkHiddenChatsActivity());
    }

    private static void showWrongPinAlert(BaseFragment fragment) {
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
        builder.setTitle(LocaleController.getString(R.string.ForkgramEnterPinTitle));
        builder.setMessage(LocaleController.getString(R.string.ForkgramWrongPin));
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> openHiddenChats(fragment));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(builder.create());
    }

    private static void showSetPinDialog(BaseFragment fragment, Runnable onSet) {
        showPinInputDialog(fragment,
                LocaleController.getString(R.string.ForkgramSetPinTitle),
                LocaleController.getString(R.string.ForkgramSetPinText),
                pin -> showPinInputDialog(fragment,
                        LocaleController.getString(R.string.ForkgramSetPinTitle),
                        LocaleController.getString(R.string.ForkgramConfirmPinText),
                        confirmed -> {
                            if (hashPin(confirmed).equals(hashPin(pin))) {
                                prefs(fragment.getCurrentAccount()).edit().putString("pin_hash", hashPin(pin)).apply();
                                onSet.run();
                            } else {
                                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.ForkgramPinsDontMatch)).show();
                                showSetPinDialog(fragment, onSet);
                            }
                        }));
    }

    private static void showPinInputDialog(BaseFragment fragment, CharSequence title, CharSequence message, Consumer<String> onPin) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        android.content.Context context = fragment.getParentActivity();
        FrameLayout container = new FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(28), AndroidUtilities.dp(12), AndroidUtilities.dp(28), 0);
        EditText editText = new EditText(context);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        editText.setGravity(Gravity.CENTER);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        editText.setSingleLine(true);
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, fragment.getResourceProvider());
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog alertDialog = builder.create();
        alertDialog.setOnShowListener(d -> {
            // Telegram's custom AlertDialog builds its buttons as TextViews (getButton returns
            // buttonsLayout.findViewWithTag), so casting to android.widget.Button crashes
            TextView button = (TextView) alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (button != null) {
                button.setOnClickListener(v -> {
                    String pin = editText.getText().toString();
                    if (pin.length() == 4) {
                        alertDialog.dismiss();
                        onPin.accept(pin);
                    } else {
                        editText.setError(LocaleController.getString(R.string.ForkgramPinMustBe4));
                    }
                });
            }
        });
        fragment.showDialog(alertDialog);
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
