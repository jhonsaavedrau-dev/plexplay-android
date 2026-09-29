package co.plexplay.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Avisos de Manzana, al estilo de las apps de idiomas (sin servidor, todo en el teléfono):
 *  - a la hora elegida, si todavía no cumplió la meta de hoy;
 *  - el mensaje cambia según la situación: racha en peligro, le falta poco para la meta, perdió la racha,
 *    lleva días sin practicar (2-3, 4-6, 7+) o un día normal (con la palabra del día);
 *  - a las 21:30, «última oportunidad» solo si hay una racha que salvar;
 *  - botones «Practicar» (abre la siguiente lección) y «En 1 hora» (lo vuelve a recordar);
 *  - tras una semana sin abrir la app, un último aviso y se calla hasta que vuelva (sin insistir).
 * Si ya cumplió la meta hoy, no molesta. Sobrevive a reinicios y actualizaciones.
 */
public class Reminder extends BroadcastReceiver {
    static final String PREFS = "plex", CH = "recordatorio",
        ACT = "co.plexplay.app.RECORDATORIO", ACT_LAST = "co.plexplay.app.ULTIMO_AVISO",
        ACT_SNOOZE = "co.plexplay.app.EN_UNA_HORA", ACT_AGAIN = "co.plexplay.app.OTRA_VEZ",
        ACT_MIDNIGHT = "co.plexplay.app.MEDIANOCHE";

    /* {título, texto}; {n} = racha, {x} = XP que faltan, {name} = «Nombre, » o nada */
    static final String[][] RACHA = {
        {"🔥 Tu racha de {n} está en peligro", "{name}Manzana te espera: 5 minutos de francés y la salvas."},
        {"😿 Manzana está preocupada", "Tu racha de {n} se apaga a medianoche. Una lección corta y listo."},
        {"🔥 {n} seguidos… ¿los dejamos ir?", "No hoy. Un reto rápido y tu racha sigue viva."},
        {"⏳ Tic, tac, tu racha", "{name}una lección y mañana serán {n1} días. On y va ?"},
        {"🥐 Pausa de francés", "Manzana preparó tu lección del día. Tu racha de {n} te lo agradecerá."}
    };
    static final String[][] ULTIMA = {
        {"🚨 Última oportunidad", "Tu racha de {n} termina a medianoche. ¡Todavía estás a tiempo!"},
        {"🌙 Manzana no se va a dormir", "…hasta que salves tu racha de {n}. Dos minutos bastan."},
        {"😾 ¿En serio?", "{n} de racha y la vas a perder por no abrir la app. Vamos, una lección."}
    };
    static final String[][] CASI = {
        {"🎯 ¡Casi llegas!", "Te faltan {x} XP para tu meta de hoy. Un reto más y listo."},
        {"💪 Ya empezaste, termina", "Solo {x} XP para cumplir la meta. Manzana cree en ti."}
    };
    static final String[][] PERDIDA = {
        {"💔 Se apagó tu racha", "Pasa en las mejores familias. Empieza una nueva hoy: Manzana te acompaña."},
        {"🌱 Día 1 otra vez", "Las rachas largas empiezan con un solo día. ¿Hoy?"}
    };
    static final String[][] EXTRANA = {
        {"🐱 Manzana te extraña", "{name}hace unos días que no practicas. ¿Un repaso rapidito?"},
        {"👋 Coucou !", "Tu francés te está esperando justo donde lo dejaste."},
        {"📚 Tu lección sigue aquí", "5 minutos hoy valen más que una hora el domingo."}
    };
    static final String[][] SEMANA = {
        {"🥺 ¿Todo bien?", "Manzana no te ha visto en toda la semana. Vuelve cuando quieras, sin presión."},
        {"🐾 Manzana sigue aquí", "Olvidar un idioma es fácil; recordarlo también. Una lección y vuelves al ritmo."}
    };
    static final String[] DIA = {
        "Bonjour ! ¿Una lección rápida?",
        "Tu repaso del día está listo. On y va ?",
        "Pequeños pasos, gran francés. Tu lección de hoy te espera.",
        "Allez ! Un reto corto y cumples la meta.",
        "5 minutos de francés y Manzana queda feliz."
    };

