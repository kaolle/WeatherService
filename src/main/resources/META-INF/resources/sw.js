const VERSION = 'v1';
const SHELL_CACHE = `windsurf-shell-${VERSION}`;
const TILES_CACHE = `windsurf-tiles-${VERSION}`;
const API_CACHE   = `windsurf-api-${VERSION}`;

const SHELL_URLS = [
  '/',
  '/index.html',
  '/icon.svg',
  '/manifest.json',
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

  // OSM tiles — cache-first, tiles never change
  if (url.hostname === 'tile.openstreetmap.org') {
    event.respondWith(cacheFirst(req, TILES_CACHE));
    return;
  }

  // API — network-first, fall back to cache when offline
  if (url.origin === self.location.origin && (url.pathname.startsWith('/spots') || url.pathname.startsWith('/settings'))) {
    event.respondWith(networkFirst(req, API_CACHE));
    return;
  }

  // App shell + Leaflet CDN — cache-first
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
