# Kubernetes Deployment Roadmap for Federated Catalogue

## Introduction

This document provides a comprehensive guide for deploying the **Eclipse XFSC Federated Catalogue Service** to a Kubernetes cluster.

---

## Architecture Components

The application consists of **4 main runtime components**:

1. **FC Service (fc-service-server)** - The main Spring Boot application
   - Exposes REST API on port 8081
   - Handles asset upload, validation, and querying
   - Requires: PostgreSQL, Neo4j, Keycloak

2. **PostgreSQL 14** - Relational database
   - Stores application data (sessions, audit logs, validator cache)
   - Also serves as the backend database for Keycloak

3. **Neo4j 5.18** - Graph database
   - Stores RDF triples with semantic relationships
   - Requires plugins: APOC, neosemantics (n10s), Graph Data Science

4. **Keycloak 26.0** - Identity and Access Management
   - Provides OAuth2/OIDC authentication
   - Manages users, roles, and client credentials
   - Uses PostgreSQL as its backend

**Optional Components:**
- **NATS** - For event streaming/publishing (if enabled)
- **FC Demo Portal** - Web UI demonstration application

---

## Prerequisites

### 1. Connect to Kubernetes Cluster with kubectl

Configuration file provided by ECO. Copy this file and set the Kubernetes context to this cluster with:

```bash
export KUBECONFIG=config.yaml
```

Now, the cluster should be configured. Test with:

```bash
kubectl config get-contexts  
CURRENT   NAME                                     CLUSTER                    AUTHINFO        NAMESPACE
*         cluster-admin@CatalogueEnhancementTeam   CatalogueEnhancementTeam   cluster-admin   
```

**Verify cluster:**
```bash
# Check cluster access
kubectl get nodes

# Check available storage classes
kubectl get storageclass
```

### 2. Helm Installation

**Install Helm 3:**
```bash
# macOS
brew install helm

# Verify installation
helm version
```

### 3. Ingress Controller

The FC service uses **nginx-ingress** for external access.

**Check if nginx-ingress is installed:**
```bash
kubectl get pods -n ingress-nginx
```

**If not installed:**
```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
helm install nginx-ingress ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --create-namespace
```

### 4. Storage Provisioner

Your cluster needs to provide storage for:
- PostgreSQL (database data)
- Neo4j (graph data)
- FC Service (file uploads - optional)

**Check for dynamic provisioning:**
```bash
kubectl get storageclass
```

Look for a storage class marked `(default)`. If none exists, you'll need to configure one or create PersistentVolumes manually.

### 5. Resource Requirements

**Recommended minimum cluster capacity:**
- **CPU**: 4-8 cores
- **Memory**: 8-16 GB RAM
- **Storage**: 20-50 GB (depends on data volume)

**Per-component estimates:**
| Component | CPU | Memory | Storage |
|-----------|-----|--------|---------|
| FC Service | 500m-2 | 1-4 GB | 2 GB (files) |
| PostgreSQL | 250m-1 | 512 MB-2 GB | 5-10 GB |
| Neo4j | 500m-2 | 2-8 GB | 5-20 GB |
| Keycloak | 250m-1 | 512 MB-2 GB | - |

### 6. DNS/Networking

DNS configuration is required for **two purposes**:

1. **External Access**: Clients (developers, CI/CD pipelines, etc.) need to reach the services via Ingress:
   - `fc-server.X.X.X.X.sslip.io` → Main FC service API
   - `key-server.X.X.X.X.sslip.io` → Keycloak authentication

2. **JWT Issuer Validation**: The FC service validates OAuth tokens by checking the issuer claim. Keycloak issues tokens with `iss: "http://key-server.X.X.X.X.sslip.io/realms/gaia-x"`, so the FC service pods must be able to resolve this hostname to validate tokens correctly.

**Without proper DNS**, you'll see errors like:
- `401 Unauthorized` when calling the FC API
- `UnknownHostException: key-server` in FC service logs
- `Invalid issuer` in JWT validation errors

---

#### Using sslip.io for Zero-Configuration DNS

**This deployment uses sslip.io**, a free wildcard DNS service that automatically resolves hostnames containing IP addresses.

**How sslip.io works:**
- You configure hostnames with embedded IP addresses: `fc-server.192.168.1.100.sslip.io`
- When anyone tries to resolve this hostname, sslip.io returns the embedded IP: `192.168.1.100`
- Works from anywhere: developer laptops, CI/CD pipelines, inside cluster pods
- **Zero local configuration required** - no `/etc/hosts` editing needed!

**Step 1: Get Your Ingress Controller IP**

```bash
# Find the ingress controller service
kubectl get svc -n ingress-nginx

# Example output:
# NAME                                    TYPE           CLUSTER-IP      EXTERNAL-IP     PORT(S)
# nginx-ingress-ingress-nginx-controller  LoadBalancer   10.43.242.156   192.168.1.100   80:30080/TCP,443:30443/TCP

# Use the EXTERNAL-IP (192.168.1.100 in this example)
# If TYPE is NodePort instead of LoadBalancer, use any node's IP address
```

**Step 2: Update values.yaml with Your Ingress IP**

The default `values.yaml` includes placeholder hostnames with `INGRESS_IP`:

```yaml
ingress:
  hosts:
    - host: fc-server.INGRESS_IP.sslip.io

keycloak:
  ingress:
    hostname: key-server.INGRESS_IP.sslip.io

env:
  - name: SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI
    value: http://key-server.INGRESS_IP.sslip.io/realms/gaia-x
```

**Replace `INGRESS_IP` with your actual IP** (e.g., `192.168.1.100`):


**Step 3: Verify DNS Resolution (Optional)**

Test that sslip.io is working before deploying:

```bash
# From your local machine
nslookup fc-server.192.168.1.100.sslip.io
# Should return: 192.168.1.100

# Or use curl to test
curl -I http://192.168.1.100
```

**Step 4: Deploy (Covered in Later Steps)**

Once deployed, all developers can access the services using these URLs:
- FC Service API: `http://fc-server.192.168.1.100.sslip.io`
- Keycloak Admin: `http://key-server.192.168.1.100.sslip.io`
- API Docs: `http://fc-server.192.168.1.100.sslip.io/api/docs`

