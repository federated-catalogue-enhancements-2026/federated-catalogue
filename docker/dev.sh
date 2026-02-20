#!/usr/bin/env bash
set -euo pipefail

# Convenience wrapper for common docker compose dev workflows.
# Works with Git Bash on Windows and any POSIX shell on macOS/Linux.
#
# Usage: ./dev.sh <command>

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

COMPOSE_DEV="docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file dev.env"
COMPOSE_FULL="docker compose --env-file dev.env"

usage() {
  cat <<EOF
Usage: ./dev.sh <command> [options]

COMMANDS:
  up          Start infrastructure only (postgres, neo4j, keycloak, nats)
              Use this to run fc-server locally with Maven
              Example: ./dev.sh up

  run         Run fc-server locally with Spring Boot (requires 'up' first)
              Starts Spring Boot with dev profile and devtools hot-reload
              Example: ./dev.sh run

  watch       Start full stack with hot-reload enabled (containerized server)
              The server container automatically restarts when the JAR changes
              Example: ./dev.sh build && ./dev.sh watch
                       # Edit code, then: ./dev.sh build

  full        Start full stack without hot-reload (traditional docker-compose)
              Example: ./dev.sh full

  down        Stop and remove all containers
              Example: ./dev.sh down

  build       Build the fc-service-server JAR (skips tests)
              Required after code changes to trigger hot-reload
              Example: ./dev.sh build

  test        Run the hot-reload verification script

  logs        Tail fc-server logs
              Example: ./dev.sh logs
                       ./dev.sh logs -f --tail=100

  ps          Show running containers
              Example: ./dev.sh ps

  help        Show this help message

WORKFLOWS:
  Development Workflow 1 — Run fc-server locally with Spring Boot devtools:
    1. ./dev.sh up       (in terminal 1)
    2. ./dev.sh run      (in terminal 2 - with automatic hot-reload)

  Development Workflow 2 — Containerized server with hot-reload:
    1. ./dev.sh build && ./dev.sh watch
    2. Edit code
    3. ./dev.sh build  (server restarts automatically)

  Development Workflow 3 — Full stack without hot-reload:
    1. ./dev.sh full

NOTES:
  - All commands pass additional options to docker compose
  - Use ./dev.sh down to clean up when switching workflows
  - The 'watch' command requires Docker Compose v2.22.0+
  - Build artifacts are cached; use 'mvn clean' if needed

For more information, see the README.md file.
EOF
}

case "${1:-}" in
  up)
    # Start infrastructure only by scaling server and portal to 0
    $COMPOSE_DEV up --scale server=0 --scale portal=0 "${@:2}"
    ;;
  run)
    echo "Starting fc-server with Spring Boot devtools (dev profile)..."
    echo "Hot-reload is enabled - changes will be picked up automatically"
    echo "Press Ctrl+C to stop"
    echo ""
    (cd .. && mvn spring-boot:run -pl fc-service-server -Dspring-boot.run.profiles=dev "${@:2}")
    ;;
  watch)
    $COMPOSE_DEV watch "${@:2}"
    ;;
  full)
    $COMPOSE_FULL up "${@:2}"
    ;;
  down)
    $COMPOSE_FULL down "${@:2}"
    ;;
  build)
    # `mvn package` covers both scenarios (bare-metal with spring-boot dev-tools and containerized).
    # For local mode with devtools, `mvn compile` would be enough here.
    (cd .. && mvn package -pl fc-service-server -am -DskipTests -Dcheckstyle.skip "${@:2}")
    ;;
  test)
    ./test-hot-reload.sh
    ;;
  logs)
    $COMPOSE_DEV logs -f server "${@:2}"
    ;;
  ps)
    $COMPOSE_DEV ps "${@:2}"
    ;;
  help|--help|-h)
    usage
    exit 0
    ;;
  *)
    echo "Error: Unknown command '${1:-}'"
    echo ""
    usage
    exit 1
    ;;
esac