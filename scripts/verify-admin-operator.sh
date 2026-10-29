#!/usr/bin/env bash
set -euo pipefail
image="${1:?Usage: bash scripts/verify-admin-operator.sh IMAGE}"
prefix="admin-operator-${RANDOM}-${RANDOM}"
network="$prefix-net"
database="$prefix-db"
operator="$prefix-command"
input="$prefix-input"
operator_db=operator
cleanup() {
  result=$?
  trap - EXIT
  if [ "$result" -ne 0 ]; then docker logs "$operator" 2>&1 || true; fi
  docker rm -fv "$operator" "$database" >/dev/null 2>&1 || true
  docker volume rm "$input" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

docker pull postgres:15-alpine >/dev/null
docker network create --internal "$network" >/dev/null
docker volume create "$input" >/dev/null
# Synthetic input supplied via stdin; the runtime user owns the 600 file.
printf '%s' 'Synthetic isolated operator password 123' | docker run --rm -i --network none --user 0 \
  -v "$input:/run/input" --entrypoint sh "$image" -c \
  'umask 077; cat > /run/input/password; chown 10001:10001 /run/input/password'
docker run -d --name "$database" --network "$network" \
  -e POSTGRES_USER=operator -e POSTGRES_DB=operator -e POSTGRES_PASSWORD=isolated-db-password \
  postgres:15-alpine >/dev/null
for ((attempt=0; attempt<60; attempt++)); do
  if docker exec "$database" pg_isready -h 127.0.0.1 -U operator >/dev/null 2>&1; then break; fi
  sleep 1
done
run_operator() {
  docker create --name "$operator" --network "$network" -v "$input:/run/input:ro" \
    -e SPRING_DATASOURCE_URL="jdbc:postgresql://$database:5432/$operator_db" \
    -e SPRING_DATASOURCE_USERNAME=operator -e SPRING_DATASOURCE_PASSWORD=isolated-db-password \
    "$image" --admin-operator "$@" >/dev/null
  docker start --attach "$operator" >/dev/null
  local result
  result=$(docker inspect --format '{{.State.ExitCode}}' "$operator")
  local logs
  logs=$(docker logs "$operator" 2>&1)
  if [[ "$logs" == *'Synthetic isolated operator password'* || "$logs" == *"\$argon2id\$"* || "$logs" == *'Tomcat started'* ]]; then
    echo 'Operator exposed sensitive input or started HTTP' >&2; return 1
  fi
  docker rm "$operator" >/dev/null
  return "$result"
}
run_operator provision --login OWNER --password-file /run/input/password
run_operator provision --login owner --password-file /run/input/password
rows=$(docker exec "$database" psql -U operator -d operator -Atc 'SELECT count(*) FROM admin_accounts')
test "$rows" = 1
bad_hashes=$(docker exec "$database" psql -U operator -d operator -Atc \
  "SELECT count(*) FROM admin_accounts WHERE password_hash NOT LIKE '\$argon2id\$%'")
test "$bad_hashes" = 0
if run_operator disable --login owner; then echo 'Disabled last admin unexpectedly' >&2; exit 1; fi
run_operator provision --login backup --password-file /run/input/password
run_operator reset-password --login owner --password-file /run/input/password
run_operator disable --login owner
run_operator enable --login owner
state=$(docker exec "$database" psql -U operator -d operator -Atc \
  "SELECT enabled,authentication_version FROM admin_accounts WHERE login='owner'")
test "$state" = 't|3'
docker exec "$database" createdb -U operator legacy_operator
docker exec -i "$database" psql -U operator -d legacy_operator < src/test/resources/db/legacy-v1_3_0.sql >/dev/null
operator_db=legacy_operator
run_operator baseline-legacy --confirm-offline
run_operator provision --login legacy-owner --password-file /run/input/password
version=$(docker exec "$database" psql -U operator -d legacy_operator -Atc \
  "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")
test "$version" = 2
echo 'Admin operator smoke passed: guarded legacy baseline, private provision, repeat safety, reset, enable/disable and last-admin protection.'
