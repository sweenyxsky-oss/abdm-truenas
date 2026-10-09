# ABDM TrueNAS desktop-style web interface

The v1.0 interface recreates the original ABDM Windows home layout using its actual icon paths and default dark/light theme colors. File, Tasks, Tools and Help menus replace the old navigation sidebar; the home page has nested All/Finished/Unfinished categories, queues, original toolbar commands, a sortable download table and status bar.

## Preserved service features

Download capture, add/import, pause/resume/retry, removal with optional file deletion, queue assignment and ordering, scheduling, categories, settings, download connection details and embedded Firefox remain connected to the same authenticated API. No downloader, headless-runtime, container or extension changes are required. Desktop-only exit/update controls are omitted because TrueNAS manages the service and updates manually.

## Browser adaptations

Phone filters scroll horizontally. The table and toolbar also scroll when space is limited. Download names open details; right-click opens task actions, and touch users can use the selection toolbar and details. Firefox restart retains the Safari touch handler and uncached request. Settings and prompts use the same desktop styling but expose only supported TrueNAS settings, rather than inventing unsupported desktop controls.

## Structure

- `js/api.js`: unchanged same-origin authenticated API client.
- `js/app.js`: existing download/task actions and page rendering.
- `js/desktop-ui.js`: menus, prompts, theme and Firefox restart presentation.
- `js/icons.js`: original ABDM Compose vector paths converted to SVG.
- `css/app.css`: original theme tokens and browser-adapted desktop layout.

The packaged Ktor service serves this directory at `/`. Firefox is streamed through noVNC; the download manager itself runs headlessly.

## UI regression test

Install Python Playwright in your development environment, serve `web/` at `http://localhost:8091`, then run `python web/tests/check_gui.py`. It intercepts the API with test fixtures and verifies desktop/light/phone/iPad rendering, filters, search, selection, add/pause/details, page navigation and the Firefox restart request. This is a GUI test, not a replacement for testing real downloads and restart on your NAS.
