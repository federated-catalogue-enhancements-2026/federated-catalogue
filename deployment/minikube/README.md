# Minikube Deployment

Run the full Federated Catalogue stack locally on Minikube.

## Prerequisites

- [Minikube](https://minikube.sigs.k8s.io/docs/start/)
- [Helm](https://helm.sh/docs/intro/install/) v3+
- [kubectl](https://kubernetes.io/docs/tasks/tools/)
- [Docker](https://docs.docker.com/get-docker/)

The setup script builds the Java services inside Minikube's Docker daemon, so a local JDK/Maven install is **not** required.

## Quick Start

```bash
cd deployment/minikube
./setup.sh
```

The script will:
1. Start Minikube (4 CPUs, 8 GB RAM)
2. Enable the NGINX ingress addon
3. Build `fc-service-server` and `fc-demo-portal` Docker images inside Minikube
4. Fetch Helm chart dependencies (PostgreSQL, Neo4j, Keycloak)
5. Deploy the Helm release into the `federated-catalogue` namespace
6. Print instructions for `/etc/hosts` configuration

## Host DNS Configuration

After setup completes, add an entry to your `/etc/hosts` so that
`fc-server` and `key-server` resolve to the Minikube VM:

```bash
# Replace <MINIKUBE_IP> with the value printed by setup.sh
echo '<MINIKUBE_IP>  fc-server key-server' | sudo tee -a /etc/hosts
```

You can obtain the IP at any time with `minikube ip`.

## Accessing Services

| Service | URL | Notes |
|---|---|---|
| FC Service health | http://fc-server/actuator/health | Should return `{"status":"UP"}` |
| Keycloak admin | http://key-server/admin | Credentials: `admin` / `admin` |
| Demo Portal | http://localhost:8088 | Requires port-forward (see below) |

### Demo Portal port-forward

```bash
kubectl port-forward svc/fc-demo-portal 8088:8088 -n federated-catalogue
```

Then open http://localhost:8088 in your browser.

## Keycloak Post-Setup

After the initial deployment, the `gaia-x` realm is imported automatically.
To use the FC Service API you need to:

1. Log into Keycloak at http://key-server/admin (`admin` / `admin`).
2. Switch to the **gaia-x** realm.
3. Go to **Clients > federated-catalogue** > **Credentials** and regenerate the client secret.
4. Update the `fc-keycloak-client-secret` Kubernetes secret:
   ```bash
   kubectl create secret generic fc-keycloak-client-secret \
     --from-literal=keycloak_client_secret=<NEW_SECRET> \
     -n federated-catalogue --dry-run=client -o yaml | kubectl apply -f -
   ```
5. Restart the FC Service pod:
   ```bash
   kubectl rollout restart deployment/fc-service -n federated-catalogue
   ```
6. Create a user in the **gaia-x** realm and assign the appropriate roles
   (`Ro-MU-CA`, `Ro-MU-A`, `Ro-SD-A`, `Ro-PA-A` for full access).

## Teardown

```bash
# Remove the Helm release and namespace, keep Minikube running
./teardown.sh

# Remove everything including the Minikube cluster
./teardown.sh --delete-minikube
```

## Troubleshooting

**Pods stuck in `Pending` or `CrashLoopBackOff`**
```bash
kubectl describe pod <POD_NAME> -n federated-catalogue
kubectl logs <POD_NAME> -n federated-catalogue
```

**Ingress not responding**
```bash
# Verify the ingress controller is running
kubectl get pods -n ingress-nginx
# Check ingress resources
kubectl get ingress -n federated-catalogue
```

**Neo4j plugins fail to download**

The Neo4j chart uses an init container to download APOC, GDS, and n10s plugins.
If you are behind a corporate proxy, ensure the proxy env vars are set before
running `setup.sh`.

**Docker build fails**

Make sure you are using Minikube's Docker daemon:
```bash
eval $(minikube docker-env)
docker images | grep fc-service-server
```

**Helm dependency errors**

```bash
cd deployment/helm/fc-service
helm dependency build
```
