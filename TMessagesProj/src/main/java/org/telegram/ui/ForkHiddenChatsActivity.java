package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Forkgram: lists the dialogs hidden via ForkHiddenChats. Tap opens the chat,
 * long-press offers Open chat / Unhide. Tele iOS: rows show the last-message
 * preview like the main chat list, and a pill row splits the hidden chats into
 * their folders (channels, groups, personal chats, bots).
 */
public class ForkHiddenChatsActivity extends BaseFragment {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_CHANNELS = 1;
    private static final int FILTER_GROUPS = 2;
    private static final int FILTER_USERS = 3;
    private static final int FILTER_BOTS = 4;

    private RecyclerListView listView;
    private TextView emptyView;
    private ListAdapter adapter;
    private final ArrayList<Long> dialogIds = new ArrayList<>();
    private final ArrayList<Long> visibleIds = new ArrayList<>();
    private int currentFilter = FILTER_ALL;
    private LinearLayout pillRow;

    private void reloadData() {
        dialogIds.clear();
        dialogIds.addAll(ForkHiddenChats.getHiddenIds(currentAccount));
        Collections.sort(dialogIds);
        applyFilter();
    }

    private void applyFilter() {
        visibleIds.clear();
        for (int i = 0; i < dialogIds.size(); i++) {
            long dialogId = dialogIds.get(i);
            if (currentFilter == FILTER_ALL || matchesFilter(dialogId, currentFilter)) {
                visibleIds.add(dialogId);
            }
        }
    }

