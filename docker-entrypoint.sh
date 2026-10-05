#!/bin/sh
set -eu

mkdir -p /config/system /temp/downloadData

# Keep ABDM's transient download working data on the dedicated TrueNAS temp dataset.
# Never replace an existing real directory: that preserves existing installations.
if [ ! -e /config/system/downloadData ] && [ ! -L /config/system/downloadData ]; then
    ln -s /temp/downloadData /config/system/downloadData
fi

exec /opt/abdm/bin/ABDMHeadless "$@"
