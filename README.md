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

**Server-side** — `docker/sqlnet.ora`:

```
SQLNET.ENCRYPTION_SERVER      = REQUIRED
SQLNET.CRYPTO_CHECKSUM_SERVER = REQUIRED
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

Then run this query:

```sql
SELECT network_service_banner
FROM   v$session_connect_info
WHERE  sid = SYS_CONTEXT('USERENV', 'SID')
ORDER  BY 1;
```

### Option B — Single docker exec command (no interactive session needed)

```bash
docker exec oracle-nne-poc bash -c "sqlplus -s appuser/AppUser123@FREEPDB1 @/dev/stdin" <<'EOF'
SELECT network_service_banner
FROM   v$session_connect_info
WHERE  sid = SYS_CONTEXT('USERENV', 'SID')
ORDER  BY 1;
EXIT;
EOF
```

### Expected outputs

**Encrypted** — output includes:
```
AES256 Encryption service adapter for linux-x86-64
SHA-256 Crypto-checksumming service adapter for linux-x86-64
```

**Plaintext** — output is:
```
no rows selected
```

---

## Scenarios

For each scenario:
1. Edit `application.properties`
2. Edit `docker/sqlnet.ora`
3. Recreate the container: `cd docker && docker compose down -v && docker compose up -d`
4. Run the app: `java -jar build/libs/oracle-nne-poc.jar`
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

**Expected result:** App runs successfully (no error). Sqlplus shows:
```
no rows selected
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

**Expected result:** App runs successfully (no error). Sqlplus shows:
```
no rows selected
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

**Expected result:** App runs successfully (no error). Sqlplus shows:
```
no rows selected
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

**Expected result:** App runs successfully (no error). Sqlplus shows:
```
no rows selected
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

**Expected result:** App runs successfully (no error). Sqlplus shows:
```
no rows selected
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

**Expected result:** App runs successfully (no error). Sqlplus shows:
```
no rows selected
```

---

### Scenario 17 — No NNE properties in application.properties

Remove all `nne.*` lines from `application.properties`. The client defaults to `ACCEPTED`.

**`application.properties`**
```properties
spring.datasource.url=jdbc:oracle:thin:@//localhost:1521/FREEPDB1
spring.datasource.username=appuser
spring.datasource.password=AppUser123
```

**`docker/sqlnet.ora`** — behaviour depends on the server setting:

| Server setting | Result |
|----------------|--------|
| `REQUIRED`     | Encrypted (server forces it, client ACCEPTED agrees) |
| `ACCEPTED`     | Plaintext (neither side actively requests it) |
| `REJECTED`     | Plaintext |
