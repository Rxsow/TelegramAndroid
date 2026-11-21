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
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;

import java.util.function.Consumer;

/**
 * Tele iOS: generic full-screen 4-digit code page (never a dialog). Two modes:
 * VERIFY – check against a caller-provided checker and run onVerified;
 * SET – enter twice, then hand the confirmed code to onSet.
 * Used for the hidden-accounts lock and the hidden-chats PIN.
 */
public class ForkCodeEntryActivity extends BaseFragment {

    private static final int ATTEMPTS_PER_COOLDOWN = 5;
    private static final long COOLDOWN_MS = 30_000L;

    public interface CodeChecker {
        boolean check(String code);
    }

    private final CharSequence pageTitle;
    private final CharSequence infoText;
    private final CodeChecker checker;
    private final Runnable onVerified;
    private final Consumer<String> onSet;
    private final boolean setTitleMode;

    private org.telegram.ui.CodeFieldContainer codeContainer;
    private TextView infoTextView;
    private TextView errorTextView;
    private int badAttempts;
    private long cooldownUntil;
    private boolean completing;
    private String firstSetCode;

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

    /** Verify an existing code and continue once it matches. */
    public ForkCodeEntryActivity(CharSequence pageTitle, CharSequence infoText, CodeChecker checker, Runnable onVerified) {
        this.pageTitle = pageTitle;
        this.infoText = infoText;
        this.checker = checker;
        this.onVerified = onVerified;
        this.onSet = null;
        this.setTitleMode = false;
    }

    /** Set a new code (entered twice) and continue with the confirmed code. */
    public ForkCodeEntryActivity(CharSequence pageTitle, CharSequence infoText, Consumer<String> onSet) {
        this.pageTitle = pageTitle;
        this.infoText = infoText;
        this.checker = null;
        this.onVerified = null;
        this.onSet = onSet;
        this.setTitleMode = true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(pageTitle);
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
        titleView.setText(pageTitle);
        titleView.setGravity(Gravity.CENTER);
        layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

        infoTextView = new TextView(context);
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        infoTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        infoTextView.setText(infoText);
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

    private org.telegram.ui.CodeFieldContainer createCodeContainer(Context context) {
        org.telegram.ui.CodeFieldContainer container = new org.telegram.ui.CodeFieldContainer(context) {
            @Override
            protected void processNextPressed() {
                verifyCode(this);
            }
        };
        container.setNumbersCount(4, org.telegram.ui.CodeFieldContainer.TYPE_PASSCODE);
        for (org.telegram.ui.CodeNumberField field : container.codeField) {
            field.setShowSoftInputOnFocusCompat(true);
            field.setTransformationMethod(PasswordTransformationMethod.getInstance());
            field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        }
        return container;
    }

    private void verifyCode(org.telegram.ui.CodeFieldContainer container) {
        if (completing) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now < cooldownUntil) {
            errorTextView.setText(LocaleController.formatString("ForkHiddenAccountUnlockCooldown", R.string.ForkHiddenAccountUnlockCooldown, (int) Math.ceil((cooldownUntil - now) / 1000.0)));
            errorTextView.setVisibility(View.VISIBLE);
            clearCodeContainer(container);
            return;
        }

        String code = container.getCode();
        if (setTitleMode) {
            if (firstSetCode == null) {
                if (!isValidForkCode(code)) {
                    badAttempts++;
                    if (badAttempts % ATTEMPTS_PER_COOLDOWN == 0) {
                        startCooldown(now + COOLDOWN_MS);
                    } else {
                        showCodeError(container);
                    }
                    return;
                }
                firstSetCode = code;
                clearCodeContainer(container);
                infoTextView.setText(LocaleController.getString(R.string.ForkSettingsLockConfirmCodeInfo));
                focusCodeContainer(container);
                return;
            }
            if (!firstSetCode.equals(code)) {
                firstSetCode = null;
                infoTextView.setText(infoText);
                AlertsCreator.showSimpleAlert(this, LocaleController.getString(R.string.ForkSettingsLockCodesDoNotMatch));
                clearCodeContainer(container);
                focusCodeContainer(container);
                return;
            }
            completing = true;
            AndroidUtilities.hideKeyboard(container);
            final String finalCode = code;
            finishFragment();
            if (onSet != null) {
                onSet.accept(finalCode);
            }
            return;
        }

        if (checker != null && checker.check(code)) {
            completing = true;
            AndroidUtilities.hideKeyboard(container);
            finishFragment();
            if (onVerified != null) {
                onVerified.run();
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

    private static boolean isValidForkCode(String code) {
        return code != null && code.matches("\\d{4}");
    }

    private void startCooldown(long until) {
        cooldownUntil = until;
        clearCodeContainer(codeContainer);
        for (org.telegram.ui.CodeNumberField field : codeContainer.codeField) {
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
        for (org.telegram.ui.CodeNumberField field : codeContainer.codeField) {
            field.setEnabled(true);
        }
        errorTextView.setVisibility(View.GONE);
        clearCodeContainer(codeContainer);
        focusCodeContainer(codeContainer);
    }

    private static void clearCodeContainer(org.telegram.ui.CodeFieldContainer container) {
        for (org.telegram.ui.CodeNumberField field : container.codeField) {
            field.setText("");
        }
        if (container.codeField[0].isEnabled()) {
            container.codeField[0].requestFocus();
        }
    }

    private static void focusCodeContainer(org.telegram.ui.CodeFieldContainer container) {
        container.codeField[0].requestFocus();
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(container.codeField[0]), 100);
    }

    private void showCodeError(org.telegram.ui.CodeFieldContainer container) {
        try {
            container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {
        }
        clearCodeContainer(container);
        for (org.telegram.ui.CodeNumberField field : container.codeField) {
            field.animateErrorProgress(1f);
        }
        AndroidUtilities.shakeViewSpring(container, 10, () -> AndroidUtilities.runOnUIThread(() -> {
            for (org.telegram.ui.CodeNumberField field : container.codeField) {
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
