/* Loaded only by the lobbies, which declare UTF-8, so the catalog can hold emoji. */
(function (root) {
  'use strict';

  var WELCOME_GIFT = 40;
  var POINTS_PER_COIN = 10;

  var CATALOG = [
    { id: 'ml', emoji: '🎮', name: 'Rest: play 1 ML (Mobile Legends) game', goal: 'ML game', coins: 40, perDay: 1 },
    { id: 'dinner', emoji: '🍽️', name: 'Choose what\'s for dinner', goal: 'Dinner pick', coins: 100 },
    { id: 'dessert', emoji: '🍨', name: 'Dessert treat', goal: 'Dessert treat', coins: 100 },
    { id: 'screen', emoji: '📱', name: '30 min extra screen time', goal: 'Extra screen time', coins: 120 },
    { id: 'movie', emoji: '🎬', name: 'Movie night pick', goal: 'Movie night', coins: 300 },
    { id: 'late', emoji: '🌙', name: 'Stay up 30 min later', goal: 'Stay up later', coins: 400 },
    { id: 'toy', emoji: '🧸', name: 'Small toy', goal: 'Small toy', coins: 500 },
    { id: 'big', emoji: '🎡', name: 'Big goal: outing or a toy you\'ve wanted', goal: 'Big goal', coins: 1200 }
  ];

  function pad(n) { return n < 10 ? '0' + n : String(n); }

  function dateKey(ms) {
    var d = new Date(ms);
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  }

  function findItem(id) {
    for (var i = 0; i < CATALOG.length; i++) if (CATALOG[i].id === id) return CATALOG[i];
    return null;
  }

  function own(obj, key) { return Object.prototype.hasOwnProperty.call(obj, key); }

  function create(storage, now, grade) {
    if (!/^grade[0-9]+$/.test(grade || '')) throw new Error('Wallet needs a grade like "grade5".');
    var KEY = grade + '_wallet_v1';

    function fresh() { return { v: 1, baselines: {}, spent: 0, purchases: [] }; }

    function read() {
      var raw = null;
      try { raw = storage.getItem(KEY); } catch (e) {}
      if (!raw) return fresh();
      try {
        var d = JSON.parse(raw);
        if (!d || d.v !== 1) return fresh();
        return {
          v: 1,
          baselines: d.baselines && typeof d.baselines === 'object' && !Array.isArray(d.baselines) ? d.baselines : {},
          spent: typeof d.spent === 'number' ? d.spent : 0,
          purchases: Array.isArray(d.purchases) ? d.purchases.filter(function (p) { return p && typeof p.t === 'number'; }) : []
        };
      } catch (e) {
        return fresh();
      }
    }

    function write(state) {
      try {
        storage.setItem(KEY, JSON.stringify(state));
        return true;
      } catch (e) {
        return false;
      }
    }

    function newPoints(state, points) {
      var gained = 0;
      Object.keys(points || {}).forEach(function (key) {
        if (own(state.baselines, key)) gained += (Number(points[key]) || 0) - (Number(state.baselines[key]) || 0);
      });
      return Math.max(0, gained);
    }

    function balanceOf(state, points) {
      var earned = Math.floor(newPoints(state, points) / POINTS_PER_COIN);
      return Math.max(0, WELCOME_GIFT + earned - state.spent);
    }

    function boughtToday(state, id) {
      var today = dateKey(now());
      return state.purchases.filter(function (p) { return p.item === id && dateKey(p.t) === today; }).length;
    }

    function check(state, id, points) {
      var item = findItem(id);
      if (!item) return { ok: false, need: 0, daily: false };
      if (item.perDay && boughtToday(state, id) >= item.perDay) return { ok: false, need: 0, daily: true };
      var need = Math.max(0, item.coins - balanceOf(state, points));
      return { ok: need === 0, need: need, daily: false };
    }

    return {
      grade: grade,
      catalog: CATALOG,

      track: function (points) {
        var state = read(), changed = false;
        Object.keys(points || {}).forEach(function (key) {
          if (!own(state.baselines, key)) { state.baselines[key] = Number(points[key]) || 0; changed = true; }
        });
        if (changed) write(state);
      },

      balance: function (points) { return balanceOf(read(), points); },

      earned: function (points) {
        var state = read(), gained = newPoints(state, points);
        return { points: gained, coins: Math.floor(gained / POINTS_PER_COIN), welcome: WELCOME_GIFT, spent: state.spent };
      },

      // Drops purchases before the last `days` local days; `spent` stays, so no coins come back.
      prune: function (days) {
        var d = new Date(now());
        var cutoff = new Date(d.getFullYear(), d.getMonth(), d.getDate() - (days - 1)).getTime();
        var state = read();
        var kept = state.purchases.filter(function (p) { return p.t >= cutoff; });
        if (kept.length === state.purchases.length) return;
        state.purchases = kept;
        write(state);
      },

      canBuy: function (id, points) { return check(read(), id, points); },

      buy: function (id, points) {
        var state = read();
        if (!check(state, id, points).ok) return null;
        var purchase = { item: id, coins: findItem(id).coins, t: now() };
        state.spent += purchase.coins;
        state.purchases.push(purchase);
        return write(state) ? purchase : null;
      },

      purchases: function () { return read().purchases; }
    };
  }

  var exported = { create: create, CATALOG: CATALOG, findItem: findItem };
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = exported;
    return;
  }
  try {
    var script = root.document && root.document.currentScript;
    root.Wallet = create(root.localStorage, Date.now, script ? script.getAttribute('data-grade') : null);
  } catch (e) {}
})(this);
