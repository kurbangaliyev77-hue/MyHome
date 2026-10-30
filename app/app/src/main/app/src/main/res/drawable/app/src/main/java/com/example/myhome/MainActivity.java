package com.example.myhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String PREFS = "myhome";
    private static final String KEY_HIDDEN = "hidden";
    private static final String PINNED = "com.google.android.youtube.tv";

    private static class Item {
        String label;
        String pkg;
        ComponentName cn;
        Drawable icon;
        boolean restore;
    }

    private final List<Item> items = new ArrayList<>();
    private GridView grid;
    private BaseAdapter adapter;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F1115);
        root.setPadding(dp(48), dp(32), dp(48), dp(16));

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextSize(48);
        time.setTextColor(Color.WHITE);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, d MMMM");
        date.setTextSize(18);
        date.setTextColor(0xFFAAB0BC);
        root.addView(time);
        root.addView(date);

        grid = new GridView(this);
        grid.setNumColumns(6);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(dp(16));
        grid.setHorizontalSpacing(dp(16));
        grid.setClipToPadding(false);
        grid.setPadding(dp(8), dp(24), dp(8), dp(8));
        GradientDrawable sel = new GradientDrawable();
        sel.setCornerRadius(dp(14));
        sel.setColor(0x22FFFFFF);
        sel.setStroke(dp(3), Color.WHITE);
        grid.setSelector(sel);
        grid.setDrawSelectorOnTop(true);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> onTap(items.get(pos)));
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            onHold(items.get(pos));
            return true;
        });
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
        grid.requestFocus();
    }

    @Override
    public void onBackPressed() {
        // This is the home screen: Back does nothing.
    }

    private Set<String> hidden() {
        return new HashSet<>(getSharedPreferences(PREFS, MODE_PRIVATE)
                .getStringSet(KEY_HIDDEN, new HashSet<String>()));
    }

    private void load() {
        PackageManager pm = getPackageManager();
        Set<String> hidden = hidden();
        Map<String, Item> map = new LinkedHashMap<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || hidden.contains(pkg) || map.containsKey(pkg)) continue;
                Item it = new Item();
                it.pkg = pkg;
                it.label = String.valueOf(ri.loadLabel(pm));
                it.cn = new ComponentName(pkg, ri.activityInfo.name);
                it.icon = ri.loadIcon(pm);
                map.put(pkg, it);
            }
        }
        items.clear();
        items.addAll(map.values());
        Collections.sort(items, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                boolean ap = a.pkg.equals(PINNED), bp = b.pkg.equals(PINNED);
                if (ap != bp) return ap ? -1 : 1;
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        if (!hidden.isEmpty()) {
            Item r = new Item();
            r.restore = true;
            r.label = "Show hidden apps";
            r.icon = getResources().getDrawable(android.R.drawable.ic_menu_revert);
            items.add(r);
        }
        adapter.notifyDataSetChanged();
    }

    private void onTap(Item it) {
        if (it.restore) {
            onHold(it);
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .setComponent(it.cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "Can't open " + it.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void onHold(final Item it) {
        final SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (it.restore) {
            new AlertDialog.Builder(this)
                    .setTitle("Show all hidden apps again?")
                    .setPositiveButton("Show", (d, w) -> {
                        sp.edit().remove(KEY_HIDDEN).apply();
                        load();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Hide " + it.label + "?")
                    .setMessage("The app stays installed. Restore it with the last tile.")
                    .setPositiveButton("Hide", (d, w) -> {
                        Set<String> h = hidden();
                        h.add(it.pkg);
                        sp.edit().putStringSet(KEY_HIDDEN, h).apply();
                        load();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            LinearLayout box;
            ImageView img;
            TextView tv;
            if (v == null) {
                box = new LinearLayout(MainActivity.this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setGravity(Gravity.CENTER);
                box.setPadding(dp(12), dp(16), dp(12), dp(12));
                img = new ImageView(MainActivity.this);
                img.setId(1);
                box.addView(img, new LinearLayout.LayoutParams(dp(72), dp(72)));
                tv = new TextView(MainActivity.this);
                tv.setId(2);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(16);
                tv.setGravity(Gravity.CENTER);
                tv.setMaxLines(1);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(8);
                box.addView(tv, lp);
                v = box;
            }
            img = v.findViewById(1);
            tv = v.findViewById(2);
            Item it = items.get(pos);
            img.setImageDrawable(it.icon);
            tv.setText(it.label);
            return v;
        }
    }
}
