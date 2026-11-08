/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.FloatProperty;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.media3.common.C;
//import com.google.android.gms.cast.framework.CastContext;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FileRefController;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSlider;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.FiltersView;
import org.telegram.ui.CastSync;
import org.telegram.ui.Cells.AudioPlayerCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ChooseQualityLayout;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.SelectAudioAlert;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AudioPlayerAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate, DownloadController.FileDownloadProgressListener {
    
    private static final int SEEK_REPEAT_DELAY_MS = 300;

    private TextView forwardButton;
    private TextView backwardButton;
    private Runnable seekRepeatRunnable;

    public static AudioPlayerAlert instance;

    private View actionBarBackground;
    private ActionBar actionBar;
    private View actionBarShadow;
    private boolean searchWas;
    private boolean searching;

    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private ListAdapter listAdapter;
    private LinearLayout emptyView;
    private ImageView emptyImageView;
    private TextView emptyTitleTextView;
    private TextView emptySubtitleTextView;

    // Tele iOS – Apple Music style Now Playing
    private FrameLayout nowPlayingLayout;
    private FrameLayout topBarLayout;
    private FrameLayout transportRow;
    private FrameLayout secondaryRow;
    private FrameLayout bottomRow;
    private DynamicBackgroundView backgroundView;
    private final DynamicBackground dynamicBackground = new DynamicBackground();
    private ImageView chevronButton;
    private SimpleTextView topLabelTextView;
    private ButtonWithCounterView saveToProfileButton;
    private ButtonWithCounterView unsaveFromProfileButton;
    private ItemTouchHelper itemTouchHelper;
    private CoverContainer coverContainer;
    private ClippingTextViewSwitcher titleTextView;
    private RLottieImageView prevButton;
    private RLottieImageView nextButton;
    private ClippingTextViewSwitcher authorTextView;
    private ActionBarMenuItem optionsButton;
    private ChooseQualityLayout.QualityIcon optionsIcon;
    private ActionBarMenuSubItem castItem;
    private CastMediaRouteButton castItemButton;
    private boolean castAvailable;
    private LineProgressView progressView;
    private AppleMusicSeekBar seekBarView;
    private TextView timeTextView;
    private TextView durationTextView;
    private ActionBarMenuItem playbackSpeedButton;
    private SpeedIconDrawable speedIcon;
    private ActionBarMenuSlider.SpeedSlider speedSlider;
    private boolean slidingSpeed;
    private ActionBarMenuSubItem[] speedItems = new ActionBarMenuSubItem[6];
    private ImageView shuffleButton;
    private ImageView queueButton;
    private ActionBarMenuItem repeatButton;
    private ActionBarMenuSubItem repeatSongItem;
    private ActionBarMenuSubItem repeatListItem;
    private ActionBarMenuSubItem shuffleListItem;
    private ActionBarMenuSubItem reverseOrderItem;
    private ImageView playButton;
    private PlayPauseDrawable playPauseDrawable;
    private FrameLayout blurredView;
    private BackupImageView bigAlbumConver;
    private boolean blurredAnimationInProgress;
    private SpringAnimation seekBarBufferSpring;

    // queue ("Playing Next") panel state
    private boolean queueOpen;
    private ValueAnimator queueAnimator;

    // dynamic Apple Music style colors
    private int uiPrimary = 0xFFFFFFFF;
    private int uiSecondary = 0x99FFFFFF;
    private boolean uiDarkText = false;

    private ActionBarMenuItem addItem;
    private ActionBarMenuItem searchItem;

    private boolean draggingSeekBar;

    private long lastBufferedPositionCheck;
    private boolean currentAudioFinishedLoading;

    private boolean scrollToSong = true;

    private int searchOpenPosition = -1;
    private int searchOpenOffset;

    private final boolean isProfilePlaylist;
    private final boolean padWithItem;

    private MessagesController.SavedMusicList savedMusicList;
    private boolean isMyList() {
        return savedMusicList != null && savedMusicList.dialogId == UserConfig.getInstance(currentAccount).getClientUserId();
    }

    private ArrayList<MessageObject> playlist;
    private MessageObject lastMessageObject;
    private boolean noforwards;

    private int scrollOffsetY = Integer.MAX_VALUE;
    private int topBeforeSwitch;

    private boolean inFullSize;

    private String currentFile;

    private AnimatorSet actionBarAnimation;

    private int lastTime;
    private int lastDuration;

    private int TAG;

    private LaunchActivity parentActivity;
    int rewindingState;
    float rewindingProgress = -1;

    int rewindingForwardPressedCount;
    long lastRewindingTime;
    long lastUpdateRewindingPlayerTime;

    private boolean wasLight;

    private final static float[] speeds = new float[] {
            .5f, 1f, 1.2f, 1.5f, 1.7f, 2f
    };

    private final Runnable forwardSeek = new Runnable() {
        @Override
        public void run() {
            long duration = MediaController.getInstance().getDuration();
            if (duration == 0 || duration == C.TIME_UNSET) {
                lastRewindingTime = System.currentTimeMillis();
                return;
            }
            float currentProgress = rewindingProgress;

            long t = System.currentTimeMillis();
            long dt = t - lastRewindingTime;
            lastRewindingTime = t;
            long updateDt = t - lastUpdateRewindingPlayerTime;
            if (rewindingForwardPressedCount == 1) {
                dt = dt * 3 - dt;
            } else if (rewindingForwardPressedCount == 2) {
                dt = dt * 6 - dt;
            } else {
                dt = dt * 12 - dt;
            }
            long currentTime = (long) (duration * currentProgress + dt);
            currentProgress = currentTime / (float) duration;
            if (currentProgress < 0) {
                currentProgress = 0;
            }
            rewindingProgress = currentProgress;
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject != null && (messageObject.isMusic() || messageObject.isVoice())) {
                if (!MediaController.getInstance().isMessagePaused()) {
                    MediaController.getInstance().getPlayingMessageObject().audioProgress = rewindingProgress;
                }
                updateProgress(messageObject);
            }
            if (rewindingState == 1 && rewindingForwardPressedCount > 0 && MediaController.getInstance().isMessagePaused()) {
                if (updateDt > 200 || rewindingProgress == 0) {
                    lastUpdateRewindingPlayerTime = t;
                    MediaController.getInstance().seekToProgress(MediaController.getInstance().getPlayingMessageObject(), currentProgress);
                }
                if (rewindingForwardPressedCount > 0 && rewindingProgress > 0) {
                    AndroidUtilities.runOnUIThread(forwardSeek, 16);
                }
            }
        }
    };

    public AudioPlayerAlert(final Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, true, resourcesProvider);
        doNotOverlayNavigationBar = true;
        fixNavigationBar();

        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject != null) {
            currentAccount = messageObject.currentAccount;
        } else {
            currentAccount = UserConfig.selectedAccount;
        }

        parentActivity = (LaunchActivity) context;

        TAG = DownloadController.getInstance(currentAccount).generateObserverTag();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoadProgressChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.musicDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.moreMusicDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.musicIdsLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.messagePlayingSpeedChanged);

        containerView = new FrameLayout(context) {

            private RectF rect = new RectF();
            private boolean ignoreLayout = false;
            private int lastMeasturedHeight;
            private int lastMeasturedWidth;

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                return !isDismissed() && super.onTouchEvent(e);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int totalHeight = MeasureSpec.getSize(heightMeasureSpec);
                int w = MeasureSpec.getSize(widthMeasureSpec);
                if (totalHeight != lastMeasturedHeight || w != lastMeasturedWidth) {
                    if (blurredView.getTag() != null) {
                        showAlbumCover(false, false);
                    }
                    lastMeasturedWidth = w;
                    lastMeasturedHeight = totalHeight;
                }
                ignoreLayout = true;
                int availableHeight = totalHeight - getPaddingTop();

                LayoutParams layoutParams = (LayoutParams) listView.getLayoutParams();
                layoutParams.topMargin = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;

                layoutParams = (LayoutParams) actionBarShadow.getLayoutParams();
                layoutParams.topMargin = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;

                layoutParams = (LayoutParams) blurredView.getLayoutParams();
                layoutParams.topMargin = -getPaddingTop();

                int padding;
                if (!queueOpen || searching || keyboardVisible) {
                    padding = dp(8);
                } else {
                    padding = (int) (availableHeight * 0.22f);
                }
                if (padWithItem) {
                    padding = 0;
                }
                if (listView.getPaddingTop() != padding) {
                    listView.setPadding(0, padding, 0, searching && keyboardVisible ? 0 : listView.getPaddingBottom());
                }
                ignoreLayout = false;
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
                inFullSize = getMeasuredHeight() >= totalHeight;
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                updateLayout();
                updateEmptyViewPosition();
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && queueOpen && scrollOffsetY != 0 && actionBar.getAlpha() == 0.0f) {
                    boolean dismiss;
                    if (listAdapter.getItemCount() > 0) {
                        dismiss = ev.getY() < scrollOffsetY + dp(12);
                    } else {
                        dismiss = ev.getY() < getMeasuredHeight() - dp(150);
                    }
                    if (dismiss) {
                        closeQueue();
                        return true;
                    }
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                if (!queueOpen || listView.getVisibility() != View.VISIBLE) {
                    return;
                }

                int offset = dp(13);
                int top = scrollOffsetY - backgroundPaddingTop - offset;
                top += listView.getTranslationY();
                if (isProfilePlaylist) {
                    top -= ActionBar.getCurrentActionBarHeight();
                    top += dp(10);
                }
                int y = top + dp(20);

                int height = getMeasuredHeight() + dp(15) + backgroundPaddingTop;
                float rad = 1.0f;

                float moveProgress = 0;
                if (!isProfilePlaylist && top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
                    float toMove = offset + dp(11 - 7);
                    moveProgress = Math.min(1.0f, (ActionBar.getCurrentActionBarHeight() - top - backgroundPaddingTop) / toMove);
                    float availableToMove = ActionBar.getCurrentActionBarHeight() - toMove;

                    int diff = (int) (availableToMove * moveProgress);
                    top -= diff;
                    y -= diff;
                    height += diff;
                    rad = 1.0f - moveProgress;
                }

                top += (int) (AndroidUtilities.statusBarHeight * (1f - moveProgress));
                y += (int) (AndroidUtilities.statusBarHeight * (1f - moveProgress));

                shadowDrawable.setBounds(0, top, getMeasuredWidth(), height);
                shadowDrawable.draw(canvas);

                if (!isProfilePlaylist && rad != 1.0f) {
                    Theme.dialogs_onlineCirclePaint.setColor(getThemedColor(Theme.key_dialogBackground));
                    rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + dp(24));
                    canvas.drawRoundRect(rect, dp(12) * rad, dp(12) * rad, Theme.dialogs_onlineCirclePaint);
                }

                if (!isProfilePlaylist && rad != 0) {
                    float alphaProgress = 1.0f;
                    int w = dp(36);
                    rect.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + dp(4));
                    int color = getThemedColor(Theme.key_sheet_scrollUp);
                    int alpha = Color.alpha(color);
                    Theme.dialogs_onlineCirclePaint.setColor(color);
                    Theme.dialogs_onlineCirclePaint.setAlpha((int) (alpha * alphaProgress * rad));
                    canvas.drawRoundRect(rect, dp(2), dp(2), Theme.dialogs_onlineCirclePaint);
                }
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                Bulletin.addDelegate(this, new Bulletin.Delegate() {
                    @Override
                    public int getBottomOffset(int tag) {
                        return dp(88);
                    }
                });
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                Bulletin.removeDelegate(this);
            }
        };
        containerView.setWillNotDraw(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        // dynamic blurred-cover background, like Apple Music
        backgroundView = new DynamicBackgroundView(context);
        containerView.addView(backgroundView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        actionBar = new ActionBar(context, resourcesProvider) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                containerView.invalidate();
            }
        };
        actionBar.setBackgroundColor(0);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(uiPrimary, false);
        actionBar.setItemsBackgroundColor(0x1F000000, false);
        actionBar.setTitleColor(uiPrimary);
        actionBar.setSubtitleColor(uiSecondary);
        actionBar.setOccupyStatusBar(true);

        final ActionBarMenu menu = actionBar.createMenu();
        menu.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBarBackground = new View(context);
        actionBarBackground.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x8C000000, 0x00000000}));
        actionBar.addView(actionBarBackground, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBarBackground.setAlpha(0.0f);
        actionBar.setAlpha(0.0f);
        // the top bar belongs to the queue ("Playing Next") state only
        actionBar.setVisibility(View.INVISIBLE);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (queueOpen) {
                        closeQueue();
                    } else {
                        dismiss();
                    }
                } else {
                    onSubItemClick(id);
                }
            }
        });

        actionBarShadow = new View(context);
        actionBarShadow.setAlpha(0.0f);
        actionBarShadow.setBackgroundResource(R.drawable.header_shadow);

        nowPlayingLayout = new NowPlayingLayout(context);

        coverContainer = new CoverContainer(context) {

            private long pressTime;

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    if (getImageReceiver().hasBitmapImage()) {
                        showAlbumCover(true, true);
                        pressTime = SystemClock.elapsedRealtime();
                    }
                } else if (action != MotionEvent.ACTION_MOVE) {
                    if (SystemClock.elapsedRealtime() - pressTime >= 400) {
                        showAlbumCover(false, true);
                    }
                }
                return true;
            }

            @Override
            protected void onImageUpdated(ImageReceiver imageReceiver) {
                final Bitmap b = imageReceiver.getBitmap();
                if (blurredView.getTag() != null) {
                    bigAlbumConver.setImageBitmap(b);
                }
                if (b != null) {
                    dynamicBackground.apply(lastMessageObject, b);
                }
            }
        };

        // ---- top bar: chevron down (dismiss), dialog label, options ----
        topBarLayout = new FrameLayout(context);
        chevronButton = new ImageView(context);
        chevronButton.setScaleType(ImageView.ScaleType.CENTER);
        chevronButton.setImageResource(R.drawable.ic_ios_back_chevron);
        chevronButton.setRotation(270f);
        chevronButton.setContentDescription(LocaleController.getString(R.string.Close));
        topBarLayout.addView(chevronButton, LayoutHelper.createFrame(40, 40, Gravity.LEFT | Gravity.TOP, 6, 4, 0, 0));
        chevronButton.setOnClickListener(v -> dismiss());
        attachBounce(chevronButton);

        topLabelTextView = new SimpleTextView(context);
        topLabelTextView.setTextSize(12);
        topLabelTextView.setTypeface(AndroidUtilities.bold());
        topLabelTextView.setGravity(Gravity.CENTER);
        topLabelTextView.setAlpha(0.72f);
        topLabelTextView.setTextColor(uiPrimary);
        topBarLayout.addView(topLabelTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 52, 15, 52, 0));

        optionsButton = new ActionBarMenuItem(context, null, 0, uiPrimary, false, resourcesProvider);
        optionsButton.setIcon(optionsIcon = new ChooseQualityLayout.QualityIcon(context, R.drawable.ic_ab_other, resourcesProvider));
        optionsButton.setLongClickEnabled(false);
        optionsButton.setBackgroundDrawable(Theme.createSelectorDrawable(0x1F000000, 1, dp(18)));
        topBarLayout.addView(optionsButton, LayoutHelper.createFrame(40, 40, Gravity.RIGHT | Gravity.TOP, 0, 4, 6, 0));
        optionsButton.setOnClickListener(this::showMenuOptions);
        optionsButton.setDelegate(this::onSubItemClick);
        optionsButton.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
        attachBounce(optionsButton);

        titleTextView = new ClippingTextViewSwitcher(context) {
            @Override
            protected TextView createTextView() {
                final TextView textView = new MarqueeTextView(context);
                textView.setTextColor(uiPrimary);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 21);
                textView.setTypeface(AndroidUtilities.bold());
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setSingleLine(true);
                textView.setGravity(Gravity.CENTER);
                return textView;
            }
        };
        titleTextView.setIsCenter();

        authorTextView = new ClippingTextViewSwitcher(context) {
            @Override
            protected TextView createTextView() {
                final TextView textView = new MarqueeTextView(context);
                textView.setTextColor(uiSecondary);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setSingleLine(true);
                textView.setGravity(Gravity.CENTER);

                textView.setOnClickListener(view -> {
                    int dialogsCount = MessagesController.getInstance(currentAccount).getTotalDialogsCount();
                    if (dialogsCount <= 10 || TextUtils.isEmpty(textView.getText().toString())) {
                        return;
                    }
                    String query = textView.getText().toString();
                    if (parentActivity.getActionBarLayout().getLastFragment() instanceof DialogsActivity) {
                        DialogsActivity dialogsActivity = (DialogsActivity) parentActivity.getActionBarLayout().getLastFragment();
                        if (!dialogsActivity.onlyDialogsAdapter()) {
                            dialogsActivity.setShowSearch(query, FiltersView.FILTER_INDEX_MUSIC);
                            dismiss();
                            return;
                        }
                    }
                    DialogsActivity fragment = new DialogsActivity(null);
                    fragment.setSearchString(query);
                    fragment.setInitialSearchType(FiltersView.FILTER_INDEX_MUSIC);
                    parentActivity.presentFragment(fragment, false, false);
                    dismiss();
                });
                return textView;
            }
        };
        authorTextView.setIsCenter();

        seekBarView = new AppleMusicSeekBar(context);
        seekBarView.setDelegate(new AppleMusicSeekBar.AppleMusicSeekBarDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                if (stop) {
                    MediaController.getInstance().seekToProgress(MediaController.getInstance().getPlayingMessageObject(), progress);
                }
                MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (messageObject != null && (messageObject.isMusic() || messageObject.isVoice())) {
                    updateProgress(messageObject);
                }
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {
                draggingSeekBar = pressed;
            }

            @Override
            public CharSequence getContentDescription() {
                final String time = LocaleController.formatPluralString("Minutes", lastTime / 60) + ' ' + LocaleController.formatPluralString("Seconds", lastTime % 60);
                final String totalTime = LocaleController.formatPluralString("Minutes", lastDuration / 60) + ' ' + LocaleController.formatPluralString("Seconds", lastDuration % 60);
                return LocaleController.formatString("AccDescrPlayerDuration", R.string.AccDescrPlayerDuration, time, totalTime);
            }
        });

        progressView = new LineProgressView(context);
        progressView.setVisibility(View.INVISIBLE);
        progressView.setBackgroundColor(0x33FFFFFF);
        progressView.setProgressColor(0xFFFFFFFF);

        seekBarBufferSpring = new SpringAnimation(new FloatValueHolder(0))
                .setSpring(new SpringForce()
                        .setStiffness(750f)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
                .addUpdateListener((animation, value, velocity) -> seekBarView.setBufferedProgress(value / 1000f));

        timeTextView = new TextView(context);
        timeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        timeTextView.setText("0:00");
        timeTextView.setTextColor(uiSecondary);
        timeTextView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        durationTextView = new TextView(context);
        durationTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        durationTextView.setTextColor(uiSecondary);
        durationTextView.setGravity(Gravity.RIGHT);
        durationTextView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        durationTextView.setText("-0:00");

        playbackSpeedButton = new ActionBarMenuItem(context, null, 0, uiPrimary, false, resourcesProvider);
        playbackSpeedButton.setLongClickEnabled(false);
        playbackSpeedButton.setShowSubmenuByMove(false);
        playbackSpeedButton.setAdditionalYOffset(-dp(224));
        playbackSpeedButton.setContentDescription(LocaleController.getString(R.string.AccDescrPlayerSpeed));
        playbackSpeedButton.setDelegate(id -> {
            if (id < 0 || id >= speeds.length) {
                return;
            }
            MediaController.getInstance().setPlaybackSpeed(true, speeds[id]);
            updatePlaybackButton(true);
        });
        playbackSpeedButton.setIcon(speedIcon = new SpeedIconDrawable(true));
        final float[] toggleSpeeds = new float[] { 1.0F, 1.5F, 2F };
        speedSlider = new ActionBarMenuSlider.SpeedSlider(getContext(), resourcesProvider);
        speedSlider.setRoundRadiusDp(6);
        speedSlider.setDrawShadow(true);
        speedSlider.setOnValueChange((value, isFinal) -> {
            slidingSpeed = !isFinal;
            MediaController.getInstance().setPlaybackSpeed(true, speedSlider.getSpeed(value));
        });
        speedItems[0] = playbackSpeedButton.addSubItem(0, R.drawable.msg_speed_slow, LocaleController.getString(R.string.SpeedSlow));
        speedItems[1] = playbackSpeedButton.addSubItem(1, R.drawable.msg_speed_normal, LocaleController.getString(R.string.SpeedNormal));
        speedItems[2] = playbackSpeedButton.addSubItem(2, R.drawable.msg_speed_medium, LocaleController.getString(R.string.SpeedMedium));
        speedItems[3] = playbackSpeedButton.addSubItem(3, R.drawable.msg_speed_fast, LocaleController.getString(R.string.SpeedFast));
        speedItems[4] = playbackSpeedButton.addSubItem(4, R.drawable.msg_speed_veryfast, LocaleController.getString(R.string.SpeedVeryFast));
        speedItems[5] = playbackSpeedButton.addSubItem(5, R.drawable.msg_speed_superfast, LocaleController.getString(R.string.SpeedSuperFast));
        if (AndroidUtilities.density >= 3.0f) {
            playbackSpeedButton.setPadding(0, 1, 0, 0);
        }
        playbackSpeedButton.setAdditionalXOffset(dp(8));
        playbackSpeedButton.setAdditionalYOffset(-dp(400));
        playbackSpeedButton.setShowedFromBottom(true);
        playbackSpeedButton.setOnClickListener(v -> {
            float currentPlaybackSpeed = MediaController.getInstance().getPlaybackSpeed(true);
            int index = -1;
            for (int i = 0; i < toggleSpeeds.length; ++i) {
                if (currentPlaybackSpeed - 0.1F <= toggleSpeeds[i]) {
                    index = i;
                    break;
                }
            }
            index++;
            if (index >= toggleSpeeds.length) {
                index = 0;
            }
            MediaController.getInstance().setPlaybackSpeed(true, toggleSpeeds[index]);

            checkSpeedHint();
        });
        playbackSpeedButton.setOnLongClickListener(view -> {
            final float speed = MediaController.getInstance().getPlaybackSpeed(true);
            speedSlider.setSpeed(speed, false);
            speedSlider.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
            updatePlaybackButton(false);
            playbackSpeedButton.setDimMenu(.15f);
            playbackSpeedButton.toggleSubMenu(speedSlider, null);
            MessagesController.getGlobalNotificationsSettings().edit().putInt("speedhint", -15).apply();
            return true;
        });
        updatePlaybackButton(false);

        transportRow = new TransportRow(context);
        secondaryRow = new SecondaryRow(context);
        bottomRow = new FrameLayout(context);

        {
            final int s = 5;
            final int color = 0x1F000000;
            final int textColor = uiSecondary;
            final FrameLayout.LayoutParams frame = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 44, Gravity.LEFT | Gravity.TOP);
            forwardButton = new TextView(context);
            forwardButton.setText("+" + s + "s");
            forwardButton.setGravity(Gravity.CENTER);
            forwardButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            forwardButton.setTypeface(AndroidUtilities.bold());
            forwardButton.setTextColor(textColor);
            secondaryRow.addView(forwardButton, frame);

            backwardButton = new TextView(context);
            backwardButton.setText("–" + s + "s");
            backwardButton.setGravity(Gravity.CENTER);
            backwardButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            backwardButton.setTypeface(AndroidUtilities.bold());
            backwardButton.setTextColor(textColor);
            secondaryRow.addView(backwardButton, frame);

            if (Build.VERSION.SDK_INT >= 21) {
                forwardButton.setBackgroundDrawable(Theme.createSelectorDrawable(color, 1, AndroidUtilities.dp(22)));
                backwardButton.setBackgroundDrawable(Theme.createSelectorDrawable(color, 1, AndroidUtilities.dp(22)));
            }

            forwardButton.setOnClickListener(view -> {
                MediaController.getInstance().seekShift(s * 1000);
            });
            backwardButton.setOnClickListener(view -> {
                MediaController.getInstance().seekShift(-s * 1000);
            });

            setupSeekRepeatOnHold(forwardButton, s * 1000);
            setupSeekRepeatOnHold(backwardButton, -s * 1000);
        }

        repeatButton = new ActionBarMenuItem(context, null, 0, uiPrimary, false, resourcesProvider);
        repeatButton.setLongClickEnabled(true);
        repeatButton.setShowSubmenuByMove(false);
        repeatButton.setAdditionalYOffset(-dp(166));
        repeatButton.setBackgroundDrawable(Theme.createSelectorDrawable(0x1F000000, 1, dp(18)));
        if (!messageObject.isVoice()) {
            secondaryRow.addView(repeatButton, LayoutHelper.createFrame(44, 44, Gravity.LEFT | Gravity.TOP));
        }
        repeatButton.setOnClickListener(v -> {
            if (SharedConfig.repeatMode == 1) {
                SharedConfig.setRepeatMode(2);
            } else if (SharedConfig.repeatMode == 2) {
                SharedConfig.setRepeatMode(0);
            } else {
                SharedConfig.setRepeatMode(1);
            }
            updateRepeatButton();
        });
        repeatButton.setOnLongClickListener(v -> {
            updateSubMenu();
            repeatButton.toggleSubMenu();
            return true;
        });
        repeatSongItem = repeatButton.addSubItem(3, R.drawable.player_new_repeatone, LocaleController.getString(R.string.RepeatSong));
        repeatListItem = repeatButton.addSubItem(4, R.drawable.player_new_repeatall, LocaleController.getString(R.string.RepeatList));
        repeatButton.addColoredGap().getLayoutParams().height = dp(4);
        shuffleListItem = repeatButton.addSubItem(2, R.drawable.player_new_shuffle, LocaleController.getString(R.string.ShuffleList));
        repeatButton.addColoredGap().getLayoutParams().height = dp(4);
        reverseOrderItem = repeatButton.addSubItem(1, R.drawable.player_new_order, LocaleController.getString(R.string.ReverseOrder));
        repeatButton.setShowedFromBottom(true);

        repeatButton.setDelegate(id -> {
            if (id == 1 || id == 2) {
                boolean oldReversed = SharedConfig.playOrderReversed;
                if (SharedConfig.playOrderReversed && id == 1 || SharedConfig.shuffleMusic && id == 2) {
                    MediaController.getInstance().setPlaybackOrderType(0);
                } else {
                    MediaController.getInstance().setPlaybackOrderType(id);
                }
                listAdapter.notifyDataSetChanged();
                if (oldReversed != SharedConfig.playOrderReversed) {
                    listView.stopScroll();
                    scrollToCurrentSong(false);
                }
            } else {
                if (id == 4) {
                    if (SharedConfig.repeatMode == 1) {
                        SharedConfig.setRepeatMode(0);
                    } else {
                        SharedConfig.setRepeatMode(1);
                    }
                } else {
                    if (SharedConfig.repeatMode == 2) {
                        SharedConfig.setRepeatMode(0);
                    } else {
                        SharedConfig.setRepeatMode(2);
                    }
                }
            }
            updateRepeatButton();
        });

        shuffleButton = new ImageView(context);
        shuffleButton.setScaleType(ImageView.ScaleType.CENTER);
        shuffleButton.setImageResource(R.drawable.player_new_shuffle);
        shuffleButton.setBackground(Theme.createSelectorDrawable(0x1F000000, 1, dp(18)));
        shuffleButton.setContentDescription(LocaleController.getString(R.string.ShuffleList));
        if (!messageObject.isVoice()) {
            secondaryRow.addView(shuffleButton, LayoutHelper.createFrame(44, 44, Gravity.LEFT | Gravity.TOP));
        }
        shuffleButton.setOnClickListener(v -> {
            if (SharedConfig.shuffleMusic) {
                MediaController.getInstance().setPlaybackOrderType(0);
            } else {
                MediaController.getInstance().setPlaybackOrderType(2);
            }
            listAdapter.notifyDataSetChanged();
            updateRepeatButton();
        });
        attachBounce(shuffleButton);
        attachBounce(repeatButton);

        float touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        prevButton = new RLottieImageView(context) {
            float startX;
            float startY;

            int pressedCount = 0;

            long lastTime;
            long lastUpdateTime;

            private final Runnable pressedRunnable = new Runnable() {
                @Override
                public void run() {
                    pressedCount++;
                    if (pressedCount == 1) {
                        rewindingState = -1;
                        rewindingProgress = MediaController.getInstance().getPlayingMessageObject().audioProgress;
                        lastTime = System.currentTimeMillis();
                        AndroidUtilities.runOnUIThread(this, 2000);
                        AndroidUtilities.runOnUIThread(backSeek);
                    } else if (pressedCount == 2) {
                        AndroidUtilities.runOnUIThread(this, 2000);
                    }
                }
            };

            private final Runnable backSeek = new Runnable() {
                @Override
                public void run() {
                    long duration = MediaController.getInstance().getDuration();
                    if (duration == 0 || duration == C.TIME_UNSET) {
                        lastTime = System.currentTimeMillis();
                        return;
                    }
                    float currentProgress = rewindingProgress;

                    long t = System.currentTimeMillis();
                    long dt = t - lastTime;
                    lastTime = t;
                    long updateDt = t - lastUpdateTime;
                    if (pressedCount == 1) {
                        dt *= 3;
                    } else if (pressedCount == 2) {
                        dt *= 6;
                    } else {
                        dt *= 12;
                    }
                    long currentTime = (long) (duration * currentProgress - dt);
                    currentProgress = currentTime / (float) duration;
                    if (currentProgress < 0) {
                        currentProgress = 0;
                    }
                    rewindingProgress = currentProgress;
                    MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                    if (messageObject != null && (messageObject.isMusic() || messageObject.isVoice())) {
                        updateProgress(messageObject);
                    }
                    if (rewindingState == -1 && pressedCount > 0) {
                        if (updateDt > 200 || rewindingProgress == 0) {
                            lastUpdateTime = t;
                            if (rewindingProgress == 0) {
                                MediaController.getInstance().seekToProgress(MediaController.getInstance().getPlayingMessageObject(), 0);
                                MediaController.getInstance().pauseByRewind();
                            } else {
                                MediaController.getInstance().seekToProgress(MediaController.getInstance().getPlayingMessageObject(), currentProgress);
                            }
                        }
                        if (pressedCount > 0 && rewindingProgress > 0) {
                            AndroidUtilities.runOnUIThread(backSeek, 16);
                        }
                    }
                }
            };

            long startTime;

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (seekBarView.isDragging() || rewindingState == 1) {
                    return false;
                }
                float x = event.getRawX();
                float y = event.getRawY();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = x;
                        startY = y;
                        startTime = System.currentTimeMillis();
                        rewindingState = 0;
                        AndroidUtilities.runOnUIThread(pressedRunnable, 300);
                        if (getBackground() != null) {
                            getBackground().setHotspot(startX, startY);
                        }
                        setPressed(true);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = x - startX;
                        float dy = y - startY;

                        if ((dx * dx + dy * dy) > touchSlop * touchSlop && rewindingState == 0) {
                            AndroidUtilities.cancelRunOnUIThread(pressedRunnable);
                            setPressed(false);
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        AndroidUtilities.cancelRunOnUIThread(pressedRunnable);
                        AndroidUtilities.cancelRunOnUIThread(backSeek);
                        if (rewindingState == 0 && event.getAction() == MotionEvent.ACTION_UP && (System.currentTimeMillis() - startTime < 300)) {
                            MediaController.getInstance().playPreviousMessage();
                            prevButton.setProgress(0f);
                            prevButton.playAnimation();
                        }
                        if (pressedCount > 0) {
                            lastUpdateTime = 0;
                            backSeek.run();
                            MediaController.getInstance().resumeByRewind();
                        }
                        rewindingProgress = -1;
                        setPressed(false);
                        rewindingState = 0;
                        pressedCount = 0;
                        break;
                }
                return true;
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        };
        prevButton.setScaleType(ImageView.ScaleType.CENTER);
        prevButton.setAnimation(R.raw.player_prev, 20, 20);
        prevButton.setLayerColor("Triangle 3", uiPrimary);
        prevButton.setLayerColor("Triangle 4", uiPrimary);
        prevButton.setLayerColor("Rectangle 4", uiPrimary);
        prevButton.setBackgroundDrawable(Theme.createSelectorDrawable(0x1F000000, 1, dp(22)));
        if (!messageObject.isVoice()) {
            transportRow.addView(prevButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        }
        prevButton.setContentDescription(LocaleController.getString(R.string.AccDescrPrevious));
        attachBounce(prevButton);

        playButton = new ImageView(context);
        playButton.setScaleType(ImageView.ScaleType.CENTER);
        playButton.setImageDrawable(playPauseDrawable = new PlayPauseDrawable(34));
        playPauseDrawable.setPause(!MediaController.getInstance().isMessagePaused(), false);
        playButton.setColorFilter(new PorterDuffColorFilter(uiPrimary, PorterDuff.Mode.MULTIPLY));
        playButton.setBackgroundDrawable(Theme.createSelectorDrawable(0x1F000000, 1, dp(35)));
        transportRow.addView(playButton, LayoutHelper.createFrame(72, 72, Gravity.LEFT | Gravity.TOP));
        attachBounce(playButton);
        playButton.setOnClickListener(v -> {
            if (MediaController.getInstance().isDownloadingCurrentMessage()) {
                return;
            }
            if (MediaController.getInstance().isMessagePaused()) {
                MediaController.getInstance().playMessage(MediaController.getInstance().getPlayingMessageObject());
            } else {
                MediaController.getInstance().pauseMessage(MediaController.getInstance().getPlayingMessageObject());
            }
        });

        nextButton = new RLottieImageView(context) {

            float startX;
            float startY;
            boolean pressed;

            private final Runnable pressedRunnable = new Runnable() {
                @Override
                public void run() {
                    if (MediaController.getInstance().getPlayingMessageObject() == null) {
                        return;
                    }
                    rewindingForwardPressedCount++;
                    if (rewindingForwardPressedCount == 1) {
                        pressed = true;
                        rewindingState = 1;
                        if (MediaController.getInstance().isMessagePaused()) {
                            startForwardRewindingSeek();
                        } else if (rewindingState == 1) {
                            AndroidUtilities.cancelRunOnUIThread(forwardSeek);
                            lastUpdateRewindingPlayerTime = 0;
                        }
                        MediaController.getInstance().setPlaybackSpeed(true, 4);
                        AndroidUtilities.runOnUIThread(this, 2000);
                    } else if (rewindingForwardPressedCount == 2) {
                        MediaController.getInstance().setPlaybackSpeed(true, 7);
                        AndroidUtilities.runOnUIThread(this, 2000);
                    } else {
                        MediaController.getInstance().setPlaybackSpeed(true, 13);
                    }
                }
            };

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (seekBarView.isDragging() || rewindingState == -1) {
                    return false;
                }
                float x = event.getRawX();
                float y = event.getRawY();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        pressed = false;
                        startX = x;
                        startY = y;
                        AndroidUtilities.runOnUIThread(pressedRunnable, 300);
                        if (getBackground() != null) {
                            getBackground().setHotspot(startX, startY);
                        }
                        setPressed(true);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = x - startX;
                        float dy = y - startY;

                        if ((dx * dx + dy * dy) > touchSlop * touchSlop && !pressed) {
                            AndroidUtilities.cancelRunOnUIThread(pressedRunnable);
                            setPressed(false);
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        if (!pressed && event.getAction() == MotionEvent.ACTION_UP && isPressed()) {
                            MediaController.getInstance().playNextMessage();
                            nextButton.setProgress(0f);
                            nextButton.playAnimation();
                        }
                        AndroidUtilities.cancelRunOnUIThread(pressedRunnable);
                        if (rewindingForwardPressedCount > 0) {
                            MediaController.getInstance().setPlaybackSpeed(true, 1f);
                            if (MediaController.getInstance().isMessagePaused()) {
                                lastUpdateRewindingPlayerTime = 0;
                                forwardSeek.run();
                            }
                        }
                        rewindingState = 0;
                        setPressed(false);
                        rewindingForwardPressedCount = 0;
                        rewindingProgress = -1;
                        break;
                }
                return true;
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
            }

        };
        nextButton.setScaleType(ImageView.ScaleType.CENTER);
        nextButton.setAnimation(R.raw.player_prev, 20, 20);
        nextButton.setLayerColor("Triangle 3", uiPrimary);
        nextButton.setLayerColor("Triangle 4", uiPrimary);
        nextButton.setLayerColor("Rectangle 4", uiPrimary);
        nextButton.setRotation(180f);
        nextButton.setBackground(Theme.createSelectorDrawable(0x1F000000, 1, dp(22)));
        if (!messageObject.isVoice()) {
            transportRow.addView(nextButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        }
        nextButton.setContentDescription(LocaleController.getString(R.string.Next));
        attachBounce(nextButton);

        castItemButton = new CastMediaRouteButton(context) {
            @Override
            public void stateUpdated(boolean connected) {
                updateColors();
                if (optionsIcon != null) {
                    optionsIcon.setCasting(CastSync.isActive(), true);
                }
            }
        };
        castAvailable = true;
        try {
//            castItemButton.setRouteSelector(CastContext.getSharedInstance(context).getMergedSelector());
        } catch (Exception e) {
            FileLog.e(e);
            castAvailable = false;
        }
        castItemButton.setVisibility(View.INVISIBLE);
        if (optionsIcon != null) {
            optionsIcon.setCasting(CastSync.isActive(), true);
        }

        emptyView = new LinearLayout(context);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        containerView.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emptyView.setOnTouchListener((v, event) -> true);

        emptyImageView = new ImageView(context);
        emptyImageView.setImageResource(R.drawable.music_empty);
        emptyImageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogEmptyImage), PorterDuff.Mode.MULTIPLY));
        emptyView.addView(emptyImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        emptyTitleTextView = new TextView(context);
        emptyTitleTextView.setTextColor(getThemedColor(Theme.key_dialogEmptyText));
        emptyTitleTextView.setGravity(Gravity.CENTER);
        emptyTitleTextView.setText(LocaleController.getString(R.string.NoAudioFound));
        emptyTitleTextView.setTypeface(AndroidUtilities.bold());
        emptyTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        emptyTitleTextView.setPadding(dp(40), 0, dp(40), 0);
        emptyView.addView(emptyTitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 11, 0, 0));

        emptySubtitleTextView = new TextView(context);
        emptySubtitleTextView.setTextColor(getThemedColor(Theme.key_dialogEmptyText));
        emptySubtitleTextView.setGravity(Gravity.CENTER);
        emptySubtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptySubtitleTextView.setPadding(dp(40), 0, dp(40), 0);
        emptyView.addView(emptySubtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 6, 0, 0));

        listView = new RecyclerListView(context) {

            boolean ignoreLayout;

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);

                if (searchOpenPosition != -1 && !actionBar.isSearchFieldVisible()) {
                    ignoreLayout = true;
                    layoutManager.scrollToPositionWithOffset(searchOpenPosition, searchOpenOffset - listView.getPaddingTop());
                    super.onLayout(false, l, t, r, b);
                    ignoreLayout = false;
                    searchOpenPosition = -1;
                } else if (scrollToSong) {
                    scrollToSong = false;
                    ignoreLayout = true;
                    if (scrollToCurrentSong(true)) {
                        super.onLayout(false, l, t, r, b);
                    }
                    ignoreLayout = false;
                }
            }

            @Override
            protected boolean allowSelectChildAtPosition(float x, float y) {
                return true;
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        listView.setClipToPadding(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        listView.setHorizontalScrollBarEnabled(false);
        listView.setVerticalScrollBarEnabled(false);
        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setGlowColor(getThemedColor(Theme.key_dialogScrollGlow));
        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof AudioPlayerCell) {
                ((AudioPlayerCell) view).didPressedButton();
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (view instanceof AudioPlayerCell && !isMyList()) {
                showOptions((AudioPlayerCell) view, ((AudioPlayerCell) view).getMessageObject());
                return true;
            }
            return false;
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int offset = dp(13);
                    int top = scrollOffsetY - backgroundPaddingTop - offset;
                    if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight() && listView.canScrollVertically(1)) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(padWithItem ? 1 : 0);
                        if (holder != null && holder.itemView.getTop() > dp(7)) {
                            listView.smoothScrollBy(0, holder.itemView.getTop() - dp(7));
                        }
                    }
                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(getCurrentFocus());
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateLayout();
                updateEmptyViewPosition();

                if (!searchWas) {
                    int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                    if (padWithItem) {
                        firstVisibleItem = Math.max(0, firstVisibleItem - 1);
                    }
                    int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
                    int totalItemCount = recyclerView.getAdapter().getItemCount();

                    MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
                    if (SharedConfig.playOrderReversed) {
                        if (firstVisibleItem < 10) {
                            MediaController.getInstance().loadMoreMusic();
                        }
                    } else {
                        if (firstVisibleItem + visibleItemCount > totalItemCount - 10) {
                            MediaController.getInstance().loadMoreMusic();
                        }
                    }
                }
            }
        });

        saveToProfileButton = new ButtonWithCounterView(context, resourcesProvider).setRound();
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append("+ ");
        sb.setSpan(new ColoredImageSpan(R.drawable.filled_track_add), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(getString(R.string.AudioAddToProfile));
        saveToProfileButton.setText(sb);
        saveToProfileButton.setOnClickListener(v -> {
            final MessageObject messageObject1 = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject1 == null || parentActivity == null) {
                return;
            }
            saveToProfile(messageObject1, true, () -> {}, false);
            setVisibleInProfile(true);
            BulletinFactory.of((FrameLayout) containerView, resourcesProvider)
                .createSimpleBulletin(R.raw.saved_messages, getString(R.string.AudioSaveToMyProfileSaved))
                .show();
        });

        unsaveFromProfileButton = new ButtonWithCounterView(context, resourcesProvider).setRound().setNeutral();
        unsaveFromProfileButton.setText(getString(R.string.AudioRemoveFromProfile));
        unsaveFromProfileButton.setOnClickListener(v -> {
            final MessageObject messageObject1 = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject1 == null || parentActivity == null) {
                return;
            }
            saveToProfile(messageObject1, false, () -> {}, false);
            setVisibleInProfile(false);
            BulletinFactory.of((FrameLayout) containerView, resourcesProvider)
                .createSimpleBulletin(R.raw.ic_delete, getString(R.string.AudioSaveToMyProfileUnsaved))
                .show();
        });

        savedMusicList = MediaController.getInstance().currentSavedMusicList;
        isProfilePlaylist = savedMusicList != null;
        actionBar.menuOccupyBack = isProfilePlaylist;
        padWithItem = isMyList();
        playlist = MediaController.getInstance().getPlaylist();
        if (isMyList()) {
            addItem = menu.addItem(8, R.drawable.msg_add);
        }
        searchItem = menu.addItem(0, R.drawable.outline_header_search)
            .setIsSearchField(true)
            .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {

            @Override
            public void onSearchCollapse() {
                if (searching) {
                    searchWas = false;
                    searching = false;
                    setAllowNestedScroll(true);
                    listAdapter.search(null);
                    if (addItem != null) {
                        addItem.setVisibility(View.VISIBLE);
                    }
                    if (queueOpen && saveToProfileButton.getVisibility() == View.VISIBLE) {
                        saveToProfileButton.animate().translationY(0).setDuration(200).start();
                        unsaveFromProfileButton.animate().translationY(0).setDuration(200).start();
                    }
                }
            }

            @Override
            public void onSearchExpand() {
                searchOpenPosition = layoutManager.findLastVisibleItemPosition();
                View firstVisView = layoutManager.findViewByPosition(searchOpenPosition);
                searchOpenOffset = firstVisView == null ? 0 : firstVisView.getTop();
                searching = true;
                setAllowNestedScroll(false);
                listAdapter.notifyDataSetChanged();
                if (addItem != null) {
                    addItem.setVisibility(View.GONE);
                }
                saveToProfileButton.animate().translationY(dp(64)).setDuration(200).start();
                unsaveFromProfileButton.animate().translationY(dp(64)).setDuration(200).start();
            }

            @Override
            public void onTextChanged(EditText editText) {
                if (editText.length() > 0) {
                    listAdapter.search(editText.getText().toString());
                } else {
                    searchWas = false;
                    listAdapter.search(null);
                }
            }
        });
        searchItem.setContentDescription(LocaleController.getString(R.string.Search));
        EditTextBoldCursor editText = searchItem.getSearchField();
        editText.setHint(LocaleController.getString(R.string.Search));
        editText.setTextColor(getThemedColor(Theme.key_player_actionBarTitle));
        editText.setHintTextColor(getThemedColor(Theme.key_player_time));
        editText.setCursorColor(getThemedColor(Theme.key_player_actionBarTitle));

        if (isProfilePlaylist) {
            listView.setSections();
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));

            actionBar.setAlpha(1.0f);
            actionBarBackground.setAlpha(0.0f);
            actionBarSlideProperty.set(actionBar, 0.0f);
        }

        listAdapter.setup();
        listAdapter.notifyDataSetChanged();

        actionBar.setTitle(LocaleController.getString(R.string.AttachMusic));
        if (savedMusicList != null) {
            if (savedMusicList.dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                actionBar.setTitle(getString(R.string.ProfilePlaylistTitleMine));
            } else {
                actionBar.setTitle(formatString(R.string.ProfilePlaylistTitle, DialogObject.getShortName(savedMusicList.dialogId)));
            }
        } else if (messageObject != null && !MediaController.getInstance().currentPlaylistIsGlobalSearch()) {
            long did = messageObject.getDialogId();
            if (DialogObject.isEncryptedDialog(did)) {
                TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(did));
                if (encryptedChat != null) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
                    if (user != null) {
                        actionBar.setTitle(ContactsController.formatName(user.first_name, user.last_name));
                    }
                }
            } else if (did == UserConfig.getInstance(currentAccount).getClientUserId()) {
                if (messageObject.getSavedDialogId() == UserObject.ANONYMOUS) {
                    actionBar.setTitle(LocaleController.getString(R.string.AnonymousForward));
                } else {
                    actionBar.setTitle(LocaleController.getString(R.string.SavedMessages));
                }
            } else if (DialogObject.isUserDialog(did)) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                if (user != null) {
                    actionBar.setTitle(ContactsController.formatName(user.first_name, user.last_name));
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                if (chat != null) {
                    actionBar.setTitle(chat.title);
                }
            }
        }

        if (isMyList()) {
            saveToProfileButton.setVisibility(View.GONE);
            unsaveFromProfileButton.setVisibility(View.GONE);

            itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
                @Override
                public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                    if (viewHolder.getItemViewType() != 0) {
                        return 0;
                    }
                    return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
                }

                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                    int fromPosition = viewHolder.getAdapterPosition();
                    int toPosition = target.getAdapterPosition();
                    if (padWithItem) {
                        if (fromPosition <= 0 || toPosition <= 0)
                            return false;
                        savedMusicList.move(fromPosition - 1, toPosition - 1);
                    } else {
                        savedMusicList.move(fromPosition, toPosition);
                    }
                    playlist.clear();
                    playlist.addAll(savedMusicList.list);
                    listAdapter.notifyItemMoved(fromPosition, toPosition);
                    return true;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

                }

                @Override
                public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                    if (viewHolder != null) {
                        listView.hideSelector(false);
                    }
                    if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                        listView.cancelClickRunnables(false);
                        if (viewHolder != null) {
                            viewHolder.itemView.setPressed(true);
                        }
                    }
                    super.onSelectedChanged(viewHolder, actionState);
                    if (viewHolder != null) {
                        viewHolder.itemView.setTag(R.id.dragging, actionState == ItemTouchHelper.ACTION_STATE_DRAG ? true : null);
                    }
                }

                @Override
                public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                    super.clearView(recyclerView, viewHolder);
                    viewHolder.itemView.setPressed(false);
                    viewHolder.itemView.setTag(R.id.dragging, null);
                }

            });
            itemTouchHelper.attachToRecyclerView(listView);
        }

        // ---- bottom row: playback speed (long audio) + queue toggle ----
        queueButton = new ImageView(context);
        queueButton.setScaleType(ImageView.ScaleType.CENTER);
        queueButton.setImageResource(R.drawable.ic_ios_player_queue);
        queueButton.setBackground(Theme.createSelectorDrawable(0x1F000000, 1, dp(20)));
        queueButton.setContentDescription(LocaleController.getString(R.string.AttachMusic));
        queueButton.setVisibility(playlist.size() > 1 ? View.VISIBLE : View.GONE);
        bottomRow.addView(queueButton, LayoutHelper.createFrame(44, 44, Gravity.RIGHT | Gravity.TOP, 0, 0, 4, 0));
        queueButton.setOnClickListener(v -> toggleQueue());
        attachBounce(queueButton);
        bottomRow.addView(playbackSpeedButton, LayoutHelper.createFrame(44, 44, Gravity.LEFT | Gravity.TOP, 4, 0, 0, 0));

        // ---- assemble the full-screen Now Playing layout ----
        nowPlayingLayout.addView(topBarLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
        nowPlayingLayout.addView(coverContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        nowPlayingLayout.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
        nowPlayingLayout.addView(authorTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
        nowPlayingLayout.addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, Gravity.TOP | Gravity.LEFT));
        nowPlayingLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2, Gravity.TOP | Gravity.LEFT));
        nowPlayingLayout.addView(timeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
        nowPlayingLayout.addView(durationTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT));
        nowPlayingLayout.addView(transportRow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        nowPlayingLayout.addView(secondaryRow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        nowPlayingLayout.addView(bottomRow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        // "Now Playing" is the front page: the playlist list starts hidden
        listView.setVisibility(View.GONE);

        containerView.addView(nowPlayingLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        // quick save / unsave buttons, shown at the bottom of the queue panel
        containerView.addView(saveToProfileButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 42, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 12, 12, 12, 12));
        containerView.addView(unsaveFromProfileButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 42, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 12, 12, 12, 12));
        saveToProfileButton.setTranslationY(dp(64));
        unsaveFromProfileButton.setTranslationY(dp(64));

        containerView.addView(actionBarShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3));
        containerView.addView(actionBar);

        blurredView = new FrameLayout(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (blurredView.getTag() != null) {
                    showAlbumCover(false, true);
                }
                return true;
            }
        };
        blurredView.setAlpha(0.0f);
        blurredView.setVisibility(View.INVISIBLE);
        getContainer().addView(blurredView);

        bigAlbumConver = new BackupImageView(context);
        bigAlbumConver.setAspectFit(true);
        bigAlbumConver.setRoundRadius(dp(14));
        bigAlbumConver.setScaleX(0.9f);
        bigAlbumConver.setScaleY(0.9f);
        blurredView.addView(bigAlbumConver, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 30, 30, 30, 30));

        updateTitle(false);
        updateRepeatButton();
        updateEmptyView();
        applyUiColors();

        final CharSequence barTitle = actionBar.getTitleTextView() != null ? actionBar.getTitleTextView().getText() : null;
        if (!TextUtils.isEmpty(barTitle)) {
            topLabelTextView.setText(barTitle.toString());
        }
    }

    @Override
    public int getContainerViewHeight() {
        if (nowPlayingLayout == null) {
            return 0;
        }
        if (!queueOpen || playlist.size() <= 1) {
            return container.getMeasuredHeight();
        } else {
            int offset = dp(13);
            int top = scrollOffsetY - backgroundPaddingTop - offset;
//            if (currentSheetAnimationType == 1) {
                top += listView.getTranslationY();
//            }
            if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
                float toMove = offset + dp(11 - 7);
                float moveProgress = Math.min(1.0f, (ActionBar.getCurrentActionBarHeight() - top - backgroundPaddingTop) / toMove);
                float availableToMove = ActionBar.getCurrentActionBarHeight() - toMove;

                int diff = (int) (availableToMove * moveProgress);
                top -= diff;
            }

            top += AndroidUtilities.statusBarHeight;

            return container.getMeasuredHeight() - top;
        }
    }

    private void setupSeekRepeatOnHold(View button, int shiftMs) {
        button.setOnLongClickListener(view -> {
            final MessageObject pressedObject = MediaController.getInstance().getPlayingMessageObject();
            if (pressedObject == null) {
                return false;
            }
            cancelSeekRepeat();
            seekRepeatRunnable = new Runnable() {
                @Override
                public void run() {
                    if (MediaController.getInstance().getPlayingMessageObject() != pressedObject) {
                        cancelSeekRepeat();
                        return;
                    }
                    MediaController.getInstance().seekShift(shiftMs);
                    AndroidUtilities.runOnUIThread(this, SEEK_REPEAT_DELAY_MS);
                }
            };
            seekRepeatRunnable.run();
            return true;
        });
        button.setOnTouchListener((view, event) -> {
            final int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                cancelSeekRepeat();
            }
            return false;
        });
    }

    private void cancelSeekRepeat() {
        if (seekRepeatRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(seekRepeatRunnable);
            seekRepeatRunnable = null;
        }
    }

    private void startForwardRewindingSeek() {
        if (rewindingState == 1) {
            lastRewindingTime = System.currentTimeMillis();
            rewindingProgress = MediaController.getInstance().getPlayingMessageObject().audioProgress;
            AndroidUtilities.cancelRunOnUIThread(forwardSeek);
            AndroidUtilities.runOnUIThread(forwardSeek);
        }
    }

    private void updateEmptyViewPosition() {
        if (emptyView.getVisibility() != View.VISIBLE) {
            return;
        }
        int h = nowPlayingLayout.getVisibility() == View.VISIBLE ? dp(150) : -dp(30);
        emptyView.setTranslationY((emptyView.getMeasuredHeight() - containerView.getMeasuredHeight() - h) / 2);
    }

    private void updateEmptyView() {
        emptyView.setVisibility(searching && listAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        updateEmptyViewPosition();
    }

    private boolean scrollToCurrentSong(boolean search) {
        MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
        if (playingMessageObject != null) {
            boolean found = false;
            if (search) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof AudioPlayerCell) {
                        if (((AudioPlayerCell) child).getMessageObject() == playingMessageObject) {
                            if (child.getBottom() <= listView.getMeasuredHeight()) {
                                found = true;
                            }
                            break;
                        }
                    }
                }
            }
            if (!found) {
                int idx = playlist.indexOf(playingMessageObject);
                if (padWithItem) {
                    idx++;
                }
                if (idx >= 0) {
                    if (SharedConfig.playOrderReversed) {
                        layoutManager.scrollToPosition(idx);
                    } else {
                        layoutManager.scrollToPosition(playlist.size() - idx);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean onCustomMeasure(View view, int width, int height) {
        if (view == blurredView) {
            final int w = getContainer().getMeasuredWidth();
            final int h = getContainer().getMeasuredHeight();
            blurredView.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            return true;
        }
        return false;
    }

    @Override
    protected boolean onCustomLayout(View view, int left, int top, int right, int bottom) {
        if (view == blurredView) {
            blurredView.layout(0, 0, blurredView.getMeasuredWidth(), blurredView.getMeasuredHeight());
            return true;
        }
        return false;
    }

    private void setMenuItemChecked(ActionBarMenuSubItem item, boolean checked) {
        if (checked) {
            item.setTextColor(getThemedColor(Theme.key_player_buttonActive));
            item.setIconColor(getThemedColor(Theme.key_player_buttonActive));
        } else {
            item.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem));
            item.setIconColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem));
        }
    }

    private HintView speedHintView;
    private long lastPlaybackClick;

    private void checkSpeedHint() {
        final long now = System.currentTimeMillis();
        if (now - lastPlaybackClick > 300) {
            int hintValue = MessagesController.getGlobalNotificationsSettings().getInt("speedhint", 0);
            hintValue++;
            if (hintValue > 2) {
                hintValue = -10;
            }
            MessagesController.getGlobalNotificationsSettings().edit().putInt("speedhint", hintValue).apply();
            if (hintValue >= 0) {
                showSpeedHint();
            }
        }
        lastPlaybackClick = now;
    }

    private void showSpeedHint() {
        if (containerView != null) {
            speedHintView = new HintView(getContext(), 5, false) {
                @Override
                public void setVisibility(int visibility) {
                    super.setVisibility(visibility);
                    if (visibility != View.VISIBLE) {
                        try {
                            ((ViewGroup) getParent()).removeView(this);
                        } catch (Exception e) {}
                    }
                }
            };
            speedHintView.setExtraTranslationY(dp(6));
            speedHintView.setText(LocaleController.getString(R.string.SpeedHint));
            nowPlayingLayout.addView(speedHintView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 0, 6, 0));
            speedHintView.showForView(playbackSpeedButton, true);
        }
    }

    private void updateSubMenu() {
        setMenuItemChecked(shuffleListItem, SharedConfig.shuffleMusic);
        setMenuItemChecked(reverseOrderItem, SharedConfig.playOrderReversed);
        setMenuItemChecked(repeatListItem, SharedConfig.repeatMode == 1);
        setMenuItemChecked(repeatSongItem, SharedConfig.repeatMode == 2);
    }

    private boolean equals(float a, float b) {
        return Math.abs(a - b) < 0.05f;
    }

    private void updatePlaybackButton(boolean animated) {
        if (playbackSpeedButton == null) {
            return;
        }
        float currentPlaybackSpeed = MediaController.getInstance().getPlaybackSpeed(true);
        speedIcon.setValue(currentPlaybackSpeed, animated);
        speedSlider.setSpeed(currentPlaybackSpeed, animated);
        updateColors();

        boolean isFinal = !slidingSpeed;
        slidingSpeed = false;

        for (int a = 0; a < speedItems.length; a++) {
            if (isFinal && equals(currentPlaybackSpeed, speeds[a])) {
                speedItems[a].setColors(getThemedColor(Theme.key_featuredStickers_addButtonPressed), getThemedColor(Theme.key_featuredStickers_addButtonPressed));
            } else {
                speedItems[a].setColors(getThemedColor(Theme.key_actionBarDefaultSubmenuItem), getThemedColor(Theme.key_actionBarDefaultSubmenuItem));
            }
        }
    }

    public void updateColors() {
        if (playbackSpeedButton != null) {
            float currentPlaybackSpeed = MediaController.getInstance().getPlaybackSpeed(true);
            final int color = getThemedColor(!equals(currentPlaybackSpeed, 1.0f) ? Theme.key_featuredStickers_addButtonPressed : Theme.key_inappPlayerClose);
            if (speedIcon != null) {
                speedIcon.setColor(color);
            }
            playbackSpeedButton.setBackground(Theme.createSelectorDrawable(color & 0x19ffffff, 1, dp(14)));
        }
        if (castItem != null) {
            castItem.setEnabledByColor(castItemButton != null && castItemButton.isConnected(), getThemedColor(Theme.key_actionBarDefaultSubmenuItem), getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon), getThemedColor(Theme.key_featuredStickers_addButton));
            castItem.setSelectorColor(castItemButton != null && castItemButton.isConnected() ? Theme.multAlpha(getThemedColor(Theme.key_featuredStickers_addButton), .10f) : getThemedColor(Theme.key_listSelector));
        }
    }

    private void onSubItemClick(int id) {
        final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject == null || parentActivity == null) {
            return;
        }
        if (id == 1) {
            forward(messageObject);
        } else if (id == 2) {
            share(messageObject);
        } else if (id == 4) {
            if (UserConfig.selectedAccount != currentAccount) {
                parentActivity.switchToAccount(currentAccount, true);
            }
            
            Bundle args = new Bundle();
            long did = messageObject.getDialogId();
            if (DialogObject.isEncryptedDialog(did)) {
                args.putInt("enc_id", DialogObject.getEncryptedChatId(did));
            } else if (DialogObject.isUserDialog(did)) {
                args.putLong("user_id", did);
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                if (chat != null && chat.migrated_to != null) {
                    args.putLong("migrated_to", did);
                    did = -chat.migrated_to.channel_id;
                }
                args.putLong("chat_id", -did);
            }
            args.putInt("message_id", messageObject.getId());
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
            parentActivity.presentFragment(new ChatActivity(args), false, false);
            dismiss();
        } else if (id == 5) {
            saveToMusic(messageObject);
        } else if (id == 6) {
//            ChromecastController.getInstance().setCurrentMediaAndCastIfNeeded(MediaController.getInstance().getCurrentChromecastMedia());
            castItemButton.performClick();
        } else if (id == 7) {
            saveToProfile(messageObject, false, () -> {
                if (savedMusicList != null) {
                    savedMusicList.remove(messageObject);
                    if (savedMusicList.list.isEmpty()) {
                        MediaController.getInstance().cleanup();
                        dismiss();
                    } else {
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.musicListLoaded, savedMusicList);
                    }
                }
            }, false);
        } else if (id == 9) {
            boolean nowDisabled = !MessagesController.getGlobalMainSettings().getBoolean("disableAutoplayNextVoice", false);
            MessagesController.getGlobalMainSettings().edit().putBoolean("disableAutoplayNextVoice", nowDisabled).apply();
        } else if (id == 8) {
            new SelectAudioAlert(getContext(), true, null, audio -> {
                if (audio == null || savedMusicList == null) return;
                final TLRPC.Document document = audio.getDocument();
                if (document == null) return;
                if (document.id != 0) {
                    final TLRPC.TL_account_saveMusic req = new TLRPC.TL_account_saveMusic();
                    req.id = new TLRPC.TL_inputDocument();
                    req.id.id = document.id;
                    req.id.access_hash = document.access_hash;
                    req.id.file_reference = document.file_reference;
                    if (savedMusicList != null) {
                        savedMusicList.add(document);
                    }
                    playlist.clear();
                    playlist.addAll(savedMusicList.list);
                    listAdapter.notifyDataSetChanged();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
                    return;
                }
                final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
                progressDialog.showDelayed(180);
                final File localFile = new File(audio.messageOwner.attachPath);
                if (!localFile.exists()) return;
                FileLoader.getInstance(currentAccount).uploadFile(localFile.getAbsolutePath(), file -> {
                    if (file == null) {
                        progressDialog.dismiss();
                        return;
                    }
                    final TLRPC.TL_messages_uploadMedia req = new TLRPC.TL_messages_uploadMedia();
                    req.peer = MessagesController.getInstance(currentAccount).getInputPeer(UserConfig.getInstance(currentAccount).getClientUserId());
                    req.media = new TLRPC.TL_inputMediaUploadedDocument();
                    req.media.file = file;
                    req.media.mime_type = document.mime_type;
                    req.media.attributes.addAll(document.attributes);
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                        progressDialog.dismiss();
                        if (res instanceof TLRPC.TL_messageMediaDocument) {
                            final TLRPC.TL_messageMediaDocument r = (TLRPC.TL_messageMediaDocument) res;

                            final TLRPC.TL_account_saveMusic req2 = new TLRPC.TL_account_saveMusic();
                            req2.id = new TLRPC.TL_inputDocument();
                            req2.id.id = r.document.id;
                            req2.id.access_hash = r.document.access_hash;
                            req2.id.file_reference = r.document.file_reference;
                            if (savedMusicList != null) {
                                savedMusicList.add(r.document);
                            }
                            playlist.clear();
                            playlist.addAll(savedMusicList.list);
                            listAdapter.notifyDataSetChanged();
                            ConnectionsManager.getInstance(currentAccount).sendRequest(req2, null);
                        }
                    }));
                });
            }, null).withoutSavedMusic().show();
        }
    }

    private void showAlbumCover(boolean show, boolean animated) {
        if (show) {
            if (blurredView.getVisibility() == View.VISIBLE || blurredAnimationInProgress) {
                return;
            }
            blurredView.setTag(1);
            bigAlbumConver.setImageBitmap(coverContainer.getImageReceiver().getBitmap());
            blurredAnimationInProgress = true;
            ScrimOptions.makeGlobalBlurBitmaps((bitmapBg, bitmapOptions) ->
                blurredView.setBackground(new BitmapDrawable(bitmapBg)));

            blurredView.setVisibility(View.VISIBLE);
            blurredView.animate().alpha(1.0f).setDuration(180).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    blurredAnimationInProgress = false;
                }
            }).start();
            bigAlbumConver.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
        } else {
            if (blurredView.getVisibility() != View.VISIBLE) {
                return;
            }
            blurredView.setTag(null);
            if (animated) {
                blurredAnimationInProgress = true;
                blurredView.animate().alpha(0.0f).setDuration(180).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        blurredView.setVisibility(View.INVISIBLE);
                        bigAlbumConver.setImageBitmap(null);
                        blurredAnimationInProgress = false;
                    }
                }).start();
                bigAlbumConver.animate().scaleX(0.9f).scaleY(0.9f).setDuration(180).start();
            } else {
                blurredView.setAlpha(0.0f);
                blurredView.setVisibility(View.INVISIBLE);
                bigAlbumConver.setImageBitmap(null);
                bigAlbumConver.setScaleX(0.9f);
                bigAlbumConver.setScaleY(0.9f);
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingDidStart || id == NotificationCenter.messagePlayingPlayStateChanged || id == NotificationCenter.messagePlayingDidReset) {
            updateTitle(id == NotificationCenter.messagePlayingDidReset && (Boolean) args[1]);
            if (id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.messagePlayingPlayStateChanged) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = listView.getChildAt(a);
                    if (view instanceof AudioPlayerCell) {
                        AudioPlayerCell cell = (AudioPlayerCell) view;
                        MessageObject messageObject = cell.getMessageObject();
                        if (messageObject != null && (messageObject.isVoice() || messageObject.isMusic())) {
                            cell.updateButtonState(false, true);
                        }
                    }
                }
                if (id == NotificationCenter.messagePlayingPlayStateChanged) {
                    if (MediaController.getInstance().getPlayingMessageObject() != null) {
                        if (MediaController.getInstance().isMessagePaused()) {
                            startForwardRewindingSeek();
                        } else if (rewindingState == 1 && rewindingProgress != -1f) {
                            AndroidUtilities.cancelRunOnUIThread(forwardSeek);
                            lastUpdateRewindingPlayerTime = 0;
                            forwardSeek.run();
                            rewindingProgress = -1f;
                        }
                    }
                }
            } else {
                MessageObject messageObject = (MessageObject) args[0];
                if (messageObject.eventId != 0) {
                    return;
                }
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = listView.getChildAt(a);
                    if (view instanceof AudioPlayerCell) {
                        AudioPlayerCell cell = (AudioPlayerCell) view;
                        MessageObject messageObject1 = cell.getMessageObject();
                        if (messageObject1 != null && (messageObject1.isVoice() || messageObject1.isMusic())) {
                            cell.updateButtonState(false, true);
                        }
                    }
                }
            }
            if (optionsIcon != null) {
                optionsIcon.setCasting(CastSync.isActive(), true);
            }
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject != null && (messageObject.isMusic() || messageObject.isVoice())) {
                updateProgress(messageObject);
            }
        } else if (id == NotificationCenter.messagePlayingSpeedChanged) {
            updatePlaybackButton(true);
        } else if (id == NotificationCenter.musicDidLoad) {
            savedMusicList = MediaController.getInstance().currentSavedMusicList;
            playlist = MediaController.getInstance().getPlaylist();
            listAdapter.notifyDataSetChanged();
        } else if (id == NotificationCenter.moreMusicDidLoad) {
            savedMusicList = MediaController.getInstance().currentSavedMusicList;
            playlist = MediaController.getInstance().getPlaylist();
            listAdapter.notifyDataSetChanged();
            if (SharedConfig.playOrderReversed) {
                listView.stopScroll();
                int addedCount = (Integer) args[0];
                int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                int position = layoutManager.findLastVisibleItemPosition();
                if (position != RecyclerView.NO_POSITION) {
                    View firstVisView = layoutManager.findViewByPosition(position);
                    int offset = firstVisView == null ? 0 : firstVisView.getTop();
                    layoutManager.scrollToPositionWithOffset(position + addedCount, offset);
                }
            }
        } else if (id == NotificationCenter.fileLoaded) {
            String name = (String) args[0];
            if (name.equals(currentFile)) {
                updateTitle(false);
                currentAudioFinishedLoading = true;
            }
        } else if (id == NotificationCenter.fileLoadProgressChanged) {
            String name = (String) args[0];
            if (name.equals(currentFile)) {
                MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (messageObject == null) {
                    return;
                }
                Long loadedSize = (Long) args[1];
                Long totalSize = (Long) args[2];
                float bufferedProgress;
                if (currentAudioFinishedLoading) {
                    bufferedProgress = 1.0f;
                } else {
                    long newTime = SystemClock.elapsedRealtime();
                    if (Math.abs(newTime - lastBufferedPositionCheck) >= 500) {
                        bufferedProgress = MediaController.getInstance().isStreamingCurrentAudio() ? FileLoader.getInstance(currentAccount).getBufferedProgressFromPosition(messageObject.audioProgress, currentFile) : 1.0f;
                        lastBufferedPositionCheck = newTime;
                    } else {
                        bufferedProgress = -1;
                    }
                }
                if (bufferedProgress != -1) {
                    seekBarBufferSpring.getSpring().setFinalPosition(bufferedProgress * 1000);
                    seekBarBufferSpring.start();
                }
            }
        } else if (id == NotificationCenter.musicIdsLoaded) {
            updateTitle(false);
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    private void updateLayout() {
        if (!queueOpen) {
            // full-screen "Now Playing" front page: keep the action bar hidden
            if (actionBar.getTag() != null) {
                actionBar.setTag(null);
            }
            if (wasLight) {
                AndroidUtilities.setLightStatusBar(this, wasLight = false);
            }
            containerView.invalidate();
            return;
        }
        if (listView.getChildCount() <= 0) {
            listView.setTopGlowOffset(scrollOffsetY = listView.getPaddingTop());
            containerView.invalidate();
            return;
        }
        View child = listView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = child instanceof AudioPlayerCell ? child.getTop() : child.getBottom();
        int newOffset = dp(7);
        if (top >= dp(7) && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
        }
        boolean show = newOffset <= dp(12);
        if (show && actionBar.getTag() == null || !show && actionBar.getTag() != null) {
            actionBar.setTag(show ? 1 : null);
            if (actionBarAnimation != null) {
                actionBarAnimation.cancel();
                actionBarAnimation = null;
            }
            actionBarAnimation = new AnimatorSet();
            if (isProfilePlaylist) {
                actionBarAnimation.playTogether(
                    ObjectAnimator.ofFloat(actionBar, actionBarSlideProperty, show ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(actionBarBackground, View.ALPHA, show ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(actionBarShadow, View.ALPHA, show ? 1.0f : 0.0f)
                );
            } else {
                actionBarAnimation.playTogether(
                    ObjectAnimator.ofFloat(actionBar, View.ALPHA, show ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(actionBarShadow, View.ALPHA, show ? 1.0f : 0.0f)
                );
            }
            actionBarAnimation.setDuration(320);
            actionBarAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            actionBarAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {

                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    actionBarAnimation = null;
                }
            });
            actionBarAnimation.start();
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
        newOffset += layoutParams.topMargin - AndroidUtilities.statusBarHeight - dp(11);
        if (scrollOffsetY != newOffset) {
            listView.setTopGlowOffset((scrollOffsetY = newOffset) - layoutParams.topMargin - AndroidUtilities.statusBarHeight);
            containerView.invalidate();
        }

        int offset = dp(13);
        top = scrollOffsetY - backgroundPaddingTop - offset;
//        if (currentSheetAnimationType == 1) {
            top += listView.getTranslationY();
//        }
        float rad = 1.0f;

        if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
            float toMove = offset + dp(11 - 7);
            float moveProgress = Math.min(1.0f, (ActionBar.getCurrentActionBarHeight() - top - backgroundPaddingTop) / toMove);

            rad = 1.0f - moveProgress;
        }

        boolean light = rad <= 0.5f && ColorUtils.calculateLuminance(getThemedColor(Theme.key_dialogBackground)) > 0.7f;
        if (light != wasLight) {
            AndroidUtilities.setLightStatusBar(this, wasLight = light);
        }
    }

    private float actionBarSlide;
    private final Property<ActionBar, Float> actionBarSlideProperty = new AnimationProperties.FloatProperty<ActionBar>("actionBarSlide") {
        @Override
        public void setValue(ActionBar actionBar, float value) {
            actionBarSlide = value;
            final View titleTextView = actionBar.getTitleTextView();
            final View backButton = actionBar.getBackButton();

            titleTextView.setTranslationX(dp(-52) * (1.0f - value));
            backButton.setTranslationX(dp(-52) * (1.0f - value));
            if (searchItem != null && searchItem.getSearchContainer() != null) {
                searchItem.getSearchContainer().setClipChildren(false);
                searchItem.getSearchContainer().setClipToPadding(false);
                searchItem.getSearchContainer().setPadding(0, 0, dp(AndroidUtilities.isTablet() ? 74 : 66), 0);
                searchItem.getSearchContainer().setTranslationX(dp(AndroidUtilities.isTablet() ? 74 : 66) + dp(-52) * (1.0f - value));
                if (searchItem.getSearchClearButton() != null) {
                    searchItem.getSearchClearButton().setTranslationX(dp(52) * (1.0f - value));
                }
            }

            backButton.setScaleX(lerp(0.6f, 1.0f, value));
            backButton.setScaleY(lerp(0.6f, 1.0f, value));
            backButton.setAlpha(lerp(0.0f, 1.0f, value));
            containerView.invalidate();
        }

        @Override
        public Float get(ActionBar object) {
            return actionBarSlide;
        }
    };

    @Override
    public void dismiss() {
        super.dismiss();
        cancelSeekRepeat();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoadProgressChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.musicDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.moreMusicDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.musicIdsLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.messagePlayingSpeedChanged);
        DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
        dynamicBackground.cancel();
        if (instance == this) {
            instance = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (actionBar != null && actionBar.isSearchFieldVisible()) {
            actionBar.closeSearchField();
            return;
        }
        if (blurredView.getTag() != null) {
            showAlbumCover(false, true);
            return;
        }
        if (queueOpen) {
            closeQueue();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onFailedDownload(String fileName, boolean canceled) {

    }

    @Override
    public void onSuccessDownload(String fileName) {

    }

    @Override
    public void onProgressDownload(String fileName, long downloadedSize, long totalSize) {
        progressView.setProgress(Math.min(1f, downloadedSize / (float) totalSize), true);
    }

    @Override
    public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {

    }

    @Override
    public int getObserverTag() {
        return TAG;
    }

    public void updateRepeatButton() {
        int mode = SharedConfig.repeatMode;
        if (mode == 2) {
            repeatButton.setIcon(R.drawable.player_new_repeatone);
            repeatButton.setContentDescription(LocaleController.getString(R.string.AccDescrRepeatOne));
        } else {
            repeatButton.setIcon(R.drawable.player_new_repeatall);
            repeatButton.setContentDescription(mode == 1 ? LocaleController.getString(R.string.AccDescrRepeatList) : LocaleController.getString(R.string.AccDescrRepeatOff));
        }
        applyUiColors();
    }

    private void updateProgress(MessageObject messageObject) {
        updateProgress(messageObject, false);
    }

    private void updateProgress(MessageObject messageObject, boolean animated) {
        if (seekBarView != null) {
            int newTime;
            if (seekBarView.isDragging()) {
                newTime = (int) (messageObject.getDuration() * seekBarView.getProgress());
            } else {
                boolean updateRewinding = rewindingProgress >= 0 && (rewindingState == -1 || (rewindingState == 1 && MediaController.getInstance().isMessagePaused()));
                if (updateRewinding) {
                    seekBarView.setProgress(rewindingProgress, animated);
                } else {
                    seekBarView.setProgress(messageObject.audioProgress, animated);
                }

                float bufferedProgress;
                if (currentAudioFinishedLoading) {
                    bufferedProgress = 1.0f;
                } else {
                    long time = SystemClock.elapsedRealtime();
                    if (Math.abs(time - lastBufferedPositionCheck) >= 500) {
                        bufferedProgress = MediaController.getInstance().isStreamingCurrentAudio() ? FileLoader.getInstance(currentAccount).getBufferedProgressFromPosition(messageObject.audioProgress, currentFile) : 1.0f;
                        lastBufferedPositionCheck = time;
                    } else {
                        bufferedProgress = -1;
                    }
                }
                if (bufferedProgress != -1) {
                    seekBarBufferSpring.getSpring().setFinalPosition(bufferedProgress * 1000);
                    seekBarBufferSpring.start();
                }
                if (updateRewinding) {
                    newTime = (int) (messageObject.getDuration() * seekBarView.getProgress());
                    messageObject.audioProgressSec = newTime;
                } else {
                    newTime = messageObject.audioProgressSec;
                }
            }
            if (lastTime != newTime) {
                lastTime = newTime;
                timeTextView.setText(AndroidUtilities.formatShortDuration(newTime));
                final int fullDuration = (int) messageObject.getDuration();
                if (fullDuration > 0) {
                    durationTextView.setText("-" + AndroidUtilities.formatShortDuration(Math.max(0, fullDuration - newTime)));
                }
            }
        }
    }

    private void checkIfMusicDownloaded(MessageObject messageObject) {
        File cacheFile = null;
        if (messageObject.messageOwner.attachPath != null && messageObject.messageOwner.attachPath.length() > 0) {
            cacheFile = new File(messageObject.messageOwner.attachPath);
            if (!cacheFile.exists()) {
                cacheFile = null;
            }
        }
        if (cacheFile == null) {
            cacheFile = FileLoader.getInstance(currentAccount).getPathToMessage(messageObject.messageOwner);
        }
        boolean canStream = SharedConfig.streamMedia && (int) messageObject.getDialogId() != 0 && messageObject.isMusic();
        if (!cacheFile.exists() && !canStream) {
            String fileName = messageObject.getFileName();
            DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, this);
            Float progress = ImageLoader.getInstance().getFileProgress(fileName);
            progressView.setProgress(progress != null ? progress : 0, false);
            progressView.setVisibility(View.VISIBLE);
            seekBarView.setVisibility(View.INVISIBLE);
            playButton.setEnabled(false);
        } else {
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
            progressView.setVisibility(View.INVISIBLE);
            seekBarView.setVisibility(View.VISIBLE);
            playButton.setEnabled(true);
        }
    }

    private void updateTitle(boolean shutdown) {
        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject == null && shutdown || messageObject != null && !(messageObject.isMusic() || messageObject.isVoice())) {
            dismiss();
        } else {
            if (messageObject == null) {
                lastMessageObject = null;
                return;
            }
            final boolean sameMessageObject = messageObject == lastMessageObject;
            lastMessageObject = messageObject;
            if (messageObject.eventId != 0 || messageObject.getId() <= -2000000000) {
                optionsButton.setVisibility(View.INVISIBLE);
            } else {
                optionsButton.setVisibility(View.VISIBLE);
            }
            final long dialogId = messageObject.getDialogId();
            final long docId = messageObject.getDocument() != null ? messageObject.getDocument().id : 0L;
            final boolean noforwards = (
                dialogId < 0 && MessagesController.getInstance(currentAccount).isPeerNoForwards(dialogId) ||
                MessagesController.getInstance(currentAccount).isPeerNoForwards(messageObject.getDialogId()) ||
                messageObject.messageOwner.noforwards
            );
            if (noforwards != this.noforwards) {
                this.noforwards = noforwards;
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
            if (noforwards) {
                optionsButton.hideSubItem(1);
                optionsButton.hideSubItem(2);
                optionsButton.hideSubItem(5);
                optionsButton.hideSubItem(6);
                optionsButton.setAdditionalYOffset(-dp(16));
            } else {
                optionsButton.showSubItem(1);
                optionsButton.showSubItem(2);
                optionsButton.showSubItem(5);
                optionsButton.setAdditionalYOffset(-dp(157 + 40));
            }

            checkIfMusicDownloaded(messageObject);
            updateProgress(messageObject, !sameMessageObject);
            updateCover(messageObject, !sameMessageObject);

            if (MediaController.getInstance().isMessagePaused()) {
                playPauseDrawable.setPause(false);
                playButton.setContentDescription(LocaleController.getString(R.string.AccActionPlay));
            } else {
                playPauseDrawable.setPause(true);
                playButton.setContentDescription(LocaleController.getString(R.string.AccActionPause));
            }
            String title = messageObject.getMusicTitle();
            String author = messageObject.getMusicAuthor();
            titleTextView.setText(title);
            authorTextView.setText(author);

            final MessagesController.SavedMusicIds musicIds = MessagesController.getInstance(currentAccount).getSavedMusicIds();
            saveToProfileButton.setLoading(musicIds.loading);
            setVisibleInProfile(musicIds.ids.contains(docId));

            int duration = lastDuration = (int) messageObject.getDuration();

            if (durationTextView != null) {
                durationTextView.setText(duration != 0 ? "-" + AndroidUtilities.formatShortDuration(Math.max(0, duration - lastTime)) : "-:--");
            }

            if (duration > 60 * 10) {
                playbackSpeedButton.setVisibility(View.VISIBLE);
            } else {
                playbackSpeedButton.setVisibility(View.GONE);
            }

            if (!sameMessageObject) {
                preloadNeighboringThumbs();
            }
        }
    }

    private void updateCover(MessageObject messageObject, boolean animated) {
        final BackupImageView imageView = animated ? coverContainer.getNextImageView() : coverContainer.getImageView();
        final AudioInfo audioInfo = MediaController.getInstance().getAudioInfo();
        if (animated) {
            coverContainer.switchImageViews();
        }
        if (audioInfo != null && audioInfo.getCover() != null) {
            imageView.setImageBitmap(audioInfo.getCover());
            currentFile = null;
            currentAudioFinishedLoading = true;
            dynamicBackground.apply(messageObject, audioInfo.getCover());
        } else {
            TLRPC.Document document = messageObject.getDocument();
            currentFile = FileLoader.getAttachFileName(document);
            currentAudioFinishedLoading = false;
            String artworkUrl = messageObject.getArtworkUrl(false);
            final ImageLocation thumbImageLocation = getArtworkThumbImageLocation(messageObject);
            if (!TextUtils.isEmpty(artworkUrl)) {
                imageView.setImage(ImageLocation.getForPath(artworkUrl), null, thumbImageLocation, null, null, 0, 1, messageObject);
            } else if (thumbImageLocation != null) {
                imageView.setImage(null, null, thumbImageLocation, null, null, 0, 1, messageObject);
            } else {
                imageView.setImageDrawable(new CoverPlaceholderDrawable());
                dynamicBackground.apply(messageObject, null);
            }
            imageView.invalidate();
        }
    }

    private ImageLocation getArtworkThumbImageLocation(MessageObject messageObject) {
        final TLRPC.Document document = messageObject.getDocument();
        TLRPC.PhotoSize thumb = document != null ? FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 360) : null;
        if (!(thumb instanceof TLRPC.TL_photoSize) && !(thumb instanceof TLRPC.TL_photoSizeProgressive)) {
            thumb = null;
        }
        if (thumb != null) {
            return ImageLocation.getForDocument(thumb, document);
        }
        final String smallArtworkUrl = messageObject.getArtworkUrl(true);
        if (smallArtworkUrl != null) {
            return ImageLocation.getForPath(smallArtworkUrl);
        }
        return null;
    }

    private void preloadNeighboringThumbs() {
        final MediaController mediaController = MediaController.getInstance();
        final List<MessageObject> playlist = mediaController.getPlaylist();
        if (playlist.size() <= 1) {
            return;
        }

        final List<MessageObject> neighboringItems = new ArrayList<>();
        final int playingIndex = mediaController.getPlayingMessageObjectNum();

        int nextIndex = playingIndex + 1;
        int prevIndex = playingIndex - 1;
        if (nextIndex >= playlist.size()) {
            nextIndex = 0;
        }
        if (nextIndex <= -1) {
            nextIndex = playlist.size() - 1;
        }
        if (prevIndex <= -1) {
            prevIndex = playlist.size() - 1;
        }
        if (prevIndex >= playlist.size()) {
            prevIndex = 0;
        }

        neighboringItems.add(playlist.get(nextIndex));
        if (nextIndex != prevIndex) {
            neighboringItems.add(playlist.get(prevIndex));
        }

        for (int i = 0, N = neighboringItems.size(); i < N; i++) {
            final MessageObject messageObject = neighboringItems.get(i);
            final ImageLocation thumbImageLocation = getArtworkThumbImageLocation(messageObject);
            if (thumbImageLocation != null) {
                if (thumbImageLocation.path != null) {
                    ImageLoader.getInstance().preloadArtwork(thumbImageLocation.path);
                } else {
                    FileLoader.getInstance(currentAccount).loadFile(thumbImageLocation, messageObject, null, FileLoader.PRIORITY_LOW, 1);
                }
            }
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private ArrayList<MessageObject> searchResult = new ArrayList<>();
        private String searchQuery;
        private Runnable searchRunnable;

        public ListAdapter(Context context) {
            this.context = context;
        }

        private boolean listViewIsVisible;
        public void setup() {
            listViewIsVisible = playlist.size() > 1;
            listView.setVisibility(View.GONE);
            listView.setTranslationY(0);
            if (queueButton != null) {
                queueButton.setVisibility(listViewIsVisible ? View.VISIBLE : View.GONE);
            }
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            boolean newVisible = playlist.size() > 1;
            if (newVisible != listViewIsVisible) {
                listViewIsVisible = newVisible;
                if (queueButton != null) {
                    queueButton.setVisibility(listViewIsVisible ? View.VISIBLE : View.GONE);
                    if (!listViewIsVisible && queueOpen) {
                        closeQueue();
                    }
                }
            }
            if (queueOpen && listViewIsVisible) {
                listView.setVisibility(View.VISIBLE);
                listView.setTranslationY(0);
                listView.setPadding(0, listView.getPaddingTop(), 0, (!isMyList() && !noforwards) ? dp(72) : 0);
            } else {
                listView.setVisibility(View.GONE);
            }
            updateEmptyView();
        }

        @Override
        public int getItemCount() {
            if (searchWas) {
                return (padWithItem ? 1 : 0) + searchResult.size();
            }
            return playlist.size() > 1 ? (padWithItem ? 1 : 0) + playlist.size() : 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (padWithItem && holder.getAdapterPosition() == 0)
                return false;
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == 1) {
                View paddingView = new View(context) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(
                            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(dp(300), MeasureSpec.EXACTLY)
                        );
                    }
                };
                paddingView.setTag(RecyclerListView.TAG_NOT_SECTION);
                return new RecyclerListView.Holder(paddingView);
            } else {
                View view = new AudioPlayerCell(context, MediaController.getInstance().currentPlaylistIsGlobalSearch() ? AudioPlayerCell.VIEW_TYPE_GLOBAL_SEARCH : AudioPlayerCell.VIEW_TYPE_DEFAULT, resourcesProvider);
                return new RecyclerListView.Holder(view);
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (padWithItem) {
                if (position == 0) {
                    final View paddingView = holder.itemView;
                    return;
                }
                position--;
            }
            final AudioPlayerCell cell = (AudioPlayerCell) holder.itemView;
            final MessageObject messageObject;
            final boolean needDivider;
            final View.OnTouchListener onReorderTouch;
            if (searchWas) {
                messageObject = searchResult.get(position);
                needDivider = position + 1 < searchResult.size();
            } else if (savedMusicList != null ? !SharedConfig.playOrderReversed : SharedConfig.playOrderReversed) {
                messageObject = playlist.get(position);
                needDivider = position + 1 < playlist.size();
            } else {
                messageObject = playlist.get(playlist.size() - position - 1);
                needDivider = (playlist.size() - position - 2) >= 0;
            }
            if (messageObject != null) {
                messageObject.setQuery(searchQuery);
            }
            if (isMyList()) {
                onReorderTouch = (v, e) -> {
                    if (e.getAction() == MotionEvent.ACTION_DOWN) {
                        itemTouchHelper.startDrag(listView.getChildViewHolder(cell));
                    }
                    return false;
                };
            } else {
                onReorderTouch = null;
            }
            cell.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
            cell.setMessageObject(messageObject, isMyList(), isMyList() || noforwards || messageObject.getId() <= 0 ? null : btn -> showOptions(cell, messageObject), needDivider, onReorderTouch);
        }

        @Override
        public int getItemViewType(int i) {
            if (padWithItem && i == 0) {
                return 1;
            }
            return 0;
        }

        public void search(final String query) {
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }
            if (query == null) {
                searchQuery = null;
                searchResult.clear();
                notifyDataSetChanged();
            } else {
                Utilities.searchQueue.postRunnable(searchRunnable = () -> {
                    searchRunnable = null;
                    processSearch(query);
                }, 300);
            }
        }

        private void processSearch(final String query) {
            AndroidUtilities.runOnUIThread(() -> {
                final ArrayList<MessageObject> copy = new ArrayList<>(playlist);
                Utilities.searchQueue.postRunnable(() -> {
                    String search1 = query.trim().toLowerCase();
                    if (search1.length() == 0) {
                        updateSearchResults(new ArrayList<>(), query);
                        return;
                    }
                    String search2 = LocaleController.getInstance().getTranslitString(search1);
                    if (search1.equals(search2) || search2.length() == 0) {
                        search2 = null;
                    }
                    String[] search = new String[1 + (search2 != null ? 1 : 0)];
                    search[0] = search1;
                    if (search2 != null) {
                        search[1] = search2;
                    }

                    ArrayList<MessageObject> resultArray = new ArrayList<>();

                    for (int a = 0; a < copy.size(); a++) {
                        MessageObject messageObject = copy.get(a);
                        for (int b = 0; b < search.length; b++) {
                            String q = search[b];
                            String name = messageObject.getDocumentName();
                            if (name == null || name.length() == 0) {
                                continue;
                            }
                            name = name.toLowerCase();
                            if (name.contains(q)) {
                                resultArray.add(messageObject);
                                break;
                            }
                            TLRPC.Document document;
                            if (messageObject.type == MessageObject.TYPE_TEXT) {
                                document = messageObject.messageOwner.media.webpage.document;
                            } else {
                                document = messageObject.messageOwner.media.document;
                            }
                            boolean ok = false;
                            for (int c = 0; c < document.attributes.size(); c++) {
                                TLRPC.DocumentAttribute attribute = document.attributes.get(c);
                                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                                    if (attribute.performer != null) {
                                        ok = attribute.performer.toLowerCase().contains(q);
                                    }
                                    if (!ok && attribute.title != null) {
                                        ok = attribute.title.toLowerCase().contains(q);
                                    }
                                    break;
                                }
                            }
                            if (ok) {
                                resultArray.add(messageObject);
                                break;
                            }
                        }
                    }

                    updateSearchResults(resultArray, query);
                });
            });
        }

        private void updateSearchResults(final ArrayList<MessageObject> documents, String query) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!searching) {
                    return;
                }
                searchWas = true;
                searchResult = documents;
                searchQuery = query;
                notifyDataSetChanged();
                layoutManager.scrollToPosition(0);
                emptySubtitleTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.NoAudioFoundPlayerInfo, query)));
            });
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate delegate = () -> {
            EditTextBoldCursor editText = searchItem.getSearchField();
            editText.setCursorColor(getThemedColor(Theme.key_player_actionBarTitle));

            repeatButton.setIconColor(getThemedColor((Integer) repeatButton.getTag()));
            Theme.setSelectorDrawableColor(repeatButton.getBackground(), getThemedColor(Theme.key_listSelector), true);

            optionsButton.setIconColor(getThemedColor(Theme.key_player_button));
            Theme.setSelectorDrawableColor(optionsButton.getBackground(), getThemedColor(Theme.key_listSelector), true);

            progressView.setBackgroundColor(getThemedColor(Theme.key_player_progressBackground));
            progressView.setProgressColor(getThemedColor(Theme.key_player_progress));

            updateSubMenu();
            repeatButton.redrawPopup(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));

            optionsButton.setPopupItemsColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem), false);
            optionsButton.setPopupItemsColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem), true);
            optionsButton.redrawPopup(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));
        };

