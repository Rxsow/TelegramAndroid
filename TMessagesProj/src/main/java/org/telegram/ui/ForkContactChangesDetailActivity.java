package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.forkgram.ForkContactChanges;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/**
 * Tele iOS: the change stream (مجرا) of a single contact – a vertical timeline
 * with a dot per change, newest first. The action bar toggle switches between
 * the timeline rail and a plain list.
 */
public class ForkContactChangesDetailActivity extends BaseFragment {

    private static final int MENU_TOGGLE = 1;

    private final String contactName;
    private final ArrayList<JSONObject> entries;

    private RecyclerListView listView;
    private TextView emptyView;
    private ListAdapter adapter;
    private boolean timelineMode = true;

    public ForkContactChangesDetailActivity(String name, ArrayList<JSONObject> changeEntries) {
        this.contactName = name;
        this.entries = changeEntries;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(contactName);
        actionBar.setSubtitle(LocaleController.getString(R.string.ContactChangesTitle));
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(true);
        actionBar.createMenu().addItem(MENU_TOGGLE, R.drawable.msg2_folder);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_TOGGLE) {
                    timelineMode = !timelineMode;
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
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
        listView.setOnItemClickListener(null);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    private class ListAdapter extends RecyclerView.Adapter<RecyclerListView.Holder> {

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public RecyclerListView.Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new ChangeRow(mContext));
        }

        @Override
        public void onBindViewHolder(RecyclerListView.Holder holder, int position) {
            JSONObject entry = entries.get(position);
            ChangeRow row = (ChangeRow) holder.itemView;
            boolean first = position == 0;
            boolean last = position == entries.size() - 1;
            String field = entry.optString("field", "");
            String from = entry.optString("from", "");
            String to = entry.optString("to", "");
            String values;
            if (from.length() == 0 && to.length() == 0) {
                values = null;
            } else {
                values = "(" + from + ") to (" + to + ")";
            }
            row.bind(timelineMode, first, last, field + " changed", ForkContactChanges.formatTimestamp(entry.optLong("t")), values, ForkContactChangesActivity.nameColor(contactName));
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }
    }

    private class ChangeRow extends FrameLayout {

        private final View railTop;
        private final View railBottom;
        private final TextView titleView;
        private final TextView timeView;
        private final TextView valuesView;
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean dotVisible;

        ChangeRow(Context context) {
            super(context);
            setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, getResourceProvider()), Theme.RIPPLE_MASK_ROUNDRECT_6DP));

            railTop = new View(context);
            railBottom = new View(context);
            addView(railTop, LayoutHelper.createFrame(2, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 26, 0, 0, 0));
            addView(railBottom, LayoutHelper.createFrame(2, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.BOTTOM, 26, 0, 0, 0));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider()));
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 48, 12, 16, 0));

            timeView = new TextView(context);
            timeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            timeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, getResourceProvider()));
            addView(timeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 48, 32, 16, 0));

            valuesView = new TextView(context);
            valuesView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            valuesView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, getResourceProvider()));
            addView(valuesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 48, 50, 16, 0));
        }

        void bind(boolean timeline, boolean first, boolean last, String title, String time, String values, int color) {
            titleView.setText(title);
            timeView.setText(time);
            if (values == null) {
                valuesView.setVisibility(View.GONE);
            } else {
                valuesView.setVisibility(View.VISIBLE);
                valuesView.setText(values);
            }
            int railColor = Theme.getColor(Theme.key_divider, getResourceProvider());
            railTop.setBackgroundColor(railColor);
            railBottom.setBackgroundColor(railColor);
            railTop.getLayoutParams().height = first ? AndroidUtilities.dp(22) : LayoutHelper.MATCH_PARENT;
            railBottom.getLayoutParams().height = last ? AndroidUtilities.dp(22) : LayoutHelper.MATCH_PARENT;
            railTop.setVisibility(timeline ? View.VISIBLE : View.GONE);
            railBottom.setVisibility(timeline ? View.VISIBLE : View.GONE);
            dotVisible = timeline;
            dotPaint.setColor(color);
            setWillNotDraw(!timeline);

            int leftMargin = AndroidUtilities.dp(timeline ? 48 : 20);
            ((LayoutParams) titleView.getLayoutParams()).leftMargin = leftMargin;
            ((LayoutParams) timeView.getLayoutParams()).leftMargin = leftMargin;
            ((LayoutParams) valuesView.getLayoutParams()).leftMargin = leftMargin;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (dotVisible) {
                canvas.drawCircle(AndroidUtilities.dp(27), AndroidUtilities.dp(27), AndroidUtilities.dp(5), dotPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            setMeasuredDimension(getMeasuredWidth(), AndroidUtilities.dp(valuesView.getVisibility() == VISIBLE ? 84 : 62));
        }
    }
}
