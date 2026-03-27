#!/usr/bin/env bash
# NNE Session Status Query — docker exec / sqlplus
# Container: oracle-nne-poc   |   user: appuser   |   password: AppUser123
#
# V$SESSION only shows CURRENTLY CONNECTED sessions.
# The application must be running with an open connection for rows to appear.
#
# Usage:
#   chmod +x queries/session_nne_status_docker.sh
#   ./queries/session_nne_status_docker.sh
#
# Override defaults:
#   CONTAINER=my-container DB_USER=APPUSER DB_PASS=AppUser123 \
#   SERVICE=FREEPDB1 APP_NAME=oracle-nne-poc ./queries/session_nne_status_docker.sh

CONTAINER="${CONTAINER:-oracle-nne-poc}"
DB_USER="${DB_USER:-appuser}"
DB_PASS="${DB_PASS:-AppUser123}"
SERVICE="${SERVICE:-FREEPDB1}"
APP_NAME="${APP_NAME:-oracle-nne-poc}"

docker exec -i "$CONTAINER" sqlplus -s "${DB_USER}/${DB_PASS}@${SERVICE}" <<EOF
SET LINESIZE 200
SET PAGESIZE 50
SET WRAP OFF
COLUMN db_name   FORMAT A12
COLUMN sid       FORMAT 9999
COLUMN serial#   FORMAT 999999
COLUMN username  FORMAT A10
COLUMN program   FORMAT A30
COLUMN machine   FORMAT A20
COLUMN logon_time FORMAT A20
COLUMN status    FORMAT A8
COLUMN encrypted FORMAT A9
COLUMN checksum  FORMAT A8

SELECT
    SYS_CONTEXT('USERENV', 'CON_NAME')                                                                    AS db_name,
    s.sid,
    s.serial#,
    s.username,
    s.program,
    s.machine,
    TO_CHAR(s.logon_time, 'DD-MON-YY HH24:MI:SS')                                                        AS logon_time,
    s.status,
    MAX(CASE WHEN sci.network_service_banner LIKE '%Encryption service adapter%'         THEN 'YES' ELSE 'NO' END) AS encrypted,
    MAX(CASE WHEN sci.network_service_banner LIKE '%Crypto-checksumming service adapter%' THEN 'YES' ELSE 'NO' END) AS checksum
FROM v\$session s
LEFT JOIN v\$session_connect_info sci
    ON s.sid = sci.sid AND s.serial# = sci.serial#
WHERE s.type     = 'USER'
  AND s.username = UPPER('${DB_USER}')
GROUP BY s.sid, s.serial#, s.username, s.program, s.machine, s.logon_time, s.status
ORDER BY s.logon_time DESC;

EXIT;
EOF