//        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, delegate, Theme.key_player_actionBarTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_player_actionBarTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBTITLECOLOR, null, null, null, null, Theme.key_player_actionBarTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_player_actionBarSelector));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_player_actionBarTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_player_time));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AudioPlayerCell.class}, null, null, null, Theme.key_chat_inLoader));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AudioPlayerCell.class}, null, null, null, Theme.key_chat_outLoader));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AudioPlayerCell.class}, null, null, null, Theme.key_chat_inLoaderSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AudioPlayerCell.class}, null, null, null, Theme.key_chat_inMediaIcon));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AudioPlayerCell.class}, null, null, null, Theme.key_chat_inMediaIconSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AudioPlayerCell.class}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AudioPlayerCell.class}, null, null, null, Theme.key_chat_inAudioSelectedProgress));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AudioPlayerCell.class}, null, null, null, Theme.key_chat_inAudioProgress));

        themeDescriptions.add(new ThemeDescription(containerView, 0, null, null, new Drawable[]{shadowDrawable}, null, Theme.key_dialogBackground));

        themeDescriptions.add(new ThemeDescription(progressView, 0, null, null, null, null, Theme.key_player_progressBackground));
        themeDescriptions.add(new ThemeDescription(progressView, 0, null, null, null, null, Theme.key_player_progress));
        themeDescriptions.add(new ThemeDescription(seekBarView, 0, null, null, null, null, Theme.key_player_progressBackground));
        themeDescriptions.add(new ThemeDescription(seekBarView, 0, null, null, null, null, Theme.key_player_progressCachedBackground));
        themeDescriptions.add(new ThemeDescription(seekBarView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_player_progress));

        themeDescriptions.add(new ThemeDescription(playbackSpeedButton, ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_inappPlayerPlayPause));
        themeDescriptions.add(new ThemeDescription(playbackSpeedButton, ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_inappPlayerClose));

        themeDescriptions.add(new ThemeDescription(repeatButton, 0, null, null, null, delegate, Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(repeatButton, 0, null, null, null, delegate, Theme.key_player_buttonActive));
        themeDescriptions.add(new ThemeDescription(repeatButton, 0, null, null, null, delegate, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(repeatButton, 0, null, null, null, delegate, Theme.key_actionBarDefaultSubmenuItem));
        themeDescriptions.add(new ThemeDescription(repeatButton, 0, null, null, null, delegate, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(optionsButton, 0, null, null, null, delegate, Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(optionsButton, 0, null, null, null, delegate, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(optionsButton, 0, null, null, null, delegate, Theme.key_actionBarDefaultSubmenuItem));
        themeDescriptions.add(new ThemeDescription(optionsButton, 0, null, null, null, delegate, Theme.key_actionBarDefaultSubmenuBackground));

        themeDescriptions.add(new ThemeDescription(prevButton, 0, null, new RLottieDrawable[]{prevButton.getAnimatedDrawable()}, "Triangle 3", Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(prevButton, 0, null, new RLottieDrawable[]{prevButton.getAnimatedDrawable()}, "Triangle 4", Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(prevButton, 0, null, new RLottieDrawable[]{prevButton.getAnimatedDrawable()}, "Rectangle 4", Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(prevButton, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(playButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(playButton, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(nextButton, 0, null, new RLottieDrawable[]{nextButton.getAnimatedDrawable()}, "Triangle 3", Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(nextButton, 0, null, new RLottieDrawable[]{nextButton.getAnimatedDrawable()}, "Triangle 4", Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(nextButton, 0, null, new RLottieDrawable[]{nextButton.getAnimatedDrawable()}, "Rectangle 4", Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(nextButton, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(emptyImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_dialogEmptyImage));
        themeDescriptions.add(new ThemeDescription(emptyTitleTextView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_dialogEmptyText));
        themeDescriptions.add(new ThemeDescription(emptySubtitleTextView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_dialogEmptyText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_dialogScrollGlow));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(progressView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder));
        themeDescriptions.add(new ThemeDescription(progressView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle));

        themeDescriptions.add(new ThemeDescription(durationTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_player_time));
        themeDescriptions.add(new ThemeDescription(timeTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_player_time));
        themeDescriptions.add(new ThemeDescription(titleTextView.getTextView(), ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_player_actionBarTitle));
        themeDescriptions.add(new ThemeDescription(titleTextView.getNextTextView(), ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_player_actionBarTitle));
        themeDescriptions.add(new ThemeDescription(authorTextView.getTextView(), ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_player_time));
        themeDescriptions.add(new ThemeDescription(authorTextView.getNextTextView(), ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_player_time));

        themeDescriptions.add(new ThemeDescription(containerView, 0, null, null, null, null, Theme.key_sheet_scrollUp));

        return themeDescriptions;
    }

    private void saveToProfile(MessageObject messageObject, boolean save, Runnable done, boolean triedFileRef) {
        final TLRPC.Document document = messageObject.getDocument();
        if (document == null) {
            return;
        }
        final long documentId = document.id;
        final TLRPC.TL_account_saveMusic req = new TLRPC.TL_account_saveMusic();
        req.unsave = !save;
        req.id = new TLRPC.TL_inputDocument();
        req.id.id = documentId;
        req.id.access_hash = document.access_hash;
        req.id.file_reference = document.file_reference;
        if (req.id.file_reference == null) {
            req.id.file_reference = new byte[0];
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
            if (err != null && FileRefController.isFileRefError(err.text)) {
                if (triedFileRef || messageObject.getId() < 0) {
                    AndroidUtilities.runOnUIThread(() -> {
                        BulletinFactory.of((FrameLayout) containerView, resourcesProvider)
                            .showForError(err);
                    });
                    return;
                }
                if (messageObject.getDialogId() >= 0) {
                    final int msg_id = messageObject.getId();
                    final TLRPC.TL_messages_getMessages fileRefReq = new TLRPC.TL_messages_getMessages();
                    fileRefReq.id.add(msg_id);
                    ConnectionsManager.getInstance(currentAccount).sendRequest(fileRefReq, (res1, err1) -> {
                        if (res1 instanceof TLRPC.messages_Messages) {
                            final TLRPC.messages_Messages r = (TLRPC.messages_Messages) res1;
                            TLRPC.Message message = null;
                            for (int i = 0; i < r.messages.size(); ++i) {
                                if (r.messages.get(i).id == msg_id) {
                                    message = r.messages.get(i);
                                    break;
                                }
                            }
                            if (message != null) {
                                saveToProfile(new MessageObject(currentAccount, message, false, true), save, done, true);
                            } else {
                                AndroidUtilities.runOnUIThread(() -> {
                                    BulletinFactory.of((FrameLayout) containerView, resourcesProvider)
                                        .createErrorBulletin(LocaleController.formatString(R.string.UnknownErrorCode, "CLIENT_MESSAGE_NOT_FOUND"))
                                        .show();
                                });
                            }
                        } else if (err1 != null) {
                            AndroidUtilities.runOnUIThread(() -> {
                                BulletinFactory.of((FrameLayout) containerView, resourcesProvider)
                                    .showForError(err1);
                            });
                        }
                    });
                } else {
                    final int msg_id = messageObject.getId();
                    final TLRPC.TL_channels_getMessages fileRefReq = new TLRPC.TL_channels_getMessages();
                    fileRefReq.channel = MessagesController.getInstance(currentAccount).getInputChannel(-messageObject.getDialogId());
                    fileRefReq.id.add(msg_id);
                    ConnectionsManager.getInstance(currentAccount).sendRequest(fileRefReq, (res1, err1) -> {
                        if (res1 instanceof TLRPC.messages_Messages) {
                            final TLRPC.messages_Messages r = (TLRPC.messages_Messages) res1;
                            TLRPC.Message message = null;
                            for (int i = 0; i < r.messages.size(); ++i) {
                                if (r.messages.get(i).id == msg_id) {
                                    message = r.messages.get(i);
                                    break;
                                }
                            }
                            if (message != null) {
                                saveToProfile(new MessageObject(currentAccount, message, false, true), save, done, true);
                            } else {
                                AndroidUtilities.runOnUIThread(() -> {
                                    BulletinFactory.of((FrameLayout) containerView, resourcesProvider)
                                        .createErrorBulletin(LocaleController.formatString(R.string.UnknownErrorCode, "CLIENT_MESSAGE_NOT_FOUND"))
                                        .show();
                                });
                            }
                        } else if (err1 != null) {
                            AndroidUtilities.runOnUIThread(() -> {
                                BulletinFactory.of((FrameLayout) containerView, resourcesProvider)
                                    .showForError(err1);
                            });
                        }
                    });
                }
                return;
            } else if (err != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    BulletinFactory.of((FrameLayout) containerView, resourcesProvider)
                        .showForError(err);
                });
            }

            AndroidUtilities.runOnUIThread(() -> {
                MessagesController.getInstance(currentAccount)
                    .getSavedMusicIds()
                    .update(documentId, save);
                final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
                final TLRPC.UserFull userInfo = MessagesController.getInstance(currentAccount).getUserFull(selfId);
                if (userInfo != null) {
                    if (save) {
                        userInfo.flags2 |= TLObject.FLAG_21;
                        userInfo.saved_music = document;
                    } else if (userInfo.saved_music != null && userInfo.saved_music.id == documentId) {
                        userInfo.flags2 &=~ TLObject.FLAG_21;
                        userInfo.saved_music = null;
                    }
                    MessagesStorage.getInstance(currentAccount).updateUserInfo(userInfo, true);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.profileMusicUpdated, selfId);
                }
                if (done != null) {
                    done.run();
                }
            });
        });
    }

    private void showMenuOptions(View v) {
        final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject == null) {
            return;
        }

        final ItemOptions o = ItemOptions.makeOptions(container, resourcesProvider, v, true);
        ItemOptions o2 = buildSaveOptions(o, messageObject);
        if (!isMyList()) {
            o.addIf(!noforwards, R.drawable.msg_stories_save, getString(R.string.AudioSaveTo), () -> o.openSwipeback(o2));
            if (!noforwards && o.getLast() != null)
                o.getLast().setRightIcon(R.drawable.msg_arrowright);
            o.addGap();
        }

        o.addIf(!noforwards, R.drawable.msg_forward, getString(R.string.Forward), () -> {
            o.dismiss();
            onSubItemClick(1);
        });
        o.addIf(!noforwards, R.drawable.msg_shareout, getString(R.string.ShareFile), () -> {
            o.dismiss();
            onSubItemClick(2);
        });
        o.addIf(messageObject.getId() > 0, R.drawable.msg_message, getString(R.string.ShowInChat), () -> {
            o.dismiss();
            onSubItemClick(4);
        });
        if (castAvailable) {
            castItem = o.add();
            castItem.setTextAndIcon(getString(R.string.VideoPlayerChromecast), R.drawable.menu_video_chromecast);
            castItem.setOnClickListener(v2 -> {
                o.dismiss();
                onSubItemClick(6);
            });
            AndroidUtilities.removeFromParent(castItemButton);
            castItem.addView(castItemButton, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            updateColors();
        }
        o.addIf(isMyList(), R.drawable.msg_delete, getString(R.string.ProfilePlaylistRemoveFromProfile), true, () -> {
            o.dismiss();
            onSubItemClick(7);
        });
        if (messageObject.isVoice()) {
            o.addChecked(!MessagesController.getGlobalMainSettings().getBoolean("disableAutoplayNextVoice", false), R.drawable.ic_action_next, getString(R.string.AudioPlayerAutoplayNext), () -> {
                o.dismiss();
                onSubItemClick(9);
            });
        }
        o.setTranslationY(dp(64));
        o.show();
    }

    private ItemOptions buildSaveOptions(ItemOptions o, MessageObject messageObject) {
        final MessagesController.SavedMusicIds musicIds = MessagesController.getInstance(currentAccount).getSavedMusicIds();
        final TLRPC.Document document = messageObject.getDocument();
        final long documentId = document != null ? document.id : 0;

        final ItemOptions o2 = o.makeSwipeback();
        o2.add(R.drawable.ic_ab_back, getString(R.string.Back), o::closeSwipeback);
        o2.addGap();
        o2.addIf(!musicIds.ids.contains(documentId), R.drawable.left_status_profile, getString(R.string.AudioSaveToMyProfile), () -> {
            saveToProfile(messageObject, true, () -> {
                setVisibleInProfile(true);
                BulletinFactory.of((FrameLayout) containerView, resourcesProvider)
                        .createSimpleBulletin(R.raw.saved_messages, getString(R.string.AudioSaveToMyProfileSaved))
                        .show();
                o.dismiss();
            }, false);
        });
        o2.add(R.drawable.msg_saved, getString(R.string.AudioSaveToSavedMessages), () -> {
            forward(messageObject, UserConfig.getInstance(currentAccount).getClientUserId());
            o.dismiss();

            BulletinFactory.of((FrameLayout) containerView, resourcesProvider)
                    .createSimpleBulletin(R.raw.saved_messages, getString(R.string.AudioSaveToSavedMessagesSaved))
                    .show();
        });
        o2.add(R.drawable.menu_download_round, getString(R.string.AudioSaveToMusicFolder), () -> {
            saveToMusic(messageObject);
            o.dismiss();
        });
        o2.addGap();
        o2.addText(getString(R.string.AudioSaveToInfo), 12, dp(200));
        return o2;
    }

    private void showOptions(AudioPlayerCell cell, MessageObject messageObject) {
        final ItemOptions o = ItemOptions.makeOptions(container, resourcesProvider, cell, true);

        if (isMyList()) {
            o.addIf(!noforwards, R.drawable.msg_forward, getString(R.string.Forward), () -> {
                o.dismiss();
                forward(messageObject);
            });
            o.addIf(!noforwards, R.drawable.msg_shareout, getString(R.string.ShareFile), () -> {
                o.dismiss();
                share(messageObject);
            });
            o.add(R.drawable.msg_delete, getString(R.string.Delete), true, () -> {
                saveToProfile(messageObject, false, () -> {
                    savedMusicList.remove(messageObject);
                    playlist.remove(messageObject);
                    listAdapter.notifyDataSetChanged();
                    o.dismiss();

                    setVisibleInProfile(false);
                    BulletinFactory.of((FrameLayout) containerView, resourcesProvider)
                        .createSimpleBulletin(R.raw.ic_delete, getString(R.string.AudioSaveToMyProfileUnsaved))
                        .show();
                }, false);
            });
        } else {
            ItemOptions o2 = buildSaveOptions(o, messageObject);

            o.addIf(!noforwards, R.drawable.msg_stories_save, getString(R.string.AudioSaveTo), () -> o.openSwipeback(o2));
            if (!noforwards && o.getLast() != null)
                o.getLast().setRightIcon(R.drawable.msg_arrowright);

            o.addGap();
            o.addIf(!noforwards, R.drawable.msg_forward, getString(R.string.Forward), () -> {
                o.dismiss();
                forward(messageObject);
            });
            o.addIf(!noforwards, R.drawable.msg_share, getString(R.string.ShareFile), () -> {
                o.dismiss();
                share(messageObject);
            });
            o.addIf(messageObject.getId() > 0, R.drawable.msg_view_file, getString(R.string.ShowInChat), () -> {
                if (UserConfig.selectedAccount != currentAccount) {
                    parentActivity.switchToAccount(currentAccount, true);
                }

                Bundle args = new Bundle();
                long did = messageObject.getDialogId();
                if (DialogObject.isEncryptedDialog(did)) {
                    args.putInt("enc_id", DialogObject.getEncryptedChatId(did));
                } else if (DialogObject.isUserDialog(did)) {
                    args.putLong("user_id", did);
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                    if (chat != null && chat.migrated_to != null) {
                        args.putLong("migrated_to", did);
                        did = -chat.migrated_to.channel_id;
                    }
                    args.putLong("chat_id", -did);
                }
                args.putInt("message_id", messageObject.getId());
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
                parentActivity.presentFragment(new ChatActivity(args), false, false);
                dismiss();
            });
        }

        o.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        o.show();
    }

    private void setVisibleInProfile(boolean visible) {
        if (isMyList() || noforwards) {
            saveToProfileButton.setVisibility(View.GONE);
            unsaveFromProfileButton.setVisibility(View.GONE);
            return;
        }
        saveToProfileButton.setVisibility(View.VISIBLE);
        unsaveFromProfileButton.setVisibility(View.VISIBLE);
        saveToProfileButton.animate()
            .alpha(visible ? 0.0f : 1.0f)
            .scaleX(visible ? 0.8f : 1.0f)
            .scaleY(visible ? 0.8f : 1.0f)
            .setDuration(420)
            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
            .withEndAction(() -> {
                saveToProfileButton.setVisibility(visible ? View.GONE : View.VISIBLE);
            })
            .start();
        unsaveFromProfileButton.animate()
            .alpha(!visible ? 0.0f : 1.0f)
            .scaleX(!visible ? 0.8f : 1.0f)
            .scaleY(!visible ? 0.8f : 1.0f)
            .setDuration(420)
            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
            .withEndAction(() -> {
                unsaveFromProfileButton.setVisibility(visible ? View.VISIBLE : View.GONE);
            })
            .start();
    }

    private void saveToMusic(MessageObject messageObject) {
        if (Build.VERSION.SDK_INT >= 23 && (Build.VERSION.SDK_INT <= 28 || BuildVars.NO_SCOPED_STORAGE) && parentActivity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            parentActivity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
            return;
        }
        String fileName = FileLoader.getDocumentFileName(messageObject.getDocument());
        if (TextUtils.isEmpty(fileName)) {
            fileName = messageObject.getFileName();
        }
        String path = messageObject.messageOwner.attachPath;
        if (path != null && path.length() > 0) {
            File temp = new File(path);
            if (!temp.exists()) {
                path = null;
            }
        }
        if (path == null || path.length() == 0) {
            path = FileLoader.getInstance(currentAccount).getPathToMessage(messageObject.messageOwner).toString();
        }
        MediaController.saveFile(path, parentActivity, 3, fileName, messageObject.getDocument() != null ? messageObject.getDocument().mime_type : "", uri -> BulletinFactory.of((FrameLayout) containerView, resourcesProvider).createDownloadBulletin(BulletinFactory.FileType.AUDIO).show());
    }

    private void share(MessageObject messageObject) {
        try {
            File f = null;
            boolean isVideo = false;

            if (!TextUtils.isEmpty(messageObject.messageOwner.attachPath)) {
                f = new File(messageObject.messageOwner.attachPath);
                if (!f.exists()) {
                    f = null;
                }
            }
            if (f == null) {
                f = FileLoader.getInstance(currentAccount).getPathToMessage(messageObject.messageOwner);
            }

            if (f.exists()) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(messageObject.getMimeType());
                if (Build.VERSION.SDK_INT >= 24) {
                    try {
                        intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(ApplicationLoader.applicationContext, ApplicationLoader.getApplicationId() + ".provider", f));
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignore) {
                        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                    }
                } else {
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                }

                parentActivity.startActivityForResult(Intent.createChooser(intent, LocaleController.getString(R.string.ShareFile)), 500);
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                builder.setTitle(LocaleController.getString(R.string.AppName));
                builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                builder.setMessage(LocaleController.getString(R.string.PleaseDownload));
                builder.show();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void show() {
        super.show();
        instance = this;
    }

    private void forward(MessageObject messageObject, long dialogId) {
        if (UserConfig.selectedAccount != currentAccount) {
            parentActivity.switchToAccount(currentAccount, true);
        }
        final ArrayList<MessageObject> fmessages;
        final TLRPC.TL_document document;
        if (messageObject.getId() < 0) {
            fmessages = null;
            if (!(messageObject.getDocument() instanceof TLRPC.TL_document)) {
                return;
            }
            document = (TLRPC.TL_document) messageObject.getDocument();
        } else {
            fmessages = new ArrayList<>();
            fmessages.add(messageObject);
            document = null;
        }
        if (fmessages != null) {
            SendMessagesHelper.getInstance(currentAccount).sendMessage(fmessages, dialogId, false, false, true, 0, 0);
        } else {
            SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(document, null, messageObject.messageOwner.attachPath, dialogId, null, null, null, null, null, null, true, 0, 0, 0, savedMusicList, null, false, false));
        }
        final BaseFragment lastFragment = LaunchActivity.getLastFragment();
        if (lastFragment != null) {
            BulletinFactory.of(lastFragment)
                .createSimpleBulletin(
                    R.raw.forward,
                    dialogId == UserConfig.getInstance(currentAccount).getClientUserId() ?
                        LocaleController.getString(R.string.FwdMessageToSavedMessages) :
                    dialogId > 0 ?
                        LocaleController.formatString(R.string.FwdMessageToUser, DialogObject.getShortName(dialogId)) :
                        LocaleController.formatString(R.string.FwdMessageToGroup, DialogObject.getShortName(dialogId))
                )
                .show();
        }
    }

    private void forward(MessageObject messageObject) {
        if (UserConfig.selectedAccount != currentAccount) {
            parentActivity.switchToAccount(currentAccount, true);
        }
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        args.putBoolean("canSelectTopics", true);
        DialogsActivity fragment = new DialogsActivity(args);
        final ArrayList<MessageObject> fmessages;
        final TLRPC.TL_document document;
        if (messageObject.getId() < 0) {
            fmessages = null;
            if (!(messageObject.getDocument() instanceof TLRPC.TL_document)) {
                return;
            }
            document = (TLRPC.TL_document) messageObject.getDocument();
        } else {
            fmessages = new ArrayList<>();
            fmessages.add(messageObject);
            document = null;
        }
        fragment.setDelegate((fragment1, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids.size() > 1 || dids.get(0).dialogId == UserConfig.getInstance(currentAccount).getClientUserId() || message != null || fmessages == null) {
                for (int a = 0; a < dids.size(); a++) {
                    long did = dids.get(a).dialogId;
                    if (message != null) {
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(message.toString(), did, null, null, null, true, null, null, null, true, 0, 0, null, false));
                    }
                    if (fmessages != null) {
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(fmessages, did, false, false, true, 0, 0);
                    } else {
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(document, null, messageObject.messageOwner.attachPath, did, null, null, null, null, null, null, notify, scheduleDate, 0, 0, savedMusicList, null, false, false));
                    }
                }
                fragment1.finishFragment();
                final BaseFragment lastFragment = LaunchActivity.getLastFragment();
                if (lastFragment != null) {
                    BulletinFactory.of(lastFragment)
                        .createSimpleBulletin(
                            R.raw.forward,
                            dids.size() == 1 && dids.get(0).dialogId == UserConfig.getInstance(currentAccount).getClientUserId() ?
                                LocaleController.getString(R.string.FwdMessageToSavedMessages) :
                            dids.size() == 1 && dids.get(0).dialogId > 0 ?
                                LocaleController.formatString(R.string.FwdMessageToUser, DialogObject.getShortName(dids.get(0).dialogId)) :
                            dids.size() == 1 && dids.get(0).dialogId < 0 ?
                                LocaleController.formatString(R.string.FwdMessageToGroup, DialogObject.getShortName(dids.get(0).dialogId)) :
                            LocaleController.formatPluralStringComma("FwdMessageToManyChats", dids.size())
                        )
                        .show();
                }
            } else {
                MessagesStorage.TopicKey topicKey = dids.get(0);
                long did = topicKey.dialogId;
                Bundle args1 = new Bundle();
                args1.putBoolean("scrollToTopOnResume", true);
                if (DialogObject.isEncryptedDialog(did)) {
                    args1.putInt("enc_id", DialogObject.getEncryptedChatId(did));
                } else if (DialogObject.isUserDialog(did)) {
                    args1.putLong("user_id", did);
                } else {
                    args1.putLong("chat_id", -did);
                }
                ChatActivity chatActivity = new ChatActivity(args1);
                if (topicKey.topicId != 0) {
                    ForumUtilities.applyTopic(chatActivity, topicKey);
                }
                if (parentActivity.presentFragment(chatActivity, true, false)) {
                    chatActivity.showFieldPanelForForward(true, fmessages);
                    if (topicKey.topicId != 0) {
                        fragment1.removeSelfFromStack();
                    }
                } else {
                    fragment1.finishFragment();
                }
            }
            return true;
        });
        parentActivity.presentFragment(fragment);
        dismiss();
    }

    private static abstract class CoverContainer extends FrameLayout {

        private final BackupImageView[] imageViews = new BackupImageView[2];

        private int activeIndex;
        private AnimatorSet animatorSet;

        public CoverContainer(@NonNull Context context) {
            super(context);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), dp(14));
                }
            });
            setClipToOutline(true);
            setElevation(dp(12));
            for (int i = 0; i < 2; i++) {
                imageViews[i] = new BackupImageView(context);
                final int index = i;
                imageViews[i].getImageReceiver().setDelegate((imageReceiver, set, thumb, memCache) -> {
                    if (index == activeIndex) {
                        onImageUpdated(imageReceiver);
                    }
                });
                imageViews[i].setRoundRadius(dp(14));
                if (i == 1) {
                    imageViews[i].setVisibility(GONE);
                }
                addView(imageViews[i], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            }
        }

        public final void switchImageViews() {
            if (animatorSet != null) {
                animatorSet.cancel();
            }
            animatorSet = new AnimatorSet();
            activeIndex = activeIndex == 0 ? 1 : 0;

            final BackupImageView prevImageView = imageViews[activeIndex == 0 ? 1 : 0];
            final BackupImageView currImageView = imageViews[activeIndex];

            final boolean hasBitmapImage = prevImageView.getImageReceiver().hasBitmapImage();

            currImageView.setAlpha(hasBitmapImage ? 1f : 0f);
            currImageView.setScaleX(0.8f);
            currImageView.setScaleY(0.8f);
            currImageView.setVisibility(VISIBLE);

            if (hasBitmapImage) {
                prevImageView.bringToFront();
            } else {
                prevImageView.setVisibility(GONE);
                prevImageView.setImageDrawable(null);
            }

            final ValueAnimator expandAnimator = ValueAnimator.ofFloat(0.8f, 1f);
            expandAnimator.setDuration(125);
            expandAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            expandAnimator.addUpdateListener(a -> {
                float animatedValue = (float) a.getAnimatedValue();
                currImageView.setScaleX(animatedValue);
                currImageView.setScaleY(animatedValue);
                if (!hasBitmapImage) {
                    currImageView.setAlpha(a.getAnimatedFraction());
                }
            });

            if (hasBitmapImage) {
                final ValueAnimator collapseAnimator = ValueAnimator.ofFloat(prevImageView.getScaleX(), 0.8f);
                collapseAnimator.setDuration(125);
                collapseAnimator.setInterpolator(CubicBezierInterpolator.EASE_IN);
                collapseAnimator.addUpdateListener(a -> {
                    float animatedValue = (float) a.getAnimatedValue();
                    prevImageView.setScaleX(animatedValue);
                    prevImageView.setScaleY(animatedValue);
                    final float fraction = a.getAnimatedFraction();
                    if (fraction > 0.25f && !currImageView.getImageReceiver().hasBitmapImage()) {
                        prevImageView.setAlpha(1f - (fraction - 0.25f) * (1f / 0.75f));
                    }
                });
                collapseAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        prevImageView.setVisibility(GONE);
                        prevImageView.setImageDrawable(null);
                        prevImageView.setAlpha(1f);
                    }
                });

                animatorSet.playSequentially(collapseAnimator, expandAnimator);
            } else {
                animatorSet.play(expandAnimator);
            }

            animatorSet.start();
        }

        public final BackupImageView getImageView() {
            return imageViews[activeIndex];
        }

        public final BackupImageView getNextImageView() {
            return imageViews[activeIndex == 0 ? 1 : 0];
        }

        public final ImageReceiver getImageReceiver() {
            return getImageView().getImageReceiver();
        }

        protected abstract void onImageUpdated(ImageReceiver imageReceiver);
    }

    public abstract static class ClippingTextViewSwitcher extends FrameLayout {

        private final TextView[] textViews = new TextView[2];
        private final float[] clipProgress = new float[]{0f, 0.75f};
        private final int gradientSize = dp(24);

        private final Matrix gradientMatrix;
        private final Paint gradientPaint;
        private final Paint erasePaint;

        private int activeIndex;
        private AnimatorSet animatorSet;
        private LinearGradient gradientShader;
        private int stableOffest = -1;
        private final RectF rectF = new RectF();
        private int rightPadding;

        public ClippingTextViewSwitcher(@NonNull Context context) {
            super(context);
            for (int i = 0; i < 2; i++) {
                textViews[i] = createTextView();
                if (i == 1) {
                    textViews[i].setAlpha(0f);
                    textViews[i].setVisibility(GONE);
                }
                addView(textViews[i], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
            }
            gradientMatrix = new Matrix();
            gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            gradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            erasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            erasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            gradientShader = new LinearGradient(gradientSize, 0, 0, 0, 0, 0xFF000000, Shader.TileMode.CLAMP);
            gradientPaint.setShader(gradientShader);
        }

        private boolean isCenter;

        public void setIsCenter() {
            isCenter = true;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            if (isCenter) {
                for (int a = 0; a < textViews.length; a++) {
                    View v = textViews[a];
                    if (v == null) continue;
                    if (v.getMeasuredWidth() < getMeasuredWidth()) {
                        int l = (getMeasuredWidth() - v.getMeasuredWidth()) / 2;
                        v.layout(l, 0, l + v.getMeasuredWidth(), v.getMeasuredHeight());
                    }
                }
            }
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            final int index = child == textViews[0] ? 0 : 1;
            final boolean result;
            boolean hasStableRect = false;

            if (isCenter) {
                stableOffest = -1;
            }

            if (stableOffest > 0) {
                for (TextView tv : textViews) {
                    if (tv instanceof MarqueeTextView && ((MarqueeTextView) tv).isNeedMarquee()) {
                        stableOffest = -1;
                        break;
                    }
                }
            }

            if (stableOffest > 0 && textViews[activeIndex].getAlpha() != 1f && textViews[activeIndex].getLayout() != null) {
                float x1 = textViews[activeIndex].getLayout().getPrimaryHorizontal(0);
                float x2 = textViews[activeIndex].getLayout().getPrimaryHorizontal(stableOffest);
                hasStableRect = true;
                if (x1 == x2) {
                    hasStableRect = false;
                } else if (x2 > x1) {
                    rectF.set(x1, 0, x2, getMeasuredHeight());
                } else {
                    rectF.set(x2, 0, x1, getMeasuredHeight());
                }

                if (hasStableRect && index == activeIndex) {
                    canvas.save();
                    canvas.clipRect(rectF);
                    textViews[0].draw(canvas);
                    canvas.restore();
                }
            }
            if (clipProgress[index] > 0f || hasStableRect) {
                final int width = Math.min(child.getWidth(), getWidth()); // - rightPadding;
                final int height = Math.min(child.getHeight(), getHeight());
                final int saveCount = canvas.saveLayer(0, 0, width, height, null, Canvas.ALL_SAVE_FLAG);
                result = super.drawChild(canvas, child, drawingTime);
                final float gradientStart = width * (1f - clipProgress[index]);
                final float gradientEnd = gradientStart + gradientSize;
                gradientMatrix.setTranslate(gradientStart, 0);
                gradientShader.setLocalMatrix(gradientMatrix);
                canvas.drawRect(gradientStart, 0, gradientEnd, height, gradientPaint);
                if (width > gradientEnd) {
                    canvas.drawRect(gradientEnd, 0, width, height, erasePaint);
                }
                if (hasStableRect) {
                    canvas.drawRect(rectF, erasePaint);
                }
                canvas.restoreToCount(saveCount);
            } else {
                result = super.drawChild(canvas, child, drawingTime);
            }
            return result;
        }

        public void setText(CharSequence text) {
            setText(text, true);
        }

        public void setText(CharSequence text, boolean animated) {
            final CharSequence currentText = textViews[activeIndex].getText();

            if (TextUtils.isEmpty(currentText) || !animated) {
                textViews[activeIndex].setText(text);
                return;
            } else if (TextUtils.equals(text, currentText)) {
                return;
            }

            stableOffest = 0;
            int n = Math.min(text.length(), currentText.length());
            for (int i = 0; i < n; i++) {
                if (text.charAt(i) != currentText.charAt(i)) {
                    break;
                }
                stableOffest++;
            }
            if (stableOffest <= 3) {
                stableOffest = -1;
            }

            final int index = activeIndex == 0 ? 1 : 0;
            final int prevIndex = activeIndex;
            activeIndex = index;

            if (animatorSet != null) {
                animatorSet.cancel();
            }
            animatorSet = new AnimatorSet();
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    textViews[prevIndex].setVisibility(GONE);
                }
            });

            textViews[index].setText(text);
            textViews[index].bringToFront();
            textViews[index].setVisibility(VISIBLE);

            final int duration = 300;

            final ValueAnimator collapseAnimator = ValueAnimator.ofFloat(clipProgress[prevIndex], 0.75f);
            collapseAnimator.setDuration(duration / 3 * 2); // 0.66
            collapseAnimator.addUpdateListener(a -> {
                clipProgress[prevIndex] = (float) a.getAnimatedValue();
                invalidate();
            });

            final ValueAnimator expandAnimator = ValueAnimator.ofFloat(clipProgress[index], 0f);
            expandAnimator.setStartDelay(duration / 3); // 0.33
            expandAnimator.setDuration(duration / 3 * 2); // 0.66
            expandAnimator.addUpdateListener(a -> {
                clipProgress[index] = (float) a.getAnimatedValue();
                invalidate();
            });

            final ObjectAnimator fadeOutAnimator = ObjectAnimator.ofFloat(textViews[prevIndex], View.ALPHA, 0f);
            fadeOutAnimator.setStartDelay(duration / 4); // 0.25
            fadeOutAnimator.setDuration(duration / 2); // 0.5

            final ObjectAnimator fadeInAnimator = ObjectAnimator.ofFloat(textViews[index], View.ALPHA, 1f);
            fadeInAnimator.setStartDelay(duration / 4); // 0.25
            fadeInAnimator.setDuration(duration / 2); // 0.5

            animatorSet.playTogether(collapseAnimator, expandAnimator, fadeOutAnimator, fadeInAnimator);
            animatorSet.start();
        }

        public TextView getTextView() {
            return textViews[activeIndex];
        }

        public TextView getNextTextView() {
            return textViews[activeIndex == 0 ? 1 : 0];
        }

        public int getCustomPaddingRight() {
            return rightPadding;
        }

        public void setCustomPaddingRight(int padding) {
            rightPadding = padding;
            for (TextView tv : textViews) {
                if (tv instanceof MarqueeTextView) {
                    ((MarqueeTextView) tv).setCustomPaddingRight(padding);
                }
            }
            invalidate();
        }

        protected abstract TextView createTextView();
    }

    @Override
    protected boolean isTouchOutside(float x, float y) {
        if (topBulletinContainer != null && topBulletinContainer.getChildCount() > 0) {
            View bulletinLayout = topBulletinContainer.getChildAt(0);
            if (
                    y >= topBulletinContainer.getY() + bulletinLayout.getY() &&
                            y <= topBulletinContainer.getY() + bulletinLayout.getY() + bulletinLayout.getHeight() &&
                            x >= topBulletinContainer.getX() + bulletinLayout.getX() &&
                            x <= topBulletinContainer.getX() + bulletinLayout.getX() + bulletinLayout.getWidth()
            )
                return false;
        }
        return y < containerView.getTop() + (shadowDrawable != null ? shadowDrawable.getBounds().top : 0) || x < containerView.getLeft() || x > containerView.getRight();
    }

    private ValueAnimator rightPaddingAnimator;

    private void setCustomPaddingRight(int padding, boolean animated) {
        if (rightPaddingAnimator != null) {
            rightPaddingAnimator.cancel();
            rightPaddingAnimator = null;
        }
        if (titleTextView.getCustomPaddingRight() == padding) {
            return;
        }

        if (!animated) {
            titleTextView.setCustomPaddingRight(padding);
            authorTextView.setCustomPaddingRight(padding);
            return;
        }

        rightPaddingAnimator = ValueAnimator.ofInt(titleTextView.getCustomPaddingRight(), padding);
        if (padding == 0) {
            rightPaddingAnimator.setStartDelay(200L);
            rightPaddingAnimator.setDuration(100L);
        } else {
            rightPaddingAnimator.setDuration(200L);
        }
        rightPaddingAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        rightPaddingAnimator.addUpdateListener(animation -> {
            titleTextView.setCustomPaddingRight((int) animation.getAnimatedValue());
            authorTextView.setCustomPaddingRight((int) animation.getAnimatedValue());
        });
        rightPaddingAnimator.start();
    }

    // ==================== Tele iOS – Apple Music style player ====================

    private void attachBounce(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90)
                        .setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(new OvershootInterpolator(2.2f)).start();
                    break;
            }
            return false;
        });
    }

    private void toggleQueue() {
        if (queueOpen) {
            closeQueue();
        } else {
            openQueue();
        }
    }

    private void openQueue() {
        if (queueOpen || playlist.size() <= 1 || listView.getVisibility() == View.VISIBLE) {
            return;
        }
        queueOpen = true;
        containerView.requestLayout();
        listView.setPadding(0, listView.getPaddingTop(), 0, (!isMyList() && !noforwards) ? dp(72) : 0);
        listView.setVisibility(View.VISIBLE);
        listView.setTranslationY(dp(56));
        listView.animate()
            .translationY(0f)
            .setDuration(320)
            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
            .setUpdateListener(a -> containerView.invalidate())
            .start();
        nowPlayingLayout.animate().cancel();
        nowPlayingLayout.animate()
            .alpha(0f)
            .translationY(dp(30))
            .setDuration(240)
            .setInterpolator(CubicBezierInterpolator.DEFAULT)
            .withEndAction(() -> {
                if (queueOpen) {
                    nowPlayingLayout.setVisibility(View.INVISIBLE);
                }
            })
            .start();
        actionBar.setVisibility(View.VISIBLE);
        actionBar.animate().alpha(1f).setDuration(240).start();
        actionBarBackground.animate().alpha(1f).setDuration(240).start();
        if (saveToProfileButton.getVisibility() == View.VISIBLE) {
            saveToProfileButton.animate().translationY(0).setDuration(300)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        }
        if (unsaveFromProfileButton.getVisibility() == View.VISIBLE) {
            unsaveFromProfileButton.animate().translationY(0).setDuration(300)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        }
        scrollToCurrentSong(false);
        boolean light = ColorUtils.calculateLuminance(getThemedColor(Theme.key_dialogBackground)) > 0.7f;
        if (light != wasLight) {
            AndroidUtilities.setLightStatusBar(this, wasLight = light);
        }
        updateLayout();
        containerView.invalidate();
    }

    private void closeQueue() {
        if (!queueOpen) {
            return;
        }
        queueOpen = false;
        if (actionBar.isSearchFieldVisible()) {
            actionBar.closeSearchField();
        }
        containerView.requestLayout();
        listView.animate()
            .translationY(dp(56))
            .setDuration(240)
            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
            .setUpdateListener(a -> containerView.invalidate())
            .withEndAction(() -> {
                if (!queueOpen) {
                    listView.setVisibility(View.GONE);
                }
            })
            .start();
        nowPlayingLayout.setVisibility(View.VISIBLE);
        nowPlayingLayout.animate().cancel();
        nowPlayingLayout.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(280)
            .setInterpolator(CubicBezierInterpolator.DEFAULT)
            .start();
        actionBar.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            if (!queueOpen) {
                actionBar.setVisibility(View.INVISIBLE);
            }
        }).start();
        actionBarBackground.animate().alpha(0f).setDuration(200).start();
        if (saveToProfileButton.getVisibility() == View.VISIBLE) {
            saveToProfileButton.animate().translationY(dp(64)).setDuration(240).start();
        }
        if (unsaveFromProfileButton.getVisibility() == View.VISIBLE) {
            unsaveFromProfileButton.animate().translationY(dp(64)).setDuration(240).start();
        }
        if (wasLight) {
            AndroidUtilities.setLightStatusBar(this, wasLight = false);
        }
        containerView.invalidate();
    }

    private void applyUiColors() {
        if (uiDarkText) {
            uiPrimary = 0xFF202024;
            uiSecondary = 0x99202024;
        } else {
            uiPrimary = 0xFFFFFFFF;
            uiSecondary = 0x99FFFFFF;
        }
        if (topLabelTextView != null) {
            topLabelTextView.setTextColor(uiPrimary);
        }
        if (timeTextView != null) {
            timeTextView.setTextColor(uiSecondary);
        }
        if (durationTextView != null) {
            durationTextView.setTextColor(uiSecondary);
        }
        if (playButton != null) {
            playButton.setColorFilter(new PorterDuffColorFilter(uiPrimary, PorterDuff.Mode.MULTIPLY));
        }
        if (playPauseDrawable != null) {
            playPauseDrawable.setColor(uiPrimary);
        }
        if (prevButton != null) {
            prevButton.setLayerColor("Triangle 3", uiPrimary);
            prevButton.setLayerColor("Triangle 4", uiPrimary);
            prevButton.setLayerColor("Rectangle 4", uiPrimary);
        }
        if (nextButton != null) {
            nextButton.setLayerColor("Triangle 3", uiPrimary);
            nextButton.setLayerColor("Triangle 4", uiPrimary);
            nextButton.setLayerColor("Rectangle 4", uiPrimary);
        }
        if (shuffleButton != null) {
            shuffleButton.setColorFilter(new PorterDuffColorFilter(SharedConfig.shuffleMusic ? uiPrimary : uiSecondary, PorterDuff.Mode.MULTIPLY));
        }
        if (repeatButton != null) {
            boolean active = SharedConfig.repeatMode != 0 || SharedConfig.shuffleMusic || SharedConfig.playOrderReversed;
            repeatButton.setIconColor(active ? uiPrimary : uiSecondary);
        }
        if (queueButton != null) {
            queueButton.setColorFilter(new PorterDuffColorFilter(uiSecondary, PorterDuff.Mode.MULTIPLY));
        }
        if (chevronButton != null) {
            chevronButton.setColorFilter(new PorterDuffColorFilter(uiPrimary, PorterDuff.Mode.MULTIPLY));
        }
        if (optionsButton != null) {
            optionsButton.setIconColor(uiPrimary);
        }
        if (playbackSpeedButton != null && speedIcon != null) {
            boolean fast = !equals(MediaController.getInstance().getPlaybackSpeed(true), 1.0f);
            speedIcon.setColor(fast ? uiPrimary : uiSecondary);
        }
        if (forwardButton != null) {
            forwardButton.setTextColor(uiSecondary);
        }
        if (backwardButton != null) {
            backwardButton.setTextColor(uiSecondary);
        }
        if (seekBarView != null) {
            seekBarView.setColors(uiPrimary, uiSecondary);
        }
        if (progressView != null) {
            progressView.setBackgroundColor(uiDarkText ? 0x33202024 : 0x33FFFFFF);
            progressView.setProgressColor(uiDarkText ? 0xCC202024 : 0xFFFFFFFF);
        }
        if (actionBar != null) {
            actionBar.setItemsColor(uiPrimary, false);
            actionBar.setTitleColor(uiPrimary);
            actionBar.setSubtitleColor(uiSecondary);
        }
        if (searchItem != null) {
            EditTextBoldCursor editText = searchItem.getSearchField();
            if (editText != null) {
                editText.setTextColor(uiPrimary);
                editText.setHintTextColor(uiSecondary);
                editText.setCursorColor(uiPrimary);
            }
        }
        if (topLabelTextView != null) {
            topLabelTextView.invalidate();
        }
    }

    private class NowPlayingLayout extends FrameLayout {

        private float downX;
        private float downY;
        private float lastY;
        private boolean draggingToDismiss;
        private VelocityTracker velocityTracker;

        NowPlayingLayout(Context context) {
            super(context);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            final int w = r - l;
            final int h = b - t;
            final int sb = AndroidUtilities.statusBarHeight;
            final int topH = dp(48);

            if (topBarLayout != null) {
                topBarLayout.layout(0, sb, w, sb + topH);
            }

            int belowCover = dp(26 + 30 + 24 + 10 + 30 + 2 + 20 + 16) + dp(84) + dp(52);
            int availableH = h - sb - dp(58);
            int coverSize = Math.min(w - dp(64), availableH - topH - belowCover);
            coverSize = (int) Utilities.clamp(coverSize, dp(350), dp(120));
            final int coverTop = sb + topH + (int) ((h - sb - topH) * 0.03f);
            if (coverContainer != null) {
                coverContainer.layout((w - coverSize) / 2, coverTop, (w + coverSize) / 2, coverTop + coverSize);
            }

            int y = coverTop + coverSize + dp(26);
            if (titleTextView != null) {
                titleTextView.layout(dp(24), y, w - dp(24), y + dp(30));
            }
            y += dp(30);
            if (authorTextView != null) {
                authorTextView.layout(dp(24), y, w - dp(24), y + dp(24));
            }
            y += dp(24) + dp(10);
            if (seekBarView != null) {
                seekBarView.layout(dp(22), y, w - dp(22), y + dp(30));
            }
            if (progressView != null) {
                progressView.layout(dp(22), y + dp(14), w - dp(22), y + dp(16));
            }
            y += dp(30) + dp(2);
            if (timeTextView != null) {
                timeTextView.layout(dp(26), y, dp(26) + dp(140), y + dp(20));
            }
            if (durationTextView != null) {
                durationTextView.layout(w - dp(26) - dp(140), y, w - dp(26), y + dp(20));
            }
            y += dp(20) + dp(16);
            if (transportRow != null) {
                transportRow.layout(dp(24), y, w - dp(24), y + dp(84));
            }
            y += dp(84) + dp(8);
            if (secondaryRow != null) {
                secondaryRow.layout(dp(24), y, w - dp(24), y + dp(52));
            }
            if (bottomRow != null) {
                bottomRow.layout(dp(8), h - dp(64), w - dp(8), h - dp(16));
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            // needed when a child (e.g. the cover) claimed the gesture first
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = ev.getX();
                downY = ev.getY();
                lastY = downY;
                draggingToDismiss = false;
            } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE && !draggingToDismiss) {
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                if (dy > 0 && dy > Math.abs(dx) * 1.2f && dy >= AndroidUtilities.touchSlop * 2) {
                    draggingToDismiss = true;
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    }
                    velocityTracker.clear();
                    velocityTracker.addMovement(ev);
                    return true;
                }
            }
            return draggingToDismiss;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    downX = event.getX();
                    downY = event.getY();
                    lastY = downY;
                    draggingToDismiss = false;
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (!draggingToDismiss) {
                        float dx = event.getX() - downX;
                        float dy = event.getY() - downY;
                        if (dy > 0 && dy > Math.abs(dx) * 1.2f && dy >= AndroidUtilities.touchSlop * 2) {
                            draggingToDismiss = true;
                            if (velocityTracker == null) {
                                velocityTracker = VelocityTracker.obtain();
                            }
                            velocityTracker.clear();
                        }
                    }
                    if (!draggingToDismiss) {
                        return true;
                    }
                    velocityTracker.addMovement(event);
                    float dy = event.getY() - downY;
                    float ty = Math.max(0, dy * 0.9f);
                    containerView.setTranslationY(ty);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    boolean wasDragging = draggingToDismiss;
                    draggingToDismiss = false;
                    if (!wasDragging || velocityTracker == null) {
                        if (velocityTracker != null) {
                            velocityTracker.recycle();
                            velocityTracker = null;
                        }
                        return true;
                    }
                    velocityTracker.addMovement(event);
                    velocityTracker.computeCurrentVelocity(1000);
                    float vy = velocityTracker.getYVelocity();
                    float ty = containerView.getTranslationY();
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                    if (ty > getMeasuredHeight() * 0.22f || vy > 2400) {
                        dismiss();
                    } else {
                        containerView.animate().translationY(0f).setDuration(260)
                            .setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                    }
                    return true;
                }
            }
            return true;
        }
    }

    private class TransportRow extends FrameLayout {

        TransportRow(Context context) {
            super(context);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            final int w = r - l;
            final int h = b - t;
            if (playButton != null) {
                final int pw = playButton.getMeasuredWidth();
                final int ph = playButton.getMeasuredHeight();
                playButton.layout((w - pw) / 2, (h - ph) / 2, (w + pw) / 2, (h + ph) / 2);
            }
            if (prevButton != null) {
                final int pw = prevButton.getMeasuredWidth();
                final int ph = prevButton.getMeasuredHeight();
                final int cx = w / 2 - dp(102);
                prevButton.layout(cx - pw / 2, (h - ph) / 2, cx + pw / 2, (h + ph) / 2);
            }
            if (nextButton != null) {
                final int pw = nextButton.getMeasuredWidth();
                final int ph = nextButton.getMeasuredHeight();
                final int cx = w / 2 + dp(102);
                nextButton.layout(cx - pw / 2, (h - ph) / 2, cx + pw / 2, (h + ph) / 2);
            }
        }
    }

    private class SecondaryRow extends FrameLayout {

        SecondaryRow(Context context) {
            super(context);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            final int w = r - l;
            final int h = b - t;
            if (shuffleButton != null && shuffleButton.getVisibility() != GONE) {
                final int pw = shuffleButton.getMeasuredWidth();
                final int ph = shuffleButton.getMeasuredHeight();
                shuffleButton.layout(dp(2), (h - ph) / 2, dp(2) + pw, (h + ph) / 2);
            }
            if (repeatButton != null && repeatButton.getVisibility() != GONE) {
                final int pw = repeatButton.getMeasuredWidth();
                final int ph = repeatButton.getMeasuredHeight();
                repeatButton.layout(w - dp(2) - pw, (h - ph) / 2, w - dp(2), (h + ph) / 2);
            }
            if (backwardButton != null && backwardButton.getVisibility() != GONE) {
                final int pw = backwardButton.getMeasuredWidth();
                final int ph = backwardButton.getMeasuredHeight();
                final int cx = w / 2 - dp(58);
                backwardButton.layout(cx - pw / 2, (h - ph) / 2, cx + pw / 2, (h + ph) / 2);
            }
            if (forwardButton != null && forwardButton.getVisibility() != GONE) {
                final int pw = forwardButton.getMeasuredWidth();
                final int ph = forwardButton.getMeasuredHeight();
                final int cx = w / 2 + dp(58);
                forwardButton.layout(cx - pw / 2, (h - ph) / 2, cx + pw / 2, (h + ph) / 2);
            }
        }
    }

    public static class AppleMusicSeekBar extends View {

        public abstract static class AppleMusicSeekBarDelegate {
            public abstract void onSeekBarDrag(boolean stop, float progress);
            public abstract void onSeekBarPressed(boolean pressed);
            public CharSequence getContentDescription() {
                return null;
            }
        }

        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bufferedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        private AppleMusicSeekBarDelegate delegate;
        private float progress;
        private float bufferedProgress = -1;
        private boolean pressed;
        private ValueAnimator progressAnimator;
        private ValueAnimator thumbAnimator;
        private float thumbAlpha;

        public AppleMusicSeekBar(Context context) {
            super(context);
            setColors(0xFFFFFFFF, 0x55FFFFFF);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        public void setDelegate(AppleMusicSeekBarDelegate delegate) {
            this.delegate = delegate;
        }

        public void setColors(int progressColor, int trackColor) {
            progressPaint.setColor(progressColor);
            trackPaint.setColor(trackColor);
            bufferedPaint.setColor(ColorUtils.setAlphaComponent(progressColor, 60));
            thumbPaint.setColor(progressColor);
            invalidate();
        }

        public void setProgress(float value, boolean animated) {
            value = Utilities.clamp(value, 1f, 0f);
            if (progressAnimator != null) {
                progressAnimator.cancel();
                progressAnimator = null;
            }
            if (animated) {
                progressAnimator = ValueAnimator.ofFloat(progress, value);
                progressAnimator.addUpdateListener(a -> {
                    progress = (float) a.getAnimatedValue();
                    invalidate();
                });
                progressAnimator.setDuration(280);
                progressAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                progressAnimator.start();
            } else {
                progress = value;
                invalidate();
            }
        }

        public float getProgress() {
            return progress;
        }

        public boolean isDragging() {
            return pressed;
        }

        public void setBufferedProgress(float value) {
            bufferedProgress = value;
            invalidate();
        }

        private void animateThumb(float target) {
            if (thumbAnimator != null) {
                thumbAnimator.cancel();
            }
            thumbAnimator = ValueAnimator.ofFloat(thumbAlpha, target);
            thumbAnimator.addUpdateListener(a -> {
                thumbAlpha = (float) a.getAnimatedValue();
                invalidate();
            });
            thumbAnimator.setDuration(180);
            thumbAnimator.start();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (delegate == null) {
                return super.onTouchEvent(event);
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    pressed = true;
                    animateThumb(1f);
                    updateFromTouch(event.getX(), false);
                    delegate.onSeekBarPressed(true);
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    updateFromTouch(event.getX(), false);
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    pressed = false;
                    animateThumb(0f);
                    updateFromTouch(event.getX(), event.getActionMasked() == MotionEvent.ACTION_UP);
                    delegate.onSeekBarPressed(false);
                    break;
                }
            }
            return true;
        }

        private void updateFromTouch(float x, boolean stop) {
            float p = Utilities.clamp(x / Math.max(1, getMeasuredWidth()), 1f, 0f);
            if (progressAnimator != null) {
                progressAnimator.cancel();
                progressAnimator = null;
            }
            progress = p;
            invalidate();
            delegate.onSeekBarDrag(stop, p);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int w = getMeasuredWidth();
            final float cy = getMeasuredHeight() / 2f;
            final float lineH = dp(3);
            final float rad = lineH / 2f;

            rect.set(0, cy - rad, w, cy + rad);
            canvas.drawRoundRect(rect, rad, rad, trackPaint);
            if (bufferedProgress >= 0) {
                rect.set(0, cy - rad, bufferedProgress * w, cy + rad);
                canvas.drawRoundRect(rect, rad, rad, bufferedPaint);
            }
            rect.set(0, cy - rad, Math.max(rad * 2, progress * w), cy + rad);
            canvas.drawRoundRect(rect, rad, rad, progressPaint);

            if (thumbAlpha > 0.01f) {
                thumbPaint.setAlpha((int) (255 * thumbAlpha));
                final float tr = dp(5.5f) * (0.6f + 0.4f * thumbAlpha);
                canvas.drawCircle(progress * w, cy, tr, thumbPaint);
            }
        }
    }

    private class DynamicBackgroundView extends View {

        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap currentBitmap;
        private Bitmap previousBitmap;
        private int currentTop = 0xFF2A2A2E;
        private int currentBottom = 0xFF101013;
        private int previousTop;
        private int previousBottom;
        private float crossfade = 1f;
        private ValueAnimator fadeAnimator;

        DynamicBackgroundView(Context context) {
            super(context);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, getMeasuredWidth(), getMeasuredHeight() + dp(24), dp(16));
                }
            });
            setClipToOutline(true);
        }

        void startCrossfade(Bitmap from, Bitmap to, int fromTop, int fromBottom, int toTop, int toBottom, boolean darkUi) {
            previousBitmap = from;
            previousTop = fromTop;
            previousBottom = fromBottom;
            currentBitmap = to;
            currentTop = toTop;
            currentBottom = toBottom;
            if (fadeAnimator != null) {
                fadeAnimator.cancel();
            }
            crossfade = previousBitmap == null ? 1f : 0f;
            uiDarkText = darkUi;
            applyUiColors();
            if (crossfade >= 1f) {
                previousBitmap = null;
                invalidate();
                return;
            }
            fadeAnimator = ValueAnimator.ofFloat(0f, 1f);
            fadeAnimator.addUpdateListener(a -> {
                crossfade = (float) a.getAnimatedValue();
                invalidate();
            });
            fadeAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    previousBitmap = null;
                    crossfade = 1f;
                    invalidate();
                }
            });
            fadeAnimator.setDuration(480);
            fadeAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            fadeAnimator.start();
        }

        void cancel() {
            if (fadeAnimator != null) {
                fadeAnimator.cancel();
                fadeAnimator = null;
            }
        }

        private void drawCover(Canvas canvas, Bitmap bitmap, float alpha) {
            if (bitmap == null || bitmap.isRecycled()) {
                return;
            }
            final int vw = getMeasuredWidth();
            final int vh = getMeasuredHeight();
            final float scale = Math.max(vw / (float) bitmap.getWidth(), vh / (float) bitmap.getHeight());
            final int bw = (int) (bitmap.getWidth() * scale);
            final int bh = (int) (bitmap.getHeight() * scale);
            final int left = (vw - bw) / 2;
            final int top = (vh - bh) / 2;
            bitmapPaint.setAlpha((int) (255 * 0.62f * alpha));
            canvas.drawBitmap(bitmap, null, new RectF(left, top, left + bw, top + bh), bitmapPaint);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // base color wash
            scrimPaint.setShader(new LinearGradient(0, 0, 0, getMeasuredHeight(), new int[] {currentTop, currentBottom}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), scrimPaint);
            if (previousBitmap != null && crossfade < 1f) {
                drawCover(canvas, previousBitmap, 1f - crossfade);
                scrimPaint.setShader(new LinearGradient(0, 0, 0, getMeasuredHeight(), new int[] {previousTop, previousBottom}, null, Shader.TileMode.CLAMP));
                scrimPaint.setAlpha((int) (255 * (1f - crossfade)));
                canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), scrimPaint);
                scrimPaint.setAlpha(255);
            }
            if (currentBitmap != null) {
                drawCover(canvas, currentBitmap, crossfade);
            }
            // subtle dark scrim at the bottom so the controls stay readable
            scrimPaint.setShader(new LinearGradient(0, getMeasuredHeight() * 0.5f, 0, getMeasuredHeight(), new int[] {0x00000000, 0x59000000}, null, Shader.TileMode.CLAMP));
            scrimPaint.setAlpha(255);
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), scrimPaint);
            scrimPaint.setShader(null);
        }
    }

    private class DynamicBackground {

        private String lastKey;
        private boolean computing;
        private Runnable pendingRunnable;

        void apply(MessageObject messageObject, Bitmap cover) {
            final String key;
            if (cover == null) {
                key = "none";
            } else {
                key = (messageObject != null ? messageObject.getId() + "_" : "") + cover.getGenerationId();
            }
            if (key.equals(lastKey) && !computing) {
                return;
            }
            lastKey = key;
            final Bitmap fromBitmap = backgroundView != null ? backgroundView.currentBitmap : null;
            final int fromTop = backgroundView != null ? backgroundView.currentTop : 0xFF2A2A2E;
            final int fromBottom = backgroundView != null ? backgroundView.currentBottom : 0xFF101013;
            final boolean fromDark = uiDarkText;
            computing = true;
            if (pendingRunnable != null) {
                Utilities.globalQueue.cancelRunnable(pendingRunnable);
            }
            pendingRunnable = () -> {
                final Bitmap resultBitmap;
                final int resultTop;
                final int resultBottom;
                final boolean resultDark;
                if (cover == null) {
                    resultBitmap = null;
                    if (Theme.isCurrentThemeDark()) {
                        resultTop = 0xFF2A2A2E;
                        resultBottom = 0xFF101013;
                        resultDark = false;
                    } else {
                        resultTop = 0xFFE9E9EE;
                        resultBottom = 0xFFC6C6CF;
                        resultDark = true;
                    }
                } else {
                    Palette palette = new Palette.Builder(cover).maximumColorCount(24).resizeBitmapArea(256 * 256).generate();
                    Palette.Swatch swatch = pickSwatch(palette);
                    int base = swatch != null ? swatch.getRgb() : 0xFF48484A;
                    resultBitmap = makeBlurred(cover);
                    resultTop = ColorUtils.blendARGB(base, Color.WHITE, 0.10f);
                    resultBottom = ColorUtils.blendARGB(base, Color.BLACK, 0.62f);
                    resultDark = ColorUtils.calculateLuminance(resultTop) < 0.7f;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    computing = false;
                    if (backgroundView != null) {
                        backgroundView.startCrossfade(fromBitmap, resultBitmap, fromTop, fromBottom, resultTop, resultBottom, resultDark);
                    }
                });
            };
            Utilities.globalQueue.postRunnable(pendingRunnable);
        }

        void cancel() {
            if (pendingRunnable != null) {
                Utilities.globalQueue.cancelRunnable(pendingRunnable);
                pendingRunnable = null;
            }
            if (backgroundView != null) {
                backgroundView.cancel();
            }
        }

        private Palette.Swatch pickSwatch(Palette palette) {
            Palette.Swatch s = palette.getVibrantSwatch();
            if (s == null) {
                s = palette.getDarkVibrantSwatch();
            }
            if (s == null) {
                s = palette.getLightVibrantSwatch();
            }
            if (s == null) {
                s = palette.getMutedSwatch();
            }
            if (s == null) {
                s = palette.getDarkMutedSwatch();
            }
            if (s == null) {
                s = palette.getLightMutedSwatch();
            }
            if (s == null) {
                for (Palette.Swatch sw : palette.getSwatches()) {
                    if (sw != null && sw.getPopulation() > 0) {
                        s = sw;
                        break;
                    }
                }
            }
            return s;
        }

        private Bitmap makeBlurred(Bitmap src) {
            try {
                final int w = 64;
                int h = (int) (64f * src.getHeight() / Math.max(1, src.getWidth()));
                h = Utilities.clamp(h, 128, 32);
                Bitmap small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(small);
                canvas.drawBitmap(src, null, new RectF(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
                Utilities.stackBlurBitmap(small, Math.max(6, w / 8));
                return small;
            } catch (Throwable t) {
                FileLog.e(t);
                return null;
            }
        }
    }

    public static class CoverPlaceholderDrawable extends Drawable {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable note;

        public CoverPlaceholderDrawable() {
            note = ApplicationLoader.applicationContext.getDrawable(R.drawable.ic_ios_music_note);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) {
                return;
            }
            final float radius = dp(14);
            paint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom, new int[] {0xFF575760, 0xFF37373F}, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom, radius, radius, paint);
            paint.setShader(null);
            if (note != null) {
                final int s = (int) (Math.min(bounds.width(), bounds.height()) * 0.34f);
                final int cx = bounds.centerX();
                final int cy = bounds.centerY();
                note.setBounds(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
                note.setAlpha(140);
                note.draw(canvas);
            }
        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }
}