#!/usr/bin/env bash

set -eu

function log {
   echo "$(date +"%T") - INFO - $*"
}

export PROJECT_NAME=akvo-lumen

if [[ "${TRAVIS_BRANCH}" != "develop" ]] && [[ "${TRAVIS_BRANCH}" != "master" ]]; then
    exit 0
fi

if [[ "${TRAVIS_PULL_REQUEST}" != "false" ]]; then
    exit 0
fi

log Making sure gcloud and kubectl are installed and up to date
gcloud components install kubectl
gcloud components update
gcloud version
which gcloud kubectl

log Authentication with gcloud and kubectl
openssl aes-256-cbc -K $encrypted_13abf95e958f_key -iv $encrypted_13abf95e958f_iv \
	-in ci/gcloud-service-account.json.enc -out ci/gcloud-service-account.json -d
gcloud auth activate-service-account --key-file ci/gcloud-service-account.json
gcloud config set project akvo-lumen
gcloud config set container/cluster europe-west1-d
gcloud config set compute/zone europe-west1-d
gcloud config set container/use_client_certificate True

ENVIRONMENT=test
if [[ "${TRAVIS_BRANCH}" == "master" ]]; then
    log Environment is production
    gcloud container clusters get-credentials production
    ENVIRONMENT=production
    BACKEND_POD_CPU_REQUESTS="100m"
    BACKEND_POD_CPU_LIMITS="100m"
    BACKEND_POD_MEM_REQUESTS="1536Mi"
    BACKEND_POD_MEM_LIMITS="2048Mi"
    CLIENT_POD_CPU_REQUESTS="100m"
    CLIENT_POD_CPU_LIMITS="100m"
    CLIENT_POD_MEM_REQUESTS="64Mi"
    CLIENT_POD_MEM_LIMITS="128Mi"
    MAPS_POD_CPU_REQUESTS="100m"
    MAPS_POD_CPU_LIMITS="100m"
    MAPS_POD_MEM_REQUESTS="512Mi"
    MAPS_POD_MEM_LIMITS="512Mi"
    REDIS_POD_CPU_REQUESTS="100m"
    REDIS_POD_CPU_LIMITS="200m"
    REDIS_POD_MEM_REQUESTS="16Mi"
    GW_POD_CPU_REQUESTS="100m"
    GW_POD_CPU_LIMITS="200m"
    GW_POD_MEM_REQUESTS="32Mi"
    GW_POD_MEM_LIMITS="32Mi"
else
    log Environment is test
    gcloud container clusters get-credentials test
    BACKEND_POD_CPU_REQUESTS="100m"
    BACKEND_POD_CPU_LIMITS="200m"
    BACKEND_POD_MEM_REQUESTS="768Mi"
    BACKEND_POD_MEM_LIMITS="1024Mi"
    CLIENT_POD_CPU_REQUESTS="100m"
    CLIENT_POD_CPU_LIMITS="200m"
    CLIENT_POD_MEM_REQUESTS="32Mi"
    CLIENT_POD_MEM_LIMITS="64Mi"
    MAPS_POD_CPU_REQUESTS="100m"
    MAPS_POD_CPU_LIMITS="200m"
    MAPS_POD_MEM_REQUESTS="256Mi"
    MAPS_POD_MEM_LIMITS="256Mi"
    REDIS_POD_CPU_REQUESTS="50m"
    REDIS_POD_CPU_LIMITS="100m"
    REDIS_POD_MEM_REQUESTS="16Mi"
    GW_POD_CPU_REQUESTS="50m"
    GW_POD_CPU_LIMITS="200m"
    GW_POD_MEM_REQUESTS="32Mi"
    GW_POD_MEM_LIMITS="32Mi"
fi

log Pushing images
gcloud docker -- push eu.gcr.io/${PROJECT_NAME}/lumen-backend
gcloud docker -- push eu.gcr.io/${PROJECT_NAME}/lumen-client
gcloud docker -- push eu.gcr.io/${PROJECT_NAME}/lumen-maps

log Finding blue/green state
LIVE_COLOR=$(./ci/live-color.sh)
log LIVE is "${LIVE_COLOR}"
DARK_COLOR=$(./ci/helpers/dark-color.sh "$LIVE_COLOR")

