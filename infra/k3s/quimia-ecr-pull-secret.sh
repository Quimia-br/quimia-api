#!/usr/bin/env bash
set -euo pipefail

export KUBECONFIG=/etc/rancher/k3s/k3s.yaml

AWS=/usr/bin/aws
KUBECTL=/usr/local/bin/kubectl
REGION=us-east-1
REGISTRY=237854152841.dkr.ecr.us-east-1.amazonaws.com
NAMESPACE=quimia-dev
SECRET=quimia-ecr-pull

"$KUBECTL" create namespace "$NAMESPACE" \
    --dry-run=client \
    -o yaml | "$KUBECTL" apply -f -

password="$($AWS ecr get-login-password --region "$REGION")"
test -n "$password"

"$KUBECTL" -n "$NAMESPACE" create secret docker-registry "$SECRET" \
    --docker-server="$REGISTRY" \
    --docker-username=AWS \
    --docker-password="$password" \
    --dry-run=client \
    -o yaml | "$KUBECTL" apply -f -
