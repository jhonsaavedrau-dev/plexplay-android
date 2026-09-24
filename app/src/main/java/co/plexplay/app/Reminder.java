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

import java.util.Calendar;

/** Recordatorio diario local (sin servidor): se programa a la hora elegida y sobrevive a reinicios. */
public class Reminder extends BroadcastReceiver {
    static final String PREFS = "plex", CH = "recordatorio";
    static final String[] MSG = {
        "Manzana te espera: 5 minutos de francés y tu racha sigue viva.",
        "Bonjour ! ¿Una lección rápida antes de dormir?",
        "Tu repaso del día está listo. On y va ?",
        "Pequeños pasos, gran francés. Tu lección de hoy te espera.",
        "Allez ! Mantén tu racha con un reto corto."
    };

    static void save(Context c, boolean on, int h, int m) {
        c.getSharedPreferences(PREFS, 0).edit().putBoolean("on", on).putInt("h", h).putInt("m", m).apply();
    }
    static String json(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        return "{\"on\":" + p.getBoolean("on", false) + ",\"h\":" + p.getInt("h", 19) + ",\"m\":" + p.getInt("m", 0) + "}";
    }
    static PendingIntent pi(Context c) {
        return PendingIntent.getBroadcast(c, 7, new Intent(c, Reminder.class).setAction("co.plexplay.app.RECORDATORIO"),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        am.cancel(pi(c));
        if (!p.getBoolean("on", false)) return;
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, p.getInt("h", 19)); t.set(Calendar.MINUTE, p.getInt("m", 0)); t.set(Calendar.SECOND, 0);
        if (t.getTimeInMillis() <= System.currentTimeMillis() + 60000) t.add(Calendar.DAY_OF_YEAR, 1);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi(c));
    }

    @Override public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()) || "android.intent.action.MY_PACKAGE_REPLACED".equals(i.getAction())) { schedule(c); return; }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null)
            nm.createNotificationChannel(new NotificationChannel(CH, "Recordatorio diario", NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE);
        String text = MSG[(int) ((System.currentTimeMillis() / 86400000L) % MSG.length)];
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notif).setContentTitle("PLEX PLAY").setContentText(text)
         .setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).setColor(0xFFD7263D);
        try { nm.notify(1, b.build()); } catch (SecurityException e) { /* sin permiso de notificaciones */ }
    }
}
