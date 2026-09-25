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
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Recordatorio diario local (sin servidor): se programa a la hora elegida y sobrevive a reinicios.
 * Si el estudiante ya cumplió su meta hoy, no molesta. Si tiene racha, avisa que está en peligro,
 * y a las 21:30 manda un último aviso si todavía no ha practicado.
 */
public class Reminder extends BroadcastReceiver {
    static final String PREFS = "plex", CH = "recordatorio", ACT = "co.plexplay.app.RECORDATORIO", ACT_LAST = "co.plexplay.app.ULTIMO_AVISO";
    static final String[] MSG = {
        "Manzana te espera: 5 minutos de francés y listo.",
        "Bonjour ! ¿Una lección rápida antes de dormir?",
        "Tu repaso del día está listo. On y va ?",
        "Pequeños pasos, gran francés. Tu lección de hoy te espera.",
        "Allez ! Empieza una racha nueva con un reto corto."
    };

    static void save(Context c, boolean on, int h, int m) {
        c.getSharedPreferences(PREFS, 0).edit().putBoolean("on", on).putInt("h", h).putInt("m", m).putBoolean("set", true).apply();
    }
    static void progress(Context c, int streak, String lastDay, String name) {
        c.getSharedPreferences(PREFS, 0).edit().putInt("streak", streak).putString("last", lastDay == null ? "" : lastDay)
            .putString("name", name == null ? "" : name).apply();
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
        am.cancel(pi(c, ACT, 7)); am.cancel(pi(c, ACT_LAST, 8));
        if (!p.getBoolean("on", false)) return;
        int h = p.getInt("h", 19), m = p.getInt("m", 0);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next(h, m), AlarmManager.INTERVAL_DAY, pi(c, ACT, 7));
        // último aviso de la noche, solo si el recordatorio principal es antes de las 21:00
        if (h < 21) am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next(21, 30), AlarmManager.INTERVAL_DAY, pi(c, ACT_LAST, 8));
    }
    static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime()); }

    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || "android.intent.action.MY_PACKAGE_REPLACED".equals(a)) { schedule(c); return; }
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        if (!p.getBoolean("on", false)) return;
        // ya practicó hoy: no hace falta recordarle nada
        if (today().equals(p.getString("last", ""))) return;
        int streak = p.getInt("streak", 0);
        boolean last = ACT_LAST.equals(a);
        if (last && streak < 1) return;   // el aviso nocturno es solo para proteger rachas
        String name = p.getString("name", "");
        String title, text;
        if (streak >= 1) {
            title = "🔥 Tu racha de " + streak + (streak == 1 ? " día" : " días") + " está en peligro";
            text = last ? "¡Última oportunidad! Una lección corta antes de medianoche y tu racha sigue viva."
                        : (name.isEmpty() ? "" : name + ", ") + "Manzana te espera: 5 minutos de francés y la salvas.";
        } else {
            title = "PLEX PLAY";
            text = MSG[(int) ((System.currentTimeMillis() / 86400000L) % MSG.length)];
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null)
            nm.createNotificationChannel(new NotificationChannel(CH, "Recordatorio diario", NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(text)
         .setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).setColor(0xFFD7263D);
        try { nm.notify(1, b.build()); } catch (SecurityException e) { /* sin permiso de notificaciones */ }
    }
}
