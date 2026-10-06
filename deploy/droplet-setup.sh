#!/usr/bin/env bash
# Run once on a fresh Ubuntu 24.04 Droplet as root
# Usage: bash droplet-setup.sh grabmyseat

set -euo pipefail
APP=${1:?"Usage: $0 <app-name>"}   # e.g. grabmyseat

# ── Docker ────────────────────────────────────────────────────────────────────
apt-get update -qq
apt-get install -y -qq ca-certificates curl gnupg nginx certbot python3-certbot-nginx

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update -qq
apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-compose-plugin

systemctl enable --now docker

# ── App directory ──────────────────────────────────────────────────────────────
mkdir -p /opt/$APP
echo "Place your docker-compose.prod.yml and .env in /opt/$APP then:"
echo "  docker compose -f /opt/$APP/docker-compose.prod.yml up -d"
echo ""
echo "After DNS is pointed at this Droplet, get SSL cert:"
echo "  certbot --nginx -d <your-domain>"
echo ""
echo "Droplet setup complete."