**Advantages:**
- ✅ **Zero local configuration** - works immediately for all developers
- ✅ **Works from anywhere** - laptops, CI/CD, inside pods
- ✅ **Single source of truth** - IP only in values.yaml
- ✅ **Easy to update** - change values file, redeploy
- ✅ **No cluster-admin required** - no CoreDNS modifications needed

**Disadvantages:**
- ⚠️ **"Ugly" URLs** - Contains IP address in hostname
- ⚠️ **External dependency** - Relies on sslip.io service (highly available, but external)
- ⚠️ **Not for production** - Use real DNS for production environments

**For Production:**
Register a proper domain and create DNS A records pointing to your ingress IP. Then use hostnames like `fc-server.staging.yourcompany.com`.

### 7. Container Registry Access

The current Helm chart references:
```
node-654e3bca7fbeeed18f81d7c7.ps-xaas.io/catalogue/fc-service-server:latest
```

**You'll need to:**
- Either have access to this registry
- Or build and push the image to your own registry

**Building the image:**
```bash
# Build the FC service image
docker build --target fc-service-server -t your-registry.io/fc-service:2.1.0 .

# Push to your registry
docker push your-registry.io/fc-service:2.1.0
```

---

## Current State Analysis

### What's in `deployment/helm/fc-service`

The existing Helm chart (version 0.4.0) provides a complete deployment solution with the following structure:

```
deployment/helm/fc-service/
├── Chart.yaml              # Chart metadata and dependencies
├── values.yaml             # Default configuration values
├── gaia-x-realm.json       # Keycloak realm configuration
└── templates/
    ├── _helpers.tpl        # Template helpers
    ├── deployment.yaml     # FC service deployment
    ├── service.yaml        # K8s service definition
    ├── ingress.yaml        # HTTP routing rules
    ├── hpa.yaml            # Horizontal Pod Autoscaler
    ├── postgres-pv-pvc.yaml # PostgreSQL storage
    ├── keycloak-config-map.yaml # Keycloak realm import
    ├── keycloak-client-secret.yaml # OAuth client secret
    ├── neo4j-secret.yaml   # Neo4j credentials
    └── serviceaccount.yaml # Pod service account
```

### Dependencies (from Chart.yaml)

The chart declares three sub-chart dependencies:

1. **PostgreSQL 12.4.3** (Bitnami)
   - Provides relational database
   - Used by both FC service and Keycloak

2. **Neo4j 5.18.1** (Official Neo4j chart)
   - Provides graph database with RDF support
   - Includes init containers to download plugins

3. **Keycloak 26.0** (Bitnami)
   - Provides OAuth2/OIDC authentication
   - Auto-imports realm configuration

### Identified Issues and Outdated Elements

#### 🔴 **CRITICAL**: Image Repository Configuration
```yaml
image:
  repository: node-654e3bca7fbeeed18f81d7c7.ps-xaas.io/catalogue/fc-service-server
  tag: "latest"
```
**Issue**: This appears to be a private/test registry that may not be accessible.
**Action Required**: Update to your own container registry.

#### 🟡 **WARNING**: Hard-coded Secrets in Values
```yaml
postgresql:
  auth:
    postgresPassword: postgres  # Hard-coded password!

neo4j:
  neo4j:
    password: "neo12345"  # Hard-coded password!

keycloak:
  auth:
    adminPassword: admin  # Hard-coded password!
```
**Issue**: Production deployments should NOT have passwords in version control.
**Action Required**: Use Kubernetes Secrets or external secret management.

#### 🟡 **WARNING**: Hard-coded IP in HostAliases
```yaml
hostAliases:
  - ip: "10.97.11.112"  # Specific to one environment!
    hostnames:
      - key-server
```
**Issue**: This IP is specific to the original deployment environment.
**Action Required**: Replace with proper service DNS or ingress configuration.

#### 🟡 **WARNING**: Missing Resource Limits
```yaml
resources: {}  # No limits defined
```
**Issue**: Pods without resource limits can consume all cluster resources.
**Action Required**: Define appropriate CPU/memory requests and limits.

#### 🟠 **INFO**: Disabled Autoscaling
```yaml
autoscaling:
  enabled: false
```
**Status**: Autoscaling is disabled by default, which is reasonable for initial deployment.
**Consider**: Enabling for production workloads.

#### 🟠 **INFO**: Storage Not Enabled for FC Service
```yaml
storage:
  enabled: false
  size: 2Gi
```
**Status**: File uploads will use emptyDir (lost on pod restart).
**Consider**: Enable persistent storage for production.

#### 🟢 **GOOD**: Health Checks Configured
```yaml
probes:
  path: /actuator/health
  initialDelaySeconds: 60
  periodSeconds: 30
```
**Status**: Proper health checks using Spring Boot Actuator.

#### 🟢 **GOOD**: Dependency Versions Aligned
- Neo4j 5.18.1 matches application requirements
- Keycloak 26.0 matches tested version
- Plugin versions (APOC 5.18.0, n10s 5.18.0) are compatible

### Compatibility with Current Application (v2.1.0)

**Overall Assessment**: The Helm chart is **mostly compatible** but requires updates for production use.

| Aspect | Status | Notes |
|--------|--------|-------|
| Application version | ✅ Matches | Chart appVersion: 2.1.0 |
| Dependencies | ✅ Compatible | PostgreSQL, Neo4j, Keycloak versions aligned |
| Configuration | ⚠️ Needs work | Hard-coded values, missing customization |
| Security | ❌ Not production-ready | Hard-coded secrets, no image pull secrets |
| Networking | ⚠️ Needs work | Hard-coded IPs, DNS dependencies |
| Storage | ⚠️ Incomplete | Missing persistent storage for uploads |
| Monitoring | ✅ Basic setup | Health checks configured |

---

## Deployment Architecture

### Network Topology

