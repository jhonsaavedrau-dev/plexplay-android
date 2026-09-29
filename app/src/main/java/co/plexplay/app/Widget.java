package co.plexplay.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Widgets de la pantalla de inicio. Dos tamaños:
 *  - Racha (2×2): Manzana, los días de racha y si la meta de hoy ya está cumplida.
 *  - Mi día (4×2): racha, barra de XP de hoy, palabra del día y el botón «Practicar».
 * Los datos los manda la página (PlexAndroid.setWidget, módulo plx68.js) y quedan guardados en el teléfono,
 * así el widget funciona sin abrir la app y se pone al día solo a medianoche.
 */
public class Widget extends AppWidgetProvider {
    static final String PREFS = "plex";

    /** widget grande: la misma lógica con otra plantilla */
    public static class Dia extends Widget {}

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { updateAll(c); }

    static void save(Context c, String json) {
        try {
            JSONObject o = new JSONObject(json);
            SharedPreferences.Editor e = c.getSharedPreferences(PREFS, 0).edit();
            e.putInt("streak", o.optInt("streak", 0))
             .putInt("xp", o.optInt("xp", 0)).putInt("goal", Math.max(1, o.optInt("goal", 20)))
             .putString("xpDay", o.optString("day", ""))
             .putString("lastAny", o.optString("lastAny", ""))
             .putString("name", o.optString("name", ""))
             .putString("course", o.optString("course", ""))
             .putString("next", o.optString("next", ""))
             .putString("fr", o.optString("fr", "")).putString("es", o.optString("es", "")).putString("ex", o.optString("ex", ""))
             .putBoolean("paused", false);   // abrió la app: los avisos vuelven a su ritmo normal
            if (o.optBoolean("done", false)) e.putString("last", o.optString("day", ""));
            e.apply();
        } catch (Exception ex) { /* datos incompletos: se ignoran */ }
        updateAll(c);
    }

    static String day(int offset) {
        Calendar t = Calendar.getInstance(); t.add(Calendar.DAY_OF_YEAR, offset);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(t.getTime());
    }

    /** racha vigente: si el último día con la meta cumplida no es hoy ni ayer, la racha ya se perdió */
    static int streakNow(SharedPreferences p) {
        String last = p.getString("last", "");
        return last.equals(day(0)) || last.equals(day(-1)) ? p.getInt("streak", 0) : 0;
    }
    static boolean doneToday(SharedPreferences p) { return day(0).equals(p.getString("last", "")); }
    static int xpToday(SharedPreferences p) { return day(0).equals(p.getString("xpDay", "")) ? p.getInt("xp", 0) : 0; }

    static PendingIntent open(Context c, boolean practicar, int code) {
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (practicar) i.putExtra(MainActivity.EXTRA_PRACTICAR, true);
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        int streak = streakNow(p), xp = xpToday(p), goal = Math.max(1, p.getInt("goal", 20));
        boolean done = doneToday(p);
        String dias = streak == 1 ? "día de racha" : "días de racha";
        String estado = done ? "✓ Meta cumplida" : streak > 0 ? "¡Sálvala hoy!" : "Empieza hoy";

        for (int id : m.getAppWidgetIds(new ComponentName(c, Widget.class))) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_racha);
            v.setImageViewResource(R.id.w_fuego, done ? R.drawable.ic_flame : R.drawable.ic_flame_off);
            v.setTextViewText(R.id.w_num, String.valueOf(streak));
            v.setTextViewText(R.id.w_lbl, dias);
            v.setTextViewText(R.id.w_est, estado);
            v.setInt(R.id.w_est, "setBackgroundResource", done ? R.drawable.widget_chip_ok : R.drawable.widget_chip);
            v.setOnClickPendingIntent(R.id.w_root, open(c, !done, 21));
            m.updateAppWidget(id, v);
        }

        String fr = p.getString("fr", ""), es = p.getString("es", "");
        if (fr.isEmpty()) { fr = "le quotidien"; es = "el día a día"; }
        String next = p.getString("next", "");
        for (int id : m.getAppWidgetIds(new ComponentName(c, Dia.class))) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_dia);
            v.setImageViewResource(R.id.w_fuego, done ? R.drawable.ic_flame : R.drawable.ic_flame_off);
            v.setTextViewText(R.id.w_num, String.valueOf(streak));
            v.setTextViewText(R.id.w_lbl, streak == 1 ? "día" : "días");
            v.setTextViewText(R.id.w_meta, done ? "✓ Meta de hoy cumplida" : "Meta de hoy · " + Math.min(xp, goal) + "/" + goal + " XP");
            v.setProgressBar(R.id.w_barra, goal, Math.min(xp, goal), false);
            v.setTextViewText(R.id.w_fr, fr);
            v.setTextViewText(R.id.w_es, es);
            v.setTextViewText(R.id.w_btn, done ? "▶ Seguir practicando" : "▶ Practicar");
            v.setTextViewText(R.id.w_next, next);
            v.setViewVisibility(R.id.w_next, next.isEmpty() ? View.GONE : View.VISIBLE);
            v.setOnClickPendingIntent(R.id.w_root, open(c, false, 22));
            v.setOnClickPendingIntent(R.id.w_btn, open(c, true, 23));
            m.updateAppWidget(id, v);
        }
    }
}
