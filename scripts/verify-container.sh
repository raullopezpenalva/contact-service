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
auth_client="${prefix}-auth"
key_volume="${prefix}-keys"
tls_dir=$(mktemp -d)
database_image="${SMOKE_DATABASE_IMAGE:-postgres:15-alpine}"
client_image="${SMOKE_HTTP_IMAGE:-curlimages/curl:8.12.1}"
auth_image="${SMOKE_AUTH_IMAGE:-python:3.12-slim}"
proxy_image="${SMOKE_PROXY_IMAGE:-nginx:stable-alpine}"

cleanup() {
  result=$?
  trap - EXIT
  if [ "$result" -ne 0 ]; then
    docker logs --tail 100 "$application" 2>&1 || true
    docker logs --tail 50 "$database" 2>&1 || true
    docker logs --tail 50 "$proxy" 2>&1 || true
  fi
  docker rm -fv "$client" "$application" "$database" "$proxy" "$auth_client" >/dev/null 2>&1 || true
  docker volume rm "$key_volume" >/dev/null 2>&1 || true
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
docker pull "$auth_image" >/dev/null
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
docker cp deploy/admin-allow.conf "$proxy:/etc/nginx/admin-allow.conf"
docker cp "$tls_dir/cert.pem" "$proxy:/tmp/cert.pem"
docker cp "$tls_dir/key.pem" "$proxy:/tmp/key.pem"
docker start "$proxy" >/dev/null
proxy_ip=$(docker inspect --format "{{(index .NetworkSettings.Networks \"$network\").IPAddress}}" "$proxy")

# Generate temporary RSA material inside the devcontainer/CI, then copy to a private
# Docker volume (the Docker daemon may be outside the devcontainer filesystem).
umask 077
mkdir -p "$tls_dir/jwt/public"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$tls_dir/jwt/private.pem" >/dev/null 2>&1
openssl pkey -in "$tls_dir/jwt/private.pem" -pubout -out "$tls_dir/jwt/public/smoke.pub.pem" >/dev/null 2>&1
printf '%s' 'Synthetic runtime admin password 47!' > "$tls_dir/jwt/password"
docker volume create "$key_volume" >/dev/null
tar -C "$tls_dir/jwt" -cf - . | docker run --rm -i --network none --user 0 \
  -v "$key_volume:/run/jwt" --entrypoint sh "$image" -c \
  'umask 077; tar -xf - -C /run/jwt; chown -R 10001:10001 /run/jwt; chmod 700 /run/jwt /run/jwt/public; chmod 400 /run/jwt/private.pem /run/jwt/password'
# The narrow operator migrates/provisions without JWT, mail or web configuration.
docker run --rm --network "$network" -v "$key_volume:/run/jwt:ro" \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://${database}:5432/contact_smoke" \
  -e SPRING_DATASOURCE_USERNAME=smoke -e SPRING_DATASOURCE_PASSWORD=isolated-test-password \
  "$image" --admin-operator provision --login smoke-admin --password-file /run/jwt/password >/dev/null

docker run -d --name "$application" --network "$network" \
  --network-alias contact-service -v "$key_volume:/run/jwt:ro" \
  -e SPRING_SERVER_PORT=8080 \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://${database}:5432/contact_smoke" \
  -e SPRING_DATASOURCE_USERNAME=smoke -e SPRING_DATASOURCE_PASSWORD=isolated-test-password \
  -e JWT_ISSUER=https://smoke.invalid -e JWT_ACTIVE_KEY_ID=smoke \
  -e JWT_PRIVATE_KEY_FILE=/run/jwt/private.pem -e JWT_PUBLIC_KEY_DIRECTORY=/run/jwt/public \
  -e SPRING_MAIL_HOST=127.0.0.1 -e SPRING_MAIL_PORT=9 \
  -e SPRING_MAIL_USERNAME=service@example.com -e SPRING_MAIL_PASSWORD=unused \
  -e MANAGEMENT_HEALTH_MAIL_ENABLED=false \
  -e NOTIFICATION_ENABLED=false -e NOTIFICATIONS_RETRY_ENABLED=false \
  -e SECURITY_TRUSTED_PROXIES="$proxy_ip" \
  "$image" >/dev/null
docker create --name "$auth_client" --network "$network" "$auth_image" sleep infinity >/dev/null
docker cp scripts/runtime-auth-smoke.py "$auth_client:/tmp/auth-smoke.py"
docker start "$auth_client" >/dev/null

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
assert_status 401 --user smoke:isolated-test-password "$base/api/v1/admin/contact/messages"

docker exec "$auth_client" python /tmp/auth-smoke.py "$base" before
docker restart "$application" >/dev/null
ready=false
for ((attempt=0; attempt<60; attempt++)); do
  if http --fail --silent --connect-timeout 2 --max-time 3 "$base/actuator/health" >/dev/null; then ready=true; break; fi
  sleep 1
done
test "$ready" = true
docker exec "$auth_client" python /tmp/auth-smoke.py "$base" after

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
# The unconfigured private management boundary fails closed at the actual TLS proxy.
private_status=$(https --output /dev/null --write-out '%{http_code}' https://smoke.invalid/api/v1/auth/login)
test "$private_status" = 403
private_status=$(https --output /dev/null --write-out '%{http_code}' https://smoke.invalid/api/v1/admin/contact/messages)
test "$private_status" = 403
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
