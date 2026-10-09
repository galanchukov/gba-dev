package ru.galanchukov.moneyrent;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final long GOAL = 12000L;
    private SharedPreferences prefs;
    private LinearLayout root, history;
    private EditText amountInput, noteInput;
    private TextView rentValue, lifeValue, earnedValue, progressValue, progressText, statusText;
    private long earned, rent, life, expenses;
    private JSONArray entries;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("money_tracker", MODE_PRIVATE);
        earned = prefs.getLong("earned", 0);
        rent = prefs.getLong("rent", 0);
        life = prefs.getLong("life", 0);
        expenses = prefs.getLong("expenses", 0);
        try { entries = new JSONArray(prefs.getString("entries", "[]")); }
        catch (Exception e) { entries = new JSONArray(); }
        buildUi();
        refresh();
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView text(String value, int size, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(Color.rgb(31, 41, 55));
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setPadding(0, dp(5), 0, dp(5));
        return v;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(28));
        root.setBackgroundColor(Color.rgb(248, 250, 252));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = text("Деньги до аренды", 27, true);
        title.setTextColor(Color.rgb(22, 101, 52));
        root.addView(title);
        root.addView(text("Личный финансовый трекер · работает офлайн", 14, false));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, dp(18), 0, dp(12));
        root.addView(card, cp);

        card.addView(text("ЦЕЛЬ НА АРЕНДУ", 12, true));
        rentValue = text("0 ₽", 30, true);
        rentValue.setTextColor(Color.rgb(22, 101, 52));
        card.addView(rentValue);
        progressValue = text("0% накоплено", 14, true);
        card.addView(progressValue);
        progressText = text("Осталось собрать 12 000 ₽", 14, false);
        card.addView(progressText);
        statusText = text("Каждая запись сохраняется на телефоне.", 12, false);
        card.addView(statusText);

        root.addView(text("Деньги на жизнь", 16, true));
        lifeValue = text("0 ₽", 24, true);
        root.addView(lifeValue);
        root.addView(text("Всего заработано", 13, false));
        earnedValue = text("0 ₽", 18, true);
        root.addView(earnedValue);
        root.addView(text("Расходы на жизнь: 0 ₽", 13, false));

        root.addView(text("Новая операция", 20, true));
        amountInput = new EditText(this);
        amountInput.setSingleLine(true);
        amountInput.setHint("Сумма в рублях");
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(amountInput, new LinearLayout.LayoutParams(-1, -2));

        noteInput = new EditText(this);
        noteInput.setSingleLine(true);
        noteInput.setHint("Комментарий: доставка, продукты...");
        root.addView(noteInput, new LinearLayout.LayoutParams(-1, -2));

        addButton("＋ Записать заработок", 0);
        addButton("↗ Отложить на аренду", 1);
        addButton("− Записать расход", 2);
        addButton("↩ Отменить последнюю операцию", 3);

        root.addView(text("История операций", 20, true));
        history = new LinearLayout(this);
        history.setOrientation(LinearLayout.VERTICAL);
        root.addView(history);
        TextView footer = text("Версия 1.0 · данные хранятся локально на этом устройстве", 11, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(20), 0, 0);
        root.addView(footer);
    }

    private void addButton(String label, int action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(4), 0, dp(4));
        root.addView(b, p);
        b.setOnClickListener(v -> {
            if (action == 3) { undo(); return; }
            String raw = amountInput.getText().toString().trim();
            long amount;
            try { amount = Long.parseLong(raw); }
            catch (Exception e) { amount = 0; }
            if (amount <= 0) {
                Toast.makeText(this, "Введи сумму больше нуля", Toast.LENGTH_SHORT).show();
                return;
            }
            String note = noteInput.getText().toString().trim();
            if (action == 0) {
                earned += amount;
                life += amount;
                addEntry("Заработок", amount, note);
            } else if (action == 1) {
                if (amount > life) {
                    Toast.makeText(this, "На жизнь сейчас только " + money(life) + ". Нельзя отложить больше остатка.", Toast.LENGTH_LONG).show();
                    return;
                }
                life -= amount;
                rent += amount;
                addEntry("В резерв на аренду", amount, note);
            } else {
                if (amount > life) {
                    Toast.makeText(this, "Недостаточно денег на жизнь: " + money(life), Toast.LENGTH_LONG).show();
                    return;
                }
                life -= amount;
                expenses += amount;
                addEntry("Расход", amount, note);
            }
            amountInput.setText("");
            noteInput.setText("");
            save();
            refresh();
        });
    }

    private void addEntry(String type, long amount, String note) {
        try {
            JSONObject item = new JSONObject();
            item.put("type", type);
            item.put("amount", amount);
            item.put("note", note);
            item.put("date", new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(new Date()));
            entries.put(item);
        } catch (Exception ignored) {}
    }

    private void undo() {
        if (entries.length() == 0) {
            Toast.makeText(this, "Пока нечего отменять", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Отменить последнюю операцию?")
            .setMessage("Баланс будет пересчитан по оставшимся записям.")
            .setNegativeButton("Нет", null)
            .setPositiveButton("Отменить", (d, w) -> {
                JSONArray old = entries;
                entries = new JSONArray();
                earned = 0; rent = 0; life = 0; expenses = 0;
                // Rebuild balances from all records except the last one.
                for (int i = 0; i < old.length() - 1; i++) {
                    JSONObject item = old.optJSONObject(i);
                    if (item == null) continue;
                    String type = item.optString("type");
                    long amount = item.optLong("amount");
                    if (type.equals("Заработок")) { earned += amount; life += amount; }
                    else if (type.equals("В резерв на аренду")) { rent += amount; life -= amount; }
                    else if (type.equals("Расход")) { expenses += amount; life -= amount; }
                    entries.put(item);
                }
                save(); refresh();
            }).show();
    }

    private void save() {
        prefs.edit().putLong("earned", earned).putLong("rent", rent)
            .putLong("life", life).putLong("expenses", expenses)
            .putString("entries", entries.toString()).apply();
    }

    private String money(long n) { return String.format(Locale.getDefault(), "%,d ₽", n).replace(',', ' '); }

    private void refresh() {
        rentValue.setText(money(rent));
        long remaining = Math.max(0, GOAL - rent);
        int pct = (int)Math.min(100, rent * 100 / GOAL);
        progressValue.setText(pct + "% накоплено");
        progressText.setText(remaining == 0 ? "Цель достигнута! Аренда под контролем." : "Осталось собрать " + money(remaining));
        lifeValue.setText(money(life));
        earnedValue.setText(money(earned));
        statusText.setText("Расходы на жизнь: " + money(expenses) + " · Данные сохраняются автоматически");
        history.removeAllViews();
        for (int i = entries.length() - 1; i >= 0; i--) {
            JSONObject item = entries.optJSONObject(i);
            if (item == null) continue;
            String line = item.optString("date") + " · " + item.optString("type") + "\n" +
                money(item.optLong("amount")) +
                (item.optString("note").isEmpty() ? "" : " · " + item.optString("note"));
            TextView row = text(line, 14, false);
            row.setPadding(dp(10), dp(9), dp(10), dp(9));
            row.setBackgroundColor(i % 2 == 0 ? Color.WHITE : Color.rgb(241, 245, 249));
            history.addView(row);
        }
        if (entries.length() == 0) history.addView(text("Пока нет операций. Добавь первый заработок.", 14, false));
    }
}