```
                         Internet
                            │
                            ▼
                    ┌──────────────┐
                    │   Ingress    │ (nginx)
                    │  Controller  │
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              │                         │
              ▼                         ▼
    ┌──────────────────┐      ┌─────────────────┐
    │  fc-server       │      │  key-server     │
    │  (host name)     │      │  (host name)    │
    └────────┬─────────┘      └────────┬────────┘
             │                         │
             ▼                         ▼
    ┌──────────────────┐      ┌─────────────────┐
    │  fc-service      │      │  fc-keycloak    │
    │  (K8s Service)   │      │  (K8s Service)  │
    └────────┬─────────┘      └────────┬────────┘
             │                         │
             ▼                         │
    ┌──────────────────┐              │
    │  fc-service      │◄─────────────┘
    │  (Pods)          │   (OAuth validation)
    └────────┬─────────┘
             │
             ├──────────────┬──────────────┐
             │              │              │
             ▼              ▼              ▼
    ┌─────────────┐  ┌──────────┐  ┌──────────┐
    │  fc-postgres│  │ fc-neo4j │  │fc-keycloak│
    │  (Service)  │  │ (Service)│  │ (Service) │
    └──────┬──────┘  └────┬─────┘  └────┬─────┘
           │              │             │
           ▼              ▼             ▼
       [PVC]          [PVC]         [PVC]
    (Database)     (Graph Data)  (Keycloak DB)
```

### Service Communication

**Internal Communication** (within cluster):
- FC Service → PostgreSQL: `jdbc:postgresql://fc-postgres:5432/postgres`
- FC Service → Neo4j: `bolt://fc-neo4j:7687`
- FC Service → Keycloak: `http://fc-keycloak-headless.fed-cat.svc.cluster.local:8080`

**External Communication** (via Ingress):
- Clients → FC Service: `http://fc-server/` → `fc-service:8081`
- Clients → Keycloak: `http://key-server/` → `fc-keycloak:8080`

### Data Flow

1. **Asset Upload Request**:
   - Client sends POST to `http://fc-server/assets`
   - Nginx Ingress routes to `fc-service:8081`
   - FC Service validates JWT token with Keycloak
   - Asset is validated (schema, signatures, semantics)
   - Metadata stored in PostgreSQL
   - RDF triples stored in Neo4j
   - Files stored in PersistentVolume (if enabled)

2. **Authentication Flow**:
   - Client requests token from `http://key-server/realms/gaia-x/protocol/openid-connect/token`
   - Keycloak validates credentials against PostgreSQL
   - Returns JWT token
   - Client includes token in requests to FC Service

---

## Step-by-Step Deployment Guide

### Step 1: Prepare Your Environment

#### 1.1 Create a Kubernetes Namespace
```bash
# Create a dedicated namespace for the FC service
kubectl create namespace federated-catalogue

# Set as default for subsequent commands (optional)
kubectl config set-context --current --namespace=federated-catalogue
```

**What this does**: Namespaces provide isolation between different applications in the same cluster.

#### 1.2 Add Helm Repositories
```bash
# Add Bitnami repository (for PostgreSQL and Keycloak)
helm repo add bitnami https://charts.bitnami.com/bitnami

# Add Neo4j repository
helm repo add neo4j https://neo4j.github.io/helm-charts/

# Update repositories
helm repo update
```

**What this does**: Helm needs to know where to download the dependency charts from.

### Step 2: Build and Push Your Container Image

The FC service needs to be built as a Docker image and pushed to a container registry accessible from your cluster.

#### 2.1 Build the Image
```bash
# From the project root directory
docker build --target fc-service-server -t your-registry.io/fc-service:2.1.0 .
```

**What this does**: Compiles the Java application and packages it into a Docker image using the multi-stage Dockerfile.

#### 2.2 Push to Registry
```bash
# Login to your container registry
docker login your-registry.io

# Push the image
docker push your-registry.io/fc-service:2.1.0
```

**Alternative**: Use a CI/CD pipeline (GitHub Actions, GitLab CI, Jenkins) to automate builds.

### Step 3: Customize Helm Values

Create a custom values file for your environment. This keeps your configuration separate from the default chart.

#### 3.1 Create Custom Values File
```bash
# Create a custom values file
cat > deployment/helm/fc-service/values-production.yaml << 'EOF'
# Custom values for production deployment

# Update image to your registry
image:
  repository: your-registry.io/fc-service
  pullPolicy: IfNotPresent
  tag: "2.1.0"

# If your registry requires authentication
imagePullSecrets:
  - name: registry-credentials

# Configure ingress for your domain
ingress:
  enabled: true
  className: nginx
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod  # If using cert-manager
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
  hosts:
    - host: fc-service.yourdomain.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: fc-service-tls
      hosts:
        - fc-service.yourdomain.com

# Define resource limits
resources:
  requests:
    cpu: 500m
    memory: 1Gi
  limits:
    cpu: 2000m
    memory: 4Gi

# Enable persistent storage for file uploads
storage:
  enabled: true
  size: 10Gi
  # storageClassName: your-storage-class  # Uncomment to specify

# Remove hard-coded host aliases
# Use proper DNS instead
hostAliases: []

# Environment variables - use secrets for sensitive data
env:
  - name: DATASTORE_FILE_PATH
    value: /var/lib/fc-service/filestore
  - name: GRAPHSTORE_IMPL
    value: neo4j
  - name: GRAPHSTORE_URI
    value: bolt://fc-neo4j:7687
  - name: GRAPHSTORE_QUERY_TIMEOUT_IN_SECONDS
    value: "5"
  - name: GRAPHSTORE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: fc-neo4j-secret
        key: password
  - name: KEYCLOAK_AUTH_SERVER_URL
    value: http://fc-keycloak-headless:8080
  - name: KEYCLOAK_CREDENTIALS_SECRET
    valueFrom:
      secretKeyRef:
        name: fc-keycloak-client-secret
        key: keycloak_client_secret
  - name: SPRING_DATASOURCE_URL
    value: jdbc:postgresql://fc-postgres:5432/postgres
  - name: SPRING_DATASOURCE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: fc-postgres
        key: postgres-password
  - name: SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI
    value: http://keycloak.yourdomain.com/realms/gaia-x

# PostgreSQL configuration
postgresql:
  fullnameOverride: fc-postgres
  auth:
    # Use strong passwords in production
    # Consider using existingSecret instead
    postgresPassword: CHANGE_ME_STRONG_PASSWORD
    username: keycloak
    password: CHANGE_ME_KEYCLOAK_PASSWORD
    database: keycloak
  primary:
    persistence:
      enabled: true
      size: 10Gi
    resources:
      requests:
        cpu: 250m
        memory: 512Mi
      limits:
        cpu: 1000m
        memory: 2Gi

# Keycloak configuration
keycloak:
  fullnameOverride: fc-keycloak
  auth:
    adminUser: admin
    adminPassword: CHANGE_ME_ADMIN_PASSWORD
  resources:
    requests:
      cpu: 250m
      memory: 512Mi
    limits:
      cpu: 1000m
      memory: 2Gi
  ingress:
    enabled: true
    ingressClassName: nginx
    hostname: keycloak.yourdomain.com
    tls: true

# Neo4j configuration
neo4j:
  fullnameOverride: fc-neo4j
  neo4j:
    password: "CHANGE_ME_NEO4J_PASSWORD"
  resources:
    requests:
      cpu: 500m
      memory: 2Gi
    limits:
      cpu: 2000m
      memory: 8Gi
  volumes:
    data:
      mode: defaultStorageClass
      defaultStorageClass:
        requests:
          storage: 20Gi
EOF
```

