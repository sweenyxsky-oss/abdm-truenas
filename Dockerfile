FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
RUN chmod +x ./gradlew && ./gradlew :desktop:app:createReleaseDistributable --no-daemon

FROM debian:bookworm-slim
ENV HOME=/config \
    ABDM_API_HOST=0.0.0.0 \
    ABDM_API_PORT=15151 \
    TZ=Asia/Riyadh
WORKDIR /opt/abdm
COPY --from=build /src/desktop/app/build/compose/binaries/main-release/app/ABDownloadManager /opt/abdm
RUN mkdir -p /config /downloads /temp && useradd --system --uid 568 --home /config --shell /usr/sbin/nologin abdm && \
    chown -R abdm:abdm /opt/abdm /config /downloads /temp
USER abdm
EXPOSE 15151
VOLUME ["/config", "/downloads", "/temp"]
ENTRYPOINT ["/opt/abdm/bin/ABDMHeadless"]
