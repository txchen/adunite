function deepmerge(target, src) {
  var array = Array.isArray(src);
  var dst = array && [] || {};

  if (array) {
    target = target || [];
    dst = dst.concat(target);
    src.forEach(function (e, i) {
      if (typeof dst[i] === 'undefined') {
        dst[i] = e;
      } else if (typeof e === 'object') {
        dst[i] = deepmerge(target[i], e);
      } else {
        if (target.indexOf(e) === -1) {
          dst.push(e);
        }
      }
    });
  } else {
    if (target && typeof target === 'object') {
      Object.keys(target).forEach(function (key) {
        dst[key] = target[key];
      })
    }
    Object.keys(src).forEach(function (key) {
      if (typeof src[key] !== 'object' || !src[key]) {
        dst[key] = src[key];
      }
      else {
        if (!target[key]) {
          dst[key] = src[key];
        } else {
          dst[key] = deepmerge(target[key], src[key]);
        }
      }
    });
  }
  return dst;
}

function log (logLine) {
  console.log('[adunite] ' + logLine)
}

module.exports = {
  /////// states
  _inited: false,
  _adsOptions: {
    showCooldown: 45, // global showCooldown in seconds
    loadCooldown: 10, // global loadCooldown in seconds
    networks: {
      fban: { name: 'fban', pid: null, weight: 100 },
      unity: { name: 'unity', pid: null, weight: 100 },
    },
  },
  _adsStates: {
    fban: { ready: false, lastShown: 0, lastLoad: 0 },
    unity: { ready: false, lastShown: 0, lastLoad: 0 }
  },

  configAds: function (options, successCallback, errorCallback) {
    this._adsOptions = deepmerge(this._adsOptions, options)
    // remove those unused networks, apply default cooldown to networks
    var availableNetworks = 0
    for (var property in this._adsOptions.networks) {
      if (!this._adsOptions.networks[property].showCooldown) {
        this._adsOptions.networks[property].showCooldown = this._adsOptions.showCooldown
      }
      if (!this._adsOptions.networks[property].loadCooldown) {
        this._adsOptions.networks[property].loadCooldown = this._adsOptions.loadCooldown
      }
      if (this._adsOptions.networks[property].pid === null) {
        delete this._adsOptions.networks[property]
        delete this._adsStates[property]
      }
      availableNetworks++
    }
    log('Actual adsOption = ' + JSON.stringify(this._adsOptions, null, 2))
    log(availableNetworks + ' network(s) available in this session')

    // call java init method
    var unityGameId = null
    if (this._adsOptions.networks.unity) {
      unityGameId = this._adsOptions.networks.unity.pid
    }

    var self = this
    cordova.exec(function (adsEvent) {
        if (adsEvent && adsEvent.network_name) {
          // adunite_event contains network_name, event_name, event_detail
          //   event_name = START | READY | FINISH | LOADERROR | CLICK
          cordova.fireWindowEvent('adunite_event', adsEvent)
          if (adsEvent.event_name === 'READY') {
            log('[warn] ' + adsEvent.network_name + ' loaded ok, is ready.')
            // set the ready state
            self._adsStates[adsEvent.network_name].ready = true
          } else if (adsEvent.event_name === 'LOADERROR') {
            // decide when to load again based on lastLoad
          } else if (adsEvent.event_name === 'FINISH') {
            // TODO: set the state, and load next
            self._loadAds(adsEvent.network_name)
          }
        } else { // not adsEvent, means first callback when init is done.
          log('Adunite.init is done')
          // now load ads from all network
          for (var property in self._adsStates) {
            self._loadAds(property)
          }
        }
      }, function (err) {
        log('[error] failed to call Adunite.init', err)
        cordova.fireWindowEvent("adunite_init_failure", { type: 'init_failure', error: err })
      }, 'Adunite', 'init', [ unityGameId ])

    successCallback()
  },

  showAds: function (successCallback, errorCallback) {
    // get all the available ads, based on java layer and showCooldown
    // then based on weight, pick one of them.
    // if fail to pick one, call errorCallback
    for (var property in this._adsStates) {
      // TODO: make it correct
      if (this._adsStates[property].ready) {
        this._adsStates[property].ready = false
        this._adsStates[property].lastShown = new Date().getTime()
        cordova.exec(successCallback, errorCallback,
          'Adunite', 'showAds', [ property ])
      }
    }
  },

  getAdsStates: function (successCallback) {
    successCallback(this._adsStates)
  },

  _loadAds: function (networkName) {
    if (networkName === 'unity') {
      return // no-op, unity ads loading is not controlled by us
    }
    if (this._adsOptions.networks[networkName]) {
      this._adsStates[networkName].lastLoad = new Date().getTime()
      cordova.exec(function () { }, function () { },
        'Adunite', 'loadAds', [ networkName, this._adsOptions.networks[networkName].pid ])
    } else {
      log('[error] ' + networkName + ' is not enabled in this session, cannot load')
    }
  },
}
