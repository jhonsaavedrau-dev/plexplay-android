package co.plexplay.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/** PLEX PLAY: la app web (PWA) dentro de una WebView, con puente para voz y recordatorios. */
public class MainActivity extends Activity {
    static final String HOST = "jhonsaavedrau-dev.github.io";
    static final String HOME = "https://" + HOST + "/portfolio-francais-c1-1/plexplay/?src=android";
    static final int REQ_MIC = 11, REQ_NOTIF = 12;
    WebView web;
    SpeechRecognizer rec;
    String pendingLang = null;

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
        s.setUserAgentString(s.getUserAgentString() + " PlexPlayAndroid/2");
        CookieManager.getInstance().setAcceptCookie(true);
        web.addJavascriptInterface(new Bridge(), "PlexAndroid");
        web.setWebChromeClient(new WebChromeClient());
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

    /* ---------- puente JavaScript ---------- */
    class Bridge {
        @JavascriptInterface public boolean speechAvailable() { return SpeechRecognizer.isRecognitionAvailable(MainActivity.this); }
        @JavascriptInterface public void startListening(String lang) { runOnUiThread(() -> listen(lang)); }
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
    }

    void speechResult(boolean ok, ArrayList<String> alts, String err) {
        try {
            JSONObject o = new JSONObject(); o.put("ok", ok);
            if (alts != null) o.put("alts", new JSONArray(alts));
            if (err != null) o.put("err", err);
            web.evaluateJavascript("window.__plexSpeech&&window.__plexSpeech(" + o + ")", null);
        } catch (Exception e) { /* nada */ }
    }

    void listen(String lang) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingLang = lang; requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC); return;
        }
        if (rec != null) { rec.destroy(); rec = null; }
        rec = SpeechRecognizer.createSpeechRecognizer(this);
        rec.setRecognitionListener(new RecognitionListener() {
            public void onResults(Bundle b) { speechResult(true, b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION), null); }
            public void onError(int code) { speechResult(false, null, code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ? "no-speech" : code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ? "not-allowed" : "error-" + code); }
            public void onReadyForSpeech(Bundle b) {} public void onBeginningOfSpeech() {} public void onRmsChanged(float v) {}
            public void onBufferReceived(byte[] b) {} public void onEndOfSpeech() {} public void onPartialResults(Bundle b) {} public void onEvent(int t, Bundle b) {}
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang == null ? "fr-FR" : lang);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang == null ? "fr-FR" : lang);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 4);
        rec.startListening(i);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        if (code == REQ_MIC) { if (ok && pendingLang != null) listen(pendingLang); else speechResult(false, null, "not-allowed"); pendingLang = null; }
        if (code == REQ_NOTIF) web.evaluateJavascript("window.__plexNotif&&window.__plexNotif(" + ok + ")", null);
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

    @Override protected void onPause() { super.onPause(); CookieManager.getInstance().flush(); }
    @Override protected void onDestroy() { if (rec != null) rec.destroy(); super.onDestroy(); }
}
