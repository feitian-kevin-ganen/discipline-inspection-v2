#!/usr/bin/env bash
# AIExamK3 目标机一键备份脚本
# 备份内容：数据库、应用包（app/config/web/scripts/middleware/README）、MinIO 数据
# 用法：
#   bash /root/AIExamK3/scripts/backup.sh
#   bash /root/AIExamK3/scripts/backup.sh /自定义备份目录 保留份数
set -euo pipefail

APP_DIR="/root/AIExamK3"
BACKUP_BASE="${1:-/root/AIExamK3-backup}"
RETAIN_COUNT="${2:-5}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="$BACKUP_BASE/$TIMESTAMP"
mkdir -p "$BACKUP_DIR"

exec > >(tee -a "$BACKUP_DIR/backup.log")
exec 2>&1

echo "[backup] 开始备份，时间戳 $TIMESTAMP"
echo "[backup] 应用目录 $APP_DIR"
echo "[backup] 备份目录 $BACKUP_DIR"

# -------------------------------------------------
# 1. 定位备份工具（优先用系统自带，没有则用随包工具）
# -------------------------------------------------
if command -v mysqldump >/dev/null 2>&1; then
  MYSQLDUMP="$(command -v mysqldump)"
else
  MYSQLDUMP="$APP_DIR/middleware/mysql-tools/bin/mysqldump"
  echo "[backup] 系统未找到 mysqldump，使用随包工具 $MYSQLDUMP"
fi
if [ ! -x "$MYSQLDUMP" ]; then
  echo "[backup] 错误：无法执行 mysqldump ($MYSQLDUMP)"; exit 1
fi

# -------------------------------------------------
# 2. 从 application-polardb.yml 解析数据库连接信息
# -------------------------------------------------
DB_CONFIG="$APP_DIR/config/application-polardb.yml"
if [ ! -f "$DB_CONFIG" ]; then
  echo "[backup] 错误：找不到数据库配置文件 $DB_CONFIG"; exit 1
fi

DB_URL=$(grep -E '^\s*url:\s*jdbc:mysql://' "$DB_CONFIG" | head -1 | sed 's/.*jdbc:mysql:\/\///')
DB_HOST=$(echo "$DB_URL" | sed -E 's/^([^:/]+).*/\1/')
DB_PORT=$(echo "$DB_URL" | sed -E 's/^[^:/]+:([0-9]+).*/\1/')
DB_NAME=$(echo "$DB_URL" | sed -E 's/^[^/]+\/([^?]+).*/\1/')
DB_USER=$(grep -A8 '^\s*datasource:' "$DB_CONFIG" | grep -E '^\s*username:' | head -1 | awk '{print $2}')
DB_PASS=$(grep -A8 '^\s*datasource:' "$DB_CONFIG" | grep -E '^\s*password:' | head -1 | awk '{print $2}')

echo "[backup] 数据库 $DB_HOST:$DB_PORT/$DB_NAME"

# -------------------------------------------------
# 3. 备份数据库
# -------------------------------------------------
echo "[backup] 备份数据库到 exam_db.sql.gz ..."
"$MYSQLDUMP" \
  -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" \
  --single-transaction --routines --triggers \
  "$DB_NAME" 2>>"$BACKUP_DIR/backup.log" | gzip > "$BACKUP_DIR/exam_db.sql.gz"
echo "[backup] 数据库备份完成：$BACKUP_DIR/exam_db.sql.gz"

# -------------------------------------------------
# 4. 备份应用包（排除运行态的 data/logs/run）
# -------------------------------------------------
echo "[backup] 备份应用包到 app-package.tar.gz ..."
tar -czf "$BACKUP_DIR/app-package.tar.gz" \
  -C "$APP_DIR" \
  app config web scripts middleware README.md
echo "[backup] 应用包备份完成：$BACKUP_DIR/app-package.tar.gz"

# -------------------------------------------------
# 5. 备份 MinIO 数据
# -------------------------------------------------
if [ -d "$APP_DIR/data/minio/exam-files" ]; then
  echo "[backup] 备份 MinIO 数据到 minio-data.tar.gz ..."
  tar -czf "$BACKUP_DIR/minio-data.tar.gz" \
    -C "$APP_DIR/data/minio" exam-files
  echo "[backup] MinIO 数据备份完成：$BACKUP_DIR/minio-data.tar.gz"
else
  echo "[backup] 警告：未找到 $APP_DIR/data/minio/exam-files，跳过 MinIO 数据备份"
fi

# -------------------------------------------------
# 6. 生成清单文件
# -------------------------------------------------
cat > "$BACKUP_DIR/manifest.txt" <<EOF
备份时间: $TIMESTAMP
应用目录: $APP_DIR
数据库  : $DB_HOST:$DB_PORT/$DB_NAME
文件列表:
  - exam_db.sql.gz      数据库 SQL 导出
  - app-package.tar.gz  应用包（app/config/web/scripts/middleware/README.md）
  - minio-data.tar.gz   MinIO exam-files 数据
EOF
echo "[backup] 清单文件：$BACKUP_DIR/manifest.txt"

# -------------------------------------------------
# 7. 清理过期备份，只保留最近 $RETAIN_COUNT 份
# -------------------------------------------------
echo "[backup] 清理旧备份，保留最近 $RETAIN_COUNT 份 ..."
ls -1d "$BACKUP_BASE"/[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]_[0-9][0-9][0-9][0-9][0-9][0-9] 2>/dev/null | sort | head -n -"$RETAIN_COUNT" | while read -r old; do
  echo "[backup] 删除旧备份：$old"
  rm -rf "$old"
done

echo "[backup] 全部完成：$BACKUP_DIR"
