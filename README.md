# Oracle Native Network Encryption (NNE) — POC

## What is Oracle NNE?

Oracle Native Network Encryption (NNE) encrypts data in transit between a JDBC client and an Oracle database at the network layer. Unlike TLS/SSL, it requires no certificates or PKI — encryption is negotiated automatically during the TNS handshake using symmetric algorithms (e.g. AES256) and integrity checksums (e.g. SHA256).

Both the client and server independently declare one of four levels:

| Level        | Meaning |
|--------------|---------|
| `REQUIRED`   | Must encrypt; reject connections that cannot |
| `REQUESTED`  | Prefer encryption; allow plaintext if peer refuses |
| `ACCEPTED`   | Encrypt if the peer requests it; plaintext otherwise |
| `REJECTED`   | Never encrypt |

The combination of both sides determines the outcome:

| Server \ Client    | `REQUIRED`  | `REQUESTED` | `ACCEPTED`  | `REJECTED`  | Not set ¹   |
|--------------------|-------------|-------------|-------------|-------------|-------------|
| `REQUIRED`         | Encrypted   | Encrypted   | Encrypted   | **FAIL**    | Encrypted   |
| `REQUESTED`        | Encrypted   | Encrypted   | Encrypted   | Plaintext   | Encrypted   |
| `ACCEPTED`         | Encrypted   | Encrypted   | Plaintext   | Plaintext   | Plaintext   |
| `REJECTED`         | **FAIL**    | Plaintext   | Plaintext   | Plaintext   | Plaintext   |
| Not set ²          | Encrypted   | Encrypted   | Plaintext   | Plaintext   | Plaintext   |

¹ Client does not set `oracle.net.encryption_client` — Oracle JDBC defaults to `ACCEPTED`.
² Server has no NNE entries in `sqlnet.ora` — Oracle server defaults to `ACCEPTED`.

---

## Setup

### 1. Start the database

```bash
cd docker
docker compose up -d
# Wait ~2 min for the healthcheck to pass
docker compose ps   # Status should show "healthy"
```

### 2. Build and run the app

```bash
# From the project root
./gradlew bootJar
java -jar build/libs/oracle-nne-poc.jar
```

The app connects to Oracle, creates an `EMPLOYEES` table, inserts 3 rows, and prints them.

---

## NNE Settings

**Client-side** — `src/main/resources/application.properties`:

```properties
nne.encryption.client=REQUIRED
nne.checksum.client=REQUIRED
```

> `DataSourceConfig` passes NNE values as Java connection properties via
> `OracleDataSource.setConnectionProperties()`. The Oracle JDBC **Thin** driver reads NNE
> settings from `oracle.net.encryption_client` / `oracle.net.crypto_checksum_client`
> connection properties — **not** from a SECURITY section in the TNS descriptor (that section
> is only read by OCI clients such as sqlplus).

**Server-side** — `docker/sqlnet.ora`:

```
SQLNET.ENCRYPTION_SERVER          = REQUIRED
SQLNET.CRYPTO_CHECKSUM_SERVER     = REQUIRED
```

> **Note:** `docker/sqlnet.ora` is read by the init script during first database creation.
> To apply server-side changes you must destroy and recreate the data volume:
> ```bash
> cd docker && docker compose down -v && docker compose up -d
> ```

---

## Validate encryption from inside the container

### Option A — Interactive sqlplus session

```bash
docker exec -it oracle-nne-poc sqlplus appuser/AppUser123@FREEPDB1
```

Then run this query to see all active user sessions, their application name, and whether NNE is active:

