# ABDM TrueNAS Web UI

This is the first native web interface for the TrueNAS/headless ABDM project.

## Current state

- Dark ABDM-style navigation and dashboard
- Downloads table with percentage, speed and ETA
- Pagination controls with 10 / 25 / 50 items per page
- Queue page with pagination
- Add-download dialog
- Browser, categories, scheduler and settings placeholders
- API adapter in `web/js/api.js`
- Uses the existing ABDM REST endpoints when available

The existing ABDM API currently exposes `/ping`, `/queues`, `/add`, and `/start-headless-download`. Rich download-management screens will be connected after the headless backend API is expanded.

## Development

Serve the repository root with any static HTTP server and open `/web/`. In the final TrueNAS app, the web directory will be served by the web container and `/api` will be reverse-proxied to the ABDM backend.

## Storage target

The intended TrueNAS datasets are:

- `dataPool/abdm/config`
- `dataPool/abdm/downloads`
- `dataPool/abdm/temp`

Do not hard-code host filesystem paths into the downloader core; container paths will be mapped by the final TrueNAS application configuration.
