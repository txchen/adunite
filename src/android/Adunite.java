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

import com.unity3d.ads.IUnityAdsListener;
import com.unity3d.ads.UnityAds;

public class Adunite extends CordovaPlugin {
    private static final String LOG_TAG = "Adunite";
    private CallbackContext _aduniteCallbackContext;

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
            return true;
        } else if (action.equals("loadAds")) {
            return loadAds(callbackContext, data);
        } else {
            return false;
        }
    }

    private void initAdunite(CallbackContext callbackContext, final String unityGameId) {
        // some sdk requires init before using
        if (unityGameId != null && !"".equals(unityGameId)) {
            _unityAdsListener = new UnityAdsListener();
            UnityAds.setListener(_unityAdsListener);
            UnityAds.initialize(getActivity(), unityGameId, _unityAdsListener);
        }
    }

    private boolean loadAds(CallbackContext callbackContext, JSONArray data) {
        return true;
    }

    private boolean showAds(CallbackContext callbackContext, JSONArray data) {
        return true;
    }

    private boolean getReadyAds(CallbackContext callbackContext) {
        return true;
    }

    private Activity getActivity() {
        return cordova.getActivity();
    }

    public void sendAdsEventToJs(String networkName, String eventName, String eventDetail) {
        PluginResult result = new PluginResult(PluginResult.Status.OK, buildAdsEvent(networkName, eventName, eventDetail));
        result.setKeepCallback(true);
        if (_aduniteCallbackContext != null) {
            _aduniteCallbackContext.sendPluginResult(result);
        } else {
            Log.e(LOG_TAG, String.format("_aduniteCallbackContext is null, cannot send result back, network=%s event=%s", networkName, eventName));
        }
    }

    public JSONObject buildAdsEvent(String networkName, String eventName, String eventDetail) {
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
            Log.w(LOG_TAG, "unity ads ready: " + zoneId);
            sendAdsEventToJs("unity", "READY", zoneId);
        }

        @Override
        public void onUnityAdsStart(String zoneId) {
            Log.w(LOG_TAG, "unity ads started: " + zoneId);
            sendAdsEventToJs("unity", "START", zoneId);
        }

        @Override
        public void onUnityAdsFinish(String zoneId, UnityAds.FinishState result) {
            Log.w(LOG_TAG, "unity ads finished " + zoneId + " " + result.toString());
            sendAdsEventToJs("unity", "FINISH", result.toString());
        }

        @Override
        public void onUnityAdsError(UnityAds.UnityAdsError error, String message) {
            Log.e(LOG_TAG, "onUnityAdsError: " + error + " - " + message);
            sendAdsEventToJs("unity", "ERROR", error.toString() + "-" + message);
        }
    }
}
