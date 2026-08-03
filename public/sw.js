const CACHE = "racket-score-v2";
const ROOT = new URL("./", self.location.href).pathname;
self.addEventListener("install", event => event.waitUntil(caches.open(CACHE).then(cache => cache.addAll([ROOT, `${ROOT}manifest.webmanifest`, `${ROOT}favicon.svg`, `${ROOT}icon-192.png`, `${ROOT}icon-512.png`, `${ROOT}icon-maskable-512.png`, `${ROOT}apple-touch-icon.png`]))));
self.addEventListener("activate", event => event.waitUntil(Promise.all([self.clients.claim(), caches.keys().then(keys => Promise.all(keys.filter(key => key !== CACHE).map(key => caches.delete(key))))])));
self.addEventListener("message", event => { if (event.data?.type === "SKIP_WAITING") self.skipWaiting(); });
self.addEventListener("fetch", event => {
  if (event.request.method !== "GET") return;
  event.respondWith(fetch(event.request).then(response => { const copy = response.clone(); caches.open(CACHE).then(cache => cache.put(event.request, copy)); return response; }).catch(async () => (await caches.match(event.request)) || (event.request.mode === "navigate" ? caches.match(ROOT) : undefined)));
});
