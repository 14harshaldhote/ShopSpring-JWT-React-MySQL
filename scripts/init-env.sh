#!/bin/sh
# Creates .env with fresh random secrets for the local Docker stack.
# Nothing here is ever committed (.env is in .gitignore).                  [OWASP A02:2025, A04:2025]
set -eu
cd "$(dirname "$0")/.."
if [ -f .env ]; then
  echo ".env already exists; delete it first if you want new secrets." >&2
  exit 1
fi
rand() { openssl rand -hex "$1"; }
# 3072-bit RSA key for signing access tokens, stored on one line with literal \n.
JWT_KEY=$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 2>/dev/null | awk 'NF { printf "%s\\n", $0 }')
# Must pass the app's own password policy, which refuses passwords containing the email name ("admin").
ADMIN_PASSWORD="Harbor-$(rand 8)-Lantern"
DEVIDP_PASSWORD="Demo-$(rand 6)"
umask 077
cat > .env <<ENV
MYSQL_ROOT_PASSWORD=$(rand 24)
DB_PASSWORD=$(rand 24)
DB_MIGRATION_PASSWORD=$(rand 24)
JWT_PRIVATE_KEY="${JWT_KEY}"
OTP_PEPPER=$(rand 32)
MOCK_PAYMENT_SECRET=$(rand 32)
ADMIN_EMAIL=admin@shopspring.dev
ADMIN_PASSWORD=${ADMIN_PASSWORD}
SECURITY_ALERT_EMAIL=security@shopspring.dev
DEVIDP_CLIENT_ID=shopspring-local
DEVIDP_CLIENT_SECRET=$(rand 24)
DEVIDP_USER_EMAIL=demo@shopspring.dev
DEVIDP_USER_PASSWORD=${DEVIDP_PASSWORD}
PAYMENT_PROVIDER=mock
# Optional real providers:
# GOOGLE_CLIENT_ID=
# GOOGLE_CLIENT_SECRET=
# GITHUB_CLIENT_ID=
# GITHUB_CLIENT_SECRET=
# PAYMENT_PROVIDER=razorpay
# RAZORPAY_KEY_ID=
# RAZORPAY_KEY_SECRET=
# RAZORPAY_WEBHOOK_SECRET=
ENV
echo "Wrote .env"
echo "  Admin sign-in:   admin@shopspring.dev / ${ADMIN_PASSWORD}"
echo "  Dev IdP sign-in: demo@shopspring.dev / ${DEVIDP_PASSWORD}"
echo "Start with: docker compose up --build   then open http://localhost:8088 (emails: http://localhost:8025)"
