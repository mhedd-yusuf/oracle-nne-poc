-- NNE Session Log — one-time setup
-- Run this script as SYS connected to FREEPDB1:
--   SQL Developer connection: localhost:1521/FREEPDB1  |  user: sys  |  role: SYSDBA
--   Docker:  docker exec -i oracle-nne-poc sqlplus -s sys/Oracle123@FREEPDB1 as sysdba < queries/nne_session_log_setup.sql
--
-- What this creates:
--   APPUSER.NNE_SESSION_LOG      — one row per APPUSER connection with NNE status
--   SYS.CAPTURE_NNE_ON_LOGON    — database-level logon trigger that populates it
--
-- After setup, every time the Java app connects a row is inserted automatically.
-- No changes to the Java application are required.

-- ── Step 1: log table ────────────────────────────────────────────────────────
CREATE TABLE appuser.nne_session_log (
    log_id     NUMBER        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sid        NUMBER,
    serial#    NUMBER,
    db_name    VARCHAR2(30),
    username   VARCHAR2(30),
    program    VARCHAR2(64),
    machine    VARCHAR2(64),
    logon_time TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    encrypted  VARCHAR2(3),
    checksum   VARCHAR2(3)
);

-- ── Step 2: logon trigger ────────────────────────────────────────────────────
-- Fires for every session that logs in to the database.
-- The IF guard restricts actual work to APPUSER connections only.
-- EXCEPTION block ensures a logging failure never blocks a connection.
CREATE OR REPLACE TRIGGER sys.capture_nne_on_logon
AFTER LOGON ON DATABASE
BEGIN
    IF SYS_CONTEXT('USERENV', 'SESSION_USER') = 'APPUSER' THEN
        INSERT INTO appuser.nne_session_log
            (sid, serial#, db_name, username, program, machine, encrypted, checksum)
        SELECT
            s.sid,
            s.serial#,
            SYS_CONTEXT('USERENV', 'CON_NAME'),
            s.username,
            s.program,
            s.machine,
            MAX(CASE WHEN sci.network_service_banner LIKE '%Encryption service adapter%'          THEN 'YES' ELSE 'NO' END),
            MAX(CASE WHEN sci.network_service_banner LIKE '%Crypto-checksumming service adapter%' THEN 'YES' ELSE 'NO' END)
        FROM v$session s
        LEFT JOIN v$session_connect_info sci
            ON s.sid = sci.sid AND s.serial# = sci.serial#
        WHERE s.sid = SYS_CONTEXT('USERENV', 'SID')
        GROUP BY s.sid, s.serial#, s.username, s.program, s.machine;
        COMMIT;
    END IF;
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/
