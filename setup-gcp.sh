#!/bin/bash
set -e

# =============================================================================
# GCP One-Time Setup Script — Koval Training
# Edit all variables in this section before running.
# =============================================================================

PROJECT_ID="koval-training"
BILLING_ACCOUNT_ID="YOUR_BILLING_ACCOUNT_ID"   # gcloud beta billing accounts list
REGION="europe-west1"
DOMAIN="koval.com"
API_DOMAIN="api.koval.com"
FIREBASE_SA_EMAIL="firebase-adminsdk-XXXXX@${PROJECT_ID}.iam.gserviceaccount.com"  # get from Firebase Console → Project Settings → Service Accounts

# Secrets
ANTHROPIC_API_KEY="sk-ant-..."
JWT_SECRET="your-jwt-secret-min-32-chars"
MONGODB_URI="mongodb+srv://user:pass@cluster.mongodb.net/training-planner"
STRAVA_CLIENT_ID="your-strava-client-id"
STRAVA_CLIENT_SECRET="your-strava-client-secret"
GOOGLE_CLIENT_ID="your-google-client-id"
GOOGLE_CLIENT_SECRET="your-google-client-secret"

# Derived (do not edit)
SA_EMAIL="github-actions@${PROJECT_ID}.iam.gserviceaccount.com"
ALLOWED_ORIGINS="https://${DOMAIN},https://www.${DOMAIN}"
STRAVA_REDIRECT_URI="https://${API_DOMAIN}/api/auth/strava/callback"
GOOGLE_REDIRECT_URI="https://${DOMAIN}/auth/google/callback"

# =============================================================================

echo "==> Step 1: Create GCP project"
gcloud projects create "$PROJECT_ID" --name="Koval Training"
gcloud config set project "$PROJECT_ID"
gcloud beta billing projects link "$PROJECT_ID" --billing-account="$BILLING_ACCOUNT_ID"

echo "==> Step 2: Enable required APIs"
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  cloudbuild.googleapis.com \
  iam.googleapis.com \
  firebase.googleapis.com

echo "==> Step 3: Create Artifact Registry repository"
gcloud artifacts repositories create training-planner \
  --repository-format=docker \
  --location="$REGION" \
  --description="Koval Training backend images"

echo "==> Step 4: Create GitHub Actions service account"
gcloud iam service-accounts create github-actions \
  --display-name="GitHub Actions Deployer"

echo "==> Step 4: Grant IAM roles to service account"
for ROLE in roles/run.admin roles/artifactregistry.writer roles/secretmanager.secretAccessor roles/iam.serviceAccountUser; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${SA_EMAIL}" \
    --role="$ROLE"
done

echo "==> Step 4: Generate service account key"
gcloud iam service-accounts keys create gcp-sa-key.json \
  --iam-account="$SA_EMAIL"
echo "    !! Add gcp-sa-key.json contents to GitHub Secret GCP_SA_KEY, then delete it."

echo "==> Step 5: Store secrets in GCP Secret Manager"
echo -n "$ANTHROPIC_API_KEY"   | gcloud secrets create ANTHROPIC_API_KEY   --data-file=-
echo -n "$JWT_SECRET"          | gcloud secrets create JWT_SECRET           --data-file=-
echo -n "$MONGODB_URI"         | gcloud secrets create MONGODB_URI          --data-file=-
echo -n "$ALLOWED_ORIGINS"     | gcloud secrets create ALLOWED_ORIGINS      --data-file=-
echo -n "$STRAVA_CLIENT_ID"    | gcloud secrets create STRAVA_CLIENT_ID     --data-file=-
echo -n "$STRAVA_CLIENT_SECRET"| gcloud secrets create STRAVA_CLIENT_SECRET --data-file=-
echo -n "$STRAVA_REDIRECT_URI" | gcloud secrets create STRAVA_REDIRECT_URI  --data-file=-
echo -n "$GOOGLE_CLIENT_ID"    | gcloud secrets create GOOGLE_CLIENT_ID     --data-file=-
echo -n "$GOOGLE_CLIENT_SECRET"| gcloud secrets create GOOGLE_CLIENT_SECRET --data-file=-
echo -n "$GOOGLE_REDIRECT_URI" | gcloud secrets create GOOGLE_REDIRECT_URI  --data-file=-

echo "==> Step 6: Connect Firebase to the GCP project"
firebase login
firebase projects:addfirebase "$PROJECT_ID"
firebase use "$PROJECT_ID"

echo "==> Step 6: Generate Firebase service account key"
echo "    Make sure FIREBASE_SA_EMAIL is set correctly at the top of this script."
gcloud iam service-accounts keys create firebase-service-account.json \
  --iam-account="$FIREBASE_SA_EMAIL"
echo "    !! Add firebase-service-account.json contents to GitHub Secret FIREBASE_SERVICE_ACCOUNT, then delete it."

echo "==> Step 8: First manual Cloud Run deploy"
gcloud run deploy training-planner-backend \
  --image="${REGION}-docker.pkg.dev/${PROJECT_ID}/training-planner/backend:latest" \
  --region="$REGION" \
  --platform=managed \
  --allow-unauthenticated \
  --port=8080 \
  --set-secrets=ANTHROPIC_API_KEY=ANTHROPIC_API_KEY:latest,JWT_SECRET=JWT_SECRET:latest,MONGODB_URI=MONGODB_URI:latest,ALLOWED_ORIGINS=ALLOWED_ORIGINS:latest,STRAVA_CLIENT_ID=STRAVA_CLIENT_ID:latest,STRAVA_CLIENT_SECRET=STRAVA_CLIENT_SECRET:latest,STRAVA_REDIRECT_URI=STRAVA_REDIRECT_URI:latest,GOOGLE_CLIENT_ID=GOOGLE_CLIENT_ID:latest,GOOGLE_CLIENT_SECRET=GOOGLE_CLIENT_SECRET:latest,GOOGLE_REDIRECT_URI=GOOGLE_REDIRECT_URI:latest

echo "==> Step 9: Map custom domain to Cloud Run"
gcloud beta run domain-mappings create \
  --service=training-planner-backend \
  --domain="$API_DOMAIN" \
  --region="$REGION"

echo ""
echo "==> Done! Remaining manual steps:"
echo "    1. Add DNS records at your registrar (Firebase Hosting for ${DOMAIN}, Cloud Run for ${API_DOMAIN})"
echo "    2. Add GitHub Secrets: GCP_PROJECT_ID=${PROJECT_ID}, GCP_SA_KEY, FIREBASE_SERVICE_ACCOUNT"
echo "    3. Delete local key files: rm gcp-sa-key.json firebase-service-account.json"
echo "    4. Push to main to trigger the first automated deploy."