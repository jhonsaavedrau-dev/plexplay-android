package co.plexplay.app;

import android.Manifest;
import android.app.Activity;
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
    static final String HOST = "jhonsaavedrau-dev.github.io";
    static final String HOME = "https://" + HOST + "/portfolio-francais-c1-1/plexplay/?src=android";
    static final int REQ_MIC = 11, REQ_NOTIF = 12, REQ_SPEECH = 13, REQ_MIC_ONLY = 14;
    WebView web;
    SpeechRecognizer rec;
    String pendingLang = null;
    PermissionRequest pendingWebPerm = null;
    boolean gotResult = false;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0F2A5C);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " PlexPlayAndroid/3");
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
                if ("https".equals(u.getScheme()) && HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { /* sin app para abrirlo */ }
                return true;
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) v.loadUrl("file:///android_asset/offline.html");
            }
        });
        if (saved != null) web.restoreState(saved); else web.loadUrl(HOME);
        Reminder.schedule(this);
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
                if (on && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                Reminder.schedule(MainActivity.this);
            });
        }
        @JavascriptInterface public String reminder() { return Reminder.json(MainActivity.this); }
        @JavascriptInterface public boolean notificationsAllowed() {
            return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        @JavascriptInterface public int bridgeVersion() { return 3; }
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
            gotResult = false;
            rec.setRecognitionListener(new RecognitionListener() {
                public void onResults(Bundle b) {
                    gotResult = true;
                    ArrayList<String> alts = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (alts == null || alts.isEmpty()) speechResult(false, null, "no-speech"); else speechResult(true, alts, null);
                }
                public void onError(int code) {
                    if (gotResult) return;
                    if (code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) { speechResult(false, null, "no-speech"); return; }
                    if (code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { speechResult(false, null, "not-allowed"); return; }
                    // servicio ocupado, sin idioma, cliente o servidor: probar con la ventana de voz de Google
                    listenWithGoogleDialog(L);
                }
                public void onReadyForSpeech(Bundle b) { js("window.__plexSpeechState&&window.__plexSpeechState('ready')"); }
                public void onBeginningOfSpeech() { js("window.__plexSpeechState&&window.__plexSpeechState('speaking')"); }
                public void onRmsChanged(float v) {}
                public void onBufferReceived(byte[] b) {}
                public void onEndOfSpeech() { js("window.__plexSpeechState&&window.__plexSpeechState('processing')"); }
                public void onPartialResults(Bundle b) {}
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
        if (code == REQ_NOTIF) js("window.__plexNotif&&window.__plexNotif(" + ok + ")");
    }

    @Override protected void onResume() {
        super.onResume();
        // al volver de Ajustes, avisar a la página si ya hay permiso
        if (web != null) micCallback(micStatus());
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override public void onBackPressed() {
        String u = web.getUrl();
        if (u != null && u.startsWith("file:")) { web.loadUrl(HOME); return; }
        web.evaluateJavascript("(function(){try{var t=document.querySelector('.tour [data-tour=skip],.tour [data-tour=lessons]');if(t){t.click();return 'tour';}"
            + "var m=document.querySelector('.gmodal [data-g=close],.gmodal .gm-x');if(m){m.click();return 'modal';}"
            + "if(typeof P!=='undefined'&&P&&typeof closePlayer==='function'){closePlayer();return 'player';}"
            + "if(typeof view!=='undefined'&&view!=='parcours'&&typeof go==='function'){go('parcours');return 'home';}}catch(e){}return 'exit';})()",
            r -> { if ("\"exit\"".equals(r)) { if (web.canGoBack()) web.goBack(); else finish(); } });
    }

    @Override protected void onPause() { super.onPause(); CookieManager.getInstance().flush(); if (rec != null) rec.cancel(); }
    @Override protected void onDestroy() { if (rec != null) rec.destroy(); super.onDestroy(); }
}