```sql
SET LINESIZE 120
SET PAGESIZE 50
COLUMN sid        FORMAT 99999   HEADING 'SID'
COLUMN serial#    FORMAT 9999999 HEADING 'SERIAL#'
COLUMN program    FORMAT A20     HEADING 'PROGRAM'
COLUMN machine    FORMAT A20     HEADING 'MACHINE'
COLUMN logon_time FORMAT A20     HEADING 'LOGON_TIME'
COLUMN status     FORMAT A10     HEADING 'STATUS'
COLUMN encrypted  FORMAT A9      HEADING 'ENCRYPTED'
COLUMN checksum   FORMAT A8      HEADING 'CHECKSUM'

SELECT
    s.sid,
    s.serial#,
    s.program,
    s.machine,
    s.logon_time,
    s.status,
    MAX(CASE WHEN sci.network_service_banner LIKE '%Encryption service adapter%'        THEN 'YES' ELSE 'NO' END) AS encrypted,
    MAX(CASE WHEN sci.network_service_banner LIKE '%Crypto-checksumming service adapter%' THEN 'YES' ELSE 'NO' END) AS checksum
FROM v$session s
LEFT JOIN v$session_connect_info sci
    ON s.sid = sci.sid AND s.serial# = sci.serial#
WHERE s.type     = 'USER'
  AND s.username IS NOT NULL
GROUP BY s.sid, s.serial#, s.program, s.machine, s.logon_time, s.status
ORDER BY s.logon_time DESC;
```

> **Why these specific patterns?** Oracle always emits a generic "Encryption service for Linux" banner even for
> plaintext connections (it is the service framework being loaded). The actual encryption is only active when
> the algorithm-specific adapter banner appears — e.g. "AES256 Encryption service adapter for linux-x86-64".
> Using `LIKE '%Encryption%'` matches the generic banner and always returns `YES`, giving a false positive on
> plaintext connections. The corrected patterns `'%Encryption service adapter%'` and
> `'%Crypto-checksumming service adapter%'` match only when a specific algorithm has been negotiated.

To filter by time (e.g. sessions from the last 30 minutes), add:

```sql
  AND s.logon_time >= SYSDATE - INTERVAL '30' MINUTE
```

### Option B — Single docker exec command (no interactive session needed)

```
docker exec -i oracle-nne-poc sqlplus -s appuser/AppUser123@FREEPDB1 <<'EOF'
SET LINESIZE 200
SET PAGESIZE 50
SET WRAP OFF
COLUMN db_name    FORMAT A12
COLUMN sid        FORMAT 9999
COLUMN serial#    FORMAT 999999
COLUMN username   FORMAT A10
COLUMN program    FORMAT A30
COLUMN machine    FORMAT A20
COLUMN logon_time FORMAT A20
COLUMN status     FORMAT A8
COLUMN encrypted  FORMAT A9
COLUMN checksum   FORMAT A8

SELECT
    SYS_CONTEXT('USERENV', 'CON_NAME')                                                                     AS db_name,
    s.sid,
    s.serial#,
    s.username,
    s.program,
    s.machine,
    TO_CHAR(s.logon_time, 'DD-MON-YY HH24:MI:SS')                                                         AS logon_time,
    s.status,
    MAX(CASE WHEN sci.network_service_banner LIKE '%Encryption service adapter%'          THEN 'YES' ELSE 'NO' END) AS encrypted,
    MAX(CASE WHEN sci.network_service_banner LIKE '%Crypto-checksumming service adapter%' THEN 'YES' ELSE 'NO' END) AS checksum
FROM v$session s
LEFT JOIN v$session_connect_info sci
    ON s.sid = sci.sid AND s.serial# = sci.serial#
WHERE s.type     = 'USER'
  AND s.username = 'APPUSER'
GROUP BY s.sid, s.serial#, s.username, s.program, s.machine, s.logon_time, s.status
ORDER BY s.logon_time DESC;

EXIT;
EOF
```

### Expected outputs

**Encrypted** — `ENCRYPTED` and `CHECKSUM` columns show `YES`:
```
SID  SERIAL#  PROGRAM          MACHINE     LOGON_TIME           STATUS    ENCRYPTED  CHECKSUM
---  -------  ---------------  ----------  -------------------  --------  ---------  --------
 23      441  oracle-nne-poc   macbook     22-MAR-26 10:01:05   INACTIVE  YES        YES
```

