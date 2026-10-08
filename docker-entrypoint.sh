#!/bin/sh
set -eu

LOG_DIR=/config/system/browser
PID_FILE="$LOG_DIR/chromium.pid"
PROFILE=/config/chromium
mkdir -p "$LOG_DIR" "$PROFILE/Default" /downloads/browser /temp/downloadData /config/system

# Defaults for a new profile only; existing preferences are never overwritten.
# Downloads that ABDM does not capture still land on the NAS, never in /config.
if [ ! -f "$PROFILE/Default/Preferences" ]; then
    printf '%s\n' '{"browser":{"show_home_button":true},"bookmark_bar":{"show_on_all_tabs":true},"download":{"default_directory":"/downloads/browser","prompt_for_download":false,"directory_upgrade":true},"savefile":{"default_directory":"/downloads/browser"}}' > "$PROFILE/Default/Preferences"
fi

# Keep ABDM's transient download working data on the dedicated TrueNAS temp dataset.
if [ ! -e /config/system/downloadData ] && [ ! -L /config/system/downloadData ]; then
    ln -s /temp/downloadData /config/system/downloadData
fi

export DISPLAY=:99

# A restarted container keeps /tmp, so a stale X lock would make Xvfb refuse to start.
rm -f /tmp/.X99-lock /tmp/.X11-unix/X99
mkdir -p /tmp/.X11-unix 2>/dev/null || true

start_browser_stack() {
    Xvfb :99 -screen 0 1440x900x24 -ac -nolisten tcp +extension RANDR >"$LOG_DIR/xvfb.log" 2>&1 &
    XVFB_PID=$!
    i=0
    while [ ! -S /tmp/.X11-unix/X99 ] && [ $i -lt 50 ]; do sleep 0.2; i=$((i + 1)); done
    if ! kill -0 "$XVFB_PID" 2>/dev/null; then
        echo "Xvfb failed to start; browser disabled (downloads keep working)" >>"$LOG_DIR/browser-startup.log"
        return 1
    fi

    # VNC listens on localhost only; the ABDM API proxies it to the web UI.
    x11vnc -display :99 -forever -shared -rfbport 5900 -nopw -listen 127.0.0.1 \
        -noxdamage -quiet >"$LOG_DIR/x11vnc.log" 2>&1 &
    echo "Browser display started: Xvfb=$XVFB_PID x11vnc=$!" >"$LOG_DIR/browser-startup.log"

    # Supervisor: keep Chromium running, restart it if it crashes or is closed.
    (
        while true; do
            # Chromium refuses a profile whose lock points at another (old) container hostname.
            rm -f "$PROFILE/SingletonLock" "$PROFILE/SingletonSocket" "$PROFILE/SingletonCookie"
            chromium \
                --user-data-dir="$PROFILE" \
                --load-extension=/opt/abdm/browser-extension,/opt/abdm/ublock-origin-lite \
                --window-position=0,0 \
                --window-size=1440,900 \
                --start-maximized \
                --force-device-scale-factor=1 \
                --ozone-platform=x11 \
                --disable-gpu \
                --no-sandbox \
                --no-first-run \
                --no-default-browser-check \
                --disable-dev-shm-usage \
                --disable-session-crashed-bubble \
                --hide-crash-restore-bubble \
                --password-store=basic \
                --disable-features=Translate,DisableLoadExtensionCommandLineSwitch \
                "https://www.google.com" >>"$LOG_DIR/chromium.log" 2>&1 &
            CPID=$!
            echo "$CPID" >"$PID_FILE"
            echo "$(date '+%F %T') Chromium started pid=$CPID" >>"$LOG_DIR/browser-startup.log"
            wait "$CPID" || true
            rm -f "$PID_FILE"
            echo "$(date '+%F %T') Chromium exited; restarting in 2s" >>"$LOG_DIR/browser-startup.log"
            sleep 2
        done
    ) &
}

# The browser is optional: a browser failure must never take the download manager down.
start_browser_stack || true

exec /opt/abdm/bin/ABDMHeadless "$@"
