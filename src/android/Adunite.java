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

import com.facebook.ads.Ad;
import com.facebook.ads.AdError;
import com.facebook.ads.InterstitialAd;
import com.facebook.ads.InterstitialAdListener;

import com.google.android.gms.ads.AdRequest;

import com.unity3d.ads.IUnityAdsListener;
import com.unity3d.ads.UnityAds;

public class Adunite extends CordovaPlugin {
    private static final String LOG_TAG = "Adunite";
    private CallbackContext _aduniteCallbackContext;

    private InterstitialAd _fbInterstitialAd;

    private UnityAdsListener _unityAdsListener;

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        if (action.equals("showAds")) {
            return showAds(callbackContext, data);
        } else if (action.equals("getReadyAds")) {
            return getReadyAds(callbackContext);
        } else if (action.equals("init")) {
            // all ads event callback goes to this callback
            _aduniteCallbackContext = callbackContext;
            initAdunite(callbackContext, data.optString(0));
            //, data.optString(0), data.optBoolean(1), data.optBoolean(2), data.optBoolean(3));
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

    private void initAdunite(CallbackContext callbackContext, final String unityGameId) {
        // some sdk requires init before using
        if ((unityGameId != null) && (!"".equals(unityGameId)) && (!"null".equals(unityGameId))) {
            Log.w(LOG_TAG, "unity ads is enabled, init it with GameId: " + unityGameId);
            _unityAdsListener = new UnityAdsListener();
            UnityAds.setListener(_unityAdsListener);
            UnityAds.initialize(getActivity(), unityGameId, _unityAdsListener);
        }
    }

    private boolean loadAds(CallbackContext callbackContext, JSONArray data) {
        final String networkName = data.optString(0);
        final String pid = data.optString(1);

        if ("fban".equals(networkName)) {
            loadFBAds(pid);
        } else if ("unity".equals(networkName)) {
            // no op
        } else {
            Log.e(LOG_TAG, "adnetwork not supported: " + networkName);
        }
        return true;
    }

    private boolean showAds(CallbackContext callbackContext, JSONArray data) {
        final String networkName = data.optString(0);
        if ("fban".equals(networkName)) {
            showFBAds(callbackContext);
        } else if ("unity".equals(networkName)) {
            showUnityAds(callbackContext);
        } else {
            Log.e(LOG_TAG, "adnetwork not supported: " + networkName);
        }
        return true;
    }

    private boolean getReadyAds(CallbackContext callbackContext) {
        return true;
    }

    // =========== END of public facing methods ================

    // FBAN
    private void loadFBAds(final String pid) {
        Log.i(LOG_TAG, "Trying to load fban ads, pid=" + pid);
        if (_fbInterstitialAd != null) {
            _fbInterstitialAd.destroy();
        }
        _fbInterstitialAd = new InterstitialAd(getActivity(), pid);
        _fbInterstitialAd.setAdListener(new FBInterstitialAdListener());
        _fbInterstitialAd.loadAd();
    }

    private void showFBAds(CallbackContext callbackContext) {
        Log.i(LOG_TAG, "Trying to show fban ads");
        if (_fbInterstitialAd != null) {
            _fbInterstitialAd.show();
        } else {
            Log.e(LOG_TAG, "fban interstitial not ready, cannot show");
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, "fban interstitial not ready, cannot show");
            callbackContext.sendPluginResult(result);
        }
    }

    // Unity

    // NOTE: unity does not have load method

    private void showUnityAds(CallbackContext callbackContext) {
        UnityAds.show(getActivity());
    }

    // Admob

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

    private class UnityAdsListener implements IUnityAdsListener {
        @Override
        public void onUnityAdsReady(final String zoneId) {
            sendAdsEventToJs("unity", "READY", zoneId);
        }

        @Override
        public void onUnityAdsStart(String zoneId) {
            sendAdsEventToJs("unity", "START", zoneId);
        }

        @Override
        public void onUnityAdsFinish(String zoneId, UnityAds.FinishState result) {
            sendAdsEventToJs("unity", "FINISH", zoneId + " " + result.toString());
        }

        @Override
        public void onUnityAdsError(UnityAds.UnityAdsError error, String message) {
            sendAdsEventToJs("unity", "LOADERROR", error.toString() + "-" + message);
        }
        // Unity Ads has no Clicked event
    }

    private class FBInterstitialAdListener implements InterstitialAdListener {
        @Override
        public void onInterstitialDisplayed(Ad ad) {
            sendAdsEventToJs("fban", "START", ad.getPlacementId());
        }

        @Override
        public void onInterstitialDismissed(Ad ad) {
            sendAdsEventToJs("fban", "FINISH", ad.getPlacementId());
        }

        @Override
        public void onError(Ad ad, AdError error) {
            sendAdsEventToJs("fban", "LOADERROR", String.valueOf(error.getErrorCode()));
        }

        @Override
        public void onAdLoaded(Ad ad) {
            sendAdsEventToJs("fban", "READY", ad.getPlacementId());
        }

        @Override
        public void onAdClicked(Ad ad) {
            sendAdsEventToJs("fban", "CLICK", ad.getPlacementId());
        }
    }
}
