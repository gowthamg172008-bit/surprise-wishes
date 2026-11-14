# Little Moments

A responsive birthday, wedding, New Year, and just-because wish app. Create a wish with a
recipient name, nickname, opening time, photo, and personal message, then share its private link.

## Features

- Responsive layout for phones, tablets, laptops, and desktop browsers.
- Private wish links backed by unguessable UUIDs.
- Scheduled opening, including automatic unlock when a wish preview is already open.
- Optional image memories and personal notes.
- Java 21 backend with a persistent file-based store.
- Health endpoint at `/api/health`.

## Run locally

Install JDK 21, then double-click `run.bat` on Windows. The launcher compiles and starts the app
and opens `http://localhost:8080`. It reuses an existing server on that port.

The local server stores wishes under `data/`. The folder is intentionally excluded from Git
because it can contain personal messages, names, and photos.

## Run with Docker

Build and run:

```sh
docker build -t little-moments .
docker run --rm -p 8080:8080 -e PORT=8080 -v little-moments-data:/var/data little-moments
```

Open `http://localhost:8080`. Mount a persistent volume at `DATA_DIR` (default `/var/data`) so
wishes survive container replacement. The server binds to `0.0.0.0` and uses the hosting
platform's `PORT` environment variable.

## Production and public deployment

The repository includes a free Render Blueprint in `render.yaml`. Create a new Blueprint from
the repository in the Render Dashboard and apply it to create the service. Render will build
the Dockerfile, configure the health check, and assign a public HTTPS URL.

Render's free web service has an ephemeral filesystem: wishes and uploaded photos are lost when
the service spins down, restarts, or redeploys, and an idle service may take about a minute to
wake up. Do not use this free setup for important or long-term wishes. Persistent disks require
a paid Render service; free Render Postgres is another option but expires after 30 days and is
not configured for this file-based app.

Alternatively, deploy the Dockerfile to a Java/Docker web host that provides HTTPS and persistent
disk/volume storage. Configure:

| Variable | Required | Default | Description |
| --- | --- | --- | --- |
| `PORT` | Platform-provided | `8080` | HTTP port assigned by the host |
| `DATA_DIR` | No | `/var/data` in Docker | Directory used to store wishes; on Render Free it is ephemeral |
| `APP_DIR` | No | `.` | Directory containing `index.html`, `style.css`, and `app.js` |
| `HOST` | No | `0.0.0.0` | Network interface for the Java server |

For durable data on another host, set `DATA_DIR` to a persistent volume mount and configure the
service's health check as `GET /api/health`. Keep the deployment on a single application instance:
the current file-based store is designed for one server with a shared local volume, not multiple
replicas. Use HTTPS so shared wish links are encrypted in transit.

The app does not currently use an external database or require database credentials. Its
existing storage format is per-wish properties files; production persistence is provided by the
host's mounted disk. No wish data, local secrets, compiled classes, or build output should be
committed to the public repository.

## GitHub

Initialize a Git repository and publish the source after selecting your public repository. Check
that `.gitignore` is active and that `data/`, `.env` files, and build output are excluded before
the first push. Never put hosting credentials or private wish data in the public repository.
