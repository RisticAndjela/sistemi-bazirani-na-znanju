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

From repository root you can use:

- `run-postgres.bat`
  - starts PostgreSQL in Docker on `localhost:5432`
- `run-backend.bat`
  - builds and installs backend artifacts (`model`, `kjar`, `service`)
- `run-frontend.bat`
  - compiles and starts the desktop frontend app connected to PostgreSQL
- `run-web-api.bat`
  - starts a lightweight REST API over the same frontend Drools + PostgreSQL logic
- `run-web-frontend.bat`
  - installs Angular packages if needed and starts the web frontend on `http://localhost:4200`
- `run-web.bat`
  - opens both the API and Angular frontend in separate terminal windows

Recommended order:

1. Double-click `run-postgres.bat`
2. Double-click `run-backend.bat`
3. For desktop: double-click `run-frontend.bat`
4. For web: double-click `run-web.bat`

## Terminal alternative (relative, machine-independent)

```bash
docker compose up -d postgres

cd backend
mvn -U clean install

cd ../frontend
mvn clean compile
mvn exec:java -Dexec.mainClass=com.sbnz.frontend.DesktopApp
```

Web version:

```bash
cd frontend
mvn clean compile
mvn exec:java -Dexec.mainClass=com.sbnz.frontend.WebApiApp

cd ../web-frontend
npm.cmd install
npm.cmd start
```

## Notes

- PostgreSQL defaults:
  - host: `localhost`
  - port: `5432`
  - database: `sbnz_respiratory`
  - user: `sbnz_user`
  - password: `sbnz_pass`
- Docker PostgreSQL init now inserts demo patients automatically if `patient_cases` is empty on first database creation.
- The desktop app auto-creates tables `patient_cases` and `rule_run_history` if they do not exist.
- Demo patients are inserted from `frontend/data/demo-children.csv` only when the database is empty.
- Connection can be overridden with `SBNZ_DB_URL`, `SBNZ_DB_USER`, and `SBNZ_DB_PASSWORD`.
- The web API listens on `localhost:8080` by default and can be changed with `-Dsbnz.web.port=...`.
- The Angular dev server proxies `/api` calls to the local Java API.
- In DBeaver, create a PostgreSQL connection with the values above and you will be able to inspect stored patients and run history.
- If Maven or Java is missing from PATH, install them and reopen terminal.