**Important**: Replace all `CHANGE_ME_*` placeholders with strong passwords!

#### 3.2 Create Image Pull Secret (if needed)
```bash
# If your container registry requires authentication
kubectl create secret docker-registry registry-credentials \
  --docker-server=your-registry.io \
  --docker-username=YOUR_USERNAME \
  --docker-password=YOUR_PASSWORD \
  --docker-email=YOUR_EMAIL \
  --namespace=federated-catalogue
```

### Step 4: Install Helm Chart Dependencies

Before installing the main chart, you need to download its dependencies.

```bash
# Navigate to the chart directory
cd deployment/helm/fc-service

# Update dependencies
helm dependency update
```

**What this does**: Downloads the PostgreSQL, Neo4j, and Keycloak charts as specified in Chart.yaml.

You should see a new directory `charts/` with the downloaded dependencies.

### Step 5: Validate the Chart

Before actually deploying, validate your configuration:

```bash
# Lint the chart for syntax errors
helm lint . -f values-production.yaml

# Dry-run to see what will be created
helm install fc-service . \
  --namespace federated-catalogue \
  --values values-production.yaml \
  --dry-run \
  --debug
```

**What this does**: Checks for errors without actually creating resources. Review the output carefully.

### Step 6: Install the Helm Chart

Now deploy the application:

```bash
# Install the chart
helm install fc-service . \
  --namespace federated-catalogue \
  --values values-production.yaml \
  --timeout 10m

# Watch the deployment
kubectl get pods -n federated-catalogue -w
```

**What this does**: Creates all Kubernetes resources and starts the pods.

**Expected behavior**:
1. PostgreSQL pods start first
2. Neo4j init containers download plugins (this takes time!)
3. Keycloak waits for PostgreSQL
4. FC service waits for all dependencies

**Installation takes 5-10 minutes** due to Neo4j plugin downloads.

### Step 7: Verify the Deployment

#### 7.1 Check Pod Status
```bash
# List all pods
kubectl get pods -n federated-catalogue

# Expected output (all Running):
# NAME                           READY   STATUS    RESTARTS   AGE
# fc-service-xxxxxx-yyyyy        1/1     Running   0          5m
# fc-postgres-0                  1/1     Running   0          5m
# fc-neo4j-0                     1/1     Running   0          5m
# fc-keycloak-xxxxxx-yyyyy       1/1     Running   0          5m
```

#### 7.2 Check Services
```bash
kubectl get svc -n federated-catalogue

# Expected services:
# fc-service           ClusterIP   10.x.x.x    <none>   8081/TCP
# fc-postgres          ClusterIP   10.x.x.x    <none>   5432/TCP
# fc-neo4j             ClusterIP   10.x.x.x    <none>   7474/TCP,7687/TCP
# fc-keycloak          ClusterIP   10.x.x.x    <none>   8080/TCP
```

#### 7.3 Check Ingress
```bash
kubectl get ingress -n federated-catalogue

# You should see ingress rules for fc-service and fc-keycloak
```

#### 7.4 Check Logs
```bash
# FC service logs
kubectl logs -n federated-catalogue -l app=fc-service --tail=100

# Look for successful startup:
# "Started CatalogueServerApplication in X seconds"

# Keycloak logs
kubectl logs -n federated-catalogue -l app.kubernetes.io/name=keycloak --tail=100

# Neo4j logs
kubectl logs -n federated-catalogue -l app=fc-neo4j-db --tail=100
```

#### 7.5 Test Health Endpoints
```bash
# Port-forward to test locally (if ingress not configured yet)
kubectl port-forward -n federated-catalogue svc/fc-service 8081:8081

# In another terminal:
curl http://localhost:8081/actuator/health

# Expected response:
# {"status":"UP"}
```

### Step 8: Verify DNS Configuration

**DNS is already configured** via sslip.io hostnames in your values.yaml. You don't need to configure anything locally!

**Verify the hostnames are accessible:**

```bash
# Get your ingress IP (should match what you put in values.yaml)
kubectl get svc -n ingress-nginx

# Test DNS resolution
nslookup fc-server.192.168.1.100.sslip.io
# Should return: 192.168.1.100

nslookup key-server.192.168.1.100.sslip.io
# Should return: 192.168.1.100
```

**No local configuration needed!** All developers on your team can access the services using the same URLs without any setup on their machines.

---

### Step 9: Test the Application

Replace `192.168.1.100` with your actual ingress IP in all commands below.

#### 9.1 Access Keycloak Admin Console
```bash
# Open in browser (replace with your ingress IP)
open http://key-server.192.168.1.100.sslip.io

# Login with:
# Username: admin
# Password: (the admin password you set in values.yaml)
```

Verify the `gaia-x` realm was imported successfully.

#### 9.2 Get an OAuth Token
```bash
# Get a token from Keycloak (replace with your ingress IP)
curl -X POST http://key-server.192.168.1.100.sslip.io/realms/gaia-x/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=federated-catalogue" \
  -d "client_secret=YOUR_CLIENT_SECRET" \
  -d "grant_type=client_credentials" \
  | jq -r '.access_token'
```

**Note:** The `client_secret` value should match what's configured in Keycloak. Check the `fc-keycloak-client-secret` Kubernetes secret or the Keycloak admin console.

