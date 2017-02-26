package com.adunite;

import org.apache.cordova.*;
import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Map;
import android.util.Log;
import android.util.DisplayMetrics;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;

public class Intenthelper extends CordovaPlugin {
    private static final String LOG_TAG = "Intenthelper";

    private CallbackContext _getAdsInfoCallbackContext;

    @Override
    public boolean execute(String action, JSONArray data, final CallbackContext callbackContext) throws JSONException {
        if (action.equals("sendBroadcast")) {
            String intentAction = data.optString(0);
            JSONObject extras = data.optJSONObject(1);

            Intent intent = new Intent(intentAction);
            if (extras != null) {
                for (int i = 0; i < extras.names().length(); i++) {
                    String keyName = extras.names().getString(i);
                    intent.putExtra(keyName, extras.getString(keyName));
                }
            }
            getActivity().getApplicationContext().sendBroadcast(intent);
            return true;
        } else if (action.equals("getAdsInfo")) {
            _getAdsInfoCallbackContext = callbackContext;
            Thread thr = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Context ctx = getActivity().getApplicationContext();
                        AdvertisingIdClient.Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(ctx);
                        Log.w(LOG_TAG, "gotAdInfo");
                        JSONObject adsObj = new JSONObject();
                        adsObj.put("adsid", adInfo.getId()); // google ads id
                        adsObj.put("adslimittracking", adInfo.isLimitAdTrackingEnabled()); // interested based ads or not
                        PluginResult result = new PluginResult(PluginResult.Status.OK, adsObj);
                        result.setKeepCallback(false);
                        _getAdsInfoCallbackContext.sendPluginResult(result);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            thr.start();
            // get ads must be done in async task
            PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);
            return true;
        } else if (action.equals("checkPackageInstalled")) {
            PackageManager pm = getActivity().getPackageManager();
            boolean appInstalled;
            try {
                pm.getPackageInfo(data.optString(0), PackageManager.GET_ACTIVITIES);
                appInstalled = true;
            }
            catch (PackageManager.NameNotFoundException e) {
                appInstalled = false;
            }
            PluginResult result = new PluginResult(PluginResult.Status.OK, appInstalled);
            callbackContext.sendPluginResult(result);
            return true;
        } else if (action.equals("getSharedPref")) {
            String prefName = data.optString(0);
            if (prefName == null || prefName.isEmpty()) {
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, "prefName is null or empty");
                callbackContext.sendPluginResult(result);
                return false;
            }
            SharedPreferences sp = getActivity().getSharedPreferences(prefName, Context.MODE_PRIVATE);
            JSONObject sPrefObj = new JSONObject();
            for (Map.Entry<String, ?> entry : sp.getAll().entrySet()) {
                sPrefObj.put(entry.getKey(), entry.getValue());
            }
            PluginResult result = new PluginResult(PluginResult.Status.OK, sPrefObj);
            callbackContext.sendPluginResult(result);
            return true;
        } else if (action.equals("setSharedPref")) {
            String prefName = data.optString(0);
            JSONObject prefDict = data.optJSONObject(1);

            if (prefName == null || prefName.isEmpty() || prefDict == null) {
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, "prefName is null or empty, or prefDict is null");
                callbackContext.sendPluginResult(result);
                return false;
            }

            SharedPreferences prefs = getActivity().getSharedPreferences(prefName, Context.MODE_PRIVATE);
            SharedPreferences.Editor prefEditor = prefs.edit();
            for (int i = 0; i < prefDict.names().length(); i++) {
                String keyName = prefDict.names().getString(i);
                Object val = prefDict.get(keyName);
                // only support string, boolean and int
                if (val instanceof Integer) {
                    prefEditor.putInt(keyName, ((Integer) val));
                } else if (val instanceof String) {
                    prefEditor.putString(keyName, val.toString());
                } else if (val instanceof Boolean) {
                    prefEditor.putBoolean(keyName, (Boolean) val);
                }
            }
            boolean success = prefEditor.commit();
            if (!success) {
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, "prefEditor commit failed");
                callbackContext.sendPluginResult(result);
                return false;
            }
            PluginResult result = new PluginResult(PluginResult.Status.OK, "prefEditor commit succeeded");
            callbackContext.sendPluginResult(result);
            return true;
        } else if (action.equals("getContentProvider")) {
            String uri = data.optString(0);
            Cursor cur = getActivity().getContentResolver().query(
                    Uri.parse(uri), null, null, null, null);

            String content = "";
            if (cur != null) { // if CP not available, cur will be null
                while (cur.moveToNext()) { // get the last row's first column'
                    content = cur.getString(0);
                }
            }
            if (content == null) {
                content = "";
            }
            PluginResult result = new PluginResult(PluginResult.Status.OK, content);
            callbackContext.sendPluginResult(result);
            Log.i(LOG_TAG, String.format("got result from content provider: len=%d", content.length()));
            return true;
        } else if (action.equals("getSystemInfo")) { // Get imei, packageName, versionName, versionCode
            return getSystemInfo(callbackContext);
        } else {
            return false;
        }
    }

    private Activity getActivity() {
        return cordova.getActivity();
    }

    private boolean getSystemInfo(CallbackContext callbackContext) {
        try {
            JSONObject obj = new JSONObject();
            TelephonyManager telephonyManager = (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
            Activity myActivity = getActivity();
            PackageManager packageManager = myActivity.getPackageManager();
            String packageName = myActivity.getPackageName();
            PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            Context context = myActivity.getBaseContext();
            int appNameResId = context.getApplicationInfo().labelRes;
            String appName = context.getString(appNameResId);
            // Local IP address V4
            WifiManager wm = (WifiManager) myActivity.getSystemService("wifi");
            String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
            // display info
            DisplayMetrics displayMetrics = myActivity.getBaseContext().getResources().getDisplayMetrics();

            obj.put("carrier", telephonyManager.getNetworkOperatorName());
            obj.put("packagename", packageName);
            obj.put("appname", appName);
            obj.put("installerpackagename", getInstallerPackageName());
            obj.put("versionname", packageInfo.versionName);
            obj.put("versioncode", packageInfo.versionCode);
            obj.put("localip", ip);
            obj.put("screenwidth", displayMetrics.widthPixels);
            obj.put("screenheight", displayMetrics.heightPixels);
            obj.put("displaydensity", displayMetrics.density);
            obj.put("useragent", System.getProperty("http.agent")); // http.agent

            PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
            callbackContext.sendPluginResult(result);
            return true;
        } catch (Exception e) {
            callbackContext.error(e.toString());
            return false;
        }
    }

    private String getInstallerPackageName() {
        String installerPackageName = "";
        try {
            Activity myActivity = getActivity();
            PackageManager packageManager = myActivity.getPackageManager();
            installerPackageName = packageManager.getInstallerPackageName(myActivity.getPackageName());
        } catch (Exception e) {
            Log.e(LOG_TAG, "failed to get installerPackageName", e);
        }
        return installerPackageName;
    }
}
