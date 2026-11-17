package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.BackupImageView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Forkgram: lists the dialogs hidden via ForkHiddenChats.
 * Tap opens the chat, long-press offers Open chat / Unhide.
 */
public class ForkHiddenChatsActivity extends BaseFragment {

    private RecyclerListView listView;
    private TextView emptyView;
    private ListAdapter adapter;
    private final ArrayList<Long> dialogIds = new ArrayList<>();

    private void reloadData() {
        dialogIds.clear();
        dialogIds.addAll(ForkHiddenChats.getHiddenIds(currentAccount));
        Collections.sort(dialogIds);
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
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setText(LocaleController.getString(R.string.ForkgramHiddenChatsEmpty));
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emptyView.setPadding(0, AndroidUtilities.dp(150), 0, 0);

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setEmptyView(emptyView);
        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < dialogIds.size()) {
                openDialog(dialogIds.get(position));
            }
        });
        listView.setOnItemLongClickListener((view, position, x, y) -> {
            if (position >= 0 && position < dialogIds.size()) {
                showItemMenu(position);
                return true;
            }
            return false;
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
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
        long dialogId = dialogIds.get(position);
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

    private class ListAdapter extends RecyclerView.Adapter<RecyclerListView.Holder> {

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout row = new FrameLayout(mContext);
            row.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ROUNDRECT_6DP));

            BackupImageView imageView = new BackupImageView(mContext);
            imageView.setRoundRadius(AndroidUtilities.dp(20));
            row.addView(imageView, LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | Gravity.LEFT, 16, 8, 0, 8));

            TextView nameView = new TextView(mContext);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameView.setTypeface(AndroidUtilities.bold());
            nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            nameView.setSingleLine(true);
            nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 72, 0, 16, 0));

            View divider = new View(mContext);
            divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
            row.addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM | Gravity.LEFT, 72, 0, 0, 0));

            return new RecyclerListView.Holder(row);
        }

        @Override
        public void onBindViewHolder(RecyclerListView.Holder holder, int position) {
            FrameLayout row = (FrameLayout) holder.itemView;
            BackupImageView imageView = (BackupImageView) row.getChildAt(0);
            TextView nameView = (TextView) row.getChildAt(1);
            bindDialog(dialogIds.get(position), imageView, nameView);
        }

        @Override
        public int getItemCount() {
            return dialogIds.size();
        }

        private void bindDialog(long dialogId, BackupImageView imageView, TextView nameView) {
            MessagesController messagesController = getMessagesController();
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            nameView.setText(chatName(dialogId));
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
