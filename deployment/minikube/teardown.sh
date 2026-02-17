#!/usr/bin/env bash
# Tear down the Federated Catalogue Minikube deployment.
# Usage: ./teardown.sh [--delete-minikube]
set -euo pipefail

NAMESPACE="federated-catalogue"
RELEASE="fc"

echo "==> Uninstalling Helm release '${RELEASE}'..."
helm uninstall "${RELEASE}" --namespace "${NAMESPACE}" 2>/dev/null || true

echo "==> Deleting namespace '${NAMESPACE}'..."
kubectl delete namespace "${NAMESPACE}" --ignore-not-found

if [[ "${1:-}" == "--delete-minikube" ]]; then
  echo "==> Stopping and deleting Minikube cluster..."
  minikube stop
  minikube delete
else
  echo ""
  echo "Minikube cluster is still running."
  echo "To stop it:   minikube stop"
  echo "To delete it: minikube delete"
  echo "Or re-run:    ./teardown.sh --delete-minikube"
fi

echo "==> Done."
