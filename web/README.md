# ABDM TrueNAS Web UI

This directory contains the native browser UI bundled into the headless TrueNAS build.

## Current implementation

- Dashboard with live active/queued/completed counts and aggregate speed
- Latest downloads with pagination and 10 / 25 / 50 items per page
- Downloads page with search, progress, speed, ETA, pause, resume, retry, queue assignment, and removal
- Download details dialog
- Queue management with start/stop, rename, concurrency, ordering, and removal from queue
- Per-queue scheduler controls for active days, start/stop times, and stop-when-empty
- Download-folder browser with root/up navigation and pagination
- Category listing, creation, rename, and deletion of custom categories
- Settings for download behavior, storage, API port, API enablement, and API-key authentication
- Same-origin API adapter with optional `X-API-Key` authentication
- Live backend refresh every 1.5 seconds

## Architecture

The page is served directly by Ktor from the packaged application resources:

```
Browser -> Ktor web UI -> authenticated REST API -> ABDM DownloadSystem
```

There is no Xvfb, no noVNC, and no virtual desktop involved.

## Development

The final container serves the UI from the root path. For local static inspection, serve the repository's `web/` directory with any static HTTP server, but API-backed features require the ABDM backend.

## Storage

The intended TrueNAS datasets are:

- `dataPool/abdm/config`
- `dataPool/abdm/downloads`
- `dataPool/abdm/temp`

Inside the container these are mounted as `/config`, `/downloads`, and `/temp`.

Do not hard-code host filesystem paths into the downloader core; host dataset paths belong in the TrueNAS/Compose deployment configuration.
