package co.plexplay.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

/**
 * PLEX PLAY: la app web (PWA) dentro de una WebView.
 * Puente «PlexAndroid» para la voz (micrófono) y el recordatorio diario.
 *
 * Micrófono, en orden:
 *  1. permiso RECORD_AUDIO (se pide con explicación; si está bloqueado, se ofrecen los Ajustes);
 *  2. SpeechRecognizer (reconocimiento dentro de la app);
 *  3. si falla o no existe: la ventana de voz de Google (RecognizerIntent), que funciona en casi todos los teléfonos.
 */
public class MainActivity extends Activity {
    /* la app vive en plexplay.app (Cloudflare Pages); la dirección vieja de GitHub Pages sigue sirviendo lo mismo */
    static final String HOST = "plexplay.app";
    static final String HOST_VIEJO = "jhonsaavedrau-dev.github.io";
    static final String HOME = "https://" + HOST + "/app/?src=android";
    static final int REQ_MIC = 11, REQ_NOTIF = 12, REQ_SPEECH = 13, REQ_MIC_ONLY = 14;
    /** abierta desde «Practicar» (notificación o widget): al cargar, la página abre la siguiente lección */
    static final String EXTRA_PRACTICAR = "practicar";
    /** 1.8: abierta desde un aviso o un widget que lleva a una pantalla concreta: racha, palabra, 1v1, avisos */
    static final String EXTRA_DESTINO = "destino";
    String destinoPendiente = null;
    boolean practicarPendiente = false;
    WebView web;
    SpeechRecognizer rec;
    String pendingLang = null;
    PermissionRequest pendingWebPerm = null;
    boolean gotResult = false;
    ArrayList<String> lastPartial = null;
    long lastRms = 0;
    /** voz francesa del teléfono: suena lo que no tiene mp3 (la WebView no trae voces para speechSynthesis) */
    TextToSpeech tts;
    boolean ttsOk = false;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0F2A5C);
        /* rendimiento (4.47): el motor de la página es lo más importante de la app; que Android no lo trate como un
           proceso de fondo cuando hay poca memoria */
        if (Build.VERSION.SDK_INT >= 26) web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " PlexPlayAndroid/7");
        CookieManager.getInstance().setAcceptCookie(true);
        web.addJavascriptInterface(new Bridge(), "PlexAndroid");
        web.setWebChromeClient(new WebChromeClient() {
            // micrófono pedido por la página (getUserMedia): se concede si la app ya tiene el permiso
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean wantsAudio = false;
                    for (String r : request.getResources()) if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) wantsAudio = true;
                    if (!wantsAudio) { request.deny(); return; }
                    if (hasMic()) request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                    else { pendingWebPerm = request; askMic(REQ_MIC_ONLY); }
                });
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                /* entrar.html (acceso con Google desde la página de PLEX PLAY) se abre en el navegador: Google no deja entrar en un WebView */
                boolean entrar = u.getPath() != null && u.getPath().endsWith("/entrar.html");
                if ("https".equals(u.getScheme()) && (HOST.equals(u.getHost()) || HOST_VIEJO.equals(u.getHost())) && !entrar) return false;
                if (esPrueba() && "10.0.2.2".equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { /* sin app para abrirlo */ }
                return true;
            }
            @Override public void onPageFinished(WebView v, String url) {
                boolean pagina = url != null && (url.startsWith("https:") || (esPrueba() && url.startsWith("http:")));
                if (practicarPendiente && pagina) { practicarPendiente = false; practicar(2500); }
                if (destinoPendiente != null && pagina) { String d = destinoPendiente; destinoPendiente = null; destino(d, 2500); }
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) v.loadUrl("file:///android_asset/offline.html");
            }
        });
        practicarPendiente = getIntent() != null && getIntent().getBooleanExtra(EXTRA_PRACTICAR, false);
        destinoPendiente = getIntent() != null ? getIntent().getStringExtra(EXTRA_DESTINO) : null;
        if (esRetorno(getIntent())) abrirRetorno(getIntent());
        else if (saved != null) web.restoreState(saved); else web.loadUrl(inicio());
        /* lo que no hace falta para pintar la página va después: antes el widget (5 dibujos), el recordatorio y el motor
           de voz se preparaban en el hilo principal mientras la WebView arrancaba */
        web.postDelayed(() -> { if (isFinishing()) return; Reminder.schedule(this); Widget.updateAll(this); iniciaVoz(); }, 2500);
    }

    void iniciaVoz() {
        if (tts != null) return;
        tts = new TextToSpeech(this, st -> {
            if (st != TextToSpeech.SUCCESS || tts == null) return;
            int r = tts.setLanguage(Locale.FRANCE);
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) r = tts.setLanguage(Locale.FRENCH);
            ttsOk = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
        });
    }


    /* ---------- permisos ---------- */
    boolean hasMic() { return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED; }
    SharedPreferences prefs() { return getSharedPreferences("plex", 0); }
    void askMic(int code) {
        prefs().edit().putBoolean("micAsked", true).apply();
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, code);
    }
    /** granted · prompt (nunca pedido o se puede volver a pedir) · blocked (el usuario marcó «no volver a preguntar») */
    String micStatus() {
        if (hasMic()) return "granted";
        if (!prefs().getBoolean("micAsked", false)) return "prompt";
        return shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) ? "prompt" : "blocked";
    }
    boolean hasSpeechIntent() {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(getPackageManager()) != null;
    }
    void js(String code) { runOnUiThread(() -> web.evaluateJavascript(code, null)); }

    /* ---------- puente JavaScript ---------- */
    class Bridge {
        @JavascriptInterface public boolean speechAvailable() {
            return SpeechRecognizer.isRecognitionAvailable(MainActivity.this) || hasSpeechIntent();
        }
        @JavascriptInterface public String micStatus() { return MainActivity.this.micStatus(); }
        @JavascriptInterface public void requestMic() {
            runOnUiThread(() -> { if (hasMic()) micCallback("granted"); else askMic(REQ_MIC_ONLY); });
        }
        @JavascriptInterface public void openSettings() {
            runOnUiThread(() -> {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(i); } catch (Exception e) { /* nada */ }
            });
        }
        @JavascriptInterface public void startListening(String lang) { runOnUiThread(() -> listen(lang)); }
        @JavascriptInterface public void stopListening() { runOnUiThread(() -> { if (rec != null) rec.stopListening(); }); }
        @JavascriptInterface public void setReminder(boolean on, int hour, int minute) {
            Reminder.save(MainActivity.this, on, hour, minute);
            runOnUiThread(() -> {
                if (on && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    prefs().edit().putBoolean("notifAsked", true).apply();
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                }
                Reminder.schedule(MainActivity.this);
            });
        }
        @JavascriptInterface public String reminder() { return Reminder.json(MainActivity.this); }
        /** racha actual y último día con la meta cumplida: el recordatorio avisa «tu racha está en peligro» */
        @JavascriptInterface public void setProgress(int streak, String lastDay, String name) { Reminder.progress(MainActivity.this, streak, lastDay, name); }
        @JavascriptInterface public boolean notificationsAllowed() {
            return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        @JavascriptInterface public boolean ttsAvailable() { return ttsOk; }
        @JavascriptInterface public void ttsSpeak(String text, float rate) {
            if (!ttsOk || tts == null || text == null) return;
            tts.setSpeechRate(rate > 0 ? rate : 0.95f);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "plex");
        }
        @JavascriptInterface public void ttsStop() { if (tts != null) tts.stop(); }
        /** datos del widget y de los avisos (plx68.js): racha, XP de hoy, meta, palabra del día… */
        @JavascriptInterface public void setWidget(String json) { if (json != null) Widget.save(MainActivity.this, json); }
        @JavascriptInterface public int bridgeVersion() { return 8; }
        /* ---- 1.8: avisos ---- */
        /** lo que Manzana avisó (JSON: [{id, t, tipo, ti, tx, d, leido}]), el más nuevo primero */
        @JavascriptInterface public String avisos() { return Reminder.avisos(MainActivity.this); }
        /** marca un aviso como leído ("*" = todos) */
        @JavascriptInterface public void avisoLeido(String id) { if (id != null) Reminder.leido(MainActivity.this, id); }
        /** {permiso: granted|denied|prompt, canal: el diario activo, racha: «Racha en peligro» activo, proximo: ms} */
        @JavascriptInterface public String notifEstado() { return MainActivity.this.notifEstado(); }
        /** pide el permiso (Android 13+); si ya lo negó y Android no deja volver a preguntar, abre los ajustes de notificaciones */
        @JavascriptInterface public void pedirNotif() {
            runOnUiThread(() -> {
                boolean falta = Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
                if (falta && (!prefs().getBoolean("notifAsked", false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                    prefs().edit().putBoolean("notifAsked", true).apply();
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                } else ajustesNotif();
            });
        }
        @JavascriptInterface public void ajustesNotificaciones() { runOnUiThread(MainActivity.this::ajustesNotif); }
        /** solo en la versión de pruebas: muestra ya el aviso que tocaría (para revisar textos, botones y destinos) */
        @JavascriptInterface public boolean probarAviso(String tipo) {
            if (!esPrueba()) return false;
            sendBroadcast(new Intent(MainActivity.this, Reminder.class).setAction(Reminder.ACT_TEST).putExtra("tipo", tipo == null ? "" : tipo));
            return true;
        }
        @JavascriptInterface public String appVersion() { try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "?"; } }
    }

    void micCallback(String status) { js("window.__plexMic&&window.__plexMic('" + status + "')"); }

    void speechResult(boolean ok, ArrayList<String> alts, String err) {
        try {
            JSONObject o = new JSONObject(); o.put("ok", ok);
            if (alts != null) o.put("alts", new JSONArray(alts));
            if (err != null) o.put("err", err);
            js("window.__plexSpeech&&window.__plexSpeech(" + o + ")");
        } catch (Exception e) { /* nada */ }
    }

    /* ---------- reconocimiento de voz ---------- */
    void listen(String lang) {
        if (lang == null || lang.isEmpty()) lang = "fr-FR";
        if (!hasMic()) {
            if ("blocked".equals(micStatus())) { speechResult(false, null, "blocked"); return; }
            pendingLang = lang; askMic(REQ_MIC); return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { listenWithGoogleDialog(lang); return; }
        final String L = lang;
        try {
            if (rec != null) { rec.destroy(); rec = null; }
            rec = SpeechRecognizer.createSpeechRecognizer(this);
            gotResult = false; lastPartial = null;
            rec.setRecognitionListener(new RecognitionListener() {
                public void onResults(Bundle b) {
                    gotResult = true;
                    ArrayList<String> alts = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (alts == null || alts.isEmpty()) speechResult(false, null, "no-speech"); else speechResult(true, alts, null);
                }
                public void onError(int code) {
                    if (gotResult) return;
                    // si ya se oyó parte de la frase, se usa aunque el reconocedor no la haya cerrado
                    if (lastPartial != null && !lastPartial.isEmpty() && code != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { gotResult = true; speechResult(true, lastPartial, null); return; }
                    if (code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) { speechResult(false, null, "no-speech"); return; }
                    if (code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { speechResult(false, null, "not-allowed"); return; }
                    // servicio ocupado, sin idioma, cliente o servidor: probar con la ventana de voz de Google
                    listenWithGoogleDialog(L);
                }
                public void onReadyForSpeech(Bundle b) { js("window.__plexSpeechState&&window.__plexSpeechState('ready')"); }
                public void onBeginningOfSpeech() { js("window.__plexSpeechState&&window.__plexSpeechState('speaking')"); }
                public void onRmsChanged(float v) {
                    long now = System.currentTimeMillis(); if (now - lastRms < 90) return; lastRms = now;
                    js("window.__plexSpeechLevel&&window.__plexSpeechLevel(" + Math.max(0f, Math.min(1f, (v + 2f) / 12f)) + ")");
                }
                public void onBufferReceived(byte[] b) {}
                public void onEndOfSpeech() { js("window.__plexSpeechState&&window.__plexSpeechState('processing')"); }
                public void onPartialResults(Bundle b) {
                    ArrayList<String> p = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (p == null || p.isEmpty() || p.get(0).trim().isEmpty()) return;
                    lastPartial = p;
                    js("window.__plexSpeechPartial&&window.__plexSpeechPartial(" + JSONObject.quote(p.get(0)) + ")");
                }
                public void onEvent(int t, Bundle b) {}
            });
            rec.startListening(speechIntent(L));
        } catch (Exception e) {
            listenWithGoogleDialog(L);
        }
    }

    Intent speechIntent(String lang) {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
        i.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        // pausas cortas al leer no deben cortar la frase
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Lee la frase en francés");
        return i;
    }

    void listenWithGoogleDialog(String lang) {
        if (rec != null) { try { rec.destroy(); } catch (Exception e) { /* nada */ } rec = null; }
        try { startActivityForResult(speechIntent(lang), REQ_SPEECH); }
        catch (ActivityNotFoundException e) { speechResult(false, null, "unavailable"); }
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != REQ_SPEECH) return;
        if (result == RESULT_OK && data != null) {
            ArrayList<String> alts = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (alts != null && !alts.isEmpty()) { speechResult(true, alts, null); return; }
        }
        speechResult(false, null, result == RESULT_CANCELED ? "cancelled" : "no-speech");
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        if (code == REQ_MIC) {
            if (ok && pendingLang != null) listen(pendingLang);
            else speechResult(false, null, "blocked".equals(micStatus()) ? "blocked" : "not-allowed");
            pendingLang = null;
        }
        if (code == REQ_MIC_ONLY) {
            micCallback(ok ? "granted" : micStatus());
            if (pendingWebPerm != null) {
                if (ok) pendingWebPerm.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE}); else pendingWebPerm.deny();
                pendingWebPerm = null;
            }
        }
        if (code == REQ_NOTIF) { Reminder.canales(this); js("window.__plexNotif&&window.__plexNotif(" + ok + ")"); js("window.__plexNotifEstado&&window.__plexNotifEstado(" + notifEstado() + ")"); }
    }

    @Override protected void onResume() {
        super.onResume();
        /* 4.47: la página vuelve a correr (se pausa en onPause) */
        if (web != null) { web.onResume(); web.resumeTimers(); }
        // al volver de Ajustes, avisar a la página si ya hay permiso (micrófono y notificaciones)
        if (web != null) { micCallback(micStatus()); js("window.__plexNotifEstado&&window.__plexNotifEstado(" + notifEstado() + ")"); }
        // abrir la app retira el aviso del sistema (en la bandeja de la app queda como no leído hasta que lo vea)
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(Reminder.NOTIF);
    }

    /* ---------- entrar con Google ----------
       Google no deja iniciar sesión dentro de un WebView: la página abre el acceso en el navegador del teléfono
       (shouldOverrideUrlLoading lo manda afuera) y, al terminar, Supabase vuelve a co.plexplay.app://auth con la
       sesión en la dirección (#access_token=… o ?code=…). Aquí se abre la app con esa dirección y la página guarda la sesión. */
    static final String RETORNO = "co.plexplay.app";
    boolean esRetorno(Intent i) { Uri d = i == null ? null : i.getData(); return d != null && RETORNO.equals(d.getScheme()); }
    void abrirRetorno(Intent i) {
        Uri d = i.getData();
        String q = d.getEncodedQuery(), f = d.getEncodedFragment();
        web.loadUrl("https://" + HOST + "/app/index.html" + (q != null ? "?" + q : "") + (f != null ? "#" + f : ""));
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (esRetorno(intent) && web != null) abrirRetorno(intent);
        else if (intent.getBooleanExtra(EXTRA_PRACTICAR, false) && web != null) practicar(300);
        else if (intent.getStringExtra(EXTRA_DESTINO) != null && web != null) destino(intent.getStringExtra(EXTRA_DESTINO), 300);
    }

    /** abre la siguiente lección (la página define window.__plexPracticar en plx68.js) */
    void practicar(long delay) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(1);
        web.postDelayed(() -> web.evaluateJavascript("window.__plexPracticar&&window.__plexPracticar()", null), delay);
    }

    /** abre una pantalla de la página (plx117.js define window.__plexAbrir): racha, palabra, 1v1, avisos */
    void destino(String d, long delay) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(Reminder.NOTIF);
        String seguro = d.replaceAll("[^a-z0-9]", "");
        web.postDelayed(() -> web.evaluateJavascript("window.__plexAbrir&&window.__plexAbrir('" + seguro + "')", null), delay);
    }

    String notifEstado() {
        boolean permiso = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean app = nm.areNotificationsEnabled(), canal = true, racha = true;
        if (Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationChannel a = nm.getNotificationChannel(Reminder.CH), b = nm.getNotificationChannel(Reminder.CH_RACHA);
            canal = a == null || a.getImportance() != NotificationManager.IMPORTANCE_NONE;
            racha = b == null || b.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        String p = permiso && app ? "granted" : (Build.VERSION.SDK_INT >= 33 && !prefs().getBoolean("notifAsked", false) ? "prompt" : "denied");
        return "{\"permiso\":\"" + p + "\",\"canal\":" + canal + ",\"racha\":" + racha + ",\"proximo\":" + prefs().getLong("proximo", 0) + ",\"prueba\":" + esPrueba() + "}";
    }
    void ajustesNotif() {
        Intent i;
        if (Build.VERSION.SDK_INT >= 26) i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        else i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception e) { /* nada */ }
    }

    /* ---------- versión de pruebas (debug): abre la página de un servidor local con «adb shell am start … -e casa URL» ---------- */
    boolean esPrueba() { return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0; }
    String inicio() {
        if (esPrueba()) {
            String casa = getIntent() != null ? getIntent().getStringExtra("casa") : null;
            if (casa != null) prefs().edit().putString("casa", casa).apply();
            casa = prefs().getString("casa", "");
            if (!casa.isEmpty()) return casa;
        }
        return HOME;
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override public void onBackPressed() {
        String u = web.getUrl();
        if (u != null && u.startsWith("file:")) { web.loadUrl(inicio()); return; }
        /* la página (plx101, 4.45) sabe cerrar lo que esté encima: juego (pausa), Quiz, diagnóstico, ventanas, lección… */
        web.evaluateJavascript("(function(){try{if(window.__plexAtras&&window.__plexAtras())return 'capa';}catch(e){}try{var t=document.querySelector('.tour [data-tour=skip],.tour [data-tour=lessons]');if(t){t.click();return 'tour';}"
            + "var m=document.querySelector('.gmodal [data-g=close],.gmodal .gm-x');if(m){m.click();return 'modal';}"
            + "if(typeof P!=='undefined'&&P&&typeof closePlayer==='function'){closePlayer();return 'player';}"
            + "if(typeof view!=='undefined'&&view!=='parcours'&&typeof go==='function'){go('parcours');return 'home';}}catch(e){}return 'exit';})()",
            r -> { if ("\"exit\"".equals(r)) { if (web.canGoBack()) web.goBack(); else finish(); } });
    }

    /* 4.47: antes la página seguía corriendo con la app cerrada (Manzana 3D, animaciones, temporizadores): gastaba batería,
       calentaba el teléfono y lo volvía lento. Ahora se pausa del todo hasta volver. */
    @Override protected void onPause() {
        super.onPause(); CookieManager.getInstance().flush(); if (rec != null) rec.cancel();
        if (web != null) { web.onPause(); web.pauseTimers(); }
    }
    @Override protected void onDestroy() {
        if (rec != null) rec.destroy();
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        super.onDestroy();
    }
}
