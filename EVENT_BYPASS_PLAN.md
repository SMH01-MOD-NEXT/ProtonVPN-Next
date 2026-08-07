# Event bypass — implementation plan

Two repositories are involved:

- App: `/home/smh01/Studio canaryProjects/ProtonVPN-Next-MIRROR`
- Website (config host): `/home/smh01/Studio canaryProjects/ProtonVPN-Next-WEB`

## Goal

Choreo is a stop-gap host: it works, but its stability and even its platform may
change at any time. Instead of shipping yet another hardcoded proxy URL in the
APK, the app gains an **Event** bypass strategy whose name and endpoint are
fetched at runtime from `event-bypass.json` published by the website.

When the platform changes, only that one JSON file has to be edited — no new
release of the app.

## Design

### Config file (website)

`public/event-bypass.json`:

```json
{
  "version": 2,
  "updatedAt": "2026-08-07T14:45:00Z",
  "events": [
    { "id": "choreo", "name": "Choreo", "url": "https://….choreoapps.dev/api/", "enabled": true }
  ]
}
```

- `events` is a list: publish as many bypasses as you like. The app shows them
  all and the user picks one; the choice is remembered across refreshes and only
  moves when that entry disappears from the config.
- `id` must be unique and stable — it is what the app stores as the choice.
- `expiresAt` is optional and written by hand: `dd-MM-yyyy` (for example
  `01-12-2026`) when the platform runs on a trial, `forever` when it does not,
  or empty when nobody knows yet. Empty and `forever` are different: the first
  shows nothing, the second says there is no deadline.
- `updatedAt` is shown in the app next to the local "last check" time, so the
  user can tell a stale config apart from a stale device.
- `name` is shown in the app as the bypass name ("Event (Choreo)").
- `url` is the proxy base URL, including any path prefix (e.g. `.../api/`).
- `enabled: false` parks an entry without losing its address; the app skips it.
- An empty list (or every entry disabled) means "nothing published right now";
  the app says so instead of routing traffic into a dead host.
- Version 1 published a single `event` object. The app still reads it, so old
  copies of the file on a stale mirror keep working.
- The file is served by every website deployment, so all mirrors expose it.

### Mirror order (app)

The app walks the mirrors in order and stops at the first valid answer:

1. GitLab raw (`gitlab.com/vpn-next-group/protonvpn-next-web`)
2. GitHub raw (`SMH01-MOD-NEXT/ProtonVPN-Next-WEB`)
3. Deno Deploy
4. Cloudflare (`kvn.protonnext.qzz.io`, then `home.protonnext.qzz.io`)
5. Netlify

Git forges come first on purpose: they are the source of truth, they are hard to
block without collateral damage, and they answer even when a hosting platform is
mid-migration.

### Choosing between bypasses

The app caches the whole published list, so switching is instant and works
offline: only the selected id, name and URL change. The name and URL of the
selected entry are stored flat next to the list, because the OkHttp interceptor
reads the routing target synchronously on every request and must not parse JSON
there. When only one bypass is published the picker is hidden.

### Refresh rules

- Automatically once a day through WorkManager (`NetworkType.CONNECTED`).
- Manually with a button on the API bypass screen.
- Refusals, surfaced in the UI instead of failing silently:
  - no internet at all;
  - a third-party VPN is up (our own tunnel does not count) — the fetch would
    go through someone else's exit node and could return their view of the file.
- Privacy builds never schedule the daily job; the manual button still works.

## Steps

- [x] 1. Website: add `public/event-bypass.json` and a test that validates it.
- [x] 2. App: `EventBypassConfig` model + `EventBypassApi`.
- [x] 3. App: `EventBypassRepository` (mirror walk, guards, persistence).
- [x] 4. App: `SettingsManager` — `STRATEGY_EVENT`, cached name/url/sync time,
      sync getters for the OkHttp interceptor. The cache is deliberately left out
      of backup/restore: it is remote data that the app refetches anyway, and a
      restored stale URL would point at a host that no longer exists.
- [x] 5. App: `NetworkModule` — route requests through the cached event URL.
- [x] 6. App: `EventBypassManager` + `EventBypassWorker`, scheduled from
      `ProtonNextApp`.
- [x] 7. App: `SettingsViewModel` + `ApiBypassScreen` — strategy row, status
      card, manual refresh button.
- [x] 8. App: strings in `values` + all six translations.
- [x] 9. App: unit tests for the config parsing and the URL guards.
- [x] 10. Build both projects, run tests, commit.
      Website: `npm test` 123/123, `npm run build` OK.
      App: `:app:compileNightlyStandardDebugKotlin` +
      `:app:testNightlyStandardDebugUnitTest` BUILD SUCCESSFUL.

## Follow-up for the user

The Choreo hostname is not in either repository, so `url` ships empty and the
strategy reports "not configured". Fill in `public/event-bypass.json` with the
real `https://<app>.choreoapps.dev/api/` endpoint and redeploy the website — the
app picks it up within a day, or immediately via the refresh button.
