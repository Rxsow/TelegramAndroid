package org.telegram.messenger.forkgram

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64

import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ContactsController
import org.telegram.messenger.FileLog
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationsController
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.tgnet.ConnectionsManager

import java.nio.charset.StandardCharsets

/**
 * Tele iOS: hidden accounts, redesigned.
 *
 * One global 4-digit code guards the whole feature (no per-account codes, no
 * stealth mode). Hidden accounts are a plain list of account numbers; they
 * disappear from every account picker, the contacts sync and the notifications
 * until the code is entered on the full-screen code page, which unlocks the
 * session until the app is restarted (or re-locked on a new launch intent).
 */
object HiddenAccountHelper {

    private const val PREFS_NAME = "mainconfig"
    private const val KEY_LOCK_HASH = "fg_hiddenAccountsLockHash"
    private const val KEY_LOCK_SALT = "fg_hiddenAccountsLockSalt"
    private const val KEY_HIDDEN_IDS = "fg_hiddenAccountsIds"

    private val CODE_PATTERN = Regex("\\d{4}")

    private var sessionUnlocked = false

    private val preferences: SharedPreferences
        get() = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---- global 4-digit code ------------------------------------------------

    @JvmStatic
    fun hasCode(): Boolean {
        val settings = preferences
        return settings.contains(KEY_LOCK_HASH) && settings.contains(KEY_LOCK_SALT)
    }

    @JvmStatic
    fun isValidCode(code: String?): Boolean {
        return code != null && CODE_PATTERN.matches(code)
    }

    @JvmStatic
    fun checkCode(code: String?): Boolean {
        if (!isValidCode(code)) {
            return false
        }
        val settings = preferences
        val hash = settings.getString(KEY_LOCK_HASH, "").orEmpty()
        val saltString = settings.getString(KEY_LOCK_SALT, "").orEmpty()
        if (hash.isEmpty() || saltString.isEmpty()) {
            return false
        }
        return try {
            val salt = Base64.decode(saltString, Base64.DEFAULT)
            hash == hashUnlockCode(code!!, salt)
        } catch (e: Exception) {
            FileLog.e(e)
            false
        }
    }

    @JvmStatic
    fun setCode(code: String) {
        try {
            val salt = ByteArray(16)
            Utilities.random.nextBytes(salt)
            preferences.edit()
                .putString(KEY_LOCK_HASH, hashUnlockCode(code, salt))
                .putString(KEY_LOCK_SALT, Base64.encodeToString(salt, Base64.DEFAULT))
                .apply()
        } catch (e: Exception) {
            FileLog.e(e)
        }
    }

    @JvmStatic
    fun removeCode() {
        preferences.edit()
            .remove(KEY_LOCK_HASH)
            .remove(KEY_LOCK_SALT)
            .apply()
    }

    // ---- hidden account list ------------------------------------------------

    @JvmStatic
    fun getHiddenIds(): HashSet<Int> {
        val stored = preferences.getStringSet(KEY_HIDDEN_IDS, null)
        val ids = HashSet<Int>()
        if (stored != null) {
            for (s in stored) {
                try {
                    ids.add(Integer.parseInt(s))
                } catch (ignore: Exception) {
                }
            }
        }
        return ids
    }

    @JvmStatic
    fun setHiddenAccount(account: Int, hidden: Boolean) {
        val ids = getHiddenIds()
        if (hidden) {
            ids.add(account)
        } else {
            ids.remove(account)
        }
        saveHiddenIds(ids)
    }

    private fun saveHiddenIds(ids: HashSet<Int>) {
        val stored = HashSet<String>()
        for (id in ids) {
            stored.add(id.toString())
        }
        preferences.edit().putStringSet(KEY_HIDDEN_IDS, stored).apply()
    }

    @JvmStatic
    fun isAccountHidden(account: Int): Boolean {
        return account >= 0 && account < UserConfig.MAX_ACCOUNT_COUNT
                && getHiddenIds().contains(account) && !isSessionUnlocked()
    }

