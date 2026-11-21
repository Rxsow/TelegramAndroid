package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.forkgram.ForkContactChanges;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tele iOS: Contact Change log, grouped by contact. Tap a contact to see the
 * change stream (timeline) of that contact. Replaces the old log dialog.
 */
public class ForkContactChangesActivity extends BaseFragment {

    private RecyclerListView listView;
    private TextView emptyView;
    private ListAdapter adapter;
    private final ArrayList<String> names = new ArrayList<>();
    private final Map<String, ArrayList<JSONObject>> byName = new LinkedHashMap<>();

    private void reloadData() {
        byName.clear();
        names.clear();
        JSONArray log = ForkContactChanges.getLog();
        for (int i = 0; i < log.length(); i++) {
            JSONObject entry = log.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            String name = entry.optString("name", "");
            if (name.length() == 0) {
                name = "?";
            }
            ArrayList<JSONObject> list = byName.get(name);
            if (list == null) {
                list = new ArrayList<>();
                byName.put(name, list);
                names.add(name);
            }
            list.add(entry);
        }
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
        actionBar.setTitle(LocaleController.getString(R.string.ContactChangesTitle));
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(true);
        actionBar.createMenu().addItem(2, R.drawable.ic_ab_other);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 2) {
                    showClearMenu();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;

        emptyView = new TextView(context);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, getResourceProvider()));
        emptyView.setText(LocaleController.getString(R.string.ContactChangesEmpty));
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emptyView.setPadding(0, AndroidUtilities.dp(150), 0, 0);

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setEmptyView(emptyView);
        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < names.size()) {
                presentFragment(new ForkContactChangesDetailActivity(names.get(position), byName.get(names.get(position))));
            }
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    private void showClearMenu() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context, getResourceProvider());
        builder.setTitle(LocaleController.getString(R.string.ContactChangesTitle));
        builder.setMessage(LocaleController.getString(R.string.ContactChangesClearConfirm));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.ContactChangesClear), (d, w) -> {
            ForkContactChanges.clearLog();
            reloadData();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });
        showDialog(builder.create());
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadData();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    static int nameColor(String name) {
        int hash = 0;
        for (int i = 0; i < name.length(); i++) {
            hash = hash * 31 + name.charAt(i);
        }
        float hue = (Math.abs(hash) % 360) / 360f;
        return Color.HSVToColor(new float[]{hue, 0.45f, 0.62f});
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

            TextView avatarView = new TextView(mContext);
            avatarView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            avatarView.setTypeface(Typeface.DEFAULT_BOLD);
            avatarView.setGravity(Gravity.CENTER);
            row.addView(avatarView, LayoutHelper.createFrame(44, 44, Gravity.CENTER_VERTICAL | Gravity.LEFT, 16, 8, 0, 8));

            TextView nameView = new TextView(mContext);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameView.setTypeface(AndroidUtilities.bold());
            nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider()));
            nameView.setSingleLine(true);
            row.addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 76, 0, 16, 0));

            TextView countView = new TextView(mContext);
            countView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            countView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, getResourceProvider()));
            countView.setSingleLine(true);
            row.addView(countView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 76, 0, 16, 8));

            View divider = new View(mContext);
            divider.setBackgroundColor(Theme.getColor(Theme.key_divider, getResourceProvider()));
            row.addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM | Gravity.LEFT, 76, 0, 0, 0));

            return new RecyclerListView.Holder(row);
        }

        @Override
        public void onBindViewHolder(RecyclerListView.Holder holder, int position) {
            FrameLayout row = (FrameLayout) holder.itemView;
            TextView avatarView = (TextView) row.getChildAt(0);
            TextView nameView = (TextView) row.getChildAt(1);
            TextView countView = (TextView) row.getChildAt(2);

            String name = names.get(position);
            int count = byName.get(name) == null ? 0 : byName.get(name).size();

            nameView.setText(name);
            countView.setText(LocaleController.formatString(R.string.ContactChangesCount, count));
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(nameColor(name));
            avatarView.setBackground(bg);
            avatarView.setText(name.length() > 0 ? name.substring(0, 1).toUpperCase() : "?");
        }

        @Override
        public int getItemCount() {
            return names.size();
        }
    }
}
