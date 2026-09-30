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
 *  - Racha (2×2), al estilo Duolingo: fondo, pose y mensaje de Manzana según la hora y tu racha (ver estado).
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
             .putString("aviso", o.optString("aviso", ""))
             .putBoolean("hay", o.optBoolean("hay", o.optInt("streak", 0) > 0 || !o.optString("lastAny", "").isEmpty()))
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

    /* ---- Racha al estilo Duolingo: la misma tabla que la app (web/app/js/plx71.js → estado) ---- */
    static class Estado {
        String id, fondo, mz, msg; int num; String lbl; boolean fantasma, apagada, oscuro;
        Estado(String id, String fondo, String mz, String msg, int num, String lbl) { this.id = id; this.fondo = fondo; this.mz = mz; this.msg = msg; this.num = num; this.lbl = lbl;
            oscuro = fondo.equals("oro") || fondo.equals("amanecer") || fondo.equals("hielo"); }
    }
    static final int[] HITOS = { 3, 7, 10, 14, 21, 30, 50, 75, 100, 150, 200, 250, 300, 365, 500, 730, 1000 };
    static boolean esHito(int n) { for (int h : HITOS) if (h == n) return true; return n > 0 && n % 100 == 0; }
    static final String[][] CELEBRA = {
        { "fuego", "racha-fuego", "¡Racha encendida!" }, { "cielo", "celebra", "¡Meta de hoy cumplida!" }, { "noche", "feliz", "Hoy brillaste ✨" },
        { "verde", "guino-pulgar", "¡Así se hace!" }, { "lila", "croissant-boina", "Bien joué ! 🥐" }, { "atardecer", "bandera", "Vive la racha !" },
        { "rosa", "tumbado-corazon", "Manzana está orgulloso" } };

    /** días desde el último día con actividad (0 = hoy, -1 = nunca) */
    static int diasDesde(SharedPreferences p) {
        String u = p.getString("lastAny", ""); if (u.isEmpty()) u = p.getString("last", "");
        if (u.isEmpty()) return -1;
        for (int i = 0; i < 800; i++) if (u.equals(day(-i))) return i;
        return 800;
    }

    static Estado estado(SharedPreferences p) {
        int n = streakNow(p), xp = xpToday(p), meta = Math.max(1, p.getInt("goal", 20)), h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean hecho = doneToday(p), hay = p.getBoolean("hay", false) || n > 0 || !p.getString("last", "").isEmpty();
        String racha = n == 1 ? "día de racha" : "días de racha";
        if (!p.getString("aviso", "").isEmpty()) return new Estado("mantenimiento", "gris", "lupa", "Manzana está ordenando cosas. Vuelve en un ratito", n, racha);
        if (!hay) return new Estado("hola", "lila", "saluda", "¡Hola! Soy Manzana. Empecemos una racha", 0, "días de racha");
        if (hecho) {
            if (esHito(n)) return new Estado("hito", "oro", n >= 100 ? "graduado" : "trofeo", "¡" + n + " días de racha! 🏆", n, racha);
            String[] k = CELEBRA[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % CELEBRA.length];
            return new Estado("hecho", k[0], k[1], k[2], n, racha);
        }
        int falta = Math.max(0, meta - xp);
        if (n > 0) {
            if (xp > 0) return new Estado("casi", "cielo", "corre", "Te faltan " + falta + " XP. ¡Ya casi!", n, racha);
            if (h < 12) return new Estado("tres", "amanecer", "taza-cafe", "¿Tienes 3 minutos?", n, racha);
            if (h < 18) return new Estado("hora", "cielo", "escribe", "Hora de practicar", n, racha);
            if (h < 21) return new Estado("salva", "fuego", "alerta", "¡Salva tu racha!", n, racha);
            if (h < 23) return new Estado("tarde", "noche", "examen-susto", "¡Es tarde! Aún puedes salvarla", n, racha);
            return new Estado("ultima", "alerta", "sorpresa", "¡Última oportunidad!", n, racha);
        }
        if (xp > 0) return new Estado("casi0", "verde", "corre", "Te faltan " + falta + " XP para una racha nueva", 0, racha);
        int d = diasDesde(p);
        String sin = d == 1 ? "día sin practicar" : "días sin practicar";
        Estado e;
        if (d >= 2 && d <= 3) e = new Estado("dias", "gris", "pensando", d + " días desde tu última lección", d, sin);
        else if (d >= 4 && d <= 7) { e = new Estado("ignora", "noche", "duda", "¿Me estás ignorando? 👻", d, sin); e.fantasma = true; e.apagada = true; }
        else if (d >= 8 && d <= 20) e = new Estado("extrana", "lila", "ovillo", "Manzana te extraña", d, sin);
        else if (d > 20) { e = new Estado("congelada", "hielo", "dormido", "Tu racha se congeló. ¡Descongélala! ❄️", d, sin); e.apagada = true; }
        else if (h >= 22) e = new Estado("zzz", "noche", "dormido", "Zzz… ¿una lección antes de dormir?", 0, racha);
        else e = new Estado("empieza", "cielo", "senala-arriba", "Empieza una lección", 0, racha);
        return e;
    }

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

        Estado st = estado(p);
        for (int id : m.getAppWidgetIds(new ComponentName(c, Widget.class))) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_racha);
            v.setInt(R.id.w_root, "setBackgroundResource", c.getResources().getIdentifier("wr_" + st.fondo, "drawable", c.getPackageName()));
            int mzId = c.getResources().getIdentifier("w_rz_" + st.mz.replace('-', '_'), "drawable", c.getPackageName());
            v.setImageViewResource(R.id.w_mz, mzId != 0 ? mzId : R.drawable.w_rz_sentado);
            v.setInt(R.id.w_mz, "setImageAlpha", st.fantasma ? 125 : 255);
            v.setTextViewText(R.id.w_msg, st.msg);
            v.setImageViewResource(R.id.w_fuego, st.apagada ? R.drawable.ic_flame_off : R.drawable.ic_flame);
            v.setTextViewText(R.id.w_num, String.valueOf(st.num));
            v.setTextViewText(R.id.w_lbl, st.lbl);
            int tinta = st.oscuro ? 0xFF3A2600 : 0xFFFFFFFF;
            v.setTextColor(R.id.w_num, tinta);
            v.setTextColor(R.id.w_lbl, st.oscuro ? 0xE63A2600 : 0xF2FFFFFF);
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
