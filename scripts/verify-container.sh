#!/usr/bin/env bash
set -euo pipefail

# Run from the devcontainer locally, or the existing GitHub-hosted CI runner.
# No host ports, production credentials, external notifications or image pushes.
image="${1:?Usage: bash scripts/verify-container.sh IMAGE}"
prefix="contact-smoke-${RANDOM}-${RANDOM}"
network="${prefix}-net"
database="${prefix}-db"
application="${prefix}-app"
client="${prefix}-http"
database_image="${SMOKE_DATABASE_IMAGE:-postgres:15-alpine}"
client_image="${SMOKE_HTTP_IMAGE:-curlimages/curl:8.12.1}"

cleanup() {
  result=$?
  trap - EXIT
  if [ "$result" -ne 0 ]; then
    docker logs --tail 100 "$application" 2>&1 || true
    docker logs --tail 50 "$database" 2>&1 || true
  fi
  docker rm -f "$client" "$application" "$database" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Pull public helper images before starting the isolated environment.
docker pull "$database_image" >/dev/null
docker pull "$client_image" >/dev/null
docker network create --internal "$network" >/dev/null
docker run -d --name "$database" --network "$network" \
  -e POSTGRES_DB=contact_smoke -e POSTGRES_USER=smoke \
  -e POSTGRES_PASSWORD=isolated-test-password "$database_image" >/dev/null

ready=false
for ((attempt = 0; attempt < 60; attempt++)); do
  if docker exec "$database" pg_isready -U smoke -d contact_smoke >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
if [ "$ready" != true ]; then
  echo "Temporary PostgreSQL did not become ready" >&2
  exit 1
fi

docker run -d --name "$application" --network "$network" \
  -e SPRING_SERVER_PORT=8080 \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://${database}:5432/contact_smoke" \
  -e SPRING_DATASOURCE_USERNAME=smoke -e SPRING_DATASOURCE_PASSWORD=isolated-test-password \
  -e SPRING_SECURITY_USER_NAME=smoke -e SPRING_SECURITY_USER_PASSWORD=isolated-test-password \
  -e SPRING_MAIL_HOST=127.0.0.1 -e SPRING_MAIL_PORT=9 \
  -e SPRING_MAIL_USERNAME=service@example.com -e SPRING_MAIL_PASSWORD=unused \
  -e MANAGEMENT_HEALTH_MAIL_ENABLED=false \
  -e NOTIFICATION_ENABLED=false -e NOTIFICATIONS_RETRY_ENABLED=false \
  "$image" >/dev/null

http() {
  docker run --rm --name "$client" --network "$network" "$client_image" "$@"
}
base="http://${application}:8080"
ready=false
for ((attempt = 0; attempt < 60; attempt++)); do
  if [ "$(docker inspect --format '{{.State.Running}}' "$application")" != true ]; then
    echo "Application container exited before becoming healthy" >&2
    exit 1
  fi
  if health=$(http --fail --silent --show-error --connect-timeout 2 --max-time 3 \
      "$base/actuator/health"); then
    health="${health//[[:space:]]/}"
    case "$health" in
      *'"status":"UP"'*) ready=true; break ;;
    esac
  fi
  sleep 1
done
if [ "$ready" != true ]; then
  echo "Runtime health did not become UP" >&2
  exit 1
fi

assert_status() {
  expected="$1"
  shift
  actual=$(http --silent --show-error --connect-timeout 2 --max-time 10 \
    --output /dev/null --write-out '%{http_code}' "$@")
  if [ "$actual" != "$expected" ]; then
    echo "Expected HTTP ${expected}, received ${actual}" >&2
    exit 1
  fi
}
assert_status 201 --header 'Content-Type: application/json' \
  --data '{"email":"visitor@example.com","subject":"Container smoke test","content":"An isolated PostgreSQL-backed smoke test."}' \
  "$base/api/v1/contact/messages"
assert_status 401 "$base/api/v1/admin/contact/messages"
assert_status 200 --user smoke:isolated-test-password "$base/api/v1/admin/contact/messages"

# The API success is backed by an actual row in temporary PostgreSQL.
rows=$(docker exec "$database" psql -U smoke -d contact_smoke -Atc \
  "SELECT count(*) FROM contact_messages WHERE email = 'visitor@example.com'")
if [ "$rows" != 1 ]; then
  echo "Expected one persisted contact message, received ${rows}" >&2
  exit 1
fi
echo "Container smoke verification passed: health, creation, persistence and admin authentication."
