-- NNE Session Status Query — SQL Developer
-- Connection: localhost:1521/FREEPDB1  |  user: appuser  |  password: AppUser123
--
-- Queries V$SESSION which only shows CURRENTLY CONNECTED sessions.
-- The application must be running and holding an open connection for rows to appear.

SELECT
    SYS_CONTEXT('USERENV', 'CON_NAME')           AS db_name,
    s.sid,
    s.serial#,
    s.username,
    s.program,
    s.machine,
    TO_CHAR(s.logon_time, 'DD-MON-YY HH24:MI:SS') AS logon_time,
    s.status,
    MAX(CASE WHEN sci.network_service_banner LIKE '%Encryption service adapter%'          THEN 'YES' ELSE 'NO' END) AS encrypted,
    MAX(CASE WHEN sci.network_service_banner LIKE '%Crypto-checksumming service adapter%' THEN 'YES' ELSE 'NO' END) AS checksum
FROM v$session s
LEFT JOIN v$session_connect_info sci
    ON s.sid = sci.sid AND s.serial# = sci.serial#
WHERE s.type     = 'USER'
  AND s.username = 'APPUSER'
  AND s.machine  LIKE '%MacBook%'
  AND s.program  LIKE '%oracle-nne-poc%'
  AND SYS_CONTEXT('USERENV', 'CON_NAME') = 'FREEPDB1'
GROUP BY s.sid, s.serial#, s.username, s.program, s.machine, s.logon_time, s.status
ORDER BY s.logon_time DESC
