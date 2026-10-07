#!/bin/sh
set -eu

LOG_DIR=/config/system/browser
mkdir -p "$LOG_DIR" /config/chromium /config/chromium/Default /downloads/browser /temp/downloadData

# Keep the real Chromium browser chrome visible. These are only defaults for a
# new profile; an existing user's Chromium preferences are never overwritten.
if [ ! -f /config/chromium/Default/Preferences ]; then
    printf '%s\n' '{"browser":{"show_home_button":true},"bookmark_bar":{"show_on_all_tabs":true}}' > /config/chromium/Default/Preferences
fi

# Keep ABDM's transient download working data on the dedicated TrueNAS temp dataset.
if [ ! -e /config/system/downloadData ] && [ ! -L /config/system/downloadData ]; then
    ln -s /temp/downloadData /config/system/downloadData
fi

export DISPLAY=:99

# Start the virtual X server first and fail early if it cannot stay alive.
Xvfb :99 -screen 0 1440x900x24 -ac +extension RANDR >"$LOG_DIR/xvfb.log" 2>&1 &
XVFB_PID=$!
sleep 1
kill -0 "$XVFB_PID" 2>/dev/null || {
    echo "Xvfb failed to start" >>"$LOG_DIR/browser-startup.log"
    exit 1
}

# Keep the VNC server on localhost. Only websockify is exposed to the network.
# This prevents direct unauthenticated RFB access on port 5900.
x11vnc -display :99 -forever -shared -rfbport 5900 -nopw -listen 127.0.0.1 >"$LOG_DIR/x11vnc.log" 2>&1 &
X11VNC_PID=$!
sleep 1
kill -0 "$X11VNC_PID" 2>/dev/null || {
    echo "x11vnc failed to start" >>"$LOG_DIR/browser-startup.log"
    exit 1
}

# Start a real Chromium session with a persistent profile. The explicit X11,
# software-rendering and sandbox flags make Chromium reliable under Xvfb in
# the TrueNAS container. We intentionally do not restore the last session:
# a stale/crashed Chromium window must not leave the noVNC desktop blank.
chromium \
    --user-data-dir=/config/chromium \
    --load-extension=/opt/abdm/browser-extension,/opt/abdm/ublock-origin-lite \
    --window-size=1440,900 \
    --force-device-scale-factor=1 \
    --ozone-platform=x11 \
    --disable-gpu \
    --no-sandbox \
    --no-first-run \
    --no-default-browser-check \
    --disable-dev-shm-usage \
    --disable-features=Translate \
    --download-default-directory=/downloads/browser \
    "https://www.google.com" >"$LOG_DIR/chromium.log" 2>&1 &
CHROMIUM_PID=$!

sleep 3
if ! kill -0 "$CHROMIUM_PID" 2>/dev/null; then
    echo "Chromium exited during startup" >>"$LOG_DIR/browser-startup.log"
    exit 1
fi
echo "Browser services started: Xvfb=$XVFB_PID x11vnc=$X11VNC_PID websockify=$WEBSOCKIFY_PID chromium=$CHROMIUM_PID" >"$LOG_DIR/browser-startup.log"

cleanup() {
    kill "$CHROMIUM_PID" "$WEBSOCKIFY_PID" "$X11VNC_PID" "$XVFB_PID" 2>/dev/null || true
}
trap cleanup INT TERM EXIT

exec /opt/abdm/bin/ABDMHeadless "$@"
