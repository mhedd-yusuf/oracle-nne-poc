#!/bin/bash
# Appends NNE settings from the mounted docker/sqlnet.ora into the Oracle sqlnet.ora.
# Runs once during first database creation (first `docker compose up`).
set -e

SQLNET_ORA="${ORACLE_HOME}/network/admin/sqlnet.ora"
NNE_CONFIG="/opt/oracle/nne_config.ora"

echo ">>> [02_nne_config] Appending NNE settings to: ${SQLNET_ORA}"

echo "" >> "${SQLNET_ORA}"
echo "# ── Native Network Encryption (NNE) ──" >> "${SQLNET_ORA}"
cat "${NNE_CONFIG}" >> "${SQLNET_ORA}"

echo ">>> [02_nne_config] Done. Final sqlnet.ora:"
cat "${SQLNET_ORA}"
