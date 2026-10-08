#!/bin/sh
set -eu

LOG_DIR=/config/system/browser
PID_FILE="$LOG_DIR/chromium.pid"
PROFILE=/config/firefox
ABDM_EXT_ID=firefox-integration@abdownloadmanager.com
UBLOCK_EXT_ID=uBOLiteRedux@raymondhill.net
mkdir -p "$LOG_DIR" "$PROFILE/extensions" /downloads/browser /temp/downloadData /config/system

# Firefox profile preferences. user.js is applied on every start, so the
# download folder and extension settings survive profile edits in the UI.
cat > "$PROFILE/user.js" <<'EOF'
user_pref("browser.shell.checkDefaultBrowser", false);
user_pref("browser.sessionstore.resume_from_crash", false);
user_pref("browser.startup.homepage", "about:blank");
user_pref("browser.download.folderList", 2);
user_pref("browser.download.dir", "/downloads/browser");
user_pref("browser.download.useDownloadDir", true);
user_pref("browser.download.alwaysOpenPanel", false);
user_pref("browser.download.viewableInternally.enabledTypes", "");
user_pref("browser.tabs.warnOnClose", false);
user_pref("toolkit.startup.max_resumed_crashes", -1);
user_pref("app.update.auto", false);
user_pref("app.update.disabledForTesting", true);
user_pref("datareporting.policy.dataSubmissionEnabled", false);
user_pref("xpinstall.signatures.required", false);
user_pref("extensions.autoDisableScopes", 0);
user_pref("extensions.enabledScopes", 15);
user_pref("extensions.update.autoUpdateDefault", false);
user_pref("signon.rememberSignons", true);
EOF

# Install the ABDM download-capture extension (unpacked; signature check is
# disabled above, which Firefox ESR honours) and the signed uBlock Origin Lite.
# Refreshed on every start so image updates replace older extension code.
rm -rf "$PROFILE/extensions/$ABDM_EXT_ID"
mkdir -p "$PROFILE/extensions/$ABDM_EXT_ID"
cp -r /opt/abdm/browser-extension/. "$PROFILE/extensions/$ABDM_EXT_ID/"
cp -f /opt/abdm/ublock-origin-lite.xpi "$PROFILE/extensions/$UBLOCK_EXT_ID.xpi"

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

    # Supervisor: keep Firefox running, restart it if it crashes or is closed.
    (
        while true; do
            # Firefox refuses a profile whose lock points at another (old) container.
            rm -f "$PROFILE/.parentlock" "$PROFILE/lock"
            firefox-esr \
                --no-remote \
                --profile "$PROFILE" \
                --width 1440 \
                --height 900 \
                "https://www.google.com" >>"$LOG_DIR/firefox.log" 2>&1 &
            FPID=$!
            echo "$FPID" >"$PID_FILE"
            echo "$(date '+%F %T') Firefox started pid=$FPID" >>"$LOG_DIR/browser-startup.log"
            wait "$FPID" || true
            rm -f "$PID_FILE"
            echo "$(date '+%F %T') Firefox exited; restarting in 2s" >>"$LOG_DIR/browser-startup.log"
            sleep 2
        done
    ) &
}

# The browser is optional: a browser failure must never take the download manager down.
start_browser_stack || true

exec /opt/abdm/bin/ABDMHeadless "$@"
