package com.adunite;

import org.apache.cordova.*;
import android.app.Activity;
import android.util.DisplayMetrics;
import android.content.Context;
import android.telephony.TelephonyManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Map;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;

import com.applovin.adview.AppLovinInterstitialAd;
import com.applovin.adview.AppLovinInterstitialAdDialog;
import com.applovin.sdk.AppLovinAd;
import com.applovin.sdk.AppLovinAdClickListener;
import com.applovin.sdk.AppLovinAdDisplayListener;
import com.applovin.sdk.AppLovinAdLoadListener;
import com.applovin.sdk.AppLovinSdk;

import com.jirbo.adcolony.AdColony;
import com.jirbo.adcolony.AdColonyAd;
import com.jirbo.adcolony.AdColonyAdAvailabilityListener;
import com.jirbo.adcolony.AdColonyAdListener;
import com.jirbo.adcolony.AdColonyVideoAd;

import com.chartboost.sdk.CBLocation;
import com.chartboost.sdk.Chartboost;
import com.chartboost.sdk.ChartboostDelegate;
import com.chartboost.sdk.Model.CBError;

public class Adunite extends CordovaPlugin {
    private static final String LOG_TAG = "Adunite";
    private CallbackContext _aduniteCallbackContext;

    private com.google.android.gms.ads.InterstitialAd _admobInterstitialAd;
    private AppLovinInterstitialAdDialog _adDialog;
    private volatile boolean _applovinReady = false;

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        if (action.equals("showAds")) {
            return showAds(callbackContext, data);
        } else if (action.equals("init")) {
            // all ads event callback goes to this callback
            _aduniteCallbackContext = callbackContext;
            initAdunite(callbackContext, data.optBoolean(0), data.optString(1), data.optString(2));
            PluginResult result = new PluginResult(PluginResult.Status.OK, new JSONObject());
            result.setKeepCallback(true);
            _aduniteCallbackContext.sendPluginResult(result);
            return true;
        } else if (action.equals("loadAds")) {
            return loadAds(callbackContext, data);
        } else {
            return false;
        }
    }

    private void initAdunite(CallbackContext callbackContext,
            final boolean enableApplovin, final String adcolonyAppAndZoneId,
            final String chartboostAppIdAndSignature) {
        // some sdk requires init before using
        // applovin
        if (enableApplovin) {
            Log.w(LOG_TAG, "applovin ads is enabled.");
            _adDialog = AppLovinInterstitialAd.create(AppLovinSdk.getInstance(getActivity()), getActivity());
            MyAppLovinListener myAppLovinListener = new MyAppLovinListener();
            _adDialog.setAdDisplayListener(myAppLovinListener);
            _adDialog.setAdLoadListener(myAppLovinListener);
            _adDialog.setAdClickListener(myAppLovinListener);
            AppLovinSdk.initializeSdk(getActivity());
            // start a polling thread to check if ads is ready to show
            Thread checkAppLovinThread = new Thread(new Runnable() { public void run() {
                while (true) {
                    if (_applovinReady == false) {
                        boolean result = _adDialog.isAdReadyToDisplay();
                        Log.d(LOG_TAG, "checking applovin ready state = " + result);
                        if (result) {
                            sendAdsEventToJs("applovin", "READY", "");
                            _applovinReady = true;
                        }
                    }
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) { e.printStackTrace(); }
                }
            }});
            checkAppLovinThread.start();
        }
        // adcolony
        if ((adcolonyAppAndZoneId != null) && (!"".equals(adcolonyAppAndZoneId)) && (!"null".equals(adcolonyAppAndZoneId))) {
            Log.w(LOG_TAG, "adcolony ads is enabled. appId_zoneId=" + adcolonyAppAndZoneId);
            String[] tokens = adcolonyAppAndZoneId.split("_");
            AdColony.configure(getActivity(), "version:1.0,store:google", tokens[0] /* appid */, tokens[1] /* zoneid */);
            AdColony.addAdAvailabilityListener(new AdColonyListener());
        }
        // chartboost
        if ((chartboostAppIdAndSignature != null) && (!"".equals(chartboostAppIdAndSignature)) && (!"null".equals(chartboostAppIdAndSignature))) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.w(LOG_TAG, "chartboost ads is enabled. appId_signatureId=" + chartboostAppIdAndSignature);
                    String[] tokens = chartboostAppIdAndSignature.split("_");
                    Chartboost.setAutoCacheAds(false);
                    Chartboost.startWithAppId(getActivity(), tokens[0] /* appid */, tokens[1] /* signature */);
                    Chartboost.setDelegate(new MyChartboostListener());
                    Chartboost.onCreate(getActivity());
                    Chartboost.onStart(getActivity());
                }
            });
        }
    }

    private boolean loadAds(CallbackContext callbackContext, JSONArray data) {
        final String networkName = data.optString(0);
        final String pid = data.optString(1);

        if ("admob".equals(networkName)) {
            loadAdmobAds(pid);
        } else if ("applovin".equals(networkName)) {
            // no op
        } else if ("adcolony".equals(networkName)) {
            // no op
        } else if ("cb".equals(networkName)) {
            loadChartboostAds();
        } else {
            Log.e(LOG_TAG, "adnetwork not supported: " + networkName);
        }
        return true;
    }

    private boolean showAds(CallbackContext callbackContext, JSONArray data) {
        final String networkName = data.optString(0);
        if ("admob".equals(networkName)) {
            showAdmobAds(callbackContext);
        } else if ("applovin".equals(networkName)) {
            showApplovinAds(callbackContext);
        } else if ("adcolony".equals(networkName)) {
            showAdcolonyAds(callbackContext);
        } else if ("cb".equals(networkName)) {
            showChartboostAds(callbackContext);
        } else {
            Log.e(LOG_TAG, "adnetwork not supported: " + networkName);
        }
        PluginResult result = new PluginResult(PluginResult.Status.OK, networkName);
        callbackContext.sendPluginResult(result);
        return true;
    }

    // =========== END of public facing methods ================

    // Admob
    private void loadAdmobAds(final String pid) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(LOG_TAG, "Trying to load admob ads, pid=" + pid);
                _admobInterstitialAd = new com.google.android.gms.ads.InterstitialAd(getActivity());
                _admobInterstitialAd.setAdUnitId(pid);
                AdRequest adRequest = new AdRequest.Builder().build();
                _admobInterstitialAd.setAdListener(new AdmobAdListener());
                _admobInterstitialAd.loadAd(adRequest);
            }
        });
    }

    private void showAdmobAds(final CallbackContext callbackContext) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(LOG_TAG, "Trying to show admob ads");
                if (_admobInterstitialAd != null) {
                    _admobInterstitialAd.show();
                } else {
                    Log.e(LOG_TAG, "abmob interstitial not ready, cannot show");
                    PluginResult result = new PluginResult(PluginResult.Status.ERROR, "admob interstitial not ready, cannot show");
                    callbackContext.sendPluginResult(result);
                }
            }
        });
    }

    // applovin
    private void showApplovinAds(final CallbackContext callbackContext) {
        if (_adDialog.isAdReadyToDisplay()) {
            Log.i(LOG_TAG, "Trying to show applovin ads");
            // NOTE: only after we call show, it would trigger loaded event
            //       this is stupid, makes the logic hard to implement
            _adDialog.show();
        } else {
            Log.e(LOG_TAG, "applovin ads not ready, cannot show");
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, "applovin ads not ready, cannot show");
            callbackContext.sendPluginResult(result);
        }
    }

    // adcolony
    private void showAdcolonyAds(final CallbackContext callbackContext) {
        Log.i(LOG_TAG, "Trying to show adcolony ads");
        AdColonyVideoAd ad = new AdColonyVideoAd().withListener(new AdColonyListener());
        ad.show();
    }

    // chartboost
    private void loadChartboostAds() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(LOG_TAG, "Trying to load Chartboost ads");
                Chartboost.cacheInterstitial(CBLocation.LOCATION_DEFAULT);
            }
        });
    }

    private void showChartboostAds(final CallbackContext callbackContext) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(LOG_TAG, "Trying to show chartboost ads");
                if (Chartboost.hasInterstitial(CBLocation.LOCATION_DEFAULT)) {
                    Chartboost.showInterstitial(CBLocation.LOCATION_DEFAULT);
                } else {
                    Log.e(LOG_TAG, "Chartboost interstitial not ready, cannot show");
                    PluginResult result = new PluginResult(PluginResult.Status.ERROR, "chartboost interstitial not ready, cannot show");
                    callbackContext.sendPluginResult(result);
                }
            }
        });
    }

    private Activity getActivity() {
        return cordova.getActivity();
    }

    private void sendAdsEventToJs(String networkName, String eventName, String eventDetail) {
        Log.w(LOG_TAG, String.format("Emit AdsEvent: %s - %s - %s", networkName, eventName, eventDetail));
        PluginResult result = new PluginResult(PluginResult.Status.OK, buildAdsEvent(networkName, eventName, eventDetail));
        result.setKeepCallback(true);
        if (_aduniteCallbackContext != null) {
            _aduniteCallbackContext.sendPluginResult(result);
        } else {
            Log.e(LOG_TAG, String.format("_aduniteCallbackContext is null, cannot send result back, network=%s event=%s", networkName, eventName));
        }
    }

    private JSONObject buildAdsEvent(String networkName, String eventName, String eventDetail) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("network_name", networkName);
            obj.put("event_name", eventName);
            obj.put("event_detail", eventDetail);
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            return null;
        }
        return obj;
    }

    private class AdmobAdListener extends AdListener {
        @Override
        public void onAdClosed() {
            sendAdsEventToJs("admob", "FINISH", "");
        }

        @Override
        public void onAdFailedToLoad(int errorCode) {
            sendAdsEventToJs("admob", "LOADERROR", String.valueOf(errorCode));
        }

        @Override
        public void onAdLeftApplication() {
            sendAdsEventToJs("admob", "CLICK", "");
        }

        @Override
        public void onAdOpened() {
            sendAdsEventToJs("admob", "START", "");
        }

        @Override
        public void onAdLoaded() {
            sendAdsEventToJs("admob", "READY", "");
        }
    }

    private class MyAppLovinListener implements AppLovinAdDisplayListener, AppLovinAdLoadListener, AppLovinAdClickListener {
        @Override
        public void adDisplayed(AppLovinAd appLovinAd) {
            sendAdsEventToJs("applovin", "START", "");
            _applovinReady = false;
        }
        @Override
        public void adHidden(AppLovinAd appLovinAd) {
            sendAdsEventToJs("applovin", "FINISH", "");
        }
        @Override
        public void adReceived(AppLovinAd appLovinAd) {
            // This will actually happen after ads is shown, so not useful
            Log.i(LOG_TAG, "applovin got adReceived event");
            // sendAdsEventToJs("applovin", "READY", String.valueOf(appLovinAd.getAdIdNumber()));
        }
        @Override
        public void failedToReceiveAd(int errorCode) {
            sendAdsEventToJs("applovin", "LOADERROR", String.valueOf(errorCode));
        }
        @Override
        public void adClicked(AppLovinAd appLovinAd) {
            sendAdsEventToJs("applovin", "CLICK", "");
        }
    }

    private class AdColonyListener implements AdColonyAdAvailabilityListener, AdColonyAdListener {
        @Override
        public void onAdColonyAdAvailabilityChange(boolean b, String s) {
            Log.i(LOG_TAG, "adcolony AdAvailabilityChange " + b + " " + s);
            if (b) {
                sendAdsEventToJs("adcolony", "READY", "");
            }
        }
        @Override
        public void onAdColonyAdAttemptFinished( AdColonyAd ad )
        {
            // Can use the ad object to determine information about the ad attempt:
            // ad.shown();
            // ad.notShown();
            // ad.canceled();
            // ad.noFill();
            // ad.skipped();
            if (ad.shown()) {
                sendAdsEventToJs("adcolony", "FINISH", "");
            }
        }

        @Override
        public void onAdColonyAdStarted(AdColonyAd ad)
        {
            sendAdsEventToJs("adcolony", "START", "");
        }
    }

    private class MyChartboostListener extends ChartboostDelegate {
        @Override
        public void didDisplayInterstitial(String location) {
            sendAdsEventToJs("cb", "START", location);
        }
        // when ads is loaded, this will be called
        @Override
        public void didCacheInterstitial(String location) {
            sendAdsEventToJs("cb", "READY", location);
        }
        @Override
        public void didFailToLoadInterstitial(String location, CBError.CBImpressionError error) {
            sendAdsEventToJs("cb", "LOADERROR", String.valueOf(error));
        }
        @Override
        public void didDismissInterstitial(String location) {
            sendAdsEventToJs("cb", "FINISH", location);
        }
        @Override
        public void didClickInterstitial(String location) {
            sendAdsEventToJs("cb", "CLICK", location);
        }
    }
}
