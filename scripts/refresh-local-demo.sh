#!/usr/bin/env bash
# Refresh one running local-demo service without rebuilding Docker images.
# Use a clean `docker compose build` only when the Dockerfile, OS packages, or image-level config changes.
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: bash scripts/refresh-local-demo.sh <frontend|java-service>" >&2
  exit 64
fi

service="$1"
container_id="$(docker compose ps -q "$service")"
if [[ -z "$container_id" ]]; then
  echo "Service '$service' is not running. Start the stack with docker compose up -d first." >&2
  exit 1
fi

if [[ "$service" == "frontend" ]]; then
  npm --prefix frontend run build
  docker cp frontend/dist/. "${container_id}:/usr/share/nginx/html/"
  echo "Refreshed frontend assets without rebuilding an image."
  exit 0
fi

if [[ ! -f "$service/pom.xml" ]]; then
  echo "'$service' is neither the frontend nor a Maven service." >&2
  exit 64
fi

./mvnw -pl "$service" -am package -DskipTests
artifact="$service/target/${service}-0.0.1-SNAPSHOT-exec.jar"
if [[ ! -f "$artifact" ]]; then
  artifact="$service/target/${service}-0.0.1-SNAPSHOT.jar"
fi
if [[ ! -f "$artifact" ]]; then
  echo "Could not find a packaged artifact for '$service'." >&2
  exit 1
fi

docker cp "$artifact" "${container_id}:/app/app.jar"
docker restart "$container_id" >/dev/null
echo "Refreshed $service without rebuilding an image."
