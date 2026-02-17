#!/usr/bin/env bash
# Bootstrap a Federated Catalogue deployment on Minikube.
# Usage: ./setup.sh
set -euo pipefail

NAMESPACE="federated-catalogue"
RELEASE="fc"
HELM_CHART_DIR="$(cd "$(dirname "$0")/../helm/fc-service" && pwd)"
VALUES_FILE="$(cd "$(dirname "$0")" && pwd)/values-minikube.yaml"
PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

# ---------------------------------------------------------------------------
# 1. Check prerequisites
# ---------------------------------------------------------------------------
echo "==> Checking prerequisites..."
for cmd in minikube helm kubectl docker; do
  if ! command -v "$cmd" &>/dev/null; then
    echo "ERROR: '$cmd' is not installed. Please install it and try again."
    exit 1
  fi
done
echo "    All prerequisites found."

# ---------------------------------------------------------------------------
# 2. Start Minikube (if not already running)
# ---------------------------------------------------------------------------
if minikube status --format='{{.Host}}' 2>/dev/null | grep -q "Running"; then
  echo "==> Minikube is already running."
else
  echo "==> Starting Minikube..."
  minikube start --cpus=4 --memory=8192
fi

# ---------------------------------------------------------------------------
# 3. Enable the ingress addon
# ---------------------------------------------------------------------------
echo "==> Enabling ingress addon..."
minikube addons enable ingress

# ---------------------------------------------------------------------------
# 4. Wait for the ingress controller to be ready
# ---------------------------------------------------------------------------
echo "==> Waiting for ingress controller to be ready..."
kubectl rollout status deployment/ingress-nginx-controller \
  -n ingress-nginx --timeout=120s

# ---------------------------------------------------------------------------
# 5. Get the ingress controller ClusterIP
# ---------------------------------------------------------------------------
echo "==> Discovering ingress controller ClusterIP..."
INGRESS_IP=$(kubectl get svc ingress-nginx-controller \
  -n ingress-nginx \
  -o jsonpath='{.spec.clusterIP}')
echo "    Ingress ClusterIP: ${INGRESS_IP}"

# ---------------------------------------------------------------------------
# 6. Point shell at Minikube's Docker daemon
# ---------------------------------------------------------------------------
echo "==> Configuring Docker to use Minikube's daemon..."
eval "$(minikube docker-env)"

# ---------------------------------------------------------------------------
# 7. Build Docker images
# ---------------------------------------------------------------------------
echo "==> Building fc-service-server image..."
docker build --target fc-service-server -t fc-service-server:latest "${PROJECT_ROOT}"

echo "==> Building fc-demo-portal image..."
docker build --target fc-demo-portal -t fc-demo-portal:latest "${PROJECT_ROOT}"

# ---------------------------------------------------------------------------
# 8. Fetch Helm dependencies
# ---------------------------------------------------------------------------
echo "==> Building Helm dependencies..."
helm dependency build "${HELM_CHART_DIR}"

# ---------------------------------------------------------------------------
# 9. Install / upgrade the Helm release
# ---------------------------------------------------------------------------
echo "==> Creating namespace ${NAMESPACE} (if needed)..."
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

echo "==> Installing Helm release '${RELEASE}' in namespace '${NAMESPACE}'..."
helm upgrade --install "${RELEASE}" "${HELM_CHART_DIR}" \
  --namespace "${NAMESPACE}" \
  -f "${VALUES_FILE}" \
  --set "hostAliases[0].ip=${INGRESS_IP}" \
  --set "hostAliases[0].hostnames[0]=key-server" \
  --timeout 10m \
  --wait=false

# ---------------------------------------------------------------------------
# 10. Wait for pods
# ---------------------------------------------------------------------------
echo "==> Waiting for pods to become ready (this may take several minutes)..."
echo "    Waiting for PostgreSQL..."
kubectl rollout status statefulset/"${RELEASE}"-fc-postgres \
  -n "${NAMESPACE}" --timeout=300s 2>/dev/null || true

echo "    Waiting for Keycloak..."
kubectl rollout status statefulset/"${RELEASE}"-fc-keycloak \
  -n "${NAMESPACE}" --timeout=300s 2>/dev/null || true

echo "    Waiting for Neo4j..."
kubectl rollout status statefulset/fc-neo4j \
  -n "${NAMESPACE}" --timeout=300s 2>/dev/null || true

echo "    Waiting for FC Service..."
kubectl rollout status deployment/fc-service \
  -n "${NAMESPACE}" --timeout=300s 2>/dev/null || true

# ---------------------------------------------------------------------------
# 11. Print access instructions
# ---------------------------------------------------------------------------
MINIKUBE_IP=$(minikube ip)

echo ""
echo "============================================================"
echo "  Federated Catalogue deployed on Minikube"
echo "============================================================"
echo ""
echo "Add the following to your /etc/hosts (requires sudo):"
echo ""
echo "  ${MINIKUBE_IP}  fc-server key-server"
echo ""
echo "Then access:"
echo "  FC Service health:  http://fc-server/actuator/health"
echo "  Keycloak admin:     http://key-server/admin  (admin / admin)"
echo "  Demo Portal:        kubectl port-forward svc/fc-demo-portal 8088:8088 -n ${NAMESPACE}"
echo "                      then open http://localhost:8088"
echo ""
echo "Current pod status:"
kubectl get pods -n "${NAMESPACE}"
echo ""
echo "To tear down: ./teardown.sh"
echo "============================================================"
