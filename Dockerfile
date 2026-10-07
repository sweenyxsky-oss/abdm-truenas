FROM eclipse-temurin:25-jdk AS build
RUN apt-get update && apt-get install -y --no-install-recommends git ca-certificates && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY . .
RUN chmod +x ./gradlew && set -o pipefail; ./gradlew :desktop:app:createReleaseDistributable --no-daemon --warning-mode=none 2>&1 | tee /tmp/gradle-build.log | sed -E '/(^|[[:space:]])w: /d; /Deprecated Gradle features were used in this build/d; /You can use.*warning-mode all/d'

FROM node:22-bookworm-slim AS browser-extension
RUN apt-get update && apt-get install -y --no-install-recommends git ca-certificates unzip wget && rm -rf /var/lib/apt/lists/*
WORKDIR /src/browser-extension
RUN git clone --depth 1 https://github.com/amir1376/ab-download-manager-browser-integration.git .
RUN sed -i 's/silentAddDownload: z.boolean().catch(false)/silentAddDownload: z.boolean().catch(true)/' src/configs/Config.ts && \
    sed -i 's/silentStartDownload: z.boolean().catch(false)/silentStartDownload: z.boolean().catch(true)/' src/configs/Config.ts
RUN npm ci --ignore-scripts && npm run build:chrome
WORKDIR /src/ublock-origin-lite
RUN wget -q -O /tmp/uBOLite_2026.926.2202.chromium.zip https://github.com/uBlockOrigin/uBOL-home/releases/download/2026.926.2202/uBOLite_2026.926.2202.chromium.zip && \
    echo "9a0d94e832fde9430f64817ff1ba3f34040f19caa113a24e6d84aad1d05eb1aa  /tmp/uBOLite_2026.926.2202.chromium.zip" | sha256sum -c - && \
    unzip -q /tmp/uBOLite_2026.926.2202.chromium.zip -d /src/ublock-origin-lite && \
    test -f /src/ublock-origin-lite/manifest.json && \
    rm /tmp/uBOLite_2026.926.2202.chromium.zip

FROM debian:bookworm-slim
LABEL org.opencontainers.image.source="https://github.com/sweenyxsky-oss/abdm-truenas"
ENV HOME=/config \
    ABDM_API_HOST=0.0.0.0 \
    ABDM_API_PORT=15151 \
    ABDM_BROWSER_PORT=15153 \
    TZ=Asia/Riyadh \
    ABDM_DOWNLOAD_FOLDER=/downloads
RUN apt-get update && apt-get install -y --no-install-recommends \
    chromium xvfb x11vnc novnc websockify dbus-x11 fonts-liberation \
    libx11-6 libxext6 libxrender1 libxtst6 libxi6 libgl1 libfontconfig1 libfreetype6 libasound2 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /opt/abdm
COPY --from=build /src/desktop/app/build/compose/binaries/main-release/app/ABDownloadManager /opt/abdm
COPY --from=browser-extension /src/browser-extension/dist/chrome /opt/abdm/browser-extension
COPY --from=browser-extension /src/ublock-origin-lite/dist/build/uBOLite.chromium /opt/abdm/ublock-origin-lite
COPY docker-entrypoint.sh /opt/abdm/docker-entrypoint.sh
RUN printf "/config\n" > /opt/abdm/.portable && mkdir -p /config /downloads /temp && chmod +x /opt/abdm/docker-entrypoint.sh && useradd --system --uid 568 --home /config --shell /usr/sbin/nologin abdm && \
    chown -R abdm:abdm /opt/abdm /config /downloads /temp
USER abdm
EXPOSE 15151 15153
VOLUME ["/config", "/downloads", "/temp"]
ENTRYPOINT ["/opt/abdm/docker-entrypoint.sh"]
