# GCP Deployment Guide — Koval Training

This guide walks through the one-time GCP setup required for the GitHub Actions workflow
(`deploy.yml`) to automatically deploy on every push to `main`.

**Architecture:**
- `koval.com` → Firebase Hosting (Angular frontend)
- `api.koval.com` → Cloud Run (Spring Boot backend)
- MongoDB Atlas (managed database, external to GCP)

---

## Prerequisites

- [gcloud CLI](https://cloud.google.com/sdk/docs/install) installed and authenticated
- [Firebase CLI](https://firebase.google.com/docs/cli) installed (`npm install -g firebase-tools`)
- A GCP billing account enabled
- A GitHub repository connected to this codebase

---

## 1. Create the GCP Project

```bash
gcloud projects create koval-training --name="Koval Training" && gcloud config set project koval-training && gcloud beta billing projects link koval-training --billing-account=YOUR_BILLING_ACCOUNT_ID
```

Get your billing account ID:
```bash
gcloud beta billing accounts list
```

---

## 2. Enable Required APIs

```bash
gcloud services enable   run.googleapis.com   artifactregistry.googleapis.com   secretmanager.googleapis.com  cloudbuild.googleapis.com   iam.googleapis.com  firebase.googleapis.com
```

---

## 3. Create the Artifact Registry Repository

This stores the backend Docker images.

```bash
gcloud artifacts repositories create training-planner \
  --repository-format=docker \
  --location=us-central1 \
  --description="Koval Training backend images"
```

---

## 4. Create the Service Account for GitHub Actions

```bash
gcloud iam service-accounts create github-actions \
  --display-name="GitHub Actions Deployer"
```

Grant the required roles:

```bash
export PROJECT_ID=koval-training
export SA_EMAIL=github-actions@${PROJECT_ID}.iam.gserviceaccount.com

# Cloud Run deployment
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/run.admin"

# Push images to Artifact Registry
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/artifactregistry.writer"

# Read secrets from Secret Manager
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/secretmanager.secretAccessor"

# Required to deploy as a service account
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/iam.serviceAccountUser"
```

Generate and download the JSON key:

```bash
gcloud iam service-accounts keys create gcp-sa-key.json \
  --iam-account=${SA_EMAIL}
```

> Keep this file secure — you will paste its contents into GitHub Secrets below.
> Delete it locally once added to GitHub.

---

## 5. Store Secrets in GCP Secret Manager

The backend Cloud Run service reads these at runtime.

```bash
# Create each secret (replace values with real ones)
gcloud secrets create ANTHROPIC_API_KEY   --data-file=<(echo -n "sk-ant-...")
gcloud secrets create JWT_SECRET          --data-file=<(echo -n "your-jwt-secret-min-32-chars")
gcloud secrets create MONGODB_URI         --data-file=<(echo -n "mongodb+srv://user:pass@cluster.mongodb.net/training-planner")
gcloud secrets create ALLOWED_ORIGINS     --data-file=<(echo -n "https://koval.com,https://www.koval.com")
gcloud secrets create STRAVA_CLIENT_ID    --data-file=<(echo -n "your-strava-client-id")
gcloud secrets create STRAVA_CLIENT_SECRET --data-file=<(echo -n "your-strava-client-secret")
gcloud secrets create STRAVA_REDIRECT_URI  --data-file=<(echo -n "https://api.koval.com/api/auth/strava/callback")
gcloud secrets create GOOGLE_CLIENT_ID    --data-file=<(echo -n "your-google-client-id")
gcloud secrets create GOOGLE_CLIENT_SECRET --data-file=<(echo -n "your-google-client-secret")
gcloud secrets create GOOGLE_REDIRECT_URI  --data-file=<(echo -n "https://koval.com/auth/google/callback")
```

Grant the Cloud Run service agent access to read secrets:

```bash
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/secretmanager.secretAccessor"
```

To update a secret later:
```bash
echo -n "new-value" | gcloud secrets versions add SECRET_NAME --data-file=-
```

---

## 6. Connect Firebase to the GCP Project

```bash
firebase login
firebase projects:addfirebase koval-training
firebase use koval-training
```

Generate the Firebase service account for GitHub Actions:

```bash
# In the Firebase Console: Project Settings → Service Accounts → Generate new private key
# Save as firebase-service-account.json
```

Or via CLI:
```bash
gcloud iam service-accounts keys create firebase-service-account.json \
  --iam-account=firebase-adminsdk-XXXXX@koval-training.iam.gserviceaccount.com
```

---

## 7. Add GitHub Secrets

In your GitHub repository go to **Settings → Secrets and variables → Actions** and add:

| Secret name | Value |
|---|---|
| `GCP_PROJECT_ID` | `koval-training` |
| `GCP_SA_KEY` | Full contents of `gcp-sa-key.json` |
| `FIREBASE_SERVICE_ACCOUNT` | Full contents of `firebase-service-account.json` |

> The application secrets (Anthropic key, JWT, MongoDB URI, etc.) are stored in GCP Secret Manager
> (step 5) and are NOT needed as GitHub Secrets — Cloud Run pulls them at deploy time.

Delete local key files after adding them to GitHub:
```bash
rm gcp-sa-key.json firebase-service-account.json
```

---

## 8. First Manual Deploy (Backend)

The workflow cannot deploy a Cloud Run service that does not exist yet.
Run this once to create it with a placeholder image:

```bash
gcloud run deploy training-planner-backend \
  --image=us-central1-docker.pkg.dev/koval-training/training-planner/backend:latest \
  --region=us-central1 \
  --platform=managed \
  --allow-unauthenticated \
  --port=8080 \
  --set-secrets=\
ANTHROPIC_API_KEY=ANTHROPIC_API_KEY:latest,\
JWT_SECRET=JWT_SECRET:latest,\
MONGODB_URI=MONGODB_URI:latest,\
ALLOWED_ORIGINS=ALLOWED_ORIGINS:latest,\
STRAVA_CLIENT_ID=STRAVA_CLIENT_ID:latest,\
STRAVA_CLIENT_SECRET=STRAVA_CLIENT_SECRET:latest,\
STRAVA_REDIRECT_URI=STRAVA_REDIRECT_URI:latest,\
GOOGLE_CLIENT_ID=GOOGLE_CLIENT_ID:latest,\
GOOGLE_CLIENT_SECRET=GOOGLE_CLIENT_SECRET:latest,\
GOOGLE_REDIRECT_URI=GOOGLE_REDIRECT_URI:latest
```

After this, every push to `main` will redeploy automatically.

---

## 9. Domain Setup — koval.com

### 9a. Register the domain

Purchase `koval.com` from any registrar (Google Domains via Squarespace, Namecheap, etc.).

### 9b. Frontend — koval.com → Firebase Hosting

In the Firebase Console:
1. Go to **Hosting → Add custom domain**
2. Enter `koval.com` and `www.koval.com`
3. Firebase will provide two DNS records to add at your registrar:

```
Type    Host    Value
A       @       151.101.1.195   (Firebase IP — use the value Firebase gives you)
A       @       151.101.65.195
CNAME   www     koval.com
```

Firebase provisions a free managed SSL certificate automatically.

### 9c. Backend — api.koval.com → Cloud Run

Get the Cloud Run service URL:
```bash
gcloud run services describe training-planner-backend \
  --region=us-central1 \
  --format="value(status.url)"
# → https://training-planner-backend-XXXX-uc.a.run.app
```

Map a custom domain to Cloud Run:
```bash
gcloud beta run domain-mappings create \
  --service=training-planner-backend \
  --domain=api.koval.com \
  --region=us-central1
```

This outputs DNS records to add at your registrar:

```
Type    Host    Value
CNAME   api     ghs.googlehosted.com
```

GCP provisions a managed SSL certificate for `api.koval.com` automatically.
Propagation takes 15–60 minutes.

### 9d. Update OAuth redirect URIs

Once the domain is live, update these in their respective consoles:

**Strava** (https://www.strava.com/settings/api):
- Authorization Callback Domain: `api.koval.com`

**Google Cloud Console** (APIs & Services → Credentials → your OAuth client):
- Authorized redirect URIs: `https://koval.com/auth/google/callback`

---

## 10. MongoDB Atlas Setup

1. Create a free cluster at https://cloud.mongodb.com
2. Create a database user with read/write access to `training-planner`
3. Whitelist all IPs (`0.0.0.0/0`) so Cloud Run can connect (Cloud Run IPs are dynamic)
4. Copy the connection string and store it as the `MONGODB_URI` secret (step 5)

Connection string format:
```
mongodb+srv://USERNAME:PASSWORD@cluster0.XXXXX.mongodb.net/training-planner?retryWrites=true&w=majority
```

---

## 11. Trigger the First Automated Deploy

Push to `main`:
```bash
git push origin main
```

The workflow runs three parallel jobs:
- **Deploy Backend** — builds JAR → Docker image → Artifact Registry → Cloud Run
- **Deploy Frontend** — builds Angular → Firebase Hosting
- **Build Android APK** — builds debug APK → uploaded as GitHub artifact

Monitor in **GitHub → Actions** tab.

---

## Environment Summary

| Resource | Value |
|---|---|
| GCP Project | `koval-training` |
| Region | `us-central1` |
| Artifact Registry | `us-central1-docker.pkg.dev/koval-training/training-planner/backend` |
| Cloud Run service | `training-planner-backend` |
| Firebase project | `koval-training` |
| Frontend URL | `https://koval.com` |
| Backend API URL | `https://api.koval.com` |
| Database | MongoDB Atlas |
