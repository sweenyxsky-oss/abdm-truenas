#!/bin/sh
set -eu

mkdir -p /config/system /config/chromium /config/chromium/Default /downloads/browser /temp/downloadData

# Keep the real Chromium browser chrome visible. These are only defaults for a
# new profile; an existing user's Chromium preferences are never overwritten.
if [ ! -f /config/chromium/Default/Preferences ]; then
    printf '%s\n' '{"browser":{"show_home_button":true},"bookmark_bar":{"show_on_all_tabs":true}}' > /config/chromium/Default/Preferences
fi

# Keep ABDM's transient download working data on the dedicated TrueNAS temp dataset.
# Never replace an existing real directory: that preserves existing installations.
if [ ! -e /config/system/downloadData ] && [ ! -L /config/system/downloadData ]; then
    ln -s /temp/downloadData /config/system/downloadData
fi

# Start a real graphical Chromium session in a virtual X display.
# The display is exported through x11vnc + noVNC so it can be used from
# desktop and mobile browsers. The official ABDM browser integration is
# loaded into this Chromium instance and talks to the local ABDM API.
export DISPLAY=:99
Xvfb :99 -screen 0 1440x900x24 -ac +extension RANDR >/tmp/xvfb.log 2>&1 &
XVFB_PID=$!

sleep 1

x11vnc -display :99 -forever -shared -rfbport 5900 -nopw -listen 0.0.0.0 >/tmp/x11vnc.log 2>&1 &
X11VNC_PID=$!

websockify --web=/usr/share/novnc 0.0.0.0:15153 127.0.0.1:5900 >/tmp/websockify.log 2>&1 &
WEBSOCKIFY_PID=$!

# Start Chromium with a persistent profile so logins, cookies and site state
# survive container restarts. The extension captures supported downloads and
# forwards their URL/request headers to the ABDM engine.
chromium \
    --user-data-dir=/config/chromium \
    --load-extension=/opt/abdm/browser-extension,/opt/abdm/ublock-origin-lite \
    --window-size=1440,900 \
    --no-first-run \
    --no-default-browser-check \
    --disable-dev-shm-usage \
    --disable-features=Translate \
    --restore-last-session \
    --download-default-directory=/downloads/browser \
    "https://www.google.com" >/tmp/chromium.log 2>&1 &
CHROMIUM_PID=$!

cleanup() {
    kill "$CHROMIUM_PID" "$WEBSOCKIFY_PID" "$X11VNC_PID" "$XVFB_PID" 2>/dev/null || true
}
trap cleanup INT TERM EXIT

exec /opt/abdm/bin/ABDMHeadless "$@"