#### 9.3 Test the FC API
```bash
# Save the token
TOKEN="<token from previous command>"

# Test API access (replace with your ingress IP)
curl -H "Authorization: Bearer $TOKEN" \
  http://fc-server.192.168.1.100.sslip.io/query

# Check health endpoint (no auth required)
curl http://fc-server.192.168.1.100.sslip.io/actuator/health

# Check API documentation
open http://fc-server.192.168.1.100.sslip.io/api/docs
```

---

## Configuration Management

### Environment-Specific Values

Maintain separate values files for different environments:

```
deployment/helm/fc-service/
├── values.yaml              # Default values (base)
├── values-dev.yaml          # Development overrides
├── values-staging.yaml      # Staging overrides
├── values-production.yaml   # Production overrides
```

**Deploy to different environments:**
```bash
# Development
helm upgrade --install fc-service . -f values-dev.yaml -n fc-dev

# Staging
helm upgrade --install fc-service . -f values-staging.yaml -n fc-staging

# Production
helm upgrade --install fc-service . -f values-production.yaml -n fc-production
```

### Using Kubernetes Secrets

**Never store secrets in Git!** Use Kubernetes Secrets or external secret managers.

#### Create Secrets Manually
```bash
# Database password
kubectl create secret generic fc-postgres \
  --from-literal=postgres-password='your-secure-password' \
  --from-literal=password='your-keycloak-db-password' \
  -n federated-catalogue

# Neo4j password
kubectl create secret generic fc-neo4j-secret \
  --from-literal=password='your-neo4j-password' \
  -n federated-catalogue

# Keycloak client secret
kubectl create secret generic fc-keycloak-client-secret \
  --from-literal=keycloak_client_secret='your-client-secret' \
  -n federated-catalogue
```

#### Reference Secrets in Helm Values
```yaml
postgresql:
  auth:
    existingSecret: fc-postgres
    secretKeys:
      adminPasswordKey: postgres-password
      userPasswordKey: password
```

### External Secret Management

For production, consider:
- **HashiCorp Vault** + **External Secrets Operator**
- **AWS Secrets Manager** + **ESO**
- **Azure Key Vault** + **Secrets Store CSI Driver**
- **Google Secret Manager** + **ESO**

### Application Configuration

The FC service uses Spring Boot's configuration system. You can override any `application.yml` property via environment variables.

**Example**: Override verification settings:
```yaml
env:
  - name: FEDERATED_CATALOGUE_VERIFICATION_SEMANTICS
    value: "true"
  - name: FEDERATED_CATALOGUE_VERIFICATION_SIGNATURES
    value: "false"
  - name: FEDERATED_CATALOGUE_VERIFICATION_TRUST_FRAMEWORK_GAIAX_ENABLED
    value: "false"
```

**Spring Boot environment variable naming**:
- Replace `.` with `_`
- Use uppercase
- Nested properties use `_`

Example: `federated-catalogue.verification.semantics` → `FEDERATED_CATALOGUE_VERIFICATION_SEMANTICS`

---

## Monitoring and Health Checks

### Health Endpoints

The FC service exposes Spring Boot Actuator endpoints:

- `/actuator/health` - Overall health status
- `/actuator/info` - Application information

**Kubernetes uses these for:**
- **Liveness Probe**: Is the application alive? (restart if not)
- **Readiness Probe**: Can it accept traffic? (remove from service if not)

**Current configuration** (values.yaml):
```yaml
probes:
  path: /actuator/health
  initialDelaySeconds: 60
  periodSeconds: 30
```

**What this means**:
- Wait 60 seconds after pod starts before first check
- Check every 30 seconds
- If health check fails, Kubernetes will restart the pod

### Viewing Health Status

```bash
# Check pod health
kubectl get pods -n federated-catalogue

# Describe pod for detailed events
kubectl describe pod fc-service-xxxxx -n federated-catalogue

# Check readiness gates
kubectl get pod fc-service-xxxxx -n federated-catalogue -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}'
```

### Logging

**View logs:**
```bash
# Real-time logs
kubectl logs -f -n federated-catalogue -l app=fc-service

# Logs from previous crash (if pod restarted)
kubectl logs -n federated-catalogue fc-service-xxxxx --previous

# Logs from specific time range
kubectl logs -n federated-catalogue fc-service-xxxxx --since=1h
```

**Log levels** are configured in `application.yml`:
```yaml
logging:
  level:
    root: INFO
    eu.xfsc.fc: DEBUG
```

**Override via environment variable:**
```yaml
env:
  - name: LOGGING_LEVEL_EU_XFSC_FC
    value: DEBUG
  - name: LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY
    value: DEBUG
```

### Monitoring Stack (Optional)

For production, deploy a monitoring stack:

**Prometheus + Grafana:**
```bash
# Install Prometheus Operator
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install prometheus prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace

# Access Grafana
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80

# Default credentials:
# Username: admin
# Password: prom-operator
```

**Configure ServiceMonitor for FC service:**
```yaml
# In values.yaml
metrics:
  enabled: true
  serviceMonitor:
    enabled: true
```

**Key metrics to monitor:**
- Request rate and latency
- JVM heap usage
- Database connection pool
- Graph query performance
- Pod CPU/memory usage

---

## Scaling and Performance

### Horizontal Scaling

The FC service can be scaled horizontally by increasing replicas:

```yaml
# In values.yaml
replicaCount: 3
```

**Deploy the change:**
```bash
helm upgrade fc-service . -f values-production.yaml -n federated-catalogue
```

**Or scale dynamically:**
```bash
kubectl scale deployment fc-service --replicas=3 -n federated-catalogue
```

### Horizontal Pod Autoscaler (HPA)

HPA automatically scales pods based on CPU/memory usage:

```yaml
autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
  targetMemoryUtilizationPercentage: 80
```

**What this does**:
- Maintains 2-10 pods
- Scales up when CPU > 70% or Memory > 80%
- Scales down when usage decreases

**Requirements**:
- Metrics Server must be installed in your cluster
- Resource requests must be defined

**Check HPA status:**
```bash
kubectl get hpa -n federated-catalogue
```

### Resource Optimization

**Define appropriate resource requests and limits:**

```yaml
resources:
  requests:
    cpu: 500m       # Guaranteed CPU
    memory: 1Gi     # Guaranteed memory
  limits:
    cpu: 2000m      # Maximum CPU
    memory: 4Gi     # Maximum memory
```

