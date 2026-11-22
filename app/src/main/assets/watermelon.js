(function () {
  'use strict';
  if (window.__WM9_ACTIVE__) return;
  window.__WM9_ACTIVE__ = true;

  var CFG = {
    feedOn: true, feedLevel: 9, mergeOn: true, mergeEveryMs: 120,
    sweepOn: true, sweepLine: 10, sweepAlso11: true, reopenAtL11: 5,
    realHammerOn: true, realOnFail: 'leave', realMaxFail: 3, realGapMs: 380,
    dropBaseMs: 500, dropJitter: 0.35, pauseEvery: 22, pauseMs: 1300,
    spendCoins: true, coinCost: 2, dynScore: true, scoreTarget: 30000,
    scoreFree: 20000, scoreRaceFirst: 5000, scoreRaceSecond: 20000,
    reopenMin: 1.5, reopenMax: 4.0, failBackoffSec: 60, autoRun: true
  };

  var S = {
    running: false, hooked: false, drops: 0, merges: 0, swept10: 0, swept11: 0,
    battles: 0, battleId: null, opening: false, openBlockUntil: 0,
    nextDropAt: 0, pauseLeft: 0, userHalt: false, settling: false,
    realUsed: 0, realSpent: 0, realFailStreak: 0, realBattleUsed: 0,
    realPending: {}, realBusy: false, l11ThisBattle: 0, l11Seen: {}, l11Total: 0,
    lastUser: null
  };

  var _ifr = null, lastMergeAt = 0, SWP = [], BK = [null,[],[],[],[],[],[],[],[],[],[]];
  function nowSec() { return Date.now() / 1000; }
  function numOr(v, d) { var n = Number(v); return isFinite(n) ? n : d; }
  function rd(o, k, d) { try { var v = o[k]; return v === undefined ? d : v; } catch (e) { return d; } }
  function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }

  function gameWindow() {
    if (!_ifr || !_ifr.isConnected) _ifr = document.querySelector('iframe[src*="vibe-lab"]') || null;
    if (_ifr && _ifr.contentWindow) return _ifr.contentWindow;
    try { if (window.cc && window.cc.director) return window; } catch (e) {}
    return null;
  }

  function sceneName() {
    try {
      var w = gameWindow();
      var sc = w && w.cc && w.cc.director && w.cc.director.getScene();
      return sc ? sc.name : null;
    } catch (e) { return null; }
  }

  function eachComponent(fn) {
    var w = gameWindow();
    var scene = w && w.cc && w.cc.director && w.cc.director.getScene();
    if (!scene) return null;
    var st = [scene], guard = 0;
    while (st.length && guard++ < 25000) {
      var n = st.pop();
      if (!n || !n.isValid) continue;
      var comps = n._components || [];
      for (var i = 0; i < comps.length; i++) {
        try { var r = fn(comps[i]); if (r) return r; } catch (e) {}
      }
      var ch = n.children || [];
      for (var j = 0; j < ch.length; j++) st.push(ch[j]);
    }
    return null;
  }

  function findGame(force) {
    var w = gameWindow();
    if (!w) return null;
    var cached = w.__WM9_G__;
    if (cached && !(cached.node && cached.node.isValid)) cached = w.__WM9_G__ = null;
    if (!force && cached && typeof cached.removeFruit === 'function') return cached;
    var g = eachComponent(function (c) {
      return (typeof c.removeFruit === 'function' && typeof c.dropCurrentFruitAt === 'function') ? c : null;
    });
    if (g) w.__WM9_G__ = g;
    return g;
  }

  function alive(g) {
    if (!g || !g.node || !g.node.isValid || !g.node.activeInHierarchy || !g.fruits) return false;
    return rd(g, 'isPlaying', false) === true && rd(g, 'gameOver', true) === false;
  }

  function userGameInfo() {
    try {
      var w = gameWindow(), h = w && w.__MAKE_WATERMELON_HOST__;
      var d = h && typeof h.getBootstrapData === 'function' ? h.getBootstrapData() : null;
      var ug = d && d.userGameInfo;
      if (!ug) return null;
      S.lastUser = { account: numOr(ug.account, 0), freeGameNum: numOr(ug.freeGameNum, 0) };
      return S.lastUser;
    } catch (e) { return null; }
  }

  function ensureHook() {
    var g = findGame();
    if (!g) return false;
    var proto = Object.getPrototypeOf(g);
    if (!proto || typeof proto.randomStartLevel !== 'function') return false;
    if (proto.__wm9Patched === true) { S.hooked = true; return true; }
    var orig = proto.__wm9Orig || proto.randomStartLevel;
    proto.__wm9Orig = orig;
    proto.randomStartLevel = function () {
      var real = orig.call(this);
      return CFG.feedOn ? CFG.feedLevel : real;
    };
    proto.__wm9Patched = true;
    S.hooked = true;
    return true;
  }

  function tryDrop(g) {
    var t = Date.now(), cur = rd(g, 'currentFruit', null), pend = rd(g, 'pendingDrop', false);
    if (!cur || pend) { S.nextDropAt = t + 80; return; }
    if (t < S.nextDropAt) return;
    if (t / 1000 < (Number(rd(g, 'nextDropAllowedAt', 0)) || 0)) { S.nextDropAt = t + 80; return; }
    var half = numOr(rd(g, 'playWidth', 684), 684) / 2;
    var x = (Math.random() * 2 - 1) * Math.min(14, half * 0.05);
    var ok = false;
    try {
      var vw = numOr(rd(g, 'visibleWidth', 0), 0), vh = numOr(rd(g, 'visibleHeight', 0), 0);
      var p = rd(rd(cur, 'node', null), 'position', null);
      if (vw > 0 && typeof g.onTouchEnd === 'function') {
        var seq = S.touchSeq = (S.touchSeq || 0) + 1;
        var touch = { getID: function () { return seq; }, getUILocation: function () { return { x: x + vw / 2, y: (p ? Number(p.y) || 0 : 500) + vh / 2 }; } };
        g.onTouchStart(touch); g.onTouchMove(touch); g.onTouchEnd(touch); ok = true;
      }
    } catch (e) {}
    if (!ok) { try { g.dropCurrentFruitAt(x); ok = true; } catch (e) {} }
    if (ok) {
      S.drops++;
      var gap = CFG.dropBaseMs * (1 + (Math.random() * 2 - 1) * CFG.dropJitter);
      if (S.drops % CFG.pauseEvery === 0) gap = CFG.pauseMs * (0.8 + Math.random() * 0.5);
      S.nextDropAt = t + gap;
    } else {
      S.nextDropAt = t + 600;
    }
  }

  function mergeCeil() { return Math.max(1, Math.min(10, numOr(CFG.sweepLine, 10) - 1)); }

  function boardPass(g) {
    SWP.length = 0;
    for (var k = 1; k <= 10; k++) BK[k].length = 0;
    var ceil = mergeCeil(), line = numOr(CFG.sweepLine, 10);
    var curNode = rd(rd(g, 'currentFruit', null), 'node', null);
    try {
      for (var f of g.fruits.values()) {
        var n = rd(f, 'node', null);
        if (!n || !n.isValid) continue;
        var lv = Number(f.level) || 0;
        if (lv >= 11) {
          var k11 = String(f.id);
          if (!S.l11Seen[k11]) { S.l11Seen[k11] = 1; S.l11ThisBattle++; S.l11Total++; }
        }
        if (CFG.sweepOn && lv >= line && (lv < 11 || CFG.sweepAlso11)) { SWP.push(f); continue; }
        if (CFG.mergeOn && lv > 0 && lv <= ceil && f.dropped && !f.merging && n !== curNode) {
          BK[lv].push(f);
        }
      }
    } catch (e) {}

    if (CFG.sweepOn) {
      var realList = [];
      for (var i = 0; i < SWP.length; i++) {
        var sf = SWP[i], slv = Number(sf.level) || 0, sid = Number(sf.id);
        if (slv >= 11 && CFG.realHammerOn && S.realFailStreak < CFG.realMaxFail) {
          if (!S.realPending[sid]) realList.push(sf);
          continue;
        }
        if (slv >= 11 && CFG.realHammerOn && CFG.realOnFail === 'leave') continue;
        try { g.removeFruit(sf, true); if (slv >= 11) S.swept11++; else S.swept10++; } catch (e) {}
      }
      if (realList.length && !S.realBusy) realClearBatch(realList, g);
    }

    if (CFG.mergeOn && typeof g.tryQueueMerge === 'function' && !rd(g, 'toolUseInProgress', false)) {
      var nowT = nowSec();
      if (nowT >= lastMergeAt + CFG.mergeEveryMs / 1000) {
        lastMergeAt = nowT;
        for (var lv = 1; lv <= ceil; lv++) {
          var arr = BK[lv];
          for (var j = 0; j + 1 < arr.length; j += 2) {
            try { if (g.tryQueueMerge(arr[j], arr[j + 1], nowT)) S.merges++; } catch (e) {}
          }
        }
      }
    }
  }

  function realClearBatch(list, g) {
    S.realBusy = true;
    (async function () {
      try {
        for (var i = 0; i < list.length; i++) {
          if (!S.running || !alive(g) || S.realFailStreak >= CFG.realMaxFail) break;
          var f = list[i], id = Number(f.id);
          if (!f.node || !f.node.isValid || S.realPending[id]) continue;
          S.realPending[id] = 1;
          var ok = false;
          try { ok = await g.confirmToolUse('hammer'); } catch (e) {}
          if (ok) {
            var p = f.node.position || { x: 0, y: 0 };
            try { g.toolUseInProgress = true; g.playHammerClear(f, { x: p.x || 0, y: p.y || 0 }); } catch (e) { g.toolUseInProgress = false; }
            S.realUsed++; S.realBattleUsed++; S.realSpent += 2; S.realFailStreak = 0;
          } else {
            S.realFailStreak++;
          }
          delete S.realPending[id];
          await sleep(CFG.realGapMs);
        }
      } finally { S.realBusy = false; }
    })();
  }

  function scoreCap() {
    if (!CFG.dynScore) return numOr(CFG.scoreTarget, 0);
    var d = new Date(), mot = d.getHours() * 60 + d.getMinutes();
    if (mot >= 30 && mot < 720) return CFG.scoreFree;
    return d.getMinutes() < 30 ? CFG.scoreRaceFirst : CFG.scoreRaceSecond;
  }

  function openBattle() {
    if (S.opening || nowSec() < S.openBlockUntil) return;
    var ug = userGameInfo();
    if (ug && ug.freeGameNum <= 0 && (!CFG.spendCoins || ug.account < CFG.coinCost)) {
      S.running = false; return;
    }
    var ctrl = eachComponent(function (c) {
      return (typeof c.startGame === 'function' && typeof c.dropCurrentFruitAt !== 'function') ? c : null;
    });
    if (!ctrl) return;
    S.opening = true;
    Promise.resolve(ctrl.startGame()).then(function () {
      return sleep(2000);
    }).then(function () {
      var g = findGame(true);
      if (g) {
        S.battles++; S.battleId = String(g.battleId || '');
        S.realFailStreak = 0; S.realBattleUsed = 0; S.l11ThisBattle = 0; S.l11Seen = {};
        ensureHook();
      }
      S.opening = false;
    }).catch(function () {
      S.opening = false;
      S.openBlockUntil = nowSec() + CFG.failBackoffSec;
    });
  }

  function tick() {
    if (CFG.autoRun && !S.running && !S.userHalt) S.running = true;
    if (!S.running) return;
    var sc = sceneName();
    if (sc === 'game') {
      ensureHook();
      var g = findGame();
      if (g && alive(g)) {
        tryDrop(g);
        boardPass(g);
        var cap = scoreCap(), score = Number(g.renderedScore) || 0;
        if (!S.settling && ((cap > 0 && score >= cap) || (CFG.reopenAtL11 > 0 && S.l11ThisBattle >= CFG.reopenAtL11))) {
          S.settling = true;
          try { g.submitActiveEnd(); } catch (e) {}
          setTimeout(function () { S.settling = false; }, 6000);
        }
      }
    } else if (sc === 'home') {
      openBattle();
    }
  }

  function buildPanel() {
    if (document.getElementById('wm9_panel')) return;
    var st = document.createElement('style');
    st.textContent = '#wm9_panel{position:fixed;top:12px;right:8px;z-index:999999;width:196px;background:rgba(22,25,34,.92);backdrop-filter:blur(8px);color:#F3F4F6;border:1px solid rgba(255,255,255,.14);border-radius:14px;box-shadow:0 8px 24px rgba(0,0,0,.35);font:11px/1.4 sans-serif;overflow:hidden}' +
      '#wm9_panel.min{width:auto;border-radius:999px}#wm9_panel.min .bd{display:none}' +
      '#wm9_panel .hd{background:rgba(255,255,255,.04);color:#F3F4F6;padding:7px 10px;font-weight:700;display:flex;align-items:center;justify-content:space-between;gap:8px}' +
      '#wm9_panel .bd{padding:7px 9px}#wm9_panel .row{display:flex;justify-content:space-between;align-items:center;margin:3px 0}' +
      '#wm9_panel .btns{display:grid;grid-template-columns:1fr 1fr;gap:5px;margin-top:6px}' +
      '#wm9_panel select{background:#242938;color:#F3F4F6;border:1px solid rgba(255,255,255,.14);border-radius:6px;padding:2px 6px;font-size:11px}#wm9_panel button{border:0;border-radius:8px;padding:6px 0;color:#fff;font-weight:700;cursor:pointer}';
    document.head.appendChild(st);
    var el = document.createElement('div');
    el.id = 'wm9_panel'; el.className = 'min';
    el.innerHTML = '<div class="hd"><span id="wm9_ttl">🍉 ● 20000 · 0局</span><span id="wm9_fold" style="background:rgba(255,255,255,.25);padding:0 6px;border-radius:4px">＋</span></div>' +
      '<div class="bd">' +
      '<div class="row"><span>状态</span><b id="wm9_run">–</b></div>' +
      '<div class="row"><span>掉落等级</span><select id="wm9_lv"></select></div>' +
      '<div class="row"><span>清扫线</span><select id="wm9_line"><option value="10">敲L10(假锤)</option><option value="11">敲L11(真锤)</option></select></div>' +
      '<div class="row"><span>本段上限</span><b id="wm9_cap">–</b></div>' +
      '<div class="row"><span>分数</span><b id="wm9_sc">–</b></div>' +
      '<div class="row"><span>局/投/合</span><b id="wm9_stat">–</b></div>' +
      '<div class="row"><span>L11/真锤</span><b id="wm9_l11">–</b></div>' +
      '<div class="btns"><button id="wm9_go" style="background:#FF6A3D">▶ 开跑</button><button id="wm9_halt" style="background:#374151">■ 停止</button></div>' +
      '</div>';
    document.body.appendChild(el);
    var sel = el.querySelector('#wm9_lv');
    for (var i = 1; i <= 11; i++) { var o = document.createElement('option'); o.value = i; o.textContent = 'L' + i; sel.appendChild(o); }
    sel.value = '9';
    sel.onchange = function (e) { CFG.feedLevel = Number(e.target.value) || 9; };
    el.querySelector('#wm9_line').onchange = function (e) { CFG.sweepLine = Number(e.target.value) === 11 ? 11 : 10; };
    el.querySelector('#wm9_go').onclick = function () { S.userHalt = false; S.running = true; };
    el.querySelector('#wm9_halt').onclick = function () { S.userHalt = true; S.running = false; };
    el.querySelector('#wm9_fold').onclick = function () {
      var min = el.classList.toggle('min');
      el.querySelector('#wm9_fold').textContent = min ? '＋' : '–';
    };
    setInterval(function () {
      var g = findGame(), sc = g && alive(g) ? (Number(g.renderedScore) || 0) : '–';
      el.querySelector('#wm9_ttl').textContent = (S.running ? '🍉 ● ' : '🍉 ○ ') + scoreCap() + ' · ' + S.battles + '局';
      el.querySelector('#wm9_run').textContent = S.running ? '运行中' : '已停止';
      el.querySelector('#wm9_cap').textContent = scoreCap() + '分';
      el.querySelector('#wm9_sc').textContent = sc;
      el.querySelector('#wm9_stat').textContent = S.battles + '/' + S.drops + '/' + S.merges;
      el.querySelector('#wm9_l11').textContent = S.l11ThisBattle + '/' + S.realUsed + '次';
    }, 1000);
  }

  window.__WM9__ = { cfg: CFG, state: S };
  var tries = 0;
  (function wait() {
    if (gameWindow() && document.body && document.head) {
      buildPanel();
      setInterval(tick, 150);
      return;
    }
    if (++tries < 60) setTimeout(wait, 500);
  })();
})();
