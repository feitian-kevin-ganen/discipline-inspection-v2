#!/usr/bin/env bash
# AIExamK3 本机一键启动：后端 8088 + 统一入口网关 6003（三端静态 + /api 反代）
# 依赖本机已有环境：JDK17、PostgreSQL 5434、Redis 6379、MinIO 9000（PaaS 托管，不由本脚本启动）
# 用法：./start_all.sh [start|status]
set -u

APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CONFIG="$APP_DIR/config/application.yml"
JAR="$APP_DIR/app/exam-server.jar"
LOG_DIR="$APP_DIR/logs"
RUN_DIR="$APP_DIR/run"
GATEWAY_PORT="${GATEWAY_PORT:-6003}"
BACKEND_PORT="${BACKEND_PORT:-8088}"
PY="${PYTHON:-python3}"

mkdir -p "$LOG_DIR" "$RUN_DIR"

pid_alive() { [ -f "$1" ] && kill -0 "$(cat "$1")" 2>/dev/null; }

tcp_open() { timeout 2 bash -c "exec 3<>/dev/tcp/$1/$2" 2>/dev/null; }

wait_http() {  # wait_http <url> <秒>
  local url="$1" left="${2:-90}"
  while [ "$left" -gt 0 ]; do
    if curl -s -o /dev/null --max-time 2 "$url"; then return 0; fi
    sleep 2
    left=$((left - 2))
  done
  return 1
}

check_deps() {
  local ok=0
  for hp in "127.0.0.1 5434" "127.0.0.1 6379" "127.0.0.1 9000"; do
    set -- $hp
    if tcp_open "$1" "$2"; then echo "[deps] $1:$2 可用"; else echo "[deps] $1:$2 不可用（PaaS 组件未启动）" >&2; ok=1; fi
  done
  command -v java >/dev/null 2>&1 || { echo "[deps] 未找到 java，请安装 JDK17" >&2; ok=1; }
  command -v "$PY" >/dev/null 2>&1 || { echo "[deps] 未找到 $PY" >&2; ok=1; }
  return $ok
}

start_backend() {
  if [ ! -f "$JAR" ]; then echo "[backend] 未找到 $JAR" >&2; return 1; fi
  if [ ! -f "$CONFIG" ]; then echo "[backend] 未找到 $CONFIG" >&2; return 1; fi
  if pid_alive "$RUN_DIR/backend.pid"; then echo "[backend] 已在运行 (pid $(cat "$RUN_DIR/backend.pid"))"; return 0; fi
  nohup java -jar "$JAR" --spring.config.additional-location="file:$APP_DIR/config/" \
    > "$LOG_DIR/exam-server.log" 2>&1 &
  echo $! > "$RUN_DIR/backend.pid"
  echo "[backend] 启动中 pid $(cat "$RUN_DIR/backend.pid")，端口 $BACKEND_PORT，日志 $LOG_DIR/exam-server.log"
}

start_gateway() {
  if pid_alive "$RUN_DIR/gateway.pid"; then echo "[gateway] 已在运行 (pid $(cat "$RUN_DIR/gateway.pid"))"; return 0; fi
  nohup "$PY" "$APP_DIR/scripts/local_gateway.py" --port "$GATEWAY_PORT" --root "$APP_DIR" \
    > "$LOG_DIR/gateway.log" 2>&1 &
  echo $! > "$RUN_DIR/gateway.pid"
  echo "[gateway] 启动中 pid $(cat "$RUN_DIR/gateway.pid")，端口 $GATEWAY_PORT，日志 $LOG_DIR/gateway.log"
}

status_all() {
  local rc=0
  for name in backend gateway; do
    local f="$RUN_DIR/$name.pid"
    if pid_alive "$f"; then
      echo "$name RUNNING (pid $(cat "$f"))"
    else
      echo "$name STOPPED"
      rc=1
    fi
  done
  curl -s -o /dev/null -w "backend  http://127.0.0.1:$BACKEND_PORT/v3/api-docs -> %{http_code}\n" --max-time 3 "http://127.0.0.1:$BACKEND_PORT/v3/api-docs"
  curl -s -o /dev/null -w "gateway  http://127.0.0.1:$GATEWAY_PORT/mgmt/      -> %{http_code}\n" --max-time 3 "http://127.0.0.1:$GATEWAY_PORT/mgmt/"
  return $rc
}

case "${1:-start}" in
  start)
    check_deps || echo "[warn] 依赖检查未全部通过，继续启动（应用可能起不来）"
    start_backend || exit 1
    if wait_http "http://127.0.0.1:$BACKEND_PORT/v3/api-docs" 120; then
      echo "[backend] 已就绪"
    else
      echo "[backend] 120 秒内未就绪，请查看 $LOG_DIR/exam-server.log" >&2
    fi
    start_gateway || exit 1
    sleep 1
    echo "---- 访问入口 ----"
    echo "管理端    http://$(hostname -I | awk '{print $1}'):$GATEWAY_PORT/mgmt/"
    echo "用户端PC  http://$(hostname -I | awk '{print $1}'):$GATEWAY_PORT/learning-web/"
    echo "用户端H5  http://$(hostname -I | awk '{print $1}'):$GATEWAY_PORT/learning-h5/"
    echo "---- 当前状态 ----"
    status_all
    ;;
  status)
    status_all
    ;;
  *)
    echo "Usage: $0 {start|status}" >&2
    exit 1
    ;;
esac
