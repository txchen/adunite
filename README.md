# adunite
ads plugin for cordova app, only support interstitial/video ads type.

Now adunite supports the following networks:

* Admob
* Applovin
* Adcolony
* Chartboost

## Usage

First, add the plugin to your cordova project:

```
cordova plugin add cordova-plugin-adunite --save
```

Note: applovin sdk read the key from meta data, instead of code. You must define your sdk key in the plugin config, like this in `config.xml`:

```
<plugin name="cordova-plugin-adunite" spec="~LATEST_VERSION">
    <variable name="APPLOVIN_KEY" value="YOUR_SECRET_KEY_FROM_DASHBOARD" />
</plugin>
```

### Config your placements

The first thing you should do in your app, is to call `configAds` method.

```js
adunite.configAds({
    showCooldown: 60, // default showCooldown in seconds for each network
    loadCooldown: 25, // default loadCooldown in seconds for each network
    globalShowCooldown: 45, // after user see an ad, he can see the next only after 45 seconds
    initLastShow: new Date().getTime(),
    maxLoadRetry: 10,
    networks: {
      admob: { name: 'admob', pid: 'YOUR_ADMOB_PID', weight: 100, maxLoadRetry: 15 },
      applovin: { name: 'applovin', pid: 'ANY_STRING', weight: 70, showCooldown: 50 },
      adcolony: { name: 'adcolony', pid: 'APPID_ZONEID', weight: 70 },
      cb: { name: 'cb', pid: 'APPID_APPSIGNATURE', weight: 70 },
    }
  }, function (actualAdsOption) {
    // successCallback
    // actualAdsOption = merge(yourInput, aduniteDefault)
  }, function (err) {
    // errorCallback
  })
```

The first argument of `configAds` is your adsOptions. Adunite has a default one internally, and your input will merge with that one:

```js
{
  showCooldown: 60, // default showCooldown in seconds for each network
  loadCooldown: 25, // default loadCooldown in seconds for each network
  globalShowCooldown: 0, // 0 means disabled. Otherwise it is a global show cooldown in seconds
  initLastShow: 0,
  maxLoadRetry: -1, // -1 means no limit
  networks: {
    admob: { name: 'admob', pid: null, weight: 100 },
    applovin: { name: 'applovin', pid: null, weight: 100, maxLoadRetry: -1 },
    adcolony: { name: 'adcolony', pid: null, weight: 100, maxLoadRetry: -1 },
    cb: { name: 'cb', pid: null, weight: 100 },
  },
}
```

For `applovin`, since the key is already defined in config.xml, you just need to put any non-empty string to enable it.

There are two cooldown settings here, `showCooldown` resets when ads appear, and `loadCooldown` resets when ads load. This is a per network setting, you can set them in each network, or the top level default one will be applied to each network.

You can set `initLastShow` to `new Date().getTime()` or `new Date().getTime() - 30000` for example, to prevent ads coming out too soon in your game. If you don't set, each network will have it as 0, so that right after the 1st load, they can show, it might be too fast and might affect your app experience.

`maxLoadRetry` can be used to limit the retry attemps count. For example, one of your placement is not approved yet, you will always get error when you load. Set this value can limit the retry count, otherwise, it will retry forever. Like the cooldown settings, global setting is used if network level value is not specified.

Also, other than the network level `showCooldown`, you can also define a global show cooldown value `globalShowCooldown`. This can be helpful if you don't want your user to see the ads too frequently. By defautl this is disabled if you don't set it.

### Show ads

You don't need to care about loading ads, adunite will do all the dirty work for you. The only thing you need to do, is to call `showAds` in the proper positions, like when game is over, when gamer enters the next stage, when gamer wants extra life, etc.

Calling it cannot be easier:

```js
adunite.showAds(1500 /* delay */, function (network) {
    // successCallback
  }, function (err) {
    // errorCallback
  })
// delay can be used when your game or app have some tween/animation you want to show user first.
// if you don't need it, set to 0.
```

adunite will pick one of the ready ads, based on the weight, randomly pick one and show it.

That's it!

### Some extra bonus

`getAvailableAds` can be useful, if your game want to do some rewards to user.

For example, game is over, you want to offer a choice to your user: you can watch a video ads to get an extra life to revive and continue. In this case, you can do like this:

```js
// when game is over
var readyAds = adunite.getAvailableAds() // will return an array
// check the array, and optionally render your UI
```

## Changelog

**2017-02-25** `1.6.1`
Add back the get carrier function.

**2017-02-25** `1.6.0`
Remove READ_PHONE_STATE permission.

**2017-02-24** `1.5.0`
Remove permissions that google does not like.

**2017-02-24** `1.4.0`
Remove FBAN support.

**2017-02-24** `1.3.0`
Remove UnityAds support.
