/* Arena AI cosmetic ad/annoyance hiding.
   Injected after page load when ad-blocking is enabled.
   Conservative list — hides ad containers, never main content. */
(function () {
  try {
    if (window.__arenaCosmeticDone) return;
    window.__arenaCosmeticDone = true;

    var SELECTORS = [
      '[id^="google_ads_"]', '[id*="-ad-"]', '[class*="-ad-"]',
      '[id*="_ad_"]', '[class*="_ad_"]',
      '[data-ad]', '[data-ad-slot]', '[data-google-query-id]',
      '.ad-banner', '.ad-banner-container', '.ad-container', '.ad-wrapper',
      '.advertisement', '.advert', '.adsbygoogle', '.adslot', '.ad-unit',
      '.sponsored-content', '.sponsored-post', '.sponsored', '.ad-slot',
      '.dfp-ad', '.gpt-ad', '.taboola', '.trc_related_container',
      'div[class*="advertise"]', 'div[class*="Advertisement"]',
      'div[id*="div-gpt-ad"]', 'iframe[src*="doubleclick"]',
      'iframe[src*="googlesyndication"]'
    ];

    function hide() {
      var nodes = document.querySelectorAll(SELECTORS.join(','));
      for (var i = 0; i < nodes.length; i++) {
        var el = nodes[i];
        if (el && el.parentNode && el.offsetParent !== null) {
          el.style.display = 'none';
        }
      }
    }

    hide();
    var t1 = setTimeout(hide, 1200);
    var t2 = setTimeout(hide, 3500);

    if (window.MutationObserver) {
      var obs = new MutationObserver(function () { hide(); });
      obs.observe(document.documentElement, { childList: true, subtree: true });
      setTimeout(function () { obs.disconnect(); clearTimeout(t1); clearTimeout(t2); }, 12000);
    }
  } catch (e) { /* never break the page */ }
})();
