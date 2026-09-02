/* Arena AI start page logic — talks to the native shell through window.ArenaBridge. */
(function () {
  "use strict";

  var PREF = {
    home: "https://arena.ai",
    search: "google",
    desktop: false,
    adblock: true,
    dark: false,
    version: "1.0.0"
  };

  function bridge() {
    return window.ArenaBridge;
  }
  function isNative() {
    return typeof bridge() !== "undefined";
  }

  function nav(url) {
    if (isNative()) {
      bridge().openUrl(url);
    } else {
      window.location.href = url;
    }
  }

  function nativeOr(fnNative, fnWeb) {
    if (isNative()) return fnNative();
    return fnWeb();
  }

  function loadPrefs() {
    try {
      if (isNative()) {
        var p = JSON.parse(bridge().getPrefs() || "{}");
        for (var k in p) if (Object.prototype.hasOwnProperty.call(p, k)) PREF[k] = p[k];
      }
    } catch (e) { /* keep defaults */ }
    applyPrefs();
  }

  function applyPrefs() {
    var engines = {
      google: "Google", bing: "Bing", duckduckgo: "DuckDuckGo",
      startpage: "Startpage", yahoo: "Yahoo"
    };
    var ec = document.getElementById("engine");
    if (ec) ec.textContent = "Search: " + (engines[PREF.search] || PREF.search);
    var vc = document.getElementById("version");
    if (vc) vc.textContent = "v" + PREF.version;
  }

  var TILES = [
    { t: "Arena Battle", d: "Two anonymous models, you vote", c: "#38E1FF",
      icon: '<svg viewBox="0 0 24 24" fill="#38E1FF"><path d="M20 2H4a2 2 0 0 0-2 2v18l4-4h14a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2z"/></svg>',
      url: "https://arena.ai" },
    { t: "Direct chat", d: "Pick any model and go", c: "#7C5CFF",
      icon: '<svg viewBox="0 0 24 24" fill="#8B7CFF"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/></svg>',
      url: "https://arena.ai/direct" },
    { t: "Image studio", d: "Generate & edit images", c: "#FFD166",
      icon: '<svg viewBox="0 0 24 24" fill="#FFD166"><path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z"/></svg>',
      url: "https://arena.ai/image" },
    { t: "Video studio", d: "Create & edit video", c: "#3DDC97",
      icon: '<svg viewBox="0 0 24 24" fill="#3DDC97"><path d="M17 10.5V7a1 1 0 0 0-1-1H4a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-3.5l4 4v-11l-4 4z"/></svg>',
      url: "https://arena.ai/video" },
    { t: "Code arena", d: "Full-stack apps & webdev", c: "#38E1FF",
      icon: '<svg viewBox="0 0 24 24" fill="#38E1FF"><path d="M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z"/></svg>',
      url: "https://arena.ai/code" },
    { t: "Agents", d: "Autonomous agent battles", c: "#8B7CFF",
      icon: '<svg viewBox="0 0 24 24" fill="#8B7CFF"><path d="M12 2l2.4 7.2L22 12l-7.6 2.8L12 22l-2.4-7.2L2 12l7.6-2.8L12 2z"/></svg>',
      url: "https://arena.ai/agent" }
  ];

  var CHIPS = [
    { t: "Leaderboards", url: "https://arena.ai/leaderboard" },
    { t: "Side-by-side", url: "https://arena.ai/side-by-side" },
    { t: "Battle history", url: "https://arena.ai/history/search" },
    { t: "Downloads", action: "downloads" },
    { t: "Site status", url: "https://status.arena.ai" },
    { t: "Help", url: "https://help.arena.ai" }
  ];

  function renderChips() {
    var el = document.getElementById("chips");
    if (!el) return;
    var html = "";
    CHIPS.forEach(function (c) {
      html += '<button class="chipbtn" data-url="' + (c.url || "") +
        '" data-action="' + (c.action || "") + '">' + c.t + "</button>";
    });
    el.innerHTML = html;
    Array.prototype.forEach.call(el.querySelectorAll(".chipbtn"), function (b) {
      b.addEventListener("click", function () {
        var action = b.getAttribute("data-action");
        if (action === "downloads") {
          if (isNative()) bridge().openDownloads();
          return;
        }
        nav(b.getAttribute("data-url"));
      });
    });
  }

  function renderTiles() {
    var grid = document.getElementById("grid");
    if (!grid) return;
    var html = "";
    TILES.forEach(function (tile, i) {
      var extra = i === 0 ? ' class="tile hero-tile"' : ' class="tile"';
      html += '<div' + extra + ' data-url="' + (tile.url || "") + '" data-action="' +
        (tile.action || "") + '" role="button" tabindex="0" aria-label="' + tile.t + '">' +
        '<div class="ico">' + tile.icon + '</div>' +
        '<div><div class="t">' + tile.t + '</div>' +
        '<div class="d">' + tile.d + '</div></div>' +
        '<div class="arrow">›</div></div>';
    });
    grid.innerHTML = html;
    Array.prototype.forEach.call(grid.querySelectorAll(".tile"), function (el) {
      function go() {
        var action = el.getAttribute("data-action");
        if (action === "downloads") {
          nativeOr(function () { bridge().openDownloads(); },
            function () { window.open("about:blank"); });
          return;
        }
        nav(el.getAttribute("data-url"));
      }
      el.addEventListener("click", go);
      el.addEventListener("keydown", function (e) {
        if (e.key === "Enter" || e.key === " ") { e.preventDefault(); go(); }
      });
    });
  }

  // greeting
  function greet() {
    var h = new Date().getHours();
    var g = h < 5 ? "Burning the midnight oil" : h < 12 ? "Good morning"
      : h < 18 ? "Good afternoon" : "Good evening";
    var el = document.getElementById("greet");
    if (el) el.textContent = g + " — where to today?";
  }

  // search box: web search or direct URL
  function wireSearch() {
    var input = document.getElementById("search");
    if (!input) return;
    function go() {
      var q = input.value.trim();
      if (!q) return;
      input.value = "";
      input.blur();
      if (/^(https?:\/\/|\/\/)/i.test(q) ||
          (/^[\w-]+(\.[\w-]+)+([\/?#].*)?$/i.test(q) && !/\s/.test(q) && q.indexOf(".") > 0)) {
        var u = q.indexOf("://") === -1 ? "https://" + q : q;
        nativeOr(function () { bridge().openUrl(u); }, function () { nav(u); });
      } else {
        nativeOr(function () { bridge().doSearch(q); },
          function () { nav("https://www.google.com/search?q=" + encodeURIComponent(q)); });
      }
    }
    input.addEventListener("keydown", function (e) {
      if (e.key === "Enter") { e.preventDefault(); go(); }
    });
  }

  function wireSettings() {
    var btn = document.getElementById("settingsBtn");
    if (!btn) return;
    btn.addEventListener("click", function () {
      nativeOr(function () { bridge().openSettings(); },
        function () { nav("https://arena.ai"); });
    });
  }

  // live preference updates from native settings changes
  if (window.addEventListener) {
    window.addEventListener("arena-prefs", function (e) {
      try {
        var p = e.detail || {};
        for (var k in p) if (Object.prototype.hasOwnProperty.call(p, k)) PREF[k] = p[k];
        applyPrefs();
      } catch (err) { /* ignore */ }
    });
  }

  function init() {
    loadPrefs();
    greet();
    renderTiles();
    renderChips();
    wireSearch();
    wireSettings();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
