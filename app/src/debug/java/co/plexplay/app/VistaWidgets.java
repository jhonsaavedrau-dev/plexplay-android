package co.plexplay.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.RemoteViews;

import org.json.JSONObject;

/**
 * Solo en la versión de pruebas: pinta todos los widgets con los datos de un escenario, a tamaño de celular, para
 * revisarlos con capturas (adb shell am start -n co.plexplay.app.prueba/co.plexplay.app.VistaWidgets -e e racha).
 * Escenarios: racha (en peligro, por la tarde), hecho (meta cumplida), protegida (protector), recupera, nuevo, ignora.
 * Usa unas preferencias aparte («plex-vista») para no tocar las de verdad.
 */
public class VistaWidgets extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String e = getIntent().getStringExtra("e");
        SharedPreferences p = getSharedPreferences("plex-vista", 0);
        datos(p, e == null ? "racha" : e);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(16), dp(16), dp(16));
        col.setBackgroundColor(0xFF3A4A6B);
        TextView t = new TextView(this); t.setText("Escenario: " + (e == null ? "racha" : e)); t.setTextColor(0xFFFFFFFF); col.addView(t);
        LinearLayout fila = fila(col);
        caja(fila, Widget.vistaRacha(this, p, false), 168, 168);
        caja(fila, Widget.vistaMeta(this, p), 168, 168);
        caja(col, Widget.vistaRacha(this, p, true), 0, 168);
        caja(col, Widget.vistaDia(this, p), 0, 180);
        caja(col, Widget.vistaPalabra(this, p), 0, 96);
        caja(col, Widget.vistaLeccion(this, p), 0, 80);
        caja(col, Widget.vistaDuelo(this, p), 0, 168);
        ScrollView sv = new ScrollView(this); sv.addView(col);
        setContentView(sv);
    }
    int dp(int v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }
    LinearLayout fila(LinearLayout col) { LinearLayout f = new LinearLayout(this); f.setOrientation(LinearLayout.HORIZONTAL); col.addView(f); return f; }
    void caja(LinearLayout padre, RemoteViews rv, int w, int h) {
        FrameLayout f = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w == 0 ? LinearLayout.LayoutParams.MATCH_PARENT : dp(w), dp(h));
        lp.setMargins(0, dp(10), dp(10), 0);
        padre.addView(f, lp);
        View v = rv.apply(this, f);
        v.setClipToOutline(true);
        f.addView(v);
    }
    static void datos(SharedPreferences p, String e) {
        String hoy = Widget.day(0), ayer = Widget.day(-1), antes = Widget.day(-2);
        SharedPreferences.Editor ed = p.edit().clear()
            .putString("name", "Jhon").putInt("goal", 20).putString("course", "Francés B1.1")
            .putString("next", "Lección 7 · Le passé composé").putBoolean("hay", true)
            .putString("fr", "la flânerie").putString("es", "el paseo sin rumbo").putString("ex", "J'adore la flânerie au bord de la Seine.")
            .putInt("copas", 340).putString("rango", "🥈 Plata II").putInt("duelosG", 12);
        try {
            JSONObject d = new JSONObject();
            for (int i = 1; i <= 9; i++) d.put(Widget.day(-i), "1");
            switch (e) {
                case "hecho": ed.putInt("streak", 12).putString("last", hoy).putString("lastOk", hoy).putInt("xp", 26).putString("xpDay", hoy).putString("lastAny", hoy); d.put(hoy, "1"); break;
                case "protegida": d.remove(ayer); d.put(ayer, "f"); ed.putInt("streak", 9).putString("last", antes).putString("lastOk", ayer).putInt("freeze", 0).putString("lastAny", antes); break;
                case "recupera": d = new JSONObject(); ed.putInt("streak", 0).putString("last", Widget.day(-2)).putString("lastOk", Widget.day(-2)).putInt("recN", 15).putString("recHasta", Widget.day(1)).putString("lastAny", Widget.day(-2)); break;
                case "nuevo": d = new JSONObject(); ed.putBoolean("hay", false).putInt("streak", 0).putInt("copas", 0).putString("rango", "").putInt("duelosG", 0); break;
                case "ignora": d = new JSONObject(); ed.putInt("streak", 0).putString("last", Widget.day(-5)).putString("lastAny", Widget.day(-5)); break;
                default: ed.putInt("streak", 9).putString("last", ayer).putString("lastOk", ayer).putInt("xp", 8).putString("xpDay", hoy).putString("lastAny", hoy);
            }
            ed.putString("dias", d.toString());
        } catch (Exception ex) { /* nada */ }
        ed.commit();
    }
}