log "Deploying to dark ($DARK_COLOR)"
sed -e "s/\${BUILD_HASH}/$TRAVIS_COMMIT/" \
  -e "s/\${COLOR}/${DARK_COLOR}/" \
  -e "s/\${ENVIRONMENT}/${ENVIRONMENT}/" \
  -e "s/\${BACKEND_POD_CPU_REQUESTS}/${BACKEND_POD_CPU_REQUESTS}/" \
  -e "s/\${BACKEND_POD_MEM_REQUESTS}/${BACKEND_POD_MEM_REQUESTS}/" \
  -e "s/\${BACKEND_POD_CPU_LIMITS}/${BACKEND_POD_CPU_LIMITS}/" \
  -e "s/\${BACKEND_POD_MEM_LIMITS}/${BACKEND_POD_MEM_LIMITS}/" \
  -e "s/\${CLIENT_POD_CPU_REQUESTS}/${CLIENT_POD_CPU_REQUESTS}/" \
  -e "s/\${CLIENT_POD_MEM_REQUESTS}/${CLIENT_POD_MEM_REQUESTS}/" \
  -e "s/\${CLIENT_POD_CPU_LIMITS}/${CLIENT_POD_CPU_LIMITS}/" \
  -e "s/\${CLIENT_POD_MEM_LIMITS}/${CLIENT_POD_MEM_LIMITS}/" \
  -e "s/\${MAPS_POD_CPU_REQUESTS}/${MAPS_POD_CPU_REQUESTS}/" \
  -e "s/\${MAPS_POD_MEM_REQUESTS}/${MAPS_POD_MEM_REQUESTS}/" \
  -e "s/\${MAPS_POD_CPU_LIMITS}/${MAPS_POD_CPU_LIMITS}/" \
  -e "s/\${MAPS_POD_MEM_LIMITS}/${MAPS_POD_MEM_LIMITS}/" \
  ci/k8s/deployment.yaml.template > deployment.yaml

kubectl apply -f deployment.yaml

sed -e "s/\${ENVIRONMENT}/${ENVIRONMENT}/" \
  -e "s/\${REDIS_POD_CPU_REQUESTS}/${REDIS_POD_CPU_REQUESTS}/" \
  -e "s/\${REDIS_POD_MEM_REQUESTS}/${REDIS_POD_MEM_REQUESTS}/" \
  -e "s/\${REDIS_POD_CPU_LIMITS}/${REDIS_POD_CPU_LIMITS}/" \
  ci/k8s/redis-master-windshaft.yaml.template > redis-master-windshaft.yaml

kubectl apply -f ci/k8s/redis-master-windshaft.yaml

sed -e "s/\${ENVIRONMENT}/${ENVIRONMENT}/" \
  -e "s/\${GW_POD_CPU_REQUESTS}/${GW_POD_CPU_REQUESTS}/" \
  -e "s/\${GW_POD_MEM_REQUESTS}/${GW_POD_MEM_REQUESTS}/" \
  -e "s/\${GW_POD_CPU_LIMITS}/${GW_POD_CPU_LIMITS}/" \
  -e "s/\${GW_POD_MEM_LIMITS}/${GW_POD_MEM_LIMITS}/" \
  ci/k8s/blue-green-gateway.yaml.template > blue-green-gateway.yaml

kubectl apply -f ci/k8s/blue-green-gateway.yaml

if [[ "${TRAVIS_BRANCH}" = "master" ]]; then
    exit 0
fi

log Waiting for k8s to finish
./ci/helpers/wait-for-k8s-deployment-to-be-ready.sh "$DARK_COLOR"
log Waiting for k8s to be healthy
./ci/helpers/wait-for-k8s-deployment-to-be-healthy.sh

log Running end to end tests against the Kubernetes TEST environment
./ci/e2e-test.sh script-test akvolumenci https://dark-lumencitest.akvotest.org/ "$USERNAME" "$PASSWORD"
log Cleaning up environment
./ci/e2e-test.sh clean-all akvolumenci https://dark-lumencitest.akvotest.org/ "$USERNAME" "$PASSWORD" || echo "Ignoring error during cleanup"