**Plaintext** — `ENCRYPTED` and `CHECKSUM` columns show `NO`:
```
SID  SERIAL#  PROGRAM          MACHINE     LOGON_TIME           STATUS    ENCRYPTED  CHECKSUM
---  -------  ---------------  ----------  -------------------  --------  ---------  --------
 31      108  oracle-nne-poc   macbook     22-MAR-26 10:00:47   INACTIVE  NO         NO
```

The `PROGRAM` column reflects `app.name` in `application.properties` — change it per application to tell connections apart when multiple apps connect to the same database.

---

## Scenarios

> **Note on rebuilding:**
> - **IntelliJ** — reads `src/main/resources/application.properties` directly from source. Just edit and re-run, no rebuild needed.
> - **Command line JAR** — run `./gradlew bootJar` after editing `application.properties`.

For each scenario:
1. Edit `src/main/resources/application.properties` — client-side NNE settings
2. Edit `docker/sqlnet.ora` — server-side NNE settings
3. Recreate the container: `cd docker && docker compose down -v && docker compose up -d`
4. Run the app — from IntelliJ click Run, or from terminal: `java -jar build/libs/oracle-nne-poc.jar`
5. Validate with the docker exec command (Option B above)

---

### Scenario 1 — FAIL: server REQUIRED, client REJECTED

**`application.properties`**
```properties
nne.encryption.client=REJECTED
nne.checksum.client=REJECTED
```

**`docker/sqlnet.ora`**
```
SQLNET.ENCRYPTION_SERVER          = REQUIRED
SQLNET.CRYPTO_CHECKSUM_SERVER     = REQUIRED
```

**Expected result:** App fails to connect:
```
ORA-12660: Encryption or crypto-checksumming parameters incompatible
```

---

### Scenario 2 — FAIL: server REJECTED, client REQUIRED

**`application.properties`**
```properties
nne.encryption.client=REQUIRED
nne.checksum.client=REQUIRED
```

**`docker/sqlnet.ora`**
```
SQLNET.ENCRYPTION_SERVER          = REJECTED
SQLNET.CRYPTO_CHECKSUM_SERVER     = REJECTED
```

**Expected result:** App fails to connect:
```
ORA-12660: Encryption or crypto-checksumming parameters incompatible
```

---

### Scenario 3 — Encrypted: server REQUIRED, client REQUIRED

**`application.properties`**
```properties
nne.encryption.client=REQUIRED
nne.checksum.client=REQUIRED
```

**`docker/sqlnet.ora`**
```
SQLNET.ENCRYPTION_SERVER          = REQUIRED
SQLNET.CRYPTO_CHECKSUM_SERVER     = REQUIRED
```

**Expected result:** App runs successfully. Sqlplus shows:
```
AES256 Encryption service adapter for linux-x86-64
SHA-256 Crypto-checksumming service adapter for linux-x86-64
```

---

### Scenario 4 — Encrypted: server REQUIRED, client REQUESTED

**`application.properties`**
```properties
nne.encryption.client=REQUESTED
nne.checksum.client=REQUESTED
```

**`docker/sqlnet.ora`**
```
SQLNET.ENCRYPTION_SERVER          = REQUIRED
SQLNET.CRYPTO_CHECKSUM_SERVER     = REQUIRED
```

**Expected result:** App runs successfully. Sqlplus shows:
```
AES256 Encryption service adapter for linux-x86-64
SHA-256 Crypto-checksumming service adapter for linux-x86-64
```

---

### Scenario 5 — Encrypted: server REQUIRED, client ACCEPTED

**`application.properties`**
```properties
nne.encryption.client=ACCEPTED
nne.checksum.client=ACCEPTED
```