**How to determine values:**
1. Start with conservative estimates
2. Monitor actual usage with `kubectl top pods`
3. Adjust based on observed patterns
4. Leave headroom for spikes (20-30%)

**Database performance:**

**PostgreSQL**:
```yaml
postgresql:
  primary:
    resources:
      requests:
        cpu: 250m
        memory: 512Mi
      limits:
        cpu: 1000m
        memory: 2Gi
```

**Neo4j**:
```yaml
neo4j:
  resources:
    requests:
      cpu: 500m
      memory: 2Gi
    limits:
      cpu: 2000m
      memory: 8Gi
  config:
    dbms.memory.heap.initial_size: "1G"
    dbms.memory.heap.max_size: "4G"
    dbms.memory.pagecache.size: "2G"
```

### Connection Pooling

The FC service uses HikariCP for database connection pooling:

```yaml
spring:
  datasource:
    hikari:
      minimumIdle: 8
      maximumPoolSize: 128
      connectionTimeout: 30000
      idleTimeout: 600000
      maxLifetime: 1800000
```

**Adjust based on:**
- Number of replicas (more replicas = fewer connections per pod)
- Database connection limit
- Expected concurrent requests

**Rule of thumb**: `(replicas × maximumPoolSize) < database_max_connections`

---

## Security Considerations

### 1. Network Policies

Restrict pod-to-pod communication:

```yaml
# network-policy.yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: fc-service-network-policy
  namespace: federated-catalogue
spec:
  podSelector:
    matchLabels:
      app: fc-service
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: nginx-ingress
      ports:
        - protocol: TCP
          port: 8081
  egress:
    - to:
        - podSelector:
            matchLabels:
              app: fc-postgres
      ports:
        - protocol: TCP
          port: 5432
    - to:
        - podSelector:
            matchLabels:
              app: fc-neo4j-db
      ports:
        - protocol: TCP
          port: 7687
    - to:
        - podSelector:
            matchLabels:
              app.kubernetes.io/name: keycloak
      ports:
        - protocol: TCP
          port: 8080
```

### 2. Pod Security Standards

Apply pod security policies:

```yaml
podSecurityContext:
  runAsNonRoot: true
  runAsUser: 1000
  fsGroup: 1000
  seccompProfile:
    type: RuntimeDefault

securityContext:
  allowPrivilegeEscalation: false
  capabilities:
    drop:
      - ALL
  readOnlyRootFilesystem: false  # Spring Boot needs write access
```

### 3. TLS/HTTPS

**Enable TLS for ingress:**

```yaml
ingress:
  enabled: true
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
  tls:
    - secretName: fc-service-tls
      hosts:
        - fc-service.yourdomain.com
```

**Install cert-manager** (if not already installed):
```bash
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml
```

### 4. Secret Management

**Use sealed secrets or external secret operators:**

```bash
# Install Sealed Secrets
helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
helm install sealed-secrets sealed-secrets/sealed-secrets -n kube-system

# Seal a secret
kubeseal -o yaml < secret.yaml > sealed-secret.yaml
```

### 5. Image Security

**Use specific tags, not `latest`:**
```yaml
image:
  tag: "2.1.0"  # Not "latest"
```

**Scan images for vulnerabilities:**
```bash
# Using Trivy
trivy image your-registry.io/fc-service:2.1.0
```

**Use image pull policies:**
```yaml
image:
  pullPolicy: IfNotPresent  # Don't always pull from registry
```

### 6. RBAC (Role-Based Access Control)

Create a service account with minimal permissions:

```yaml
serviceAccount:
  create: true
  annotations: {}
  name: fc-service-sa

# Grant only necessary permissions
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: fc-service-role
rules:
  - apiGroups: [""]
    resources: ["configmaps", "secrets"]
    verbs: ["get", "list"]
```

### 7. Database Security

**Use TLS for database connections:**

For PostgreSQL:
```yaml
env:
  - name: SPRING_DATASOURCE_URL
    value: jdbc:postgresql://fc-postgres:5432/postgres?sslmode=require
```

For Neo4j:
```yaml
env:
  - name: GRAPHSTORE_URI
    value: bolt+s://fc-neo4j:7687  # TLS enabled
```

**Encrypt data at rest:**
- Use encrypted PersistentVolumes
- Enable database-level encryption

---

## Troubleshooting

### Common Issues and Solutions

#### Issue 1: Pods Not Starting

**Symptoms:**
```bash
kubectl get pods -n federated-catalogue
# NAME                     READY   STATUS             RESTARTS   AGE
# fc-service-xxx-yyy       0/1     CrashLoopBackOff   5          5m
```

**Diagnosis:**
```bash
# Check pod events
kubectl describe pod fc-service-xxx-yyy -n federated-catalogue

# Check logs
kubectl logs fc-service-xxx-yyy -n federated-catalogue
```

**Common causes:**
- **Database not ready**: FC service tries to connect before PostgreSQL is ready
  - Solution: Increase `initialDelaySeconds` in probes
- **Configuration error**: Wrong environment variables
  - Solution: Check env vars in deployment, validate against application.yml
- **Image pull failure**: Cannot pull image from registry
  - Solution: Check imagePullSecrets, verify registry access

#### Issue 2: Neo4j Init Container Failing

**Symptoms:**
```bash
kubectl get pods -n federated-catalogue
# fc-neo4j-0   0/1   Init:Error   0   2m
```

**Diagnosis:**
```bash
# Check init container logs
kubectl logs fc-neo4j-0 -c init-plugins -n federated-catalogue
```

**Common causes:**
- **Network connectivity**: Cannot download plugins
  - Solution: Check network policies, proxy settings
- **Plugin download URLs changed**: GitHub releases moved
  - Solution: Update plugin URLs in values.yaml

**Workaround**: Pre-build Neo4j image with plugins:
```dockerfile
FROM neo4j:5.18.1
RUN wget https://github.com/neo4j-contrib/neo4j-apoc-procedures/releases/download/5.18.0/apoc-5.18.0-extended.jar \
         -O /var/lib/neo4j/plugins/apoc-5.18.0-extended.jar
# ... download other plugins
```

#### Issue 3: Keycloak Realm Not Imported

**Symptoms**: Keycloak starts but `gaia-x` realm is missing