    @JvmStatic
    fun hasAnyHiddenAccounts(): Boolean {
        if (isSessionUnlocked()) {
            return false
        }
        val ids = getHiddenIds()
        for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
            if (UserConfig.getInstance(a).isClientActivated() && ids.contains(a)) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun getHiddenAccountsCount(): Int {
        var count = 0
        val ids = getHiddenIds()
        for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
            if (UserConfig.getInstance(a).isClientActivated() && ids.contains(a)) {
                count++
            }
        }
        return count
    }

    @JvmStatic
    fun isVisibleActivatedAccount(account: Int): Boolean {
        return UserConfig.isValidAccount(account) && UserConfig.getInstance(account).isClientActivated() && !isAccountHidden(account)
    }

    @JvmStatic
    fun getVisibleAccountsCount(): Int {
        var count = 0
        for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
            if (isVisibleActivatedAccount(a)) {
                count++
            }
        }
        return count
    }

    @JvmStatic
    fun getVisibleAccountsCountExcluding(account: Int): Int {
        var count = 0
        for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
            if (a != account && isVisibleActivatedAccount(a)) {
                count++
            }
        }
        return count
    }

    @JvmStatic
    @JvmOverloads
    fun collectVisibleAccountNumbers(out: ArrayList<Int>, excludedAccount: Int = -1, testBackend: Boolean? = null) {
        out.clear()
        for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
            if (a == excludedAccount || !isVisibleActivatedAccount(a)) {
                continue
            }
            if (testBackend != null && ConnectionsManager.getInstance(a).isTestBackend() != testBackend) {
                continue
            }
            out.add(a)
        }
        AccountOrder.sort(out)
    }

    @JvmStatic
    fun canHideAccount(account: Int): Boolean {
        return getHiddenIds().contains(account) || getVisibleAccountsCountExcluding(account) > 0
    }

    @JvmStatic
    fun getFallbackVisibleAccount(excludedAccount: Int): Int {
        for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
            if (a != excludedAccount && isVisibleActivatedAccount(a)) {
                return a
            }
        }
        return -1
    }

    @JvmStatic
    fun clearAccount(account: Int) {
        setHiddenAccount(account, false)
    }

    // ---- unlock session -----------------------------------------------------

    @JvmStatic
    fun isSessionUnlocked(): Boolean = synchronized(this) { sessionUnlocked }

    @JvmStatic
    fun unlockSession() {
        synchronized(this) { sessionUnlocked = true }
    }

    @JvmStatic
    fun lockSession() {
        synchronized(this) { sessionUnlocked = false }
    }

    // legacy names kept for the existing call sites
    @JvmStatic
    fun setUnlockedHiddenAccount(@Suppress("UNUSED_PARAMETER") account: Int) {
        unlockSession()
    }

    @JvmStatic
    fun isUnlockedHiddenAccount(@Suppress("UNUSED_PARAMETER") account: Int): Boolean = isSessionUnlocked()

    @JvmStatic
    fun clearUnlockedHiddenAccount() {
        lockSession()
    }

    @JvmStatic
    fun canUseHiddenAccounts(): Boolean = true

    @JvmStatic
    fun shouldShowSettingsEntry(@Suppress("UNUSED_PARAMETER") currentAccount: Int): Boolean = true

    /** Refresh account-dependent caches after a hide/unhide. */
    @JvmStatic
    fun notifySettingsChanged(account: Int) {
        ContactsController.getInstance(account).checkAppAccount()
        NotificationsController.getInstance(account).showNotifications()
        MediaDataController.getInstance(UserConfig.selectedAccount).buildShortcuts()
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.mainUserInfoChanged)
    }

    private fun hashUnlockCode(code: String, salt: ByteArray): String {
        val codeBytes = code.toByteArray(StandardCharsets.UTF_8)
        val bytes = ByteArray(32 + codeBytes.size)
        System.arraycopy(salt, 0, bytes, 0, 16)
        System.arraycopy(codeBytes, 0, bytes, 16, codeBytes.size)
        System.arraycopy(salt, 0, bytes, codeBytes.size + 16, 16)
        return Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.size.toLong()))
    }
}
