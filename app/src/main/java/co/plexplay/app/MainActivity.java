package co.plexplay.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** PLEX PLAY: la app web (PWA) dentro de una WebView, con sesión y progreso guardados. */
public class MainActivity extends Activity {
    static final String HOST = "jhonsaavedrau-dev.github.io";
    static final String HOME = "https://" + HOST + "/portfolio-francais-c1-1/plexplay/?src=android";
    WebView web;

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
        s.setUserAgentString(s.getUserAgentString() + " PlexPlayAndroid/1");
        CookieManager.getInstance().setAcceptCookie(true);
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
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override public void onBackPressed() {
        String u = web.getUrl();
        if (u != null && u.startsWith("file:")) { web.loadUrl(HOME); return; }
        web.evaluateJavascript("(function(){try{var m=document.querySelector('.gmodal [data-g=close],.gmodal .gm-x');if(m){m.click();return 'modal';}"
            + "if(typeof P!=='undefined'&&P&&typeof closePlayer==='function'){closePlayer();return 'player';}"
            + "if(typeof view!=='undefined'&&view!=='parcours'&&typeof go==='function'){go('parcours');return 'home';}}catch(e){}return 'exit';})()",
            r -> { if ("\"exit\"".equals(r)) { if (web.canGoBack()) web.goBack(); else finish(); } });
    }

    @Override protected void onPause() { super.onPause(); CookieManager.getInstance().flush(); }
}
