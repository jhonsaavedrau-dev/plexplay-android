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

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Avisos de Manzana, al estilo de las apps de idiomas (sin servidor, todo en el teléfono):
 *  - a la hora elegida, si todavía no cumplió la meta de hoy;
 *  - el mensaje cambia según la situación: racha en peligro, racha salvada por el protector, le falta poco para la meta,
 *    racha que todavía se puede recuperar, racha perdida, días sin practicar (2-6, 7+) o un día normal (palabra del día);
 *  - a las 21:30, «última oportunidad» solo si hay una racha que salvar (canal «Racha en peligro», más insistente);
 *  - botones «Practicar» (abre la siguiente lección, o la pantalla de la racha) y «En 1 hora» (lo vuelve a recordar);
 *  - tras una semana sin abrir la app, un último aviso y se calla hasta que vuelva (sin insistir).
 * Si ya cumplió la meta hoy, no molesta. Sobrevive a reinicios y actualizaciones.
 *
 * 1.8 (15-oct-2026): antes las alarmas eran «inexactas repetidas», que Android puede retrasar horas (llegaban tarde o
 * se juntaban con la del día siguiente). Ahora cada alarma es de una sola vez con setAndAllowWhileIdle (llega en su
 * minuto aunque el teléfono esté en reposo) y se vuelve a programar al sonar. Cada aviso queda en una bandeja
 * («avisos», JSON) que la página muestra con leído/no leído; al abrir la app el aviso del sistema se retira.
 */
public class Reminder extends BroadcastReceiver {
    static final String PREFS = "plex", CH = "recordatorio", CH_RACHA = "racha",
        ACT = "co.plexplay.app.RECORDATORIO", ACT_LAST = "co.plexplay.app.ULTIMO_AVISO",
        ACT_SNOOZE = "co.plexplay.app.EN_UNA_HORA", ACT_AGAIN = "co.plexplay.app.OTRA_VEZ",
        ACT_MIDNIGHT = "co.plexplay.app.MEDIANOCHE", ACT_TEST = "co.plexplay.app.PRUEBA";
    static final int NOTIF = 1, MAX_AVISOS = 30;

    /* {título, texto}; {n} = racha, {n1} = racha de mañana, {x} = XP que faltan, {name} = «Nombre, » o nada */
    static final String[][] RACHA = {
        {"🔥 Tu racha de {n} está en peligro", "{name}Manzana te espera: 5 minutos de francés y la salvas."},
        {"😿 Manzana está preocupado", "Tu racha de {n} se apaga a medianoche. Una lección corta y listo."},
        {"🔥 {n} seguidos… ¿los dejamos ir?", "No hoy. Un reto rápido y tu racha sigue viva."},
        {"⏳ Tic, tac, tu racha", "{name}una lección y mañana serán {n1}. On y va ?"},
        {"🥐 Pausa de francés", "Manzana preparó tu lección del día. Tu racha de {n} te lo agradecerá."}
    };
    static final String[][] PROTEGIDA = {
        {"🛡️ Tu protector salvó tu racha", "Ayer no practicaste y tu racha de {n} sigue viva. Hoy no hay otro: una lección y la mantienes."},
        {"🛡️ Racha a salvo… por ahora", "{name}el protector cubrió el día que faltaste. Practica hoy para no perder tus {n}."}
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
    static final String[][] RECUPERA = {
        {"🩹 Tu racha de {r} se puede recuperar", "{name}cumple la meta de hoy y Manzana la repara. Solo por {h}."},
        {"💔→🔥 ¿Recuperamos tu racha?", "Perdiste {r} por poco. Hoy todavía puedes repararla: entra y mira cómo."}
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

    /** lo que se va a avisar: tipo (para la bandeja y las pruebas), textos, a dónde lleva y por qué canal */
    static class Aviso {
        String tipo, titulo, texto, destino, canal;
        Aviso(String tipo, String[] msg, String destino, String canal) { this.tipo = tipo; titulo = msg[0]; texto = msg[1]; this.destino = destino; this.canal = canal; }
    }

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
        return "{\"on\":" + p.getBoolean("on", false) + ",\"set\":" + p.getBoolean("set", false) + ",\"h\":" + p.getInt("h", 19) + ",\"m\":" + p.getInt("m", 0)
            + ",\"proximo\":" + p.getLong("proximo", 0) + ",\"ultimo\":" + p.getLong("ultimoAviso", 0) + "}";
    }
    static PendingIntent pi(Context c, String action, int code) {
        return PendingIntent.getBroadcast(c, code, new Intent(c, Reminder.class).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static long next(int h, int m) {
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, h); t.set(Calendar.MINUTE, m); t.set(Calendar.SECOND, 0); t.set(Calendar.MILLISECOND, 0);
        if (t.getTimeInMillis() <= System.currentTimeMillis() + 30000) t.add(Calendar.DAY_OF_YEAR, 1);
        return t.getTimeInMillis();
    }
    /** una sola vez y a su hora. Probado en el emulador (dumpsys alarm): setAndAllowWhileIdle a 15 h de distancia
     *  queda con una ventana de 1 hora (el de las 21:30 podía llegar a las 22:30). Ahora: exacta si el teléfono deja
     *  (permiso «Alarmas y recordatorios»); si no, con una ventana de 10 minutos, que Android sí respeta sin permiso. */
    static void alarma(AlarmManager am, int tipo, long t, PendingIntent p) {
        boolean exacta = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exacta && Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(tipo, t, p);
        else if (Build.VERSION.SDK_INT >= 19) am.setWindow(tipo, t, 10 * 60000L, p);
        else am.set(tipo, t, p);
    }

    static void canales(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CH) == null) {
            NotificationChannel ch = new NotificationChannel(CH, "Recordatorio diario", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("A la hora que elijas, si todavía no practicaste ese día.");
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(CH_RACHA) == null) {
            NotificationChannel ch = new NotificationChannel(CH_RACHA, "Racha en peligro", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Cuando tu racha se puede perder hoy, y la última oportunidad de la noche.");
            nm.createNotificationChannel(ch);
        }
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        am.cancel(pi(c, ACT, 7)); am.cancel(pi(c, ACT_LAST, 8)); am.cancel(pi(c, ACT_MIDNIGHT, 10));
        canales(c);
        // el widget cambia de día a medianoche (la llama se apaga si no has practicado); no despierta al teléfono
        alarma(am, AlarmManager.RTC, next(0, 1), pi(c, ACT_MIDNIGHT, 10));
        if (!p.getBoolean("on", false)) { p.edit().putLong("proximo", 0).apply(); return; }
        int h = p.getInt("h", 19), m = p.getInt("m", 0);
        long t = next(h, m);
        alarma(am, AlarmManager.RTC_WAKEUP, t, pi(c, ACT, 7));
        // último aviso de la noche, solo si el recordatorio principal es antes de las 21:00
        if (h < 21) alarma(am, AlarmManager.RTC_WAKEUP, next(21, 30), pi(c, ACT_LAST, 8));
        p.edit().putLong("proximo", t).apply();
    }
    static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime()); }

