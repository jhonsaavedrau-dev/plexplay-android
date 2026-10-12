package co.plexplay.app;

import android.app.Activity;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/**
 * Anuncios con recompensa de AdMob (PLEX PLAY 4.24, rama «monetizacion»).
 *
 * La página los pide solo cuando quien juega toca «Ver anuncio» (monedas o protector de racha) y solo si la
 * monetización está encendida en el servidor: mientras esté apagada, MobileAds ni siquiera se inicia.
 * El resultado vuelve a la página con window.__plxAnuncio(true) si el anuncio se vio completo, o (false).
 *
 * IDs: por defecto los de PRUEBA de Google (no generan dinero ni riesgo). Los reales se ponen como secretos del
 * repositorio ADMOB_APP y ADMOB_PREMIADO (ver app/build.gradle y herramientas/monetizacion/LEEME.md).
 */
final class Anuncios {
    private final Activity act;
    private final Callback cb;
    private boolean iniciado = false, ocupado = false;

    interface Callback { void resultado(boolean visto); }

    Anuncios(Activity act, Callback cb) { this.act = act; this.cb = cb; }

    /** se llama en el hilo de la interfaz */
    void mostrar() {
        if (ocupado) { cb.resultado(false); return; }
        ocupado = true;
        if (!iniciado) {
            iniciado = true;
            MobileAds.initialize(act, estado -> cargar());
        } else cargar();
    }

    private void cargar() {
        act.runOnUiThread(() -> RewardedAd.load(act, BuildConfig.ADMOB_PREMIADO, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override public void onAdLoaded(RewardedAd ad) { ensenar(ad); }
            @Override public void onAdFailedToLoad(LoadAdError e) { fin(false); }
        }));
    }

    private void ensenar(RewardedAd ad) {
        final boolean[] premio = {false};
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { fin(premio[0]); }
            @Override public void onAdFailedToShowFullScreenContent(AdError e) { fin(false); }
        });
        ad.show(act, item -> premio[0] = true);
    }

    private void fin(boolean visto) { ocupado = false; cb.resultado(visto); }
}
