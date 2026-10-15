package co.plexplay.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Widgets de la pantalla de inicio, con Manzana 2D (el estilo del logo; poses en drawable-nodpi/w2_*, salen de
 * herramientas/widgets-2d.py) que cambia según tu día:
 *  - Racha (2x2; a lo ancho en Android 12+): fondo, pose y mensaje según la hora y tu racha (ver estado), y la semana.
 *  - Mi día (4x2): racha, barra de XP de hoy, la semana y el botón «Practicar».
 *  - Palabra del día (4x1): la palabra, su traducción y un ejemplo.
 *  - Continúa tu curso (4x1): la siguiente lección y «Empezar».
 *  - Meta de hoy (2x2): anillo de XP de hoy contra la meta.
 *  - PLEX 1V1 (4x2, 1.8): tus copas y tu rango, y «Jugar duelo».
 * Los datos los manda la página (PlexAndroid.setWidget, módulos plx68.js y plx117.js) y quedan guardados en el teléfono,
 * así el widget funciona sin abrir la app y se pone al día solo a medianoche.
 * Los layouts salen de herramientas/widgets-layouts.py.
 */
public class Widget extends AppWidgetProvider {
    static final String PREFS = "plex";

    /** las demás plantillas comparten la misma lógica (updateAll las pinta todas) */
    public static class Dia extends Widget {}
    public static class Palabra extends Widget {}
    public static class Leccion extends Widget {}
    public static class Meta extends Widget {}
    public static class Duelo extends Widget {}

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { updateAll(c); }
    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) { updateAll(c); }

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
            // 1.8: racha con protectores (el último día que cuenta para la racha, cumplido O cubierto por un protector),
            // protectores que quedan, racha que se puede recuperar, la semana y el 1V1
            if (o.has("lastOk")) e.putString("lastOk", o.optString("lastOk", ""));
            if (o.has("freeze")) e.putInt("freeze", o.optInt("freeze", 0));
            if (o.has("recN")) e.putInt("recN", o.optInt("recN", 0)).putString("recHasta", o.optString("recHasta", ""));
            if (o.has("dias")) e.putString("dias", o.optJSONObject("dias") != null ? o.optJSONObject("dias").toString() : "{}");
            if (o.has("avRacha")) e.putBoolean("avRacha", o.optBoolean("avRacha", true)).putBoolean("avRec", o.optBoolean("avRec", true)).putBoolean("avVolver", o.optBoolean("avVolver", true));
            if (o.has("copas")) e.putInt("copas", o.optInt("copas", 0)).putString("rango", o.optString("rango", "")).putInt("duelosG", o.optInt("duelosG", 0));
            boolean done = o.optBoolean("done", false);
            if (done) e.putString("last", o.optString("day", ""));
            e.apply();
            // con la meta cumplida, el aviso del día ya no hace falta
            if (done) ((android.app.NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(Reminder.NOTIF);
        } catch (Exception ex) { /* datos incompletos: se ignoran */ }
        updateAll(c);
    }

    static String day(int offset) {
        Calendar t = Calendar.getInstance(); t.add(Calendar.DAY_OF_YEAR, offset);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(t.getTime());
    }
    /** días entre una fecha (yyyy-MM-dd) y hoy; 0 = hoy, -1 = vacía, 800 = muy vieja */
    static int hace(String d) {
        if (d == null || d.isEmpty()) return -1;
        for (int i = 0; i < 800; i++) if (d.equals(day(-i))) return i;
        return 800;
    }

    /** último día que sostiene la racha: el último con la meta cumplida o cubierto por un protector */
    static int haceOk(SharedPreferences p) {
        int a = hace(p.getString("last", "")), b = hace(p.getString("lastOk", ""));
        if (a < 0) return b; if (b < 0) return a;
        return Math.min(a, b);
    }
    /** racha vigente: si el último día que cuenta no es hoy ni ayer, solo sigue viva si los protectores cubren el hueco
     *  (la app los gasta al abrirla: freezeApply). Sin eso, el aviso decía «se apagó tu racha» a quien tenía protector. */
    static int streakNow(SharedPreferences p) {
        int h = haceOk(p), n = p.getInt("streak", 0);
        if (h < 0) return 0;
        if (h <= 1) return n;
        return h - 1 <= p.getInt("freeze", 0) ? n : 0;
    }
    /** la racha sigue viva gracias a un protector: el último día con la meta cumplida fue antes de ayer */
    static boolean protegida(SharedPreferences p) { int h = hace(p.getString("last", "")); return h >= 2 && streakNow(p) > 0; }
    static boolean doneToday(SharedPreferences p) { return day(0).equals(p.getString("last", "")); }
    static int xpToday(SharedPreferences p) { return day(0).equals(p.getString("xpDay", "")) ? p.getInt("xp", 0) : 0; }
    /** racha perdida que todavía se puede reparar (la app la ofrece hasta recHasta); 0 = nada que recuperar */
    static int recuperable(SharedPreferences p) {
        int n = p.getInt("recN", 0); String hasta = p.getString("recHasta", "");
        if (n <= 0 || hasta.isEmpty() || streakNow(p) > 0 || doneToday(p)) return 0;
        return hasta.compareTo(day(0)) >= 0 ? n : 0;
    }
    static String recuperaHasta(SharedPreferences p) {
        String h = p.getString("recHasta", "");
        return h.equals(day(0)) ? "hoy" : h.equals(day(1)) ? "hoy y mañana" : "unos días";
    }

    /* ---- Racha al estilo Duolingo: la misma tabla que la app (web/app/js/plx71.js → estado, y plx117.js) ---- */
    static class Estado {
        String id, fondo, mz, msg; int num; String lbl; boolean fantasma, apagada, oscuro;
        Estado(String id, String fondo, String mz, String msg, int num, String lbl) { this.id = id; this.fondo = fondo; this.mz = mz; this.msg = msg; this.num = num; this.lbl = lbl;
            oscuro = fondo.equals("oro") || fondo.equals("amanecer") || fondo.equals("hielo"); }
    }
    static final int[] HITOS = { 3, 7, 10, 14, 21, 30, 50, 75, 100, 150, 200, 250, 300, 365, 500, 730, 1000 };
    static boolean esHito(int n) { for (int h : HITOS) if (h == n) return true; return n > 0 && n % 100 == 0; }
    /* {fondo, pose 3D, mensaje} al cumplir la meta; rota por día del año */
    static final String[][] CELEBRA = {
        { "fuego", "estrella", "¡Racha encendida!" }, { "cielo", "celebrar", "¡Meta de hoy cumplida!" }, { "noche", "estrella", "Hoy brillaste ✨" },
        { "verde", "pulgar", "¡Así se hace!" }, { "lila", "croissant", "Bien joué ! 🥐" }, { "atardecer", "celebrar", "Vive la racha !" },
        { "rosa", "corazones", "Manzana está orgulloso" } };

    /** días desde el último día con actividad (0 = hoy, -1 = nunca) */
    static int diasDesde(SharedPreferences p) {
        String u = p.getString("lastAny", ""); if (u.isEmpty()) u = p.getString("last", "");
        return hace(u);
    }

    static Estado estado(SharedPreferences p) {
        int n = streakNow(p), xp = xpToday(p), meta = Math.max(1, p.getInt("goal", 20)), h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean hecho = doneToday(p), hay = p.getBoolean("hay", false) || n > 0 || !p.getString("last", "").isEmpty();
        String racha = n == 1 ? "día de racha" : "días de racha";
        if (!p.getString("aviso", "").isEmpty()) return new Estado("mantenimiento", "gris", "pensativo", "Manzana está ordenando cosas. Vuelve en un ratito", n, racha);
        if (!hay) return new Estado("hola", "lila", "saludar", "¡Hola! Soy Manzana. Empecemos una racha", 0, "días de racha");
        if (hecho) {
            if (esHito(n)) return new Estado("hito", "oro", "trofeo", "¡" + n + " días de racha! 🏆", n, racha);
            String[] k = CELEBRA[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % CELEBRA.length];
            return new Estado("hecho", k[0], k[1], k[2], n, racha);
        }
        int falta = Math.max(0, meta - xp);
        if (n > 0) {
            if (xp > 0) return new Estado("casi", "cielo", "escribiendo", "Te faltan " + falta + " XP. ¡Ya casi!", n, racha);
            if (protegida(p)) return new Estado("protegida", "hielo", "pulgar", "🛡️ El protector salvó tu racha. ¡Hoy te toca!", n, racha);
            if (h < 12) return new Estado("tres", "amanecer", "croissant", "¿Tienes 3 minutos?", n, racha);
            if (h < 18) return new Estado("hora", "cielo", "senalar", "Hora de practicar", n, racha);
            if (h < 21) return new Estado("salva", "fuego", "reloj", "¡Salva tu racha!", n, racha);
            if (h < 23) return new Estado("tarde", "noche", "reloj", "¡Es tarde! Aún puedes salvarla", n, racha);
            return new Estado("ultima", "alerta", "sorpresa", "¡Última oportunidad!", n, racha);
        }
        int rec = recuperable(p);
        if (rec > 0) { Estado e = new Estado("recupera", "rosa", "asomandose", "¡Recupera tu racha de " + rec + "! Cumple la meta de hoy", rec, rec == 1 ? "día por recuperar" : "días por recuperar"); e.apagada = true; return e; }
        if (xp > 0) return new Estado("casi0", "verde", "escribiendo", "Te faltan " + falta + " XP para una racha nueva", 0, racha);
        int d = diasDesde(p);
        String sin = d == 1 ? "día sin practicar" : "días sin practicar";
        Estado e;
        if (d >= 2 && d <= 3) e = new Estado("dias", "gris", "pensativo", d + " días desde tu última lección", d, sin);
        else if (d >= 4 && d <= 7) { e = new Estado("ignora", "noche", "enojado", "¿Me estás ignorando? 👻", d, sin); e.fantasma = true; e.apagada = true; }
        else if (d >= 8 && d <= 20) e = new Estado("extrana", "lila", "triste", "Manzana te extraña", d, sin);
        else if (d > 20) { e = new Estado("congelada", "hielo", "dormir", "Tu racha se congeló. ¡Descongélala! ❄️", d, sin); e.apagada = true; }
        else if (h >= 22) e = new Estado("zzz", "noche", "dormir", "Zzz… ¿una lección antes de dormir?", 0, racha);
        else e = new Estado("empieza", "cielo", "senalar", "Empieza una lección", 0, racha);
        return e;
    }

    /** pose 3D para el ícono grande de cada aviso (Reminder) */
    static int poseAviso(Context c, String tipo) {
        String k;
        switch (tipo == null ? "" : tipo) {
            case "racha": k = "reloj"; break;
            case "ultima": k = "sorpresa"; break;
            case "protegida": k = "pulgar"; break;
            case "casi": k = "escribiendo"; break;
            case "recupera": k = "asomandose"; break;
            case "perdida": k = "triste"; break;
            case "extrana": k = "pensativo"; break;
            case "semana": k = "dormir"; break;
            default: k = "saludar";
        }
        return pose(c, k);
    }
    static int pose(Context c, String k) {
        int id = c.getResources().getIdentifier("w2_" + k, "drawable", c.getPackageName());
        return id != 0 ? id : R.drawable.w2_saludar;
    }

    /** abre la app; destino: "" (inicio), "practicar" (la siguiente lección), "racha", "palabra", "1v1", "avisos" */
    static PendingIntent abrir(Context c, String destino, int code) { return abrir(c, destino, code, null); }
    /** con aviso: el id del aviso de la bandeja, que se marca leído al abrirlo (MainActivity.leeAviso) */
    static PendingIntent abrir(Context c, String destino, int code, String aviso) {
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setAction("co.plexplay.app.ABRIR." + code);   // la acción distinta evita que Android junte los PendingIntent
        if ("practicar".equals(destino) || "leccion".equals(destino)) i.putExtra(MainActivity.EXTRA_PRACTICAR, true);
        else if (destino != null && !destino.isEmpty()) i.putExtra(MainActivity.EXTRA_DESTINO, destino);
        if (aviso != null) i.putExtra(MainActivity.EXTRA_AVISO, aviso);
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static PendingIntent open(Context c, boolean practicar, int code) { return abrir(c, practicar ? "practicar" : "", code); }

    /* ---- la semana: lunes a domingo; hecho (meta), hielo (protector), hoy pendiente, nada ---- */
    static final int[] DIAS = { R.id.w_d0, R.id.w_d1, R.id.w_d2, R.id.w_d3, R.id.w_d4, R.id.w_d5, R.id.w_d6 };
    static void semana(Context c, RemoteViews v, SharedPreferences p, boolean claro) {
        JSONObject dias;
        try { dias = new JSONObject(p.getString("dias", "{}")); } catch (Exception e) { dias = new JSONObject(); }
        Calendar t = Calendar.getInstance();
        int hoy = (t.get(Calendar.DAY_OF_WEEK) + 5) % 7;     // lunes = 0
        String suf = claro ? "_c" : "";
        for (int i = 0; i < 7; i++) {
            String k = day(i - hoy), st = dias.optString(k, "");
            if (i == hoy && doneToday(p)) st = "1";
            String bg = "1".equals(st) ? "hecho" : "f".equals(st) ? "hielo" : i == hoy ? "hoy" : "nada";
            int res = c.getResources().getIdentifier("w8_dia_" + bg + suf, "drawable", c.getPackageName());
            v.setInt(DIAS[i], "setBackgroundResource", res);
            boolean marcado = "1".equals(st) || "f".equals(st);
            v.setTextColor(DIAS[i], marcado ? (claro ? 0xFFFFFFFF : 0xFF1F2430) : (claro ? 0xFF5A6480 : 0xCCFFFFFF));
        }
    }
    static void pintarRacha(Context c, RemoteViews v, SharedPreferences p, Estado st, boolean done) {
        v.setInt(R.id.w_root, "setBackgroundResource", c.getResources().getIdentifier("wr_" + st.fondo, "drawable", c.getPackageName()));
        v.setImageViewResource(R.id.w_mz, pose(c, st.mz));
        v.setInt(R.id.w_mz, "setImageAlpha", st.fantasma ? 125 : 255);
        v.setTextViewText(R.id.w_msg, st.msg);
        v.setImageViewResource(R.id.w_fuego, st.apagada ? R.drawable.ic_flame_off : R.drawable.ic_flame);
        v.setTextViewText(R.id.w_num, String.valueOf(st.num));
        v.setTextViewText(R.id.w_lbl, st.lbl);
        int tinta = st.oscuro ? 0xFF3A2600 : 0xFFFFFFFF;
        v.setTextColor(R.id.w_num, tinta);
        v.setTextColor(R.id.w_lbl, st.oscuro ? 0xE63A2600 : 0xF2FFFFFF);
        semana(c, v, p, st.oscuro);   // en los fondos claros (oro, amanecer, hielo) la semana va en azul
        v.setOnClickPendingIntent(R.id.w_root, abrir(c, "recupera".equals(st.id) ? "racha" : done ? "" : "practicar", 21));
    }

    /* ---- cada widget se arma en su función: updateAll los manda al lanzador y la pantalla de pruebas (debug) los pinta ---- */
    static RemoteViews vistaRacha(Context c, SharedPreferences p, boolean ancho) {
        RemoteViews v = new RemoteViews(c.getPackageName(), ancho ? R.layout.widget_racha_ancho : R.layout.widget_racha);
        pintarRacha(c, v, p, estado(p), doneToday(p));
        return v;
    }
    static RemoteViews vistaDia(Context c, SharedPreferences p) {
        int streak = streakNow(p), xp = xpToday(p), goal = Math.max(1, p.getInt("goal", 20));
        boolean done = doneToday(p);
        String next = p.getString("next", ""), name = p.getString("name", "");
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_dia);
        v.setImageViewResource(R.id.w_fuego, done ? R.drawable.ic_flame : R.drawable.ic_flame_off);
        v.setImageViewResource(R.id.w_mz, pose(c, done ? "estrella" : xp > 0 ? "escribiendo" : streak > 0 ? "senalar" : "saludar"));
        v.setTextViewText(R.id.w_hola, name.isEmpty() ? "Bonjour !" : "Bonjour, " + name + " !");
        v.setTextViewText(R.id.w_num, String.valueOf(streak));
        v.setTextViewText(R.id.w_lbl, streak == 1 ? "día" : "días");
        v.setTextViewText(R.id.w_meta, done ? "✓ Meta de hoy cumplida" : "Meta de hoy · " + Math.min(xp, goal) + "/" + goal + " XP");
        v.setProgressBar(R.id.w_barra, goal, Math.min(xp, goal), false);
        semana(c, v, p, false);
        v.setTextViewText(R.id.w_btn, done ? "▶ Seguir" : "▶ Practicar");
        v.setTextViewText(R.id.w_next, next);
        v.setViewVisibility(R.id.w_next, next.isEmpty() ? View.GONE : View.VISIBLE);
        v.setOnClickPendingIntent(R.id.w_root, abrir(c, "", 22));
        v.setOnClickPendingIntent(R.id.w_btn, abrir(c, "practicar", 23));
        return v;
    }
    static RemoteViews vistaPalabra(Context c, SharedPreferences p) {
        String fr = p.getString("fr", ""), es = p.getString("es", ""), ex = p.getString("ex", "");
        if (fr.isEmpty()) { fr = "le quotidien"; es = "el día a día"; ex = ""; }
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_palabra);
        v.setTextViewText(R.id.w_fr, fr);
        v.setTextViewText(R.id.w_es, es);
        v.setTextViewText(R.id.w_ex, ex.isEmpty() ? "" : "«" + ex + "»");
        v.setViewVisibility(R.id.w_ex, ex.isEmpty() ? View.GONE : View.VISIBLE);
        v.setOnClickPendingIntent(R.id.w_root, abrir(c, "palabra", 24));
        return v;
    }
    static RemoteViews vistaLeccion(Context c, SharedPreferences p) {
        String next = p.getString("next", ""), course = p.getString("course", "");
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_leccion);
        v.setTextViewText(R.id.w_curso, course.isEmpty() ? "CONTINÚA TU CURSO" : course.toUpperCase(Locale.ROOT));
        v.setTextViewText(R.id.w_next, next.isEmpty() ? "Abre PLEX PLAY y elige tu curso" : next);
        v.setOnClickPendingIntent(R.id.w_root, abrir(c, "", 25));
        v.setOnClickPendingIntent(R.id.w_btn, abrir(c, next.isEmpty() ? "" : "practicar", 26));
        return v;
    }
    static RemoteViews vistaMeta(Context c, SharedPreferences p) {
        int xp = xpToday(p), goal = Math.max(1, p.getInt("goal", 20));
        boolean done = doneToday(p);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_meta);
        v.setImageViewResource(R.id.w_mz, pose(c, done ? "celebrar" : xp > 0 ? "escribiendo" : "lista"));
        v.setProgressBar(R.id.w_anillo, goal, Math.min(xp, goal), false);
        v.setViewVisibility(R.id.w_anillo, done ? View.GONE : View.VISIBLE);
        v.setViewVisibility(R.id.w_anillo_ok, done ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.w_xp, Math.min(xp, 9999) + " / " + goal + " XP");
        v.setTextViewText(R.id.w_est, done ? "✓ ¡Meta cumplida!" : xp > 0 ? "Te faltan " + Math.max(0, goal - xp) + " XP" : "¡A practicar!");
        v.setOnClickPendingIntent(R.id.w_root, abrir(c, done ? "" : "practicar", 27));
        return v;
    }
    static RemoteViews vistaDuelo(Context c, SharedPreferences p) {
        int copas = p.getInt("copas", 0), ganados = p.getInt("duelosG", 0);
        String rango = p.getString("rango", "");
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_duelo);
        v.setTextViewText(R.id.w_rango, rango.isEmpty() ? "Sin rango" : rango);
        v.setTextViewText(R.id.w_copas, "🏆 " + copas + (copas == 1 ? " copa" : " copas") + (ganados > 0 ? " · " + ganados + (ganados == 1 ? " victoria" : " victorias") : " · ¿tu primer duelo?"));
        v.setOnClickPendingIntent(R.id.w_root, abrir(c, "1v1", 28));
        v.setOnClickPendingIntent(R.id.w_btn, abrir(c, "1v1", 29));
        return v;
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        for (int id : m.getAppWidgetIds(new ComponentName(c, Widget.class))) {
            if (Build.VERSION.SDK_INT >= 31) {
                // Android 12+: el mismo widget cambia de diseño al estirarlo (a lo ancho, Manzana a un lado y la semana con letras)
                Map<SizeF, RemoteViews> mapa = new HashMap<>();
                mapa.put(new SizeF(100f, 100f), vistaRacha(c, p, false));
                mapa.put(new SizeF(220f, 100f), vistaRacha(c, p, true));
                m.updateAppWidget(id, new RemoteViews(mapa));
            } else m.updateAppWidget(id, vistaRacha(c, p, false));
        }
        for (int id : m.getAppWidgetIds(new ComponentName(c, Dia.class))) m.updateAppWidget(id, vistaDia(c, p));
        for (int id : m.getAppWidgetIds(new ComponentName(c, Palabra.class))) m.updateAppWidget(id, vistaPalabra(c, p));
        for (int id : m.getAppWidgetIds(new ComponentName(c, Leccion.class))) m.updateAppWidget(id, vistaLeccion(c, p));
        for (int id : m.getAppWidgetIds(new ComponentName(c, Meta.class))) m.updateAppWidget(id, vistaMeta(c, p));
        for (int id : m.getAppWidgetIds(new ComponentName(c, Duelo.class))) m.updateAppWidget(id, vistaDuelo(c, p));
    }
}
