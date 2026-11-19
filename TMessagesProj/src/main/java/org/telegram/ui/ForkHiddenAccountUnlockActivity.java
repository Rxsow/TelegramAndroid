package org.telegram.ui;

import android.content.Context;
import android.os.SystemClock;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.forkgram.HiddenAccountHelper;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Full-screen page that reveals a hidden account by its 4-digit unlock code.
 * Opened by long-pressing the "Add account" row in the account selector.
 */
public class ForkHiddenAccountUnlockActivity extends BaseFragment {

    private static final int ATTEMPTS_PER_COOLDOWN = 5;
    private static final long COOLDOWN_MS = 30_000L;

    private CodeFieldContainer codeContainer;
    private TextView infoTextView;
    private TextView errorTextView;
    private int badAttempts;
    private long cooldownUntil;
    private boolean unlocking;

    private final Runnable cooldownUpdater = new Runnable() {
        @Override
        public void run() {
            if (getParentActivity() == null || codeContainer == null) {
                return;
            }
            long remaining = cooldownUntil - SystemClock.elapsedRealtime();
            if (remaining <= 0) {
                endCooldown();
            } else {
                errorTextView.setText(LocaleController.formatString("ForkHiddenAccountUnlockCooldown", R.string.ForkHiddenAccountUnlockCooldown, (int) Math.ceil(remaining / 1000.0)));
                AndroidUtilities.runOnUIThread(cooldownUpdater, 250);
            }
        }
    };

    @Override
    public boolean onFragmentCreate() {
        if (!HiddenAccountHelper.hasAnyHiddenAccounts()) {
            return false;
        }
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.HiddenAccounts));
        actionBar.setAllowOverlayTitle(false);
        actionBar.setCastShadows(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        fragmentView = layout;

        ImageView lockView = new ImageView(context);
        lockView.setImageResource(R.drawable.msg2_secret);
        lockView.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        layout.addView(lockView, LayoutHelper.createLinear(44, 44, Gravity.CENTER_HORIZONTAL, 0, 48, 0, 0));

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setText(LocaleController.getString(R.string.ForkHiddenAccountUnlockTitle));
        titleView.setGravity(Gravity.CENTER);
        layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

        infoTextView = new TextView(context);
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        infoTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        infoTextView.setText(LocaleController.getString(R.string.ForkHiddenAccountUnlockInfo));
        infoTextView.setGravity(Gravity.CENTER);
        layout.addView(infoTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 8, 32, 0));

        codeContainer = createCodeContainer(context);
        layout.addView(codeContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 32, 0, 0));

        errorTextView = new TextView(context);
        errorTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        errorTextView.setTypeface(AndroidUtilities.bold());
        errorTextView.setTextColor(getThemedColor(Theme.key_text_RedBold));
        errorTextView.setGravity(Gravity.CENTER);
        errorTextView.setVisibility(View.GONE);
        layout.addView(errorTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 16, 32, 0));

        return fragmentView;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        AndroidUtilities.cancelRunOnUIThread(cooldownUpdater);
    }

    private CodeFieldContainer createCodeContainer(Context context) {
        CodeFieldContainer container = new CodeFieldContainer(context) {
            @Override
            protected void processNextPressed() {
                verifyCode(this);
            }
        };
        container.setNumbersCount(4, CodeFieldContainer.TYPE_PASSCODE);
        for (CodeNumberField field : container.codeField) {
            field.setShowSoftInputOnFocusCompat(true);
            field.setTransformationMethod(PasswordTransformationMethod.getInstance());
            field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        }
        return container;
    }

    private void verifyCode(CodeFieldContainer container) {
        if (unlocking) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now < cooldownUntil) {
            errorTextView.setText(LocaleController.formatString("ForkHiddenAccountUnlockCooldown", R.string.ForkHiddenAccountUnlockCooldown, (int) Math.ceil((cooldownUntil - now) / 1000.0)));
            errorTextView.setVisibility(View.VISIBLE);
            clearCodeContainer(container);
            return;
        }

        int account = HiddenAccountHelper.findHiddenAccountByCode(container.getCode());
        if (account >= 0) {
            unlocking = true;
            AndroidUtilities.hideKeyboard(container);
            HiddenAccountHelper.setUnlockedHiddenAccount(account);
            finishFragment();
            if (LaunchActivity.instance != null && UserConfig.selectedAccount != account) {
                LaunchActivity.instance.switchToAccount(account, true);
            }
            return;
        }

        badAttempts++;
        if (badAttempts % ATTEMPTS_PER_COOLDOWN == 0) {
            startCooldown(now + COOLDOWN_MS);
        } else {
            showCodeError(container);
        }
    }

    private void startCooldown(long until) {
        cooldownUntil = until;
        clearCodeContainer(codeContainer);
        for (CodeNumberField field : codeContainer.codeField) {
            field.setEnabled(false);
        }
        errorTextView.setVisibility(View.VISIBLE);
        AndroidUtilities.hideKeyboard(codeContainer);
        cooldownUpdater.run();
    }

    private void endCooldown() {
        cooldownUntil = 0;
        if (codeContainer == null) {
            return;
        }
        for (CodeNumberField field : codeContainer.codeField) {
            field.setEnabled(true);
        }
        errorTextView.setVisibility(View.GONE);
        clearCodeContainer(codeContainer);
        focusCodeContainer(codeContainer);
    }

    private static void clearCodeContainer(CodeFieldContainer container) {
        for (CodeNumberField field : container.codeField) {
            field.setText("");
        }
        if (container.codeField[0].isEnabled()) {
            container.codeField[0].requestFocus();
        }
    }

    private static void focusCodeContainer(CodeFieldContainer container) {
        container.codeField[0].requestFocus();
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(container.codeField[0]), 100);
    }

    private void showCodeError(CodeFieldContainer container) {
        try {
            container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {
        }
        clearCodeContainer(container);
        for (CodeNumberField field : container.codeField) {
            field.animateErrorProgress(1f);
        }
        AndroidUtilities.shakeViewSpring(container, 10, () -> AndroidUtilities.runOnUIThread(() -> {
            for (CodeNumberField field : container.codeField) {
                field.animateErrorProgress(0f);
            }
        }, 150));
    }

    @Override
    public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        if (SystemClock.elapsedRealtime() < cooldownUntil) {
            cooldownUpdater.run();
        } else {
            focusCodeContainer(codeContainer);
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return false;
    }
}
