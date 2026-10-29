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
proxy="${prefix}-proxy"
tls_dir=$(mktemp -d)
database_image="${SMOKE_DATABASE_IMAGE:-postgres:15-alpine}"
client_image="${SMOKE_HTTP_IMAGE:-curlimages/curl:8.12.1}"
proxy_image="${SMOKE_PROXY_IMAGE:-nginx:stable-alpine}"

cleanup() {
  result=$?
  trap - EXIT
  if [ "$result" -ne 0 ]; then
    docker logs --tail 100 "$application" 2>&1 || true
    docker logs --tail 50 "$database" 2>&1 || true
    docker logs --tail 50 "$proxy" 2>&1 || true
  fi
  docker rm -fv "$client" "$application" "$database" "$proxy" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf "$tls_dir"
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Pull public helper images before starting the isolated environment.
docker pull "$database_image" >/dev/null
docker pull "$client_image" >/dev/null
docker pull "$proxy_image" >/dev/null
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

# Exercise the actual repository proxy template with an isolated test certificate.
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj '/CN=smoke.invalid' \
  -addext 'subjectAltName=DNS:smoke.invalid' \
  -keyout "$tls_dir/key.pem" -out "$tls_dir/cert.pem" >/dev/null 2>&1
docker create --name "$proxy" --network "$network" "$proxy_image" sh -c \
  'mkdir -p /etc/nginx/tls; cp /tmp/cert.pem /etc/nginx/tls/fullchain.pem; cp /tmp/key.pem /etc/nginx/tls/privkey.pem; until nginx -t >/dev/null 2>&1; do sleep 1; done; exec nginx -g "daemon off;"' >/dev/null
docker cp deploy/nginx.conf "$proxy:/etc/nginx/conf.d/default.conf"
docker cp "$tls_dir/cert.pem" "$proxy:/tmp/cert.pem"
docker cp "$tls_dir/key.pem" "$proxy:/tmp/key.pem"
docker start "$proxy" >/dev/null
proxy_ip=$(docker inspect --format "{{(index .NetworkSettings.Networks \"$network\").IPAddress}}" "$proxy")

docker run -d --name "$application" --network "$network" \
  --network-alias contact-service \
  -e SPRING_SERVER_PORT=8080 \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://${database}:5432/contact_smoke" \
  -e SPRING_DATASOURCE_USERNAME=smoke -e SPRING_DATASOURCE_PASSWORD=isolated-test-password \
  -e SPRING_SECURITY_USER_NAME=smoke -e SPRING_SECURITY_USER_PASSWORD=isolated-test-password \
  -e SPRING_MAIL_HOST=127.0.0.1 -e SPRING_MAIL_PORT=9 \
  -e SPRING_MAIL_USERNAME=service@example.com -e SPRING_MAIL_PASSWORD=unused \
  -e MANAGEMENT_HEALTH_MAIL_ENABLED=false \
  -e NOTIFICATION_ENABLED=false -e NOTIFICATIONS_RETRY_ENABLED=false \
  -e SECURITY_TRUSTED_PROXIES="$proxy_ip" \
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
case "$health" in *components*|*details*) echo 'Health exposed internal details' >&2; exit 1 ;; esac

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
  --header 'X-Forwarded-For: 203.0.113.99' --header 'X-Real-IP: 203.0.113.99' \
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
spoofed=$(docker exec "$database" psql -U smoke -d contact_smoke -Atc \
  "SELECT count(*) FROM contact_messages WHERE sourceip = '203.0.113.99'")
test "$spoofed" = 0

# Read the JSON error contract without printing response bodies to CI artifacts.
failure=$(http --silent --show-error "$base/api/v1/admin/contact/messages")
[[ "$failure" == *'"status":401'* && "$failure" == *'"requestId":'* && "$failure" == *'"details":[]'* ]]
assert_status 401 "$base/v3/api-docs"
assert_status 401 "$base/actuator/env"

https() {
  docker create --name "$client" --network "$network" "$client_image" \
    --silent --show-error --connect-timeout 2 --max-time 10 --cacert /tmp/ca.pem \
    --resolve "smoke.invalid:443:$proxy_ip" "$@" >/dev/null
  docker cp "$tls_dir/cert.pem" "$client:/tmp/ca.pem"
  docker start --attach "$client"
  local result
  result=$(docker inspect --format '{{.State.ExitCode}}' "$client")
  docker rm "$client" >/dev/null
  return "$result"
}
proxy_ready=false
for ((attempt = 0; attempt < 20; attempt++)); do
  if https --fail https://smoke.invalid/actuator/health >/dev/null; then proxy_ready=true; break; fi
  sleep 1
done
test "$proxy_ready" = true
proxy_status=$(https --output /dev/null --write-out '%{http_code}' \
  --header 'Content-Type: application/json' --header 'X-Real-IP: 203.0.113.99' \
  --header 'X-Forwarded-For: 203.0.113.99' \
  --data '{"email":"proxy@example.com","subject":"Proxy smoke test","content":"A proxy identity verification message."}' \
  https://smoke.invalid/api/v1/contact/messages)
test "$proxy_status" = 201
edge_client=$(docker logs "$proxy" 2>&1 | awk '/"POST \/api\/v1\/contact\/messages/ {print $1}' | tail -1)
stored_client=$(docker exec "$database" psql -U smoke -d contact_smoke -Atc \
  "SELECT sourceip FROM contact_messages WHERE email = 'proxy@example.com'")
test -n "$edge_client"
test "$edge_client" = "$stored_client"
test "$stored_client" != 203.0.113.99
failure=$(https --header 'Content-Type: application/json' --data '{"email":"private-fixture"' \
  https://smoke.invalid/api/v1/contact/messages)
[[ "$failure" == *'"status":400'* && "$failure" == *'"requestId":'* && "$failure" != *private-fixture* ]]
echo "Container smoke verification passed: health, errors, proxy identity, persistence and admin authentication."
