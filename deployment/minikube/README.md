# Minikube Deployment

Run the full Federated Catalogue stack locally on Minikube.

## Prerequisites

- [Minikube](https://minikube.sigs.k8s.io/docs/start/)
- [Helm](https://helm.sh/docs/intro/install/) v3+
- [kubectl](https://kubernetes.io/docs/tasks/tools/)
- [Podman](https://podman.io/docs/installation)

The setup script builds the Java services directly inside Minikube using `minikube image build`, so a local JDK/Maven install is **not** required.

## Corporate Proxy / ZScaler

If you are behind a corporate proxy that performs TLS inspection (e.g. ZScaler),
the Minikube container will fail to connect to image registries because it does
not trust the proxy's CA certificate. You will see errors like:

```
Failing to connect to https://registry.k8s.io/ from inside the minikube container
```

To fix this, pass the proxy root CA certificate (PEM format) when running the
setup script. **The cluster must be created fresh** for the certificate to be
picked up:

```bash
# Delete any existing cluster first
minikube delete

# Then run setup with the CA cert
CUSTOM_CA_CERT=/path/to/ZscalerRootCA.pem ./setup.sh
```

To export the ZScaler root CA on macOS:
1. Open **Keychain Access**
2. Search for "Zscaler Root CA"
3. Right-click the certificate and choose **Export**
4. Save as `.pem` format

The script copies the certificate into `~/.minikube/certs/`, where Minikube
picks it up at cluster creation time and adds it to the VM's trust store.

## Quick Start

```bash
cd deployment/minikube
./setup.sh
```

The script will:
1. Start Minikube with the Podman driver (2 CPUs, Podman machine's available memory)
2. Enable the NGINX ingress addon
3. Build `fc-service-server` and `fc-demo-portal` container images inside Minikube
4. Fetch Helm chart dependencies (PostgreSQL, Neo4j, Keycloak)
5. Deploy the Helm release into the `federated-catalogue` namespace
6. Print instructions for `/etc/hosts` configuration

You can override CPU and memory settings via environment variables:
```bash
MINIKUBE_CPUS=4 MINIKUBE_MEMORY=8192 ./setup.sh
```

When `MINIKUBE_MEMORY` is not set, Minikube uses whatever the Podman machine
provides, avoiding the "not enough memory" error.

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

**Image build fails**

Verify the images are present inside Minikube:
```bash
minikube image ls | grep fc-service-server
```

If the build itself fails, try building manually:
```bash
minikube image build --target fc-service-server -t fc-service-server:latest .
```

**Podman machine has insufficient memory**

If you explicitly set `MINIKUBE_MEMORY` higher than what the Podman machine has,
you will get `MK_USAGE: Podman has only X MB memory but Y MB were specified`.
Either omit `MINIKUBE_MEMORY` (uses the machine default) or increase the Podman
machine's memory:
```bash
podman machine stop
podman machine set --memory 8192
podman machine start
```

**Helm dependency errors**

```bash
cd deployment/helm/fc-service
helm dependency build
```
