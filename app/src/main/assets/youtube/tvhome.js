// Injected at document start into youtube.com/tv (YouTube on TV) by YouTubeActivity.
// - Ads: InnerTube responses are cleaned before the app reads them (no ad breaks, no ad tiles).
// - SponsorBlock: segments come from the app (TVHomeBridge), playback skips over them.
// - window.close (the app's "Exit YouTube" choice) closes the activity.
(() => {
  if (window.__tvh) return;
  const bridge = window.TVHomeBridge;
  const post = (msg) => { try { bridge && bridge.postMessage(JSON.stringify(msg)); } catch (e) {} };

  // ---- ads -------------------------------------------------------------------------------------
  const notAd = (item) => !(item && (item.adSlotRenderer || item.adSlot));

  const cleanShelves = (list) => {
    if (!Array.isArray(list)) return list;
    const kept = list.filter(notAd);
    for (const shelf of kept) {
      const items = shelf?.shelfRenderer?.content?.horizontalListRenderer;
      if (items && Array.isArray(items.items)) items.items = items.items.filter(notAd);
    }
    return kept;
  };

  const clean = (r) => {
    if (!r || typeof r !== 'object' || Array.isArray(r)) return;
    if (r.adPlacements) r.adPlacements = [];
    if (r.adSlots) r.adSlots = [];
    if (r.playerAds) r.playerAds = false;
    if (r.adBreakHeartbeatParams) delete r.adBreakHeartbeatParams;

    const surface = r.contents?.tvBrowseRenderer?.content?.tvSurfaceContentRenderer?.content;
    if (surface?.sectionListRenderer?.contents) {
      surface.sectionListRenderer.contents = cleanShelves(surface.sectionListRenderer.contents);
    }
    if (surface?.gridRenderer?.items) surface.gridRenderer.items = surface.gridRenderer.items.filter(notAd);

    const cont = r.continuationContents;
    if (cont?.sectionListContinuation?.contents) {
      cont.sectionListContinuation.contents = cleanShelves(cont.sectionListContinuation.contents);
    }
    if (cont?.horizontalListContinuation?.items) {
      cont.horizontalListContinuation.items = cont.horizontalListContinuation.items.filter(notAd);
    }
    if (cont?.gridContinuation?.items) cont.gridContinuation.items = cont.gridContinuation.items.filter(notAd);
    if (r.contents?.sectionListRenderer?.contents) {
      r.contents.sectionListRenderer.contents = cleanShelves(r.contents.sectionListRenderer.contents);
    }

    // Shorts feed: sponsored reels.
    if (Array.isArray(r.entries)) {
      r.entries = r.entries.filter((e) => !e?.command?.reelWatchEndpoint?.adClientParams?.isAd);
    }
  };

  const parse = JSON.parse;
  JSON.parse = function (text, reviver) {
    const r = parse.call(this, text, reviver);
    try { clean(r); } catch (e) {}
    return r;
  };
  const json = Response.prototype.json;
  Response.prototype.json = function () {
    return json.call(this).then((r) => { try { clean(r); } catch (e) {} return r; });
  };

  // ---- DRM -------------------------------------------------------------------------------------
  // Many videos are Widevine-wrapped for TV clients. The TV player asks for Cobalt-only options
  // (extra MIME parameters, hardware-secure decode) that a WebView rejects. Retry with what a
  // WebView supports (software crypto, hardware decode) and report back the configuration the
  // player asked for, otherwise it refuses the formats ("This video format is not supported").
  const requestAccess = navigator.requestMediaKeySystemAccess?.bind(navigator);
  if (requestAccess) {
    const plainType = (t) => t && t.split(';').filter((p, i) => i === 0 || /^\s*codecs=/i.test(p)).join(';');
    const supported = (c) => ({ ...c, contentType: plainType(c.contentType), robustness: c.robustness ? 'SW_SECURE_CRYPTO' : '' });
    navigator.requestMediaKeySystemAccess = (keySystem, configs) => requestAccess(keySystem, configs).catch((err) => {
      if (keySystem !== 'com.widevine.alpha' || !Array.isArray(configs) || !configs.length) throw err;
      const fixed = configs.map((c) => ({
        ...c,
        videoCapabilities: (c.videoCapabilities || []).map(supported),
        audioCapabilities: (c.audioCapabilities || []).map(supported),
      }));
      return requestAccess(keySystem, fixed).then((access) => {
        const conf = { ...access.getConfiguration(), videoCapabilities: configs[0].videoCapabilities, audioCapabilities: configs[0].audioCapabilities };
        return { keySystem: access.keySystem, getConfiguration: () => conf, createMediaKeys: () => access.createMediaKeys() };
      });
    });
  }

  // ---- SponsorBlock ----------------------------------------------------------------------------
  let videoId = null;
  let segments = [];
  let lastSkip = { start: -1, at: 0 };

  const currentId = () => {
    const m = /[?&]v=([\w-]{11})/.exec(location.hash);
    return m ? m[1] : null;
  };

  const onRoute = () => {
    const id = currentId();
    if (id === videoId) return;
    videoId = id;
    segments = [];
    if (id) post({ t: 'video', id });
  };
  window.addEventListener('hashchange', onRoute);
  window.addEventListener('DOMContentLoaded', onRoute);

  // Media events don't bubble, but a capturing listener on document still sees them.
  document.addEventListener('timeupdate', (e) => {
    const v = e.target;
    if (!segments.length || !(v instanceof HTMLMediaElement) || v.paused) return;
    if (currentId() !== videoId) onRoute();
    const t = v.currentTime;
    for (const s of segments) {
      if (t >= s.start && t < s.end - 0.5) {
        // A user seeking back into a segment right after a skip gets to watch it.
        if (lastSkip.start === s.start && Date.now() - lastSkip.at < 8000) return;
        lastSkip = { start: s.start, at: Date.now() };
        v.currentTime = Math.min(s.end, (v.duration || s.end) - 0.1);
        post({ t: 'skipped', c: s.category });
        return;
      }
    }
  }, true);

  if (bridge) {
    bridge.onmessage = (e) => {
      let m;
      try { m = JSON.parse(e.data); } catch (err) { return; }
      if (m.t === 'segments' && m.id === videoId) {
        segments = m.s.map(([start, end, category]) => ({ start, end, category }));
      } else if (m.t === 'pause') {
        document.querySelectorAll('video').forEach((v) => v.pause());
      }
    };
  }

  // ---- exit ------------------------------------------------------------------------------------
  window.close = () => post({ t: 'exit' });

  window.__tvh = { post };
})();
