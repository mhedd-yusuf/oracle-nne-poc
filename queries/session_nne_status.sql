-- NNE Session Status Query
-- Run in SQL Developer connected to: localhost:1521/FREEPDB1 (appuser / AppUser123)
--
-- Shows all user sessions from the last 24 hours with their NNE encryption status.
-- Filter by username, machine (host), and program (v$session.program / app.name).
-- Remove any AND line you don't need.

SELECT
    s.sid,
    s.serial#,
    s.username,
    s.program,
    s.machine,
    TO_CHAR(s.logon_time, 'DD-MON-YY HH24:MI:SS') AS logon_time,
    s.status,
    MAX(CASE WHEN sci.network_service_banner LIKE '%Encryption service adapter%'        THEN 'YES' ELSE 'NO' END) AS encrypted,
    MAX(CASE WHEN sci.network_service_banner LIKE '%Crypto-checksumming service adapter%' THEN 'YES' ELSE 'NO' END) AS checksum
FROM v$session s
LEFT JOIN v$session_connect_info sci
    ON s.sid = sci.sid AND s.serial# = sci.serial#
WHERE s.type        = 'USER'
  AND s.username    = 'APPUSER'               -- Oracle DB user (uppercase)
  AND s.machine     LIKE '%MacBook%'          -- client hostname
  AND s.program     LIKE '%oracle-nne-poc%'   -- app.name from application.properties
  AND s.logon_time >= SYSDATE - 1             -- last 24 hours (change to SYSDATE - 7 for 7 days)
GROUP BY s.sid, s.serial#, s.username, s.program, s.machine, s.logon_time, s.status
ORDER BY s.logon_time DESC