**Diagnosis:**
```bash
# Check Keycloak logs
kubectl logs -l app.kubernetes.io/name=keycloak -n federated-catalogue | grep import

# Check ConfigMap exists
kubectl get configmap fc-keycloak-realm-configmap -n federated-catalogue
```

**Common causes:**
- **ConfigMap not mounted**: Volume mount missing
  - Solution: Verify extraVolumes and extraVolumeMounts in values.yaml
- **Realm JSON invalid**: Syntax error in gaia-x-realm.json
  - Solution: Validate JSON syntax
- **Import flag missing**: `--import-realm` not set
  - Solution: Check `KEYCLOAK_EXTRA_ARGS` environment variable

**Fix**:
```bash
# Manually import realm
kubectl exec -it fc-keycloak-xxx-yyy -n federated-catalogue -- \
  /opt/bitnami/keycloak/bin/kc.sh import \
  --file /opt/bitnami/keycloak/data/import/gaia-x-realm.json
```

#### Issue 4: Health Checks Failing

**Symptoms**:
```bash
kubectl describe pod fc-service-xxx-yyy -n federated-catalogue
# Events:
# Liveness probe failed: HTTP probe failed with statuscode: 503
```

**Common causes:**
- **Graph database unhealthy**: Neo4j connection failed
  - Check: Neo4j pod status, network connectivity
- **PostgreSQL unhealthy**: Database migration failed
  - Check: PostgreSQL logs, Liquibase logs in FC service
- **Application startup slow**: Health check before app ready
  - Solution: Increase `initialDelaySeconds`

**Test health manually:**
```bash
kubectl port-forward fc-service-xxx-yyy 8081:8081 -n federated-catalogue
curl http://localhost:8081/actuator/health

# Response should show component statuses:
# {
#   "status": "UP",
#   "components": {
#     "db": {"status": "UP"},
#     "diskSpace": {"status": "UP"},
#     "graphStore": {"status": "UP"}
#   }
# }
```

#### Issue 5: Ingress Not Working

**Symptoms**: Cannot access `http://fc-service.yourdomain.com`

**Diagnosis:**
```bash
# Check ingress
kubectl get ingress -n federated-catalogue

# Describe ingress
kubectl describe ingress fc-service -n federated-catalogue

# Check ingress controller logs
kubectl logs -n ingress-nginx -l app.kubernetes.io/name=ingress-nginx
```

**Common causes:**
- **Ingress controller not installed**
  - Solution: Install nginx-ingress controller
- **DNS not configured**
  - Solution: Add DNS records or /etc/hosts entry
- **Ingress class mismatch**
  - Check: `ingressClassName` matches your controller
- **Backend service not ready**
  - Check: Service endpoints exist (`kubectl get endpoints`)

**Test backend directly:**
```bash
# Port-forward to service
kubectl port-forward svc/fc-service 8081:8081 -n federated-catalogue
curl http://localhost:8081/actuator/health
```

#### Issue 6: OAuth Authentication Failing

**Symptoms**: 401 Unauthorized when calling FC API

**Diagnosis:**
```bash
# Enable security debug logging
kubectl set env deployment/fc-service \
  LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=DEBUG \
  -n federated-catalogue

# Check logs
kubectl logs -f -l app=fc-service -n federated-catalogue | grep -i oauth
```

**Common causes:**
- **JWT issuer mismatch**: `issuer-uri` doesn't match Keycloak URL
  - Solution: Verify `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`
- **Keycloak not accessible**: FC service can't reach Keycloak
  - Test: `kubectl exec fc-service-xxx -- curl http://fc-keycloak-headless:8080`
- **Client secret wrong**: Mismatched client credentials
  - Solution: Verify secret in Keycloak admin console
- **Token expired**: Validity period elapsed
  - Solution: Request new token

**Get token and inspect:**
```bash
# Get token
TOKEN=$(curl -X POST http://keycloak.yourdomain.com/realms/gaia-x/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=federated-catalogue" \
  -d "client_secret=YOUR_SECRET" \
  -d "grant_type=client_credentials" \
  | jq -r '.access_token')

# Decode JWT (use jwt.io or jwt-cli)
echo $TOKEN | jwt decode -
```

#### Issue 7: Persistent Volume Issues

**Symptoms**: Pod pending with `FailedScheduling`

**Diagnosis:**
```bash
kubectl describe pod fc-postgres-0 -n federated-catalogue
# Events: FailedScheduling: persistentvolumeclaim "data-fc-postgres-0" not found
```

**Common causes:**
- **No storage class available**
  - Check: `kubectl get storageclass`
  - Solution: Create or configure default storage class
- **Insufficient storage**: Not enough capacity
  - Solution: Increase cluster storage or reduce PVC size
- **Access mode incompatible**: PVC requires ReadWriteMany but storage only supports ReadWriteOnce
  - Solution: Use compatible access mode

**Manual PV creation:**
```yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: fc-postgres-pv
spec:
  capacity:
    storage: 10Gi
  accessModes:
    - ReadWriteOnce
  hostPath:
    path: /mnt/data/fc-postgres  # Node-local storage
```

### Debugging Commands Cheat Sheet

```bash
# Pod debugging
kubectl get pods -n federated-catalogue
kubectl describe pod POD_NAME -n federated-catalogue
kubectl logs POD_NAME -n federated-catalogue
kubectl logs POD_NAME -c CONTAINER_NAME -n federated-catalogue --previous
kubectl exec -it POD_NAME -n federated-catalogue -- /bin/sh

# Service debugging
kubectl get svc -n federated-catalogue
kubectl get endpoints -n federated-catalogue
kubectl port-forward svc/SERVICE_NAME LOCAL_PORT:REMOTE_PORT -n federated-catalogue

# Ingress debugging
kubectl get ingress -n federated-catalogue
kubectl describe ingress INGRESS_NAME -n federated-catalogue

# ConfigMap/Secret debugging
kubectl get configmap -n federated-catalogue
kubectl describe configmap CONFIGMAP_NAME -n federated-catalogue
kubectl get secret -n federated-catalogue
kubectl get secret SECRET_NAME -o yaml -n federated-catalogue

# Resource usage
kubectl top pods -n federated-catalogue
kubectl top nodes

# Events
kubectl get events -n federated-catalogue --sort-by='.lastTimestamp'

# Helm debugging
helm list -n federated-catalogue
helm status fc-service -n federated-catalogue
helm get values fc-service -n federated-catalogue
helm get manifest fc-service -n federated-catalogue
```

