package org.telegram.ui;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.forkgram.HiddenAccountHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Tele iOS: hidden-accounts management, opened from the gear in the account
 * menu. One global 4-digit code guards the feature; every activated account
 * can be hidden or revealed from this single screen. All code entry happens on
 * full-screen code pages, never in dialogs.
 */
public class ForkAccountHidingActivity extends UniversalFragment {

    private static final int ID_SET_CODE = 1;
    private static final int ID_REMOVE_CODE = 2;
    private static final int ACCOUNT_ID_OFFSET = 100;

    /** Open the management screen, asking the code first when one is set. */
    public static void open(BaseFragment from) {
        if (from == null || from.getParentActivity() == null) {
            return;
        }
        if (HiddenAccountHelper.hasCode() && !HiddenAccountHelper.isSessionUnlocked()) {
            from.presentFragment(new ForkCodeEntryActivity(
                    LocaleController.getString(R.string.HiddenAccounts),
                    LocaleController.getString(R.string.ForkHiddenAccountUnlockInfo),
                    HiddenAccountHelper::checkCode,
                    () -> {
                        HiddenAccountHelper.unlockSession();
                        from.presentFragment(new ForkAccountHidingActivity());
                    }));
        } else {
            from.presentFragment(new ForkAccountHidingActivity());
        }
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.HiddenAccounts);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean hasCode = HiddenAccountHelper.hasCode();
        boolean hasHidden = HiddenAccountHelper.getHiddenAccountsCount() > 0;

        items.add(UItem.asHeader(LocaleController.getString(R.string.ForkAccountsHidingSecurity)));
        items.add(UItem.asButton(ID_SET_CODE, R.drawable.msg2_secret,
                LocaleController.getString(hasCode ? R.string.ForkSettingsLockChangeCode : R.string.ForkSettingsLockSetCode),
                LocaleController.getString(hasCode ? R.string.PasswordOn : R.string.PasswordOff)));
        if (hasCode && !hasHidden) {
            items.add(UItem.asButton(ID_REMOVE_CODE, R.drawable.msg2_secret,
                    LocaleController.getString(R.string.ForkSettingsLockRemoveCode),
                    ""));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.ForkAccountsHidingInfo)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.ForkAccountsHidingAccounts)));
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            TLRPC.User user = UserConfig.getInstance(a).getCurrentUser();
            if (user == null) {
                continue;
            }
            boolean hidden = HiddenAccountHelper.getHiddenIds().contains(a);
            items.add(UItem.asButton(ACCOUNT_ID_OFFSET + a, R.drawable.msg2_secret,
                    UserObject.getUserName(user),
                    LocaleController.getString(hidden ? R.string.HiddenAccountsStateHidden : R.string.HiddenAccountsStateVisible))
                    .setEnabled(hasCode));
        }
        if (!hasCode) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.ForkAccountsHidingNeedsCode)));
        } else {
            items.add(UItem.asShadow(LocaleController.getString(R.string.ForkAccountsHidingAccountsInfo)));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_SET_CODE) {
            showChangeCodeFlow();
            return;
        }
        if (item.id == ID_REMOVE_CODE) {
            showRemoveCodeConfirm();
            return;
        }
        if (item.id >= ACCOUNT_ID_OFFSET) {
            toggleAccount(item.id - ACCOUNT_ID_OFFSET);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void showChangeCodeFlow() {
        if (HiddenAccountHelper.hasCode()) {
            presentFragment(new ForkCodeEntryActivity(
                    LocaleController.getString(R.string.HiddenAccounts),
                    LocaleController.getString(R.string.ForkSettingsLockConfirmCurrentCodeInfo),
                    HiddenAccountHelper::checkCode,
                    () -> presentFragment(new ForkCodeEntryActivity(
                            LocaleController.getString(R.string.HiddenAccounts),
                            LocaleController.getString(R.string.ForkAccountsHidingSetCodeInfo),
                            code -> {
                                HiddenAccountHelper.setCode(code);
                                postCodeChanged();
                            }))));
        } else {
            presentFragment(new ForkCodeEntryActivity(
                    LocaleController.getString(R.string.HiddenAccounts),
                    LocaleController.getString(R.string.ForkAccountsHidingSetCodeInfo),
                    code -> {
                        HiddenAccountHelper.setCode(code);
                        postCodeChanged();
                    }));
        }
    }

    private void postCodeChanged() {
        if (getParentActivity() != null) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, LocaleController.getString(R.string.ForkAccountsHidingCodeSaved)).show();
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void showRemoveCodeConfirm() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context, getResourceProvider());
        builder.setTitle(LocaleController.getString(R.string.ForkSettingsLockRemoveCode));
        builder.setMessage(LocaleController.getString(R.string.ForkAccountsHidingRemoveCodeInfo));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.ForkSettingsLockRemoveCode), (d, w) -> {
            HiddenAccountHelper.removeCode();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        });
        showDialog(builder.create());
    }

    private void toggleAccount(int account) {
        boolean hidden = HiddenAccountHelper.getHiddenIds().contains(account);
        if (hidden) {
            HiddenAccountHelper.setHiddenAccount(account, false);
            HiddenAccountHelper.notifySettingsChanged(account);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            return;
        }
        if (!HiddenAccountHelper.hasCode()) {
            AlertsCreator.showSimpleAlert(this, LocaleController.getString(R.string.ForkAccountsHidingNeedsCode));
            return;
        }
        if (!HiddenAccountHelper.canHideAccount(account)) {
            AlertsCreator.showSimpleAlert(this, LocaleController.getString(R.string.HiddenAccountsNeedVisibleAccount));
            return;
        }
        boolean switchingAway = UserConfig.selectedAccount == account;
        int fallback = HiddenAccountHelper.getFallbackVisibleAccount(account);
        if (switchingAway && fallback < 0) {
            AlertsCreator.showSimpleAlert(this, LocaleController.getString(R.string.HiddenAccountsNeedVisibleAccount));
            return;
        }
        HiddenAccountHelper.setHiddenAccount(account, true);
        HiddenAccountHelper.notifySettingsChanged(account);
        if (getParentActivity() != null) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, LocaleController.getString(R.string.ForkAccountsHidingAccountHidden)).show();
        }
        if (switchingAway && fallback >= 0 && LaunchActivity.instance != null) {
            LaunchActivity.instance.switchToAccount(fallback, true);
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }
}
