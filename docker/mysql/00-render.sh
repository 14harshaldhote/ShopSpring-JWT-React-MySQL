#!/bin/sh
# Renders 01-users.sql with passwords from the environment, then lets the MySQL entrypoint run it.
set -eu
sed -e "s/__MIGRATOR_PASSWORD__/${DB_MIGRATION_PASSWORD}/" -e "s/__APP_PASSWORD__/${DB_PASSWORD}/" \
    /docker-entrypoint-initdb.d/01-users.sql.template > /tmp/01-users.sql
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" < /tmp/01-users.sql
rm -f /tmp/01-users.sql
