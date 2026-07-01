# SBNZ Project - Quick Start

This repository has two top-level apps:

- `backend` - Drools rules and service logic
- `frontend` - desktop UI for interactive testing
- `web-frontend` - Angular web UI with the same patient workflow and outputs
- `postgres` - initialization scripts for the local PostgreSQL database

## One-time prerequisites

- Java 17
- Maven 3.9+

## Run on any machine (double-click option)

The web stack now runs through Docker instead of local Maven + Node processes.

From repository root you can use:

- `run-postgres.bat`
  - starts PostgreSQL in Docker on `localhost:5432`
- `run-backend.bat`
  - builds the Docker image that packages backend artifacts for the web API
- `run-frontend.bat`
  - starts the desktop Swing frontend locally connected to PostgreSQL
- `run-web-api.bat`
  - starts the Dockerized REST API on `http://localhost:8080`
- `run-web-frontend.bat`
  - starts the Dockerized Angular frontend on `http://localhost:4200`
- `run-web.bat`
  - starts PostgreSQL, the API, and Angular together through Docker Compose

Recommended order:

1. Double-click `run-postgres.bat`
2. For web: double-click `run-web.bat`
3. For desktop only: double-click `run-frontend.bat`

## Terminal alternative (relative, machine-independent)

```bash
docker compose up -d --build postgres web-api web-frontend
```

Desktop app remains a local Java Swing process:

```bash
docker compose up -d postgres
cd frontend
mvn compile
mvn exec:java -Dexec.mainClass=com.sbnz.frontend.DesktopApp
```

If you want only the API in Docker:

```bash
docker compose up -d --build postgres web-api
```

If port `8080` is already occupied on your machine, override the host port before starting Docker:

```bat
set SBNZ_WEB_API_HOST_PORT=8081
run-web-api.bat
```

## Notes

- PostgreSQL defaults:
  - host: `localhost`
  - port: `5432`
  - database: `sbnz_respiratory`
  - user: `sbnz_user`
  - password: `sbnz_pass`
- Docker Compose exposes:
  - Angular frontend on `http://localhost:4200`
  - Web API on `http://localhost:8080` by default
- If `8080` is already used by another app, set `SBNZ_WEB_API_HOST_PORT` to a free host port such as `8081`.
- Docker PostgreSQL init now inserts demo patients automatically if `patient_cases` is empty on first database creation.
- The desktop app auto-creates tables `patient_cases` and `rule_run_history` if they do not exist.
- Demo patients are inserted from `frontend/data/demo-children.csv` only when the database is empty.
- Connection can be overridden with `SBNZ_DB_URL`, `SBNZ_DB_USER`, and `SBNZ_DB_PASSWORD`.
- The web API listens on `localhost:8080` by default and can be changed with `-Dsbnz.web.port=...`.
- The Angular dev server proxies `/api` calls to the Java API, using `localhost` locally and the Docker service name inside Compose.
- In DBeaver, create a PostgreSQL connection with the values above and you will be able to inspect stored patients and run history.
- `run-frontend.bat` still requires local Java and Maven because Docker is not used for the desktop Swing GUI.
