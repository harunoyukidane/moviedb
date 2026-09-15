#!/bin/sh
# Runs inside the `minio-init` container (quay.io/minio/mc) against the `minio`
# service. Idempotent: safe to rerun on every `docker compose up`. Mirrors the
# mc sequence proven in backend/media's CrossBucketIsolationTest.
set -eu

mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"

mc mb --ignore-existing local/catalogue-artwork
mc mb --ignore-existing local/people-photos

mc admin user add local "$CATALOGUE_MINIO_ACCESS_KEY" "$CATALOGUE_MINIO_SECRET_KEY"
mc admin user add local "$PEOPLE_MINIO_ACCESS_KEY" "$PEOPLE_MINIO_SECRET_KEY"

cat > /tmp/catalogue-policy.json <<'EOF'
{ "Version": "2012-10-17", "Statement": [ { "Effect": "Allow",
  "Action": ["s3:GetObject","s3:PutObject","s3:DeleteObject","s3:ListBucket"],
  "Resource": ["arn:aws:s3:::catalogue-artwork","arn:aws:s3:::catalogue-artwork/*"] } ] }
EOF
cat > /tmp/people-policy.json <<'EOF'
{ "Version": "2012-10-17", "Statement": [ { "Effect": "Allow",
  "Action": ["s3:GetObject","s3:PutObject","s3:DeleteObject","s3:ListBucket"],
  "Resource": ["arn:aws:s3:::people-photos","arn:aws:s3:::people-photos/*"] } ] }
EOF

mc admin policy create local catalogue-policy /tmp/catalogue-policy.json
mc admin policy create local people-policy /tmp/people-policy.json
mc admin policy attach local catalogue-policy --user "$CATALOGUE_MINIO_ACCESS_KEY"
mc admin policy attach local people-policy --user "$PEOPLE_MINIO_ACCESS_KEY"

echo "MinIO buckets, identities, and policies provisioned."
