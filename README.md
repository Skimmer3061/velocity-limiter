## Quick start with Docker (any operating system)

The only requirement is Docker (Docker Desktop on macOS and Windows) running, plus Git to get the code. Where
commands differ, macOS/Linux and Windows PowerShell versions are shown.

**0. Get the code** and run every following command from the project folder:

```bash
git clone https://github.com/Skimmer3061/velocity-limiter.git
cd velocity-limiter
```

**1. Build the images** (compiles the app inside Docker; the first build takes a few minutes; run it again after
changing the code):

```bash
docker compose --profile batch build
```

**2. Process a file.** Put your file in the project folder (paths are relative to it), e.g. as `input.txt`, then:

```bash
docker compose run --rm batch --input=input.txt --output=output.txt
```

The answers are written to `output.txt`. The run ends with a summary and the location:
`Batch finished: input=input.txt, output=output.txt, lines=…, accepted=…, declined=…, duplicates=…, rejected=…`
and `Output written to output.txt`.
Leave out `--output` to print the answers in the console instead. The command is the same on macOS, Linux and
Windows. On Linux, if writing the output fails with a permission error, add `--user "$(id -u):$(id -g)"` after
`--rm`.

**3. Check against the task's expected output.** Process the provided input, then compare. The provided
`output.txt` uses Windows (CRLF) line endings and this program writes LF, so compare lines, not bytes.

```bash
docker compose run --rm batch --input=src/test/resources/input.txt --output=output.txt
```

macOS / Linux (no output means the files match):

```bash
diff --strip-trailing-cr src/test/resources/output.txt output.txt
```

Windows PowerShell (no output means the files match):

```powershell
Compare-Object (Get-Content src/test/resources/output.txt) (Get-Content output.txt)
```

**4. Run the tests** (unit, integration, and an end-to-end replay of `input.txt` that must produce exactly
`output.txt`). This uses the official JDK image, so nothing is installed locally; it downloads Gradle and the
dependencies on each run.

```bash
docker run --rm -v "$PWD:/workspace" -w /workspace eclipse-temurin:25-jdk ./gradlew clean build
```

On Windows PowerShell, use `"${PWD}:/workspace"`. The test report is `build/reports/tests/test/index.html`.

## Without Docker

With Java 25 installed (the Gradle toolchain accepts any vendor):

```bash
./gradlew clean build
java -jar build/libs/velocity-limiter-0.0.1-SNAPSHOT.jar --spring.profiles.active=batch \
  --input=input.txt --output=output.txt
```

On Windows, use `gradlew.bat clean build`. The database is in-memory H2, created and migrated by Flyway at
startup, so there is nothing else to set up.

## What a batch run does with bad input

- **A bad line is skipped**, not fatal: unreadable JSON, an unknown or misspelt field, a field that fails
  validation, or bytes that are not valid UTF-8. Each one is logged once at WARN with its line number, the reason
  (with the field name as written in the file) and the start of the line. It gets no answer line.
- **Validation:** `id` and `customer_id` not blank and at most 64 characters; `load_amount` a positive dollar
  amount with exactly 2 decimals and no leading zeros, like `$123.45`; `time` an ISO-8601 instant between years
  0001 and 9999.
- **A summary** is logged at the end:
  `Batch finished: input=…, output=…, lines=…, accepted=…, declined=…, duplicates=…, rejected=…, durationMs=…`
  (`output=stdout` when the answers went to the console). A run that stops early logs the counts so far as
  `Batch stopped: …` at WARN.
- **If any line was rejected, the run fails** (non-zero exit code) after all valid lines have been answered and
  the output is in place, so a scheduler notices.
- **Too many bad lines stop the run early:** over `batch.max-rejected-lines` (default 100; `0` stops at the first
  bad line). Override it on the command line, e.g. `--batch.max-rejected-lines=0`.
- **A system failure** (for example the database) stops the run at once, naming the line it was on: continuing
  could decide later loads wrongly.
- **`--output` is written atomically:** the answers go to `<output>.tmp` and are renamed over `<output>` only when
  the run completes. A failed or stopped run leaves an existing output file untouched and no partial file behind.
  A successful run replaces an existing output file.
- **A line longer than `batch.max-line-length`** (default 10,000 characters) is rejected like any bad line,
  without being kept in memory, so a wrong file (binary, one huge line) can't exhaust memory.
- A UTF-8 byte order mark at the start of the file is ignored.

To see why each load was accepted or declined, add `--logging.level.com.dkozel.velocitylimiter.service=DEBUG`
(one line per load, with the limits it broke). Log timestamps are in UTC.

## The HTTP endpoint

Start the service (with Docker: `docker compose up --build`; it listens on `127.0.0.1:8080` only. Without Docker:
`./gradlew bootRun`). Then send one load.

macOS / Linux:

```bash
curl -i -X POST http://localhost:8080/loads -H 'Content-Type: application/json' \
  -d '{"id":"1","customer_id":"528","load_amount":"$3318.47","time":"2000-01-01T00:00:00Z"}'
```

Windows PowerShell:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/loads -ContentType 'application/json' `
  -Body '{"id":"1","customer_id":"528","load_amount":"$3318.47","time":"2000-01-01T00:00:00Z"}'
```

- `200` with `{"id":"1","customer_id":"528","accepted":true}` (or `false` when a limit is exceeded).
- `200` with the **original** answer and the header `Idempotent-Replayed: true` when the same load (same customer,
  id, amount and time) is sent again, e.g. a retry after a timeout. Nothing new is decided or stored.
- `409` (`application/problem+json`) when the same customer and id come with a different amount or time.
- `400` as `application/problem+json` (RFC 9457) listing every invalid field, e.g.
  `"errors": [{"pointer": "#/load_amount", "detail": "must be a positive dollar amount …"}]`.
- `503` with `Retry-After: 1` when another load of the same customer holds its lock for over a second.
- `500` with a generic message for anything unexpected (logged once at ERROR, nothing internal returned).

Health check: `http://localhost:8080/actuator/health`. Stop the service with `docker compose down` (or Ctrl-C).

## Known limits

- **In-memory database:** each run starts empty, so separate batch runs don't share history. File-based H2 is a
  configuration change; PostgreSQL is the production choice.
- **Standard output is not atomic:** answers stream out as they are decided; use `--output` for an all-or-nothing
  file.
