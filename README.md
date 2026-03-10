# RIA2-Load

`RIA2-Load` is a Spring Boot service that:

1. downloads a JSON file from a public HTTP(S) URL,
2. validates and parses the payload,
3. generates a SQL `INSERT` script for the `events` table,
4. uploads the generated `.sql` file to an external bucket-adapter service.

The current application exposes one REST endpoint for this import flow.

## What the application does

The import flow implemented in the code is:

```text
Client -> RIA2-Load -> download remote JSON
                  -> parse payload
                  -> generate SQL script
                  -> send SQL file to bucket-adapter
                  -> return import result
```

The generated SQL targets this table shape:

```sql
INSERT INTO events (
    uid,
    dtstamp,
    dtstart,
    dtend,
    summary,
    description,
    categories,
    organizer,
    attendee,
    location,
    timezone
) VALUES (...);
```

## Tech Stack

- Java 21
- Spring Boot 4
- Spring Web MVC
- Springdoc OpenAPI / Swagger UI
- Maven
- Docker

## Project Structure

```text
.
|-- Dockerfile
|-- docker-compose.yml
|-- pom.xml
|-- checkstyle.xml
|-- Insomnia_2026-01-09.yaml
|-- docs/
|   `-- sequence-diagram.puml
|-- src/
|   |-- main/
|   |   |-- java/com/load/
|   |   |   |-- LoadApplication.java
|   |   |   |-- config/
|   |   |   |   |-- DotenvInitializer.java
|   |   |   |   `-- HttpClientConfig.java
|   |   |   |-- controller/
|   |   |   |   `-- LoadController.java
|   |   |   |-- dto/
|   |   |   |   |-- BucketUploadResponse.java
|   |   |   |   |-- ImportFromUrlRequest.java
|   |   |   |   |-- Rows.java
|   |   |   |   `-- TestPayload.java
|   |   |   `-- service/
|   |   |       |-- TestPayloadReader.java
|   |   |       |-- UrlDownloadService.java
|   |   |       `-- sql/
|   |   |           |-- SqlScriptService.java
|   |   |           |-- SqlScriptTransferClient.java
|   |   |           `-- SqlValueEncoder.java
|   |   `-- resources/
|   |       `-- application.properties
|   `-- test/
|       `-- json/
|           |-- data.json
|           `-- result.sql
`-- README.md
```

## API

The application runs under the `/api` context path.

### Import JSON from URL

`POST /api/load/objects/import?remote={remote-path}`

Request body:

```json
{
  "url": "https://example.com/event.json"
}
```

Query parameter:

- `remote`: destination path used when uploading the generated SQL file to the bucket-adapter service

Behavior:

- downloads the file from `url`
- accepts only `http` and `https`
- rejects local/private addresses
- limits downloads to 50 MB
- requires `uid`, `dtstamp`, `dtstart`, and `dtend`
- converts the destination path to `.sql`

Path conversion rules:

- `events/demo.json` -> `events/demo.sql`


Success response:

```json
{
  "remote": "events/demo.json",
  "sizeBytes": 365,
  "uid": "john-20250303@mycompany.com",
  "dtstart": "20250303T090000",
  "dtend": "20250303T150000",
  "bucketRemote": "events/demo.sql",
  "shareUrl": "https://bucket-adapter.example/share/...",
  "expirationTime": 1746700000
}
```

Main status codes:

- `201 Created`: import succeeded
- `400 Bad Request`: invalid URL, missing required fields, invalid host, invalid payload
- `500 Internal Server Error`: unexpected failure during import or upload

## JSON Payload Format

The code maps the remote JSON file to this structure:

```json
{
  "uid": "john-20250303@mycompany.com",
  "dtstamp": "20250201T080000Z",
  "dtstart": "20250303T090000",
  "dtend": "20250303T150000",
  "summary": "Work session",
  "description": "[acme.ch] Development session",
  "categories": "BUSINESS",
  "organizer": "contact@acme.ch",
  "attendee": "john.doe@mycompany.com",
  "location": "https://maps.google.com/?q=46.2044,6.1432",
  "timezone": "Europe/Bern"
}
```

Notes:

- unknown JSON properties are ignored
- nullable fields are encoded as `NULL` in SQL
- single quotes are escaped in generated SQL

Sample fixtures are available in [src/test/json/data.json](/Users/julienschneider/Desktop/cpnv/S7/RIA2/RIA2-Load/src/test/json/data.json) and [src/test/json/result.sql](/Users/julienschneider/Desktop/cpnv/S7/RIA2/RIA2-Load/src/test/json/result.sql).

## Configuration

The application loads configuration from:

- environment variables
- a local `.env` file

There is currently no `.env.example` file in the repository, so create `.env` manually.

Required variables:

```bash
SERVER_PORT=8090
BUCKET_ADAPTER_BASE_URL=http://localhost:8081
```

What they are used for:

- `SERVER_PORT`: HTTP port used by Spring Boot
- `BUCKET_ADAPTER_BASE_URL`: base URL of the external bucket-adapter service used to upload the generated SQL file

The upload client sends the SQL file to:

```text
POST {BUCKET_ADAPTER_BASE_URL}/api/v1/objects?remote={remote}
```

with a multipart form field named `file`.

## Run Locally

Requirements:

- Java 21
- Maven 3.9+ or the included Maven Wrapper

Start the application:

```bash
./mvnw spring-boot:run
```

or:

```bash
mvn spring-boot:run
```

If you created `.env`, Spring loads it automatically at startup.

## Build

Package the application:

```bash
./mvnw clean package
```

Run code quality checks included in the Maven lifecycle:

```bash
./mvnw verify
```

This project includes:

- Checkstyle during `verify`
- SpotBugs during `verify`

## Docker

Build and run with Docker Compose:

```bash
docker compose up --build
```

Important:

- the compose file starts only this service
- you still need a reachable `BUCKET_ADAPTER_BASE_URL`
- compose uses `${SERVER_PORT:-8080}` if `SERVER_PORT` is not set

## API Documentation

Swagger UI is exposed by Springdoc once the application is running:

- `http://localhost:{SERVER_PORT}/api/swagger-ui/index.html`

Example with `SERVER_PORT=8090`:

- `http://localhost:8090/api/swagger-ui/index.html`

## Example Request

```bash
curl -X POST "http://localhost:8090/api/load/objects/import?remote=events/demo.json" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com/event.json"
  }'
```

## Development Notes

- `UrlDownloadService` handles remote download, URL validation, and basic SSRF protection.
- `TestPayloadReader` parses the JSON payload into the internal DTO.
- `SqlScriptService` generates the SQL script and derives the target `.sql` path.
- `SqlScriptTransferClient` uploads the generated SQL file to the bucket-adapter service.

## Current Limitations

- only one import endpoint is implemented
- no database execution happens in this service; it only generates and uploads SQL
- the repository currently contains sample JSON fixtures in `src/test/json`, but no automated test classes
- the Maven coordinates in `pom.xml` still use `bucket-adapter` naming and do not fully match the repository name

## License

This repository is distributed under the MIT License. See [LICENSE](/Users/julienschneider/Desktop/cpnv/S7/RIA2/RIA2-Load/LICENSE).
