#!/usr/bin/env bash
# Nightly backup: a compressed PostgreSQL dump plus the uploaded files and generated PDFs.
#   0 2 * * * /opt/examgrid/deploy/backup.sh >> /var/log/examgrid-backup.log 2>&1
set -euo pipefail
cd "$(dirname "$0")"
stamp=$(date +%Y%m%d-%H%M%S)
dest=${BACKUP_DIR:-/var/backups/examgrid}
mkdir -p "$dest"
docker compose -f docker-compose.prod.yml exec -T postgres pg_dump -U examgrid -Fc examgrid > "$dest/db-$stamp.dump"
docker compose -f docker-compose.prod.yml exec -T api tar -C /data -cz storage > "$dest/files-$stamp.tar.gz"
find "$dest" -type f -mtime +14 -delete
echo "backup $stamp done"