**`docker/sqlnet.ora`**
```
SQLNET.ENCRYPTION_SERVER          = REQUIRED
SQLNET.CRYPTO_CHECKSUM_SERVER     = REQUIRED
```

**Expected result:** App runs successfully. Sqlplus shows:
```
AES256 Encryption service adapter for linux-x86-64
SHA-256 Crypto-checksumming service adapter for linux-x86-64
```

---

### Scenario 6 — Encrypted: server REQUESTED, client REQUIRED

**`application.properties`**
```properties
nne.encryption.client=REQUIRED
nne.checksum.client=REQUIRED
```

**`docker/sqlnet.ora`**
```
SQLNET.ENCRYPTION_SERVER          = REQUESTED
SQLNET.CRYPTO_CHECKSUM_SERVER     = REQUESTED
```

**Expected result:** App runs successfully. Sqlplus shows:
```
AES256 Encryption service adapter for linux-x86-64
SHA-256 Crypto-checksumming service adapter for linux-x86-64
```

---

### Scenario 7 — Encrypted: server REQUESTED, client REQUESTED

**`application.properties`**
```properties
nne.encryption.client=REQUESTED
nne.checksum.client=REQUESTED
```

**`docker/sqlnet.ora`**
```
SQLNET.ENCRYPTION_SERVER          = REQUESTED
SQLNET.CRYPTO_CHECKSUM_SERVER     = REQUESTED
```

**Expected result:** App runs successfully. Sqlplus shows:
```
AES256 Encryption service adapter for linux-x86-64
SHA-256 Crypto-checksumming service adapter for linux-x86-64
```

---

### Scenario 8 — Encrypted: server REQUESTED, client ACCEPTED

**`application.properties`**
```properties
nne.encryption.client=ACCEPTED
nne.checksum.client=ACCEPTED
```

**`docker/sqlnet.ora`**
```
SQLNET.ENCRYPTION_SERVER          = REQUESTED
SQLNET.CRYPTO_CHECKSUM_SERVER     = REQUESTED
```

**Expected result:** App runs successfully. Sqlplus shows:
```
AES256 Encryption service adapter for linux-x86-64
SHA-256 Crypto-checksumming service adapter for linux-x86-64
```

---

### Scenario 9 — Encrypted: server ACCEPTED, client REQUIRED

**`application.properties`**
```properties
nne.encryption.client=REQUIRED
nne.checksum.client=REQUIRED
```

**`docker/sqlnet.ora`**
```
SQLNET.ENCRYPTION_SERVER          = ACCEPTED
SQLNET.CRYPTO_CHECKSUM_SERVER     = ACCEPTED
```

**Expected result:** App runs successfully. Sqlplus shows:
```
AES256 Encryption service adapter for linux-x86-64
SHA-256 Crypto-checksumming service adapter for linux-x86-64
```

---

### Scenario 10 — Encrypted: server ACCEPTED, client REQUESTED

**`application.properties`**
```properties
nne.encryption.client=REQUESTED
nne.checksum.client=REQUESTED
```

**`docker/sqlnet.ora`**
```
SQLNET.ENCRYPTION_SERVER          = ACCEPTED
SQLNET.CRYPTO_CHECKSUM_SERVER     = ACCEPTED
```

**Expected result:** App runs successfully. Sqlplus shows:
```
AES256 Encryption service adapter for linux-x86-64
SHA-256 Crypto-checksumming service adapter for linux-x86-64
```

---

### Scenario 11 — Plaintext: server REQUESTED, client REJECTED

**`application.properties`**
```properties
nne.encryption.client=REJECTED
nne.checksum.client=REJECTED
```

**`docker/sqlnet.ora`**
```
SQLNET.ENCRYPTION_SERVER          = REQUESTED
SQLNET.CRYPTO_CHECKSUM_SERVER     = REQUESTED
```

**Expected result:** App runs successfully (no error). Option B shows `ENCRYPTED: NO, CHECKSUM: NO`:
```
 31      108  oracle-nne-poc   macbook     22-MAR-26 10:00:47   INACTIVE  NO         NO
```