    private boolean matchesFilter(long dialogId, int filter) {
        MessagesController messagesController = getMessagesController();
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User user = messagesController.getUser(dialogId);
            if (user != null && user.bot) {
                return filter == FILTER_BOTS;
            }
            return filter == FILTER_USERS;
        }
        if (DialogObject.isEncryptedDialog(dialogId)) {
            return filter == FILTER_USERS;
        }
        TLRPC.Chat chat = messagesController.getChat(-dialogId);
        if (chat == null) {
            return false;
        }
        if (chat.broadcast && !chat.megagroup) {
            return filter == FILTER_CHANNELS;
        }
        return filter == FILTER_GROUPS;
    }

    @Override
    public boolean onFragmentCreate() {
        if (!super.onFragmentCreate()) {
            return false;
        }
        reloadData();
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.ForkgramHiddenChats));
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;

        emptyView = new TextView(context);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, getResourceProvider()));
        emptyView.setText(LocaleController.getString(R.string.ForkgramHiddenChatsEmpty));
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emptyView.setPadding(0, AndroidUtilities.dp(180), 0, 0);

        // Tele iOS: folder pills above the list – every hidden chat sits in its
        // own folder (channel / group / personal chat / bot) like the main list
        HorizontalScrollView pillsScroll = new HorizontalScrollView(context);
        pillsScroll.setHorizontalScrollBarEnabled(false);
        pillRow = new LinearLayout(context);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        pillRow.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        pillsScroll.addView(pillRow, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
        String[] pillTitles = new String[] {
                LocaleController.getString(R.string.ForkgramFilterAll),
                LocaleController.getString(R.string.ForkgramFilterChannels),
                LocaleController.getString(R.string.ForkgramFilterGroups),
                LocaleController.getString(R.string.ForkgramFilterUsers),
                LocaleController.getString(R.string.ForkgramFilterBots)
        };
        for (int i = 0; i < pillTitles.length; i++) {
            final int filter = i;
            TextView pill = new TextView(context);
            pill.setText(pillTitles[i]);
            pill.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            pill.setTypeface(Typeface.DEFAULT_BOLD);
            pill.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(7), AndroidUtilities.dp(16), AndroidUtilities.dp(7));
            pill.setGravity(Gravity.CENTER);
            pill.setOnClickListener(v -> {
                currentFilter = filter;
                applyFilter();
                restylePills();
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            });
            pillRow.addView(pill, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 4, 0, 4, 0));
        }
        restylePills();
        frameLayout.addView(pillsScroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setEmptyView(emptyView);
        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < visibleIds.size()) {
                openDialog(visibleIds.get(position));
            }
        });
        listView.setOnItemLongClickListener((view, position, x, y) -> {
            if (position >= 0 && position < visibleIds.size()) {
                showItemMenu(position);
                return true;
            }
            return false;
        });
        FrameLayout.LayoutParams listParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT);
        listParams.topMargin = AndroidUtilities.dp(46);
        frameLayout.addView(listView, listParams);

        return fragmentView;
    }

    private void restylePills() {
        for (int i = 0; i < pillRow.getChildCount(); i++) {
            TextView pill = (TextView) pillRow.getChildAt(i);
            boolean selected = i == currentFilter;
            pill.setTextColor(Theme.getColor(selected ? Theme.key_chats_verifiedCheck : Theme.key_windowBackgroundWhiteGrayText, getResourceProvider()));
            pill.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16),
                    Theme.getColor(selected ? Theme.key_chats_verifiedBackground : Theme.key_graySection, getResourceProvider())));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadData();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void openDialog(long dialogId) {
        Bundle args = new Bundle();
        if (DialogObject.isEncryptedDialog(dialogId)) {
            args.putInt("encrypted_id", DialogObject.getEncryptedChatId(dialogId));
        } else if (DialogObject.isUserDialog(dialogId)) {
            args.putLong("user_id", dialogId);
        } else {
            args.putLong("chat_id", -dialogId);
        }
        presentFragment(new ChatActivity(args));
    }

    private void showItemMenu(int position) {
        long dialogId = visibleIds.get(position);
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(chatName(dialogId));
        builder.setItems(new CharSequence[]{
                LocaleController.getString(R.string.ForkgramOpenChat),
                LocaleController.getString(R.string.ForkgramUnhide)
        }, (d, which) -> {
            if (which == 0) {
                openDialog(dialogId);
            } else if (which == 1) {
                unhideDialog(dialogId);
            }
        });
        showDialog(builder.create());
    }

    private void unhideDialog(long dialogId) {
        Set<Long> ids = new HashSet<>(ForkHiddenChats.getHiddenIds(currentAccount));
        ids.remove(dialogId);
        ForkHiddenChats.saveIds(currentAccount, ids);
        reloadData();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private String chatName(long dialogId) {
        MessagesController messagesController = getMessagesController();
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User user = messagesController.getUser(dialogId);
            return user != null ? ContactsController.formatName(user.first_name, user.last_name) : LocaleController.getString(R.string.ForkgramUnknownChat);
        } else if (DialogObject.isEncryptedDialog(dialogId)) {
            TLRPC.EncryptedChat encryptedChat = messagesController.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
            TLRPC.User user = encryptedChat != null ? messagesController.getUser(encryptedChat.user_id) : null;
            return user != null ? ContactsController.formatName(user.first_name, user.last_name) : LocaleController.getString(R.string.ForkgramUnknownChat);
        } else {
            TLRPC.Chat chat = messagesController.getChat(-dialogId);
            return chat != null ? chat.title : LocaleController.getString(R.string.ForkgramUnknownChat);
        }
    }

    /** Last-message preview line, same source as the main chat list. */
    private CharSequence lastMessagePreview(long dialogId) {
        ArrayList<MessageObject> messages = getMessagesController().dialogMessage.get(dialogId);
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        MessageObject messageObject = messages.get(0);
        if (messageObject == null || messageObject.messageOwner == null) {
            return null;
        }
        CharSequence text = messageObject.messageText;
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        return text;
    }

    private class ListAdapter extends RecyclerView.Adapter<RecyclerListView.Holder> {

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public RecyclerListView.Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout row = new FrameLayout(mContext);
            row.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, getResourceProvider()), Theme.RIPPLE_MASK_ROUNDRECT_6DP));

            BackupImageView imageView = new BackupImageView(mContext);
            imageView.setRoundRadius(AndroidUtilities.dp(20));
            row.addView(imageView, LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | Gravity.LEFT, 16, 8, 0, 8));

            TextView nameView = new TextView(mContext);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameView.setTypeface(AndroidUtilities.bold());
            nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider()));
            nameView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            nameView.setSingleLine(true);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 72, 9, 16, 0));

            TextView previewView = new TextView(mContext);
            previewView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            previewView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, getResourceProvider()));
            previewView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            previewView.setSingleLine(true);
            previewView.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(previewView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 72, 0, 16, 10));

            View divider = new View(mContext);
            divider.setBackgroundColor(Theme.getColor(Theme.key_divider, getResourceProvider()));
            row.addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM | Gravity.LEFT, 72, 0, 0, 0));

            return new RecyclerListView.Holder(row);
        }

        @Override
        public void onBindViewHolder(RecyclerListView.Holder holder, int position) {
            FrameLayout row = (FrameLayout) holder.itemView;
            BackupImageView imageView = (BackupImageView) row.getChildAt(0);
            TextView nameView = (TextView) row.getChildAt(1);
            TextView previewView = (TextView) row.getChildAt(2);
            bindDialog(visibleIds.get(position), imageView, nameView, previewView);
        }

        @Override
        public int getItemCount() {
            return visibleIds.size();
        }

        private void bindDialog(long dialogId, BackupImageView imageView, TextView nameView, TextView previewView) {
            MessagesController messagesController = getMessagesController();
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            nameView.setText(chatName(dialogId));
            CharSequence preview = lastMessagePreview(dialogId);
            if (preview == null) {
                previewView.setVisibility(View.GONE);
            } else {
                previewView.setVisibility(View.VISIBLE);
                previewView.setText(preview);
            }
            if (DialogObject.isUserDialog(dialogId)) {
                TLRPC.User user = messagesController.getUser(dialogId);
                if (user != null) {
                    avatarDrawable.setInfo(currentAccount, user);
                }
                imageView.setImage(ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL), "50_50", avatarDrawable, user);
            } else if (DialogObject.isEncryptedDialog(dialogId)) {
                TLRPC.EncryptedChat encryptedChat = messagesController.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
                TLRPC.User user = encryptedChat != null ? messagesController.getUser(encryptedChat.user_id) : null;
                if (user != null) {
                    avatarDrawable.setInfo(currentAccount, user);
                }
                imageView.setImage(ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL), "50_50", avatarDrawable, user);
            } else {
                TLRPC.Chat chat = messagesController.getChat(-dialogId);
                if (chat != null) {
                    avatarDrawable.setInfo(currentAccount, chat);
                }
                imageView.setImage(ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL), "50_50", avatarDrawable, chat);
            }
        }
    }
}
