# CurseForge Cloudflare Worker

This Worker keeps the private CurseForge API key outside the Android app.

## Configuration

1. Create a Cloudflare Worker.
2. Add a secret named `CURSEFORGE_API_KEY`.
3. Deploy `worker.js`.
4. Build the app with `CURSEFORGE_PROXY_URL` set to the Worker URL plus `/v1`, for example:

```bash
CURSEFORGE_PROXY_URL=https://your-worker.your-account.workers.dev/v1 ./gradlew :app_onyxlauncher:assembleDebug
```

The Android app will call the Worker. The Worker forwards only `/v1/...` requests to the official CurseForge API and injects the API key server-side.
