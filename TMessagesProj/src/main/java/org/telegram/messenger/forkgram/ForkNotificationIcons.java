package org.telegram.messenger.forkgram;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;

/**
 * Tele iOS: selectable status bar notification icon.
 * Index 0 keeps the stock Telegram icon, all other options are iOS gray
 * vectors that stay visible on both light and dark backgrounds.
 */
public class ForkNotificationIcons {

    private static final int[] ICONS = {
        0,
        R.drawable.ntf_icon_plane,
        R.drawable.ntf_icon_bell,
        R.drawable.ntf_icon_shield,
        R.drawable.ntf_icon_star,
        R.drawable.ntf_icon_heart,
        R.drawable.ntf_icon_chat
    };

    private static final int[] NAMES = {
        R.string.TeleIOSNtfIconDefault,
        R.string.TeleIOSNtfIconPlane,
        R.string.TeleIOSNtfIconBell,
        R.string.TeleIOSNtfIconShield,
        R.string.TeleIOSNtfIconStar,
        R.string.TeleIOSNtfIconHeart,
        R.string.TeleIOSNtfIconChat
    };

    public static int count() {
        return ICONS.length;
    }

    public static int iconAt(int index) {
        if (index < 0 || index >= ICONS.length) {
            return 0;
        }
        return ICONS[index];
    }

    private static final String[] FALLBACK_NAMES = {
        "Default", "Telegram", "Bell", "Shield", "Star", "Heart", "Chat"
    };

    public static String nameAt(int index) {
        if (index < 0 || index >= NAMES.length) {
            return "";
        }
        // Tele iOS: never let a missing resource crash the settings page –
        // raw resources -> LocaleController asset lookup -> hardcoded fallback
        try {
            return ApplicationLoader.applicationContext.getString(NAMES[index]);
        } catch (Throwable ignore) {
            try {
                String value = LocaleController.getString(NAMES[index]);
                if (value != null && !value.startsWith("LOC_ERR")) {
                    return value;
                }
            } catch (Throwable ignore2) {}
            return index < FALLBACK_NAMES.length ? FALLBACK_NAMES[index] : "";
        }
    }

    public static int getIndex() {
        try {
            int index = MessagesController.getGlobalMainSettings().getInt("ntfStatusBarIcon", 0);
            if (index < 0 || index >= ICONS.length) {
                return 0;
            }
            return index;
        } catch (Exception ignore) {
            return 0;
        }
    }

    public static void setIndex(int index) {
        if (index < 0 || index >= ICONS.length) {
            index = 0;
        }
        MessagesController.getGlobalMainSettings().edit().putInt("ntfStatusBarIcon", index).commit();
    }

    public static int currentIconRes() {
        int res = iconAt(getIndex());
        return res != 0 ? res : R.drawable.notification;
    }

    public static String currentName() {
        int index = getIndex();
        String name = nameAt(index);
        return name;
    }
}
