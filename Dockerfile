FROM eclipse-temurin:25-jdk AS build
RUN apt-get update && apt-get install -y --no-install-recommends git ca-certificates && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY . .
RUN chmod +x ./gradlew && ./gradlew :desktop:app:createReleaseDistributable --no-daemon

FROM debian:bookworm-slim
LABEL org.opencontainers.image.source="https://github.com/sweenyxsky-oss/abdm-truenas"
ENV HOME=/config \
    ABDM_API_HOST=0.0.0.0 \
    ABDM_API_PORT=15151 \
    TZ=Asia/Riyadh \
    ABDM_DOWNLOAD_FOLDER=/downloads
RUN apt-get update && apt-get install -y --no-install-recommends libx11-6 libxext6 libxrender1 libxtst6 libxi6 libgl1 libfontconfig1 libfreetype6 libasound2 && rm -rf /var/lib/apt/lists/*
WORKDIR /opt/abdm
COPY --from=build /src/desktop/app/build/compose/binaries/main-release/app/ABDownloadManager /opt/abdm
RUN printf "/config\n" > /opt/abdm/.portable && mkdir -p /config /downloads /temp && useradd --system --uid 568 --home /config --shell /usr/sbin/nologin abdm && \
    chown -R abdm:abdm /opt/abdm /config /downloads /temp
USER abdm
EXPOSE 15151
VOLUME ["/config", "/downloads", "/temp"]
ENTRYPOINT ["/opt/abdm/bin/ABDMHeadless"]