---

### Scenario 12 — Plaintext: server ACCEPTED, client ACCEPTED

**`application.properties`**
```properties
nne.encryption.client=ACCEPTED
nne.checksum.client=ACCEPTED
```

**`docker/sqlnet.ora`**
```
SQLNET.ENCRYPTION_SERVER          = ACCEPTED
SQLNET.CRYPTO_CHECKSUM_SERVER     = ACCEPTED
```

**Expected result:** App runs successfully (no error). Option B shows `ENCRYPTED: NO, CHECKSUM: NO`:
```
 31      108  oracle-nne-poc   macbook     22-MAR-26 10:00:47   INACTIVE  NO         NO
```

---

### Scenario 13 — Plaintext: server ACCEPTED, client REJECTED

**`application.properties`**
```properties
nne.encryption.client=REJECTED
nne.checksum.client=REJECTED
```

**`docker/sqlnet.ora`**
```
SQLNET.ENCRYPTION_SERVER          = ACCEPTED
SQLNET.CRYPTO_CHECKSUM_SERVER     = ACCEPTED
```

**Expected result:** App runs successfully (no error). Option B shows `ENCRYPTED: NO, CHECKSUM: NO`:
```
 31      108  oracle-nne-poc   macbook     22-MAR-26 10:00:47   INACTIVE  NO         NO
```

---

### Scenario 14 — Plaintext: server REJECTED, client REQUESTED

**`application.properties`**
```properties
nne.encryption.client=REQUESTED
nne.checksum.client=REQUESTED
```

**`docker/sqlnet.ora`**
```
SQLNET.ENCRYPTION_SERVER          = REJECTED
SQLNET.CRYPTO_CHECKSUM_SERVER     = REJECTED
```

**Expected result:** App runs successfully (no error). Option B shows `ENCRYPTED: NO, CHECKSUM: NO`:
```
 31      108  oracle-nne-poc   macbook     22-MAR-26 10:00:47   INACTIVE  NO         NO
```

---

### Scenario 15 — Plaintext: server REJECTED, client ACCEPTED

**`application.properties`**
```properties
nne.encryption.client=ACCEPTED
nne.checksum.client=ACCEPTED
```

**`docker/sqlnet.ora`**
```
SQLNET.ENCRYPTION_SERVER          = REJECTED
SQLNET.CRYPTO_CHECKSUM_SERVER     = REJECTED
```

**Expected result:** App runs successfully (no error). Option B shows `ENCRYPTED: NO, CHECKSUM: NO`:
```
 31      108  oracle-nne-poc   macbook     22-MAR-26 10:00:47   INACTIVE  NO         NO
```

---

### Scenario 16 — Plaintext: server REJECTED, client REJECTED

**`application.properties`**
```properties
nne.encryption.client=REJECTED
nne.checksum.client=REJECTED
```

**`docker/sqlnet.ora`**
```
SQLNET.ENCRYPTION_SERVER          = REJECTED
SQLNET.CRYPTO_CHECKSUM_SERVER     = REJECTED
```

**Expected result:** App runs successfully (no error). Option B shows `ENCRYPTED: NO, CHECKSUM: NO`:
```
 31      108  oracle-nne-poc   macbook     22-MAR-26 10:00:47   INACTIVE  NO         NO
```

---

### Scenario 17 — No NNE settings in application.properties

Remove the `nne.*` lines from `application.properties`. The client defaults to `ACCEPTED`.

**`application.properties`** — omit nne.* lines

**`docker/sqlnet.ora`** — behaviour depends on the server setting:

| Server setting | Result |
|----------------|--------|
| `REQUIRED`     | Encrypted (server forces it, client ACCEPTED agrees) |
| `ACCEPTED`     | Plaintext (neither side actively requests it) |
| `REJECTED`     | Plaintext |
