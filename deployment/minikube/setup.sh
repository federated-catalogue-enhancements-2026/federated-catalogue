#!/usr/bin/env bash
# Bootstrap a Federated Catalogue deployment on Minikube.
# Usage: ./setup.sh
set -euo pipefail

NAMESPACE="federated-catalogue"
RELEASE="fc"
HELM_CHART_DIR="$(cd "$(dirname "$0")/../helm/fc-service" && pwd)"
VALUES_FILE="$(cd "$(dirname "$0")" && pwd)/values-minikube.yaml"
PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

# Minikube resource settings — override via environment variables:
#   MINIKUBE_CPUS=2 MINIKUBE_MEMORY=4096 ./setup.sh
MINIKUBE_CPUS="${MINIKUBE_CPUS:-2}"
MINIKUBE_MEMORY="${MINIKUBE_MEMORY:-}"

# Custom CA certificate (e.g. ZScaler root CA for corporate proxies).
# Point this at a PEM file and the script will install it into Minikube's
# trust store.  Requires a fresh cluster (minikube delete first).
#   CUSTOM_CA_CERT=~/certs/ZscalerRootCA.pem ./setup.sh
CUSTOM_CA_CERT="${CUSTOM_CA_CERT:-}"

# ---------------------------------------------------------------------------
# 1. Check prerequisites
# ---------------------------------------------------------------------------
echo "==> Checking prerequisites..."
for cmd in minikube helm kubectl podman; do
  if ! command -v "$cmd" &>/dev/null; then
    echo "ERROR: '$cmd' is not installed. Please install it and try again."
    exit 1
  fi
done
echo "    All prerequisites found."

# ---------------------------------------------------------------------------
# 2. Install custom CA certificate (corporate proxy / ZScaler)
# ---------------------------------------------------------------------------
if [[ -n "${CUSTOM_CA_CERT}" ]]; then
  if [[ ! -f "${CUSTOM_CA_CERT}" ]]; then
    echo "ERROR: CUSTOM_CA_CERT points to '${CUSTOM_CA_CERT}' but the file does not exist."
    exit 1
  fi
  MINIKUBE_CERTS_DIR="${MINIKUBE_HOME:-${HOME}/.minikube}/certs"
  mkdir -p "${MINIKUBE_CERTS_DIR}"
  cp "${CUSTOM_CA_CERT}" "${MINIKUBE_CERTS_DIR}/"
  echo "==> Installed CA certificate into ${MINIKUBE_CERTS_DIR}/$(basename "${CUSTOM_CA_CERT}")"
  echo "    NOTE: Minikube reads certs only at cluster creation time."
  echo "    If the cluster already exists, run 'minikube delete' first, then re-run this script."
fi

# ---------------------------------------------------------------------------
# 3. Start Minikube (if not already running)
# ---------------------------------------------------------------------------
if minikube status --format='{{.Host}}' 2>/dev/null | grep -q "Running"; then
  echo "==> Minikube is already running."
else
  MEMORY_FLAG=""
  if [[ -n "${MINIKUBE_MEMORY}" ]]; then
    MEMORY_FLAG="--memory=${MINIKUBE_MEMORY}"
  fi
  echo "==> Starting Minikube with Podman driver (cpus=${MINIKUBE_CPUS}, memory=${MINIKUBE_MEMORY:-podman default})..."
  minikube start --driver=podman --cpus="${MINIKUBE_CPUS}" ${MEMORY_FLAG}
fi

# ---------------------------------------------------------------------------
# 4. Enable the ingress addon
# ---------------------------------------------------------------------------
echo "==> Enabling ingress addon..."
# The built-in addon verification may time out with the Podman driver while
# images are still being pulled.  We ignore that error and do our own wait
# in the next step with a longer timeout.
minikube addons enable ingress || true

# ---------------------------------------------------------------------------
# 5. Wait for the ingress controller to be ready
# ---------------------------------------------------------------------------
echo "==> Waiting for ingress controller to be ready (up to 5 min)..."
kubectl wait --namespace ingress-nginx \
  --for=condition=Available deployment/ingress-nginx-controller \
  --timeout=300s

# ---------------------------------------------------------------------------
# 6. Get the ingress controller ClusterIP
# ---------------------------------------------------------------------------
echo "==> Discovering ingress controller ClusterIP..."
INGRESS_IP=$(kubectl get svc ingress-nginx-controller \
  -n ingress-nginx \
  -o jsonpath='{.spec.clusterIP}')
echo "    Ingress ClusterIP: ${INGRESS_IP}"

# ---------------------------------------------------------------------------
# 7. Build container images inside Minikube
# ---------------------------------------------------------------------------
echo "==> Building fc-service-server image inside Minikube..."
minikube image build --target fc-service-server -t fc-service-server:latest "${PROJECT_ROOT}"

echo "==> Building fc-demo-portal image inside Minikube..."
minikube image build --target fc-demo-portal -t fc-demo-portal:latest "${PROJECT_ROOT}"

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
