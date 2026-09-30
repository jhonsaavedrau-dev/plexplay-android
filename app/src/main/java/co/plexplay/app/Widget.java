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
 * Widgets de la pantalla de inicio, con Manzana pintado (cambia de pose según tu día):
 *  - Racha (2×2): días de racha, los últimos 7 días y el estado de hoy.
 *  - Mi día (4×2): racha, barra de XP de hoy, palabra del día y el botón «Practicar».
 *  - Palabra del día (4×1): la palabra, su traducción y un ejemplo.
 *  - Continúa tu curso (4×1): la siguiente lección y «Empezar».
 *  - Meta de hoy (2×2): XP de hoy contra la meta.
 * Los datos los manda la página (PlexAndroid.setWidget, módulo plx68.js) y quedan guardados en el teléfono,
 * así el widget funciona sin abrir la app y se pone al día solo a medianoche.
 */
public class Widget extends AppWidgetProvider {
    static final String PREFS = "plex";

    /** las demás plantillas comparten la misma lógica (updateAll las pinta todas) */
    public static class Dia extends Widget {}
    public static class Palabra extends Widget {}
    public static class Leccion extends Widget {}
    public static class Meta extends Widget {}

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

    static final int[] PUNTOS = { R.id.w_d1, R.id.w_d2, R.id.w_d3, R.id.w_d4, R.id.w_d5, R.id.w_d6, R.id.w_d7 };

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        int streak = streakNow(p), xp = xpToday(p), goal = Math.max(1, p.getInt("goal", 20));
        boolean done = doneToday(p);
        String dias = streak == 1 ? "día de racha" : "días de racha";
        String estado = done ? "✓ Meta cumplida" : streak > 0 ? "¡Sálvala hoy!" : "Empieza hoy";
        /* Manzana: celebra si ya cumpliste, duerme si la racha está en peligro, saluda si empiezas */
        int mz = done ? R.drawable.w_mz_fuego : streak > 0 ? R.drawable.w_mz_duerme : R.drawable.w_mz_saluda;

        for (int id : m.getAppWidgetIds(new ComponentName(c, Widget.class))) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_racha);
            v.setImageViewResource(R.id.w_fuego, done ? R.drawable.ic_flame : R.drawable.ic_flame_off);
            v.setImageViewResource(R.id.w_mz, mz);
            v.setTextViewText(R.id.w_num, String.valueOf(streak));
            v.setTextViewText(R.id.w_lbl, dias);
            v.setTextViewText(R.id.w_est, estado);
            v.setInt(R.id.w_est, "setBackgroundResource", done ? R.drawable.widget_chip_ok : R.drawable.widget_chip);
            /* los últimos 7 días: el último punto es hoy (lleno si ya cumpliste, anillo si falta) */
            int llenos = Math.min(streak, 7);
            for (int k = 0; k < 7; k++) {
                int desdeHoy = 6 - k;   // 0 = hoy
                boolean hoy = desdeHoy == 0, lleno;
                if (done) lleno = desdeHoy < llenos;
                else lleno = !hoy && desdeHoy <= Math.min(streak, 6);
                v.setImageViewResource(PUNTOS[k], lleno ? R.drawable.widget_dot_on : hoy ? R.drawable.widget_dot_hoy : R.drawable.widget_dot_off);
            }
            v.setOnClickPendingIntent(R.id.w_root, open(c, !done, 21));
            m.updateAppWidget(id, v);
        }

        String fr = p.getString("fr", ""), es = p.getString("es", ""), ex = p.getString("ex", "");
        if (fr.isEmpty()) { fr = "le quotidien"; es = "el día a día"; ex = ""; }
        String next = p.getString("next", ""), course = p.getString("course", ""), name = p.getString("name", "");
        for (int id : m.getAppWidgetIds(new ComponentName(c, Dia.class))) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_dia);
            v.setImageViewResource(R.id.w_fuego, done ? R.drawable.ic_flame : R.drawable.ic_flame_off);
            v.setImageViewResource(R.id.w_mz, done ? R.drawable.w_mz_trofeo : R.drawable.w_mz_escribe);
            v.setTextViewText(R.id.w_hola, name.isEmpty() ? "Bonjour !" : "Bonjour, " + name + " !");
            v.setTextViewText(R.id.w_num, String.valueOf(streak));
            v.setTextViewText(R.id.w_lbl, streak == 1 ? "día" : "días");
            v.setTextViewText(R.id.w_meta, done ? "✓ Meta de hoy cumplida" : "Meta de hoy · " + Math.min(xp, goal) + "/" + goal + " XP");
            v.setProgressBar(R.id.w_barra, goal, Math.min(xp, goal), false);
            v.setTextViewText(R.id.w_fr, fr);
            v.setTextViewText(R.id.w_es, es);
            v.setTextViewText(R.id.w_btn, done ? "▶ Seguir" : "▶ Practicar");
            v.setTextViewText(R.id.w_next, next);
            v.setViewVisibility(R.id.w_next, next.isEmpty() ? View.GONE : View.VISIBLE);
            v.setOnClickPendingIntent(R.id.w_root, open(c, false, 22));
            v.setOnClickPendingIntent(R.id.w_btn, open(c, true, 23));
            m.updateAppWidget(id, v);
        }

        for (int id : m.getAppWidgetIds(new ComponentName(c, Palabra.class))) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_palabra);
            v.setTextViewText(R.id.w_fr, fr);
            v.setTextViewText(R.id.w_es, es);
            v.setTextViewText(R.id.w_ex, ex.isEmpty() ? "" : "«" + ex + "»");
            v.setViewVisibility(R.id.w_ex, ex.isEmpty() ? View.GONE : View.VISIBLE);
            v.setOnClickPendingIntent(R.id.w_root, open(c, false, 24));
            m.updateAppWidget(id, v);
        }

        for (int id : m.getAppWidgetIds(new ComponentName(c, Leccion.class))) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_leccion);
            v.setTextViewText(R.id.w_curso, course.isEmpty() ? "CONTINÚA TU CURSO" : course.toUpperCase(Locale.ROOT));
            v.setTextViewText(R.id.w_next, next.isEmpty() ? "Abre PLEX PLAY y elige tu curso" : next);
            v.setTextViewText(R.id.w_btn, next.isEmpty() ? "▶ Abrir" : "▶ Empezar");
            v.setOnClickPendingIntent(R.id.w_root, open(c, false, 25));
            v.setOnClickPendingIntent(R.id.w_btn, open(c, true, 26));
            m.updateAppWidget(id, v);
        }

        for (int id : m.getAppWidgetIds(new ComponentName(c, Meta.class))) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_meta);
            v.setImageViewResource(R.id.w_mz, done ? R.drawable.w_mz_trofeo : R.drawable.w_mz_celular);
            v.setTextViewText(R.id.w_xp, String.valueOf(Math.min(xp, 9999)));
            v.setTextViewText(R.id.w_goal, "de " + goal + " XP");
            v.setProgressBar(R.id.w_barra, goal, Math.min(xp, goal), false);
            v.setTextViewText(R.id.w_est, done ? "✓ ¡Meta cumplida!" : "Te faltan " + Math.max(0, goal - xp) + " XP");
            v.setOnClickPendingIntent(R.id.w_root, open(c, !done, 27));
            m.updateAppWidget(id, v);
        }
    }
}
