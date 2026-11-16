#!/usr/bin/env bash
# AIExamK3 目标机一键启动脚本
# 启动：PolarDB-X 容器（如未运行）、Redis、MinIO、后端、Nginx
set -u

CRA_DIR="/root/AIExamK3"
LOG_DIR="$CRA_DIR/logs"
RUN_DIR="$CRA_DIR/run"
JDK="$CRA_DIR/middleware/jdk21/bin/java"
mkdir -p "$LOG_DIR" "$RUN_DIR"

start_polardb() {
  if docker ps --format '{{.Names}}' | grep -q 'pxc-tryout-dn-0-Cand-14397'; then
    echo "[polardb] 容器已在运行"
  else
    echo "[polardb] 容器未运行，请先在目标机执行 pxd tryout 部署 PolarDB-X"
  fi
}

start_redis() {
  if [ -f "$RUN_DIR/redis.pid" ] && kill -0 "$(cat "$RUN_DIR/redis.pid")" 2>/dev/null; then
    echo "[redis] 已在运行 (pid $(cat "$RUN_DIR/redis.pid"))"; return 0
  fi
  mkdir -p "$CRA_DIR/data/redis"
  nohup /usr/bin/redis-server --port 6379 --bind 127.0.0.1 \
    --dir "$CRA_DIR/data/redis" --appendonly yes --daemonize no \
    > "$LOG_DIR/redis.log" 2>&1 &
  echo $! > "$RUN_DIR/redis.pid"
  echo "[redis] 启动中 pid $(cat "$RUN_DIR/redis.pid")，日志 $LOG_DIR/redis.log"
}

start_minio() {
  if [ -f "$RUN_DIR/minio.pid" ] && kill -0 "$(cat "$RUN_DIR/minio.pid")" 2>/dev/null; then
    echo "[minio] 已在运行 (pid $(cat "$RUN_DIR/minio.pid"))"; return 0
  fi
  export MINIO_ROOT_USER=minioadmin MINIO_ROOT_PASSWORD=minioadmin
  nohup "$CRA_DIR/middleware/minio" server "$CRA_DIR/data/minio" \
    --address :9000 --console-address :9001 \
    > "$LOG_DIR/minio.log" 2>&1 &
  echo $! > "$RUN_DIR/minio.pid"
  echo "[minio] 启动中 pid $(cat "$RUN_DIR/minio.pid")，日志 $LOG_DIR/minio.log"
}

start_backend() {
  local jar="$CRA_DIR/app/exam-server.jar"
  if [ -f "$RUN_DIR/backend.pid" ] && kill -0 "$(cat "$RUN_DIR/backend.pid")" 2>/dev/null; then
    echo "[backend] 已在运行 (pid $(cat "$RUN_DIR/backend.pid"))"; return 0
  fi
  nohup "$JDK" -jar "$jar" \
    --spring.profiles.active=polardb \
    --spring.config.additional-location=file:/root/AIExamK3/config/ \
    > "$LOG_DIR/exam-server.log" 2>&1 &
  echo $! > "$RUN_DIR/backend.pid"
  echo "[backend] 启动中 pid $(cat "$RUN_DIR/backend.pid")，日志 $LOG_DIR/exam-server.log"
}

start_nginx() {
  if systemctl is-active --quiet nginx; then
    echo "[nginx] 已在运行"
  else
    systemctl start nginx && echo "[nginx] 已启动" || echo "[nginx] 启动失败，请检查 systemctl"
  fi
}

case "${1:-start}" in
  start)
    start_polardb
    start_redis
    start_minio
    start_backend
    start_nginx
    ;;
  stop)
    for f in "$RUN_DIR"/*.pid; do
      [ -f "$f" ] && kill "$(cat "$f")" 2>/dev/null && rm -f "$f" && echo "stopped $f"
    done
    ;;
  status)
    for f in "$RUN_DIR"/*.pid; do
      [ -f "$f" ] && { kill -0 "$(cat "$f")" 2>/dev/null && echo "$f RUNNING ($(cat "$f"))" || echo "$f DEAD"; }
    done
    systemctl is-active --quiet nginx && echo "nginx RUNNING" || echo "nginx STOPPED"
    ;;
  *)
    echo "Usage: $0 {start|stop|status}"; exit 1
    ;;
esac