---

## Next Steps and Recommendations

### Immediate Actions (Before Production)

1. **Update Image Repository** ✅ CRITICAL
   - Build and push image to your registry
   - Update `image.repository` in values.yaml

2. **Replace Hard-coded Secrets** ✅ CRITICAL
   - Generate strong passwords for all components
   - Use Kubernetes Secrets or external secret manager
   - Remove passwords from values files

3. **Configure DNS** ✅ REQUIRED
   - Set up proper DNS records for ingress hosts
   - Or configure LoadBalancer with external DNS

4. **Define Resource Limits** ✅ REQUIRED
   - Set appropriate CPU/memory requests and limits
   - Prevent resource exhaustion

5. **Enable Persistent Storage** ✅ REQUIRED
   - Enable `storage.enabled: true` for file uploads
   - Configure backup strategy

6. **Test the Deployment** ✅ REQUIRED
   - Verify all pods are running
   - Test OAuth flow end-to-end
   - Upload and query test assets

### Short-term Improvements (Within 1-2 Weeks)

1. **Set Up Monitoring**
   - Deploy Prometheus + Grafana
   - Create dashboards for key metrics
   - Configure alerting

2. **Configure TLS/HTTPS**
   - Install cert-manager
   - Enable TLS on ingresses
   - Enforce HTTPS redirects

3. **Implement Backup Strategy**
   - Automated PostgreSQL backups (pg_dump or Velero)
   - Neo4j backups (neo4j-admin backup)
   - Test restore procedures

4. **Document Runbooks**
   - Deployment procedures
   - Rollback procedures
   - Incident response

5. **Set Up CI/CD**
   - Automated builds on commit
   - Automated tests
   - GitOps deployment (ArgoCD or Flux)

### Medium-term Enhancements (1-3 Months)

1. **High Availability**
   - Run multiple FC service replicas
   - Enable autoscaling (HPA)
   - Configure pod anti-affinity

2. **Database High Availability**
   - PostgreSQL: Use Bitnami HA chart or CloudNativePG operator
   - Neo4j: Configure Neo4j cluster (Enterprise license required)

3. **Disaster Recovery**
   - Cross-region backups
   - Documented recovery procedures
   - Regular DR drills

4. **Security Hardening**
   - Network policies
   - Pod security policies
   - Security scanning in CI/CD
   - Periodic penetration testing

5. **Performance Optimization**
   - Load testing
   - Database query optimization
   - Caching strategy (Redis/Memcached)
   - CDN for static assets

6. **Observability**
   - Distributed tracing (Jaeger/Zipkin)
   - Application Performance Monitoring (APM)
   - Log aggregation (ELK/Loki)

### Long-term Considerations (3+ Months)

1. **Multi-tenancy**
   - Namespace per tenant
   - Resource quotas
   - Network isolation

2. **Multi-region Deployment**
   - Geo-distributed clusters
   - Global load balancing
   - Data replication

3. **Compliance & Auditing**
   - Audit logging
   - Compliance scanning
   - Policy enforcement (OPA/Kyverno)

4. **Cost Optimization**
   - Resource right-sizing
   - Spot instances for non-critical workloads
   - Storage tiering

### Recommended Helm Chart Improvements

Create a new branch and update the chart:

1. **Parameterize Hard-coded Values**
   ```yaml
   # Add to values.yaml with better defaults
   hostAliases: []  # Empty by default

   # Add ability to use existing secrets
   postgresql:
     auth:
       existingSecret: ""  # If set, use existing secret
   ```

2. **Add Resource Limits by Default**
   ```yaml
   resources:
     requests:
       cpu: 500m
       memory: 1Gi
     limits:
       cpu: 2000m
       memory: 4Gi
   ```

3. **Add NetworkPolicy Template**
   ```yaml
   # templates/networkpolicy.yaml
   {{- if .Values.networkPolicy.enabled }}
   # NetworkPolicy resource
   {{- end }}
   ```

4. **Add ServiceMonitor Template**
   ```yaml
   # templates/servicemonitor.yaml
   {{- if .Values.metrics.enabled }}
   # ServiceMonitor for Prometheus
   {{- end }}
   ```

5. **Update Chart Version**
   ```yaml
   # Chart.yaml
   version: 0.5.0  # Increment version
   ```

6. **Document Values**
   - Add comprehensive comments to values.yaml
   - Create a separate VALUES.md with all options

### Testing Checklist

Before going to production, verify:

- [ ] All pods are in `Running` state
- [ ] Health checks pass consistently
- [ ] OAuth authentication works
- [ ] Can upload and retrieve assets
- [ ] API documentation is accessible
- [ ] Logs are readable and appropriate level
- [ ] Metrics are being collected
- [ ] Backups are running successfully
- [ ] Disaster recovery procedure tested
- [ ] Load testing completed
- [ ] Security scan passed
- [ ] DNS resolution works
- [ ] TLS certificates are valid
- [ ] Resource limits are appropriate
- [ ] Autoscaling works as expected
- [ ] Monitoring alerts are configured
- [ ] Documentation is complete

---

## Conclusion

This roadmap provides a comprehensive guide for deploying the Federated Catalogue to Kubernetes. The existing Helm chart provides a solid foundation but requires updates for production readiness, particularly around secrets management, resource configuration, and storage.

### Key Takeaways

1. **The Helm chart is functional** but contains hard-coded values suitable only for development
2. **Security must be addressed** before production deployment (secrets, TLS, network policies)
3. **Persistent storage** should be enabled to avoid data loss
4. **Monitoring and logging** are essential for operational visibility
5. **Start simple**, then iterate toward high availability and advanced features

### Getting Help

- **GitHub Issues**: https://github.com/eclipse-xfsc/federated-catalogue/issues
- **Wiki**: https://github.com/eclipse-xfsc/federated-catalogue/wiki
- **GXFS Community**: https://www.gxfs.eu/

### Document Maintenance

This roadmap should be updated as:
- New versions of the application are released
- Kubernetes best practices evolve
- Dependency versions change
- New features are added

**Last Updated**: 2026-04-07
**Application Version**: 2.1.0
**Helm Chart Version**: 0.4.0
**Author**: Claude Code AI Assistant
