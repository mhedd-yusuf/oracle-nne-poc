#!/bin/bash
# --------------------------------------------------------
# Runs once on first container start, after APP_USER is
# created by the gvenzl entrypoint.
# Grants the appuser read access to V$SESSION_CONNECT_INFO
# so the Java app can validate NNE encryption status.
# --------------------------------------------------------
set -e

echo ">>> Granting V\$SESSION_CONNECT_INFO access to ${APP_USER}..."

sqlplus -s "SYSTEM/${ORACLE_PASSWORD}@//localhost:1521/FREEPDB1" << 'ENDSQL'
GRANT SELECT ON V_$SESSION_CONNECT_INFO TO appuser;
COMMIT;
EXIT;
ENDSQL

echo ">>> Grants complete."
