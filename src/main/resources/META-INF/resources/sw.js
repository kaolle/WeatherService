const VERSION = 'v3';
const SHELL_CACHE = `windsurf-shell-${VERSION}`;
const TILES_CACHE = `windsurf-tiles-${VERSION}`;
const API_CACHE   = `windsurf-api-${VERSION}`;

// Only pre-cache immutable CDN assets. index.html is fetched fresh on every load
// so app updates are always picked up (fallback to cache only when offline).
const SHELL_URLS = [
  'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css',
  'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'
];

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(SHELL_CACHE).then(cache => cache.addAll(SHELL_URLS)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys => Promise.all(
      keys.filter(k => ![SHELL_CACHE, TILES_CACHE, API_CACHE].includes(k)).map(k => caches.delete(k))
    )).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  const req = event.request;
  if (req.method !== 'GET') return;

  const url = new URL(req.url);

  // OSM tiles — cache-first (tiles never change for a given z/x/y)
  if (url.hostname === 'tile.openstreetmap.org') {
    event.respondWith(cacheFirst(req, TILES_CACHE));
    return;
  }

  // Anything on our own origin — network-first, cache is offline fallback.
  // This ensures HTML/JS/manifest updates are always picked up on next reload.
  if (url.origin === self.location.origin) {
    const isApi = url.pathname.startsWith('/spots') || url.pathname.startsWith('/settings') || url.pathname.startsWith('/discovery');
    event.respondWith(networkFirst(req, isApi ? API_CACHE : SHELL_CACHE));
    return;
  }

  // External assets (Leaflet CDN, etc.) — cache-first
  event.respondWith(cacheFirst(req, SHELL_CACHE));
});

async function cacheFirst(req, cacheName) {
  const cached = await caches.match(req);
  if (cached) return cached;
  try {
    const res = await fetch(req);
    if (res.ok) {
      const cache = await caches.open(cacheName);
      cache.put(req, res.clone());
    }
    return res;
  } catch (e) {
    return cached || Response.error();
  }
}

async function networkFirst(req, cacheName) {
  try {
    const res = await fetch(req);
    if (res.ok) {
      const cache = await caches.open(cacheName);
      cache.put(req, res.clone());
    }
    return res;
  } catch (e) {
    const cached = await caches.match(req);
    return cached || Response.error();
  }
}