    static void save(Context c, boolean on, int h, int m) {
        c.getSharedPreferences(PREFS, 0).edit().putBoolean("on", on).putInt("h", h).putInt("m", m).putBoolean("set", true).apply();
    }
    static void progress(Context c, int streak, String lastDay, String name) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, 0).edit().putInt("streak", streak).putString("name", name == null ? "" : name);
        if (lastDay != null && !lastDay.isEmpty()) e.putString("last", lastDay);
        e.apply();
        Widget.updateAll(c);
    }
    static String json(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        return "{\"on\":" + p.getBoolean("on", false) + ",\"set\":" + p.getBoolean("set", false) + ",\"h\":" + p.getInt("h", 19) + ",\"m\":" + p.getInt("m", 0) + "}";
    }
    static PendingIntent pi(Context c, String action, int code) {
        return PendingIntent.getBroadcast(c, code, new Intent(c, Reminder.class).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static long next(int h, int m) {
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, h); t.set(Calendar.MINUTE, m); t.set(Calendar.SECOND, 0); t.set(Calendar.MILLISECOND, 0);
        if (t.getTimeInMillis() <= System.currentTimeMillis() + 60000) t.add(Calendar.DAY_OF_YEAR, 1);
        return t.getTimeInMillis();
    }
    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        am.cancel(pi(c, ACT, 7)); am.cancel(pi(c, ACT_LAST, 8)); am.cancel(pi(c, ACT_MIDNIGHT, 10));
        // el widget cambia de día a medianoche (la llama se apaga si no has practicado)
        am.setInexactRepeating(AlarmManager.RTC, next(0, 1), AlarmManager.INTERVAL_DAY, pi(c, ACT_MIDNIGHT, 10));
        if (!p.getBoolean("on", false)) return;
        int h = p.getInt("h", 19), m = p.getInt("m", 0);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next(h, m), AlarmManager.INTERVAL_DAY, pi(c, ACT, 7));
        // último aviso de la noche, solo si el recordatorio principal es antes de las 21:00
        if (h < 21) am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next(21, 30), AlarmManager.INTERVAL_DAY, pi(c, ACT_LAST, 8));
    }
    static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime()); }

    /** días desde la última práctica (con algo de XP); -1 si no se sabe */
    static int daysAway(SharedPreferences p) {
        String last = p.getString("lastAny", "");
        if (last.isEmpty()) last = p.getString("last", "");
        if (last.isEmpty()) return -1;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            long a = f.parse(last).getTime(), b = f.parse(today()).getTime();
            return (int) Math.round((b - a) / 86400000.0);
        } catch (Exception e) { return -1; }
    }

    static String[] pick(String[][] pool, int salt) {
        return pool[(int) (((System.currentTimeMillis() / 86400000L) + salt) % pool.length)];
    }
    static String fill(String s, int streak, int falta, String name) {
        return s.replace("{n1}", String.valueOf(streak + 1))
                .replace("{n}", streak + (streak == 1 ? " día" : " días"))
                .replace("{x}", String.valueOf(falta))
                .replace("{name}", name.isEmpty() ? "" : name + ", ");
    }

    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || "android.intent.action.MY_PACKAGE_REPLACED".equals(a)) { schedule(c); Widget.updateAll(c); return; }
        if (ACT_MIDNIGHT.equals(a)) { Widget.updateAll(c); return; }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (ACT_SNOOZE.equals(a)) {
            nm.cancel(1);
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 3600000L, pi(c, ACT_AGAIN, 9));
            return;
        }
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        if (!p.getBoolean("on", false)) return;
        // ya cumplió la meta hoy: no hace falta recordarle nada
        if (today().equals(p.getString("last", ""))) return;
        if (p.getBoolean("paused", false)) return;   // se calló tras una semana; vuelve cuando abra la app

        int streak = Widget.streakNow(p), away = daysAway(p), xp = Widget.xpToday(p), goal = Math.max(1, p.getInt("goal", 20));
        boolean last = ACT_LAST.equals(a);
        if (last && streak < 1) return;   // el aviso nocturno es solo para proteger rachas
        String name = p.getString("name", "");
        String[] msg;
        if (last) msg = pick(ULTIMA, 0);
        else if (xp > 0) msg = pick(CASI, 0);
        else if (streak >= 1) msg = pick(RACHA, 0);
        else if (away >= 7) { msg = pick(SEMANA, 0); p.edit().putBoolean("paused", true).apply(); }
        else if (away >= 2) msg = pick(EXTRANA, 0);
        else if (away == 1 || p.getInt("streak", 0) > 0) msg = pick(PERDIDA, 0);
        else {
            String fr = p.getString("fr", ""), es = p.getString("es", "");
            msg = fr.isEmpty() ? new String[]{"PLEX PLAY", DIA[(int) ((System.currentTimeMillis() / 86400000L) % DIA.length)]}
                               : new String[]{"📖 Palabra del día: " + fr, es + " · " + DIA[(int) ((System.currentTimeMillis() / 86400000L) % DIA.length)]};
        }
        String title = fill(msg[0], streak, Math.max(1, goal - xp), name), text = fill(msg[1], streak, Math.max(1, goal - xp), name);

        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null)
            nm.createNotificationChannel(new NotificationChannel(CH, "Recordatorio diario", NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(text)
         .setStyle(new Notification.BigTextStyle().bigText(text))
         .setContentIntent(Widget.open(c, false, 30)).setAutoCancel(true).setColor(0xFFD7263D);
        Bitmap manzana = BitmapFactory.decodeResource(c.getResources(), R.mipmap.ic_launcher_fg);
        if (manzana != null) b.setLargeIcon(manzana);
        b.addAction(new Notification.Action.Builder(null, "▶ Practicar", Widget.open(c, true, 31)).build());
        if (!last) b.addAction(new Notification.Action.Builder(null, "⏰ En 1 hora", pi(c, ACT_SNOOZE, 32)).build());
        try { nm.notify(1, b.build()); } catch (SecurityException e) { /* sin permiso de notificaciones */ }
    }
}
