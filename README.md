# adunite
ads plugin for cordova app, only support interstitial/video ads type.

Now adunite supports the following networks:

* Facebook Audience Network
* Admob
* Unity Ads

## Usage

First, add the plugin to your cordova project:

```
cordova plugin add cordova-plugin-adunite --save
```

### Config your placements

The first thing you should do in your app, is to call `configAds` method.

```js
adunite.configAds({
    showCooldown: 60, // global showCooldown in seconds
    loadCooldown: 25, // global loadCooldown in seconds
    initLastShow: new Date().getTime(),
    networks: {
      fban: { name: 'fban', pid: 'YOUR_FBAN_PID', weight: 100 },
      unity: { name: 'unity', pid: 'YOUR_UNITY_ADS_GAME_ID', weight: 50, showCooldown: 50 },
      admob: { name: 'admob', pid: 'YOUR_ADMOB_PID', weight: 100 },
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
  showCooldown: 60, // global showCooldown in seconds
  loadCooldown: 25, // global loadCooldown in seconds
  initLastShow: 0,
  networks: {
    fban: { name: 'fban', pid: null, weight: 100 },
    unity: { name: 'unity', pid: null, weight: 100 },
    admob: { name: 'admob', pid: null, weight: 100 },
  },
}
```

There are two cooldown settings here, `showCooldown` resets when ads appear, and `loadCooldown` resets when ads load. The global ones will be used, if network level setting is not specified.

You can set `initLastShow` to `new Date().getTime()` or `new Date().getTime() - 30000` for example, to prevent ads coming out too soon in your game. If you don't set, each network will have it as 0, so that right after the 1st load, they can show, it might be too fast and might affect your app experience.

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
