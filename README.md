# AB Download Manager — TrueNAS SCALE Edition

A TrueNAS SCALE-focused fork of [AB Download Manager](https://github.com/amir1376/ab-download-manager).

This repository provides a **headless TrueNAS deployment** of AB Download Manager with a web interface served directly by the application. It is designed to run as a TrueNAS Custom App / Docker container without a desktop environment, Xvfb, or noVNC.

## What is included

- Headless AB Download Manager runtime for TrueNAS SCALE
- Web interface served by the ABDM service
- Download dashboard with progress, speed and ETA
- Pause, resume, retry, remove and bulk download actions
- Download queues and queue controls
- Queue scheduling
- Categories
- Download/file browser
- Persistent configuration and download storage
- Configurable download connections
- API-key authentication support
- Import links from TXT
- Download details and direct-link editing
- Mobile-friendly web UI
- Docker image published to GitHub Container Registry (GHCR)

## Project structure

The repository is based on the upstream AB Download Manager source tree. The TrueNAS-specific work is primarily located in:

```text
abdm-truenas/
├── desktop/                  # Desktop/runtime application source
├── integration/              # API/integration models and endpoints
├── truenas/                  # TrueNAS-specific integration
├── web/                      # TrueNAS web interface
├── Dockerfile                # TrueNAS container build
├── docker-entrypoint.sh      # Container startup
├── docker-compose.truenas.yml
└── .github/
    └── workflows/
        └── truenas-image.yml # TrueNAS Docker build/publish workflow
```

### Important build detail

The TrueNAS Docker image is built from the **desktop application runtime**:

```text
:desktop:app:createReleaseDistributable
```

The container does **not** use the `truenas/app` module as the standalone runtime. TrueNAS-specific integration code is included in the application build.

---

# Using the TrueNAS container

## Recommended storage layout

Create persistent datasets/directories similar to:

```text
dataPool
└── abdm
    ├── config
    ├── downloads
    └── temp
```

The container paths are:

| Container path | Purpose |
|---|---|
| `/config` | Persistent ABDM configuration and application data |
| `/downloads` | Download destination |
| `/temp` | Temporary download data |

The container runs as UID/GID **568**, which matches the normal TrueNAS Apps user.

For example:

```bash
sudo mkdir -p /mnt/dataPool/abdm/config
sudo mkdir -p /mnt/dataPool/abdm/downloads
sudo mkdir -p /mnt/dataPool/abdm/temp

sudo chown -R 568:568 /mnt/dataPool/abdm
sudo chmod -R u+rwX /mnt/dataPool/abdm
```

Adjust the dataset path to match your TrueNAS installation.

## Environment variables

The container uses:

| Variable | Default | Purpose |
|---|---|---|
| `HOME` | `/config` | Persistent application home |
| `ABDM_API_HOST` | `0.0.0.0` | API/web bind address |
| `ABDM_API_PORT` | `15151` | Web/API port |
| `ABDM_DOWNLOAD_FOLDER` | `/downloads` | Initial download directory |
| `TZ` | `Asia/Riyadh` | Container timezone |

The download directory is normally configured from the web Settings page after initialization.

## TrueNAS Custom App

The repository contains:

```text
docker-compose.truenas.yml
```

A typical deployment maps:

```text
TrueNAS host                  Container
--------------------------------------------
/mnt/dataPool/abdm/config  -> /config
/mnt/dataPool/abdm/downloads -> /downloads
/mnt/dataPool/abdm/temp    -> /temp
TrueNAS port 15152          -> 15151
```

After deployment, open:

```text
http://TRUENAS-IP:15152/
```

Use whatever host port you configured.

If the application is exposed outside a trusted LAN, enable **Require API key authentication** and use HTTPS through an appropriate reverse proxy.

---

# Building the Docker image

There are two supported approaches.

## 1. Build with GitHub Actions — recommended

The repository contains:

```text
.github/workflows/truenas-image.yml
```

The workflow:

1. Checks out the repository.
2. Sets up Docker Buildx.
3. Logs in to GitHub Container Registry.
4. Builds the TrueNAS Docker image.
5. Pushes the image to GHCR.

The workflow runs on:

- pushes to `truenas-web`
- tags matching `v*`
- manual `workflow_dispatch`

### Image name

```text
ghcr.io/sweenyxsky-oss/abdm-truenas
```

Branch builds are tagged:

```text
ghcr.io/sweenyxsky-oss/abdm-truenas:truenas-web
```

The `truenas-web` branch also updates:

```text
ghcr.io/sweenyxsky-oss/abdm-truenas:latest
```

Version tags such as `v0.2` produce:

```text
ghcr.io/sweenyxsky-oss/abdm-truenas:v0.2
```

## 2. Build locally with Docker

From the repository root:

```bash
docker build -t abdm-truenas:local .
```

The Dockerfile performs the application build automatically.

The build stage runs:

```bash
./gradlew :desktop:app:createReleaseDistributable --no-daemon --warning-mode=none
```

The resulting application is copied into the runtime image.

Run the image locally:

```bash
docker run --rm -it \
  -p 15151:15151 \
  -e HOME=/config \
  -e ABDM_API_HOST=0.0.0.0 \
  -e ABDM_API_PORT=15151 \
  -e ABDM_DOWNLOAD_FOLDER=/downloads \
  -v "$PWD/config:/config" \
  -v "$PWD/downloads:/downloads" \
  -v "$PWD/temp:/temp" \
  abdm-truenas:local
```

Then open:

```text
http://localhost:15151/
```

---

# Building the application without Docker

The project uses Gradle and the included Gradle wrapper.

Make sure a compatible JDK is installed. The TrueNAS Docker build currently uses:

```text
Eclipse Temurin JDK 25
```

From the repository root:

```bash
chmod +x ./gradlew
./gradlew :desktop:app:createReleaseDistributable --no-daemon
```

For the exact TrueNAS container build, use Docker because the Dockerfile also installs the required Linux runtime libraries and packages the application into the final image.

---

# GitHub Actions build flow

The complete TrueNAS build is:

```text
Git push
   │
   ▼
GitHub Actions
   │
   ├── Checkout source
   ├── Docker Buildx
   ├── Gradle application build
   │      └── :desktop:app:createReleaseDistributable
   │
   ├── Build runtime image
   │
   └── Push to GHCR
          │
          ▼
ghcr.io/sweenyxsky-oss/abdm-truenas
```

The Docker image is a multi-stage build:

```text
Eclipse Temurin 25 JDK
        │
        │ Gradle build
        ▼
ABDownloadManager distributable
        │
        ▼
Debian Bookworm Slim runtime
        │
        ▼
TrueNAS container image
```

---

# Updating a TrueNAS deployment

For a Custom App using:

```text
ghcr.io/sweenyxsky-oss/abdm-truenas:truenas-web
```

pull the latest image:

```bash
sudo docker pull ghcr.io/sweenyxsky-oss/abdm-truenas:truenas-web
```

TrueNAS should then be used to redeploy/restart the Custom App so that the container is recreated from the new image.

Check the running container:

```bash
sudo docker ps
```

Check logs:

```bash
sudo docker logs --tail 100 <container-name>
```

Do not manually edit the generated Docker Compose files under `/mnt/.ix-apps`. TrueNAS owns those generated files.

---

# Versioning and stable releases

Stable versions should be created from a commit that has successfully completed the **Build TrueNAS image** GitHub Actions workflow.

Example:

```text
v0.2
 │
 └── exact tested source commit
       │
       └── GitHub Actions: successful
             │
             └── GHCR: ghcr.io/sweenyxsky-oss/abdm-truenas:v0.2
```

For development, use the `truenas-web` branch.

For stable releases, use a version tag such as:

```bash
git tag -a v0.2 <COMMIT> -m "ABDM TrueNAS v0.2"
git push origin v0.2
```

The GitHub Actions workflow automatically recognizes `v*` tags and publishes the corresponding Docker image.

---

# Development

The recommended development flow is:

```text
truenas-web
    │
    ├── Make changes
    │
    ├── Commit
    │
    ├── GitHub Actions build
    │
    ├── Deploy/test on a separate TrueNAS Custom App
    │
    └── Promote to stable release when verified
```

For TrueNAS testing, use a separate Custom App rather than replacing a known-working deployment.

This makes it possible to test a new image without risking the existing application data or configuration.

---

# Backup policy

Before major changes, create a Git branch pointing to the last known-good commit.

Example:

```bash
git branch backup/<description> <KNOWN-GOOD-COMMIT>
git push origin backup/<description>
```

A stable backup should always point to the **exact source commit used to build the tested image**.

---

# Security

The web interface provides access to download management and filesystem-related functionality.

Recommended:

- Keep the application on a trusted LAN unless protected by HTTPS and authentication.
- Enable API-key authentication when the service is not strictly local.
- Do not expose the API directly to the public Internet.
- Protect the `/config` and `/downloads` datasets with appropriate TrueNAS permissions.
- Use a reverse proxy for HTTPS if remote access is required.

---

# Upstream project

This project is based on **AB Download Manager** by Amir1376.

Upstream project:

https://github.com/amir1376/ab-download-manager

Official website:

https://abdownloadmanager.com

This fork adds TrueNAS SCALE-oriented headless/container deployment and web-management functionality while retaining the upstream application architecture.

---

# License

See the repository's existing license files and the upstream AB Download Manager project for the applicable licensing terms.

Changes made specifically for the TrueNAS edition remain part of this fork.
