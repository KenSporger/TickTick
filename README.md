# TickTick personal server

## Android build modes

The default build produces the unified knowledge-backpack APK. On first launch it scans a one-time binding QR code and stores the exchanged bearer credential locally.

```bash
./gradlew assembleDebug
```

An independent private build embeds both the server URL and its installation-wide API token, so it opens directly without binding:

```bash
./gradlew assembleDebug \
  -PticktickApiBaseUrl=https://tasks.example.com/ \
  -PticktickApiToken=replace-with-the-server-api-token
```

Do not distribute an independent APK: its embedded credential can be recovered from the package. Use HTTPS for any server reachable outside a trusted private network.

## Backend runtime

Install `backend/requirements.txt`, set `TICKTICK_DB_PATH` and `TICKTICK_BINDING_STATE`, then run `backend/run.sh`. The example systemd unit in `backend/ticktick.service` uses the knowledge-backpack deployment paths.

All task and sync endpoints require the bearer returned by `POST /api/bind/consume`. The bind endpoint consumes a short-lived token exactly once.

## Release artifacts

`scripts/package-release.sh VERSION dist` creates a signed universal APK, a backend archive and `release-manifest.json` with SHA-256 values. It intentionally fails unless the four `TICKTICK_KEY*` signing variables are configured. Tagged GitHub builds read those values from repository secrets.