    static String[] pick(String[][] pool, int salt) {
        return pool[(int) (((System.currentTimeMillis() / 86400000L) + salt) % pool.length)];
    }
    static String fill(String s, int streak, int falta, String name, int rota, String hasta) {
        return s.replace("{n1}", (streak + 1) + " días")
                .replace("{n}", streak + (streak == 1 ? " día" : " días"))
                .replace("{r}", rota + (rota == 1 ? " día" : " días"))
                .replace("{h}", hasta)
                .replace("{x}", String.valueOf(falta))
                .replace("{name}", name.isEmpty() ? "" : name + ", ");
    }

    /** qué aviso toca ahora (null = ninguno). Es la misma regla para el recordatorio, el de la noche y «En 1 hora». */
    static Aviso elegir(SharedPreferences p, boolean noche) {
        if (!p.getBoolean("on", false)) return null;
        if (Widget.doneToday(p)) return null;                 // ya cumplió la meta hoy: no hace falta recordarle nada
        if (p.getBoolean("paused", false)) return null;       // se calló tras una semana; vuelve cuando abra la app
        int streak = Widget.streakNow(p), away = Widget.diasDesde(p), xp = Widget.xpToday(p);
        // lo que la persona eligió en Ajustes de avisos (plx117): racha en peligro, recuperar la racha, volver a estudiar
        boolean avRacha = p.getBoolean("avRacha", true), avRec = p.getBoolean("avRec", true), avVolver = p.getBoolean("avVolver", true);
        if (noche) return streak >= 1 && avRacha ? new Aviso("ultima", pick(ULTIMA, 0), "leccion", CH_RACHA) : null;
        if (xp > 0) return new Aviso("casi", pick(CASI, 0), "leccion", streak >= 1 ? CH_RACHA : CH);
        if (streak >= 1 && avRacha) return Widget.protegida(p) ? new Aviso("protegida", pick(PROTEGIDA, 0), "leccion", CH_RACHA)
                                                    : new Aviso("racha", pick(RACHA, 0), "leccion", CH_RACHA);
        if (streak < 1 && Widget.recuperable(p) > 0 && avRec) return new Aviso("recupera", pick(RECUPERA, 0), "racha", CH_RACHA);
        if (away >= 7) return !avVolver ? null : new Aviso("semana", pick(SEMANA, 0), "leccion", CH);
        if (away >= 2) return !avVolver ? null : new Aviso("extrana", pick(EXTRANA, 0), "leccion", CH);
        if (p.getInt("streak", 0) > 0 || away == 1) return new Aviso("perdida", pick(PERDIDA, 0), "leccion", CH);
        String fr = p.getString("fr", ""), es = p.getString("es", ""), dia = DIA[(int) ((System.currentTimeMillis() / 86400000L) % DIA.length)];
        return fr.isEmpty() ? new Aviso("dia", new String[]{"PLEX PLAY", dia}, "leccion", CH)
                            : new Aviso("dia", new String[]{"📖 Palabra del día: " + fr, es + " · " + dia}, "palabra", CH);
    }

    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || "android.intent.action.MY_PACKAGE_REPLACED".equals(a)
            || Intent.ACTION_TIME_CHANGED.equals(a) || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) { schedule(c); Widget.updateAll(c); return; }
        if (ACT_MIDNIGHT.equals(a)) { schedule(c); Widget.updateAll(c); return; }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (ACT_SNOOZE.equals(a)) {
            nm.cancel(NOTIF);
            alarma((AlarmManager) c.getSystemService(Context.ALARM_SERVICE), AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 3600000L, pi(c, ACT_AGAIN, 9));
            return;
        }
        // la alarma diaria es de una sola vez: se deja programada la de mañana antes de cualquier otra cosa
        if (ACT.equals(a) || ACT_LAST.equals(a)) schedule(c);
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        boolean noche = ACT_LAST.equals(a);
        Aviso av;
        if (ACT_TEST.equals(a)) {
            // solo en la versión de pruebas (MainActivity.esPrueba): fuerza un tipo concreto para verlo en pantalla
            String tipo = i.getStringExtra("tipo");
            av = elegir(p, "ultima".equals(tipo));
            if (av == null) av = new Aviso("dia", new String[]{"PLEX PLAY", DIA[0]}, "leccion", CH);
        } else av = elegir(p, noche);
        if (av == null) return;
        if ("semana".equals(av.tipo)) p.edit().putBoolean("paused", true).apply();
        mostrar(c, nm, p, av, !noche);
    }

    static void mostrar(Context c, NotificationManager nm, SharedPreferences p, Aviso av, boolean snooze) {
        int streak = Widget.streakNow(p), goal = Math.max(1, p.getInt("goal", 20)), falta = Math.max(1, goal - Widget.xpToday(p));
        int rota = Widget.recuperable(p);
        String name = p.getString("name", ""), hasta = Widget.recuperaHasta(p);
        String title = fill(av.titulo, streak, falta, name, rota, hasta), text = fill(av.texto, streak, falta, name, rota, hasta);
        canales(c);
        // en la bandeja primero: su id viaja en el toque, así abrir el aviso lo marca leído
        String id = guardar(c, av.tipo, title, text, av.destino);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, av.canal) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(text)
         .setStyle(new Notification.BigTextStyle().bigText(text))
         .setContentIntent(Widget.abrir(c, av.destino, 30, id)).setAutoCancel(true).setColor(0xFF2F6BFF)
         .setCategory(Notification.CATEGORY_REMINDER).setShowWhen(true).setWhen(System.currentTimeMillis());
        if (Build.VERSION.SDK_INT < 26) b.setPriority(CH_RACHA.equals(av.canal) ? Notification.PRIORITY_HIGH : Notification.PRIORITY_DEFAULT);
        Bitmap manzana = BitmapFactory.decodeResource(c.getResources(), Widget.poseAviso(c, av.tipo));
        if (manzana != null) b.setLargeIcon(manzana);
        String btn = "racha".equals(av.destino) ? "🩹 Recuperar" : "▶ Practicar";
        b.addAction(new Notification.Action.Builder(null, btn, Widget.abrir(c, "racha".equals(av.destino) ? "racha" : "practicar", 31, id)).build());
        if (snooze) b.addAction(new Notification.Action.Builder(null, "⏰ En 1 hora", pi(c, ACT_SNOOZE, 32)).build());
        try { nm.notify(NOTIF, b.build()); } catch (SecurityException e) { /* sin permiso de notificaciones */ }
    }

    /* ---------- bandeja: lo que se avisó, para mostrarlo en la app con leído/no leído ---------- */
    static synchronized String guardar(Context c, String tipo, String ti, String tx, String destino) {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        long t = System.currentTimeMillis();
        try {
            JSONArray viejo = new JSONArray(p.getString("avisos", "[]")), nuevo = new JSONArray();
            nuevo.put(new JSONObject().put("id", "a" + t).put("t", t).put("tipo", tipo).put("ti", ti).put("tx", tx).put("d", destino).put("leido", false));
            for (int k = 0; k < viejo.length() && nuevo.length() < MAX_AVISOS; k++) nuevo.put(viejo.get(k));
            p.edit().putString("avisos", nuevo.toString()).putLong("ultimoAviso", t).apply();
        } catch (Exception e) { /* bandeja dañada: se empieza de nuevo */ p.edit().putString("avisos", "[]").apply(); }
        return "a" + t;
    }
    static String avisos(Context c) { return c.getSharedPreferences(PREFS, 0).getString("avisos", "[]"); }
    /** id = uno; "*" = todos */
    static synchronized void leido(Context c, String id) {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        try {
            JSONArray a = new JSONArray(p.getString("avisos", "[]"));
            for (int k = 0; k < a.length(); k++) { JSONObject o = a.getJSONObject(k); if ("*".equals(id) || id.equals(o.optString("id"))) o.put("leido", true); }
            p.edit().putString("avisos", a.toString()).apply();
        } catch (Exception e) { /* nada */ }
    }
}
