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
  "version": 1,
  "updatedAt": "2026-08-07T11:30:00Z",
  "event": { "id": "choreo", "name": "Choreo", "url": "", "enabled": false }
}
```

- `name` is shown in the app as the bypass name ("Event (Choreo)").
- `url` is the proxy base URL, including any path prefix (e.g. `.../api/`).
- `enabled: false` or an empty `url` means "nothing published right now"; the
  app says so instead of routing traffic into a dead host.
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
