#!/usr/bin/env bash
# AIExamK3 目标机一键启动脚本
#   后端：8088（jar + config/application-polardb.yml）
#   Redis / MinIO：从配置读取地址，本机才启动，远端只做连通性检查
#   反向代理：目标机为 InforSuiteLB（/data/InforSuiteLB），不是 Nginx
# 用法：
#   ./start_all.sh            启动 Redis/MinIO/后端，并打印状态
#   ./start_all.sh status     只看状态
#   ./start_all.sh reload-lb  检查并重载 InforSuiteLB 配置
set -u

APP_DIR="/root/AIExamK3"
CONFIG="$APP_DIR/config/application-polardb.yml"
LOG_DIR="$APP_DIR/logs"
RUN_DIR="$APP_DIR/run"
JDK="$APP_DIR/middleware/jdk21/bin/java"
LB_HOME="${LB_HOME:-/data/InforSuiteLB}"
LB_BIN="$LB_HOME/sbin/InforSuiteLB"
LB_CONF="$LB_HOME/conf/InforSuiteLB.conf"

mkdir -p "$LOG_DIR" "$RUN_DIR"

if [ ! -f "$CONFIG" ]; then
  echo "[fatal] 找不到配置 $CONFIG" >&2
  exit 1
fi

# ---------------- 配置读取 ----------------
yml_value() {  # yml_value <块起始正则> <字段名>
  grep -A12 "$1" "$CONFIG" 2>/dev/null \
    | grep -m1 -E "^[[:space:]]*$2:" \
    | sed "s/^[^:]*:[[:space:]]*//; s/^['\"]//; s/['\"]$//"
}

REDIS_HOST=$(yml_value '^    redis:' host)
REDIS_PORT=$(yml_value '^    redis:' port)
REDIS_PASS=$(yml_value '^    redis:' password)
MINIO_EP=$(yml_value '^  minio:' endpoint)
SERVER_PORT=$(yml_value '^server:' port)

# ---------------- 小工具 ----------------
tcp_open() {
  timeout 2 bash -c "exec 3<>/dev/tcp/$1/$2" 2>/dev/null
}

is_local_host() {
  case "$1" in
    127.0.0.1|localhost|::1) return 0 ;;
  esac
  ip -4 -o addr show 2>/dev/null | awk '{print $4}' | cut -d/ -f1 | grep -qx "$1"
}

pid_alive() {
  [ -f "$1" ] && kill -0 "$(cat "$1")" 2>/dev/null
}

# ---------------- Redis ----------------
start_redis() {
  if [ -z "${REDIS_HOST:-}" ]; then
    echo "[redis] 配置未读到 host，跳过"
    return 0
  fi
  if tcp_open "$REDIS_HOST" "$REDIS_PORT"; then
    echo "[redis] $REDIS_HOST:$REDIS_PORT 已可用"
    return 0
  fi
  if ! is_local_host "$REDIS_HOST"; then
    echo "[redis] 配置指向远端 $REDIS_HOST:$REDIS_PORT 且不可达，本机不启动，请检查远端实例"
    return 0
  fi
  if pid_alive "$RUN_DIR/redis.pid"; then
    echo "[redis] 已在运行 (pid $(cat "$RUN_DIR/redis.pid"))"
    return 0
  fi
  local bin="/usr/bin/redis-server"
  if [ ! -x "$bin" ]; then
    echo "[redis] 未找到 $bin，请先安装 redis-server"
    return 1
  fi
  mkdir -p "$APP_DIR/data/redis"
  local args=(--port "$REDIS_PORT" --bind 0.0.0.0 --dir "$APP_DIR/data/redis" --appendonly yes --daemonize no)
  [ -n "${REDIS_PASS:-}" ] && args+=(--requirepass "$REDIS_PASS")
  nohup "$bin" "${args[@]}" > "$LOG_DIR/redis.log" 2>&1 &
  echo $! > "$RUN_DIR/redis.pid"
  echo "[redis] 启动中 pid $(cat "$RUN_DIR/redis.pid")，端口 $REDIS_PORT，日志 $LOG_DIR/redis.log"
}

# ---------------- MinIO ----------------
start_minio() {
  if [ -z "${MINIO_EP:-}" ]; then
    echo "[minio] 配置未读到 endpoint，跳过"
    return 0
  fi
  local hp="${MINIO_EP#*://}"
  local host="${hp%%:*}"
  local port="${hp##*:}"
  [ "$port" = "$hp" ] && port=9000

  if tcp_open "$host" "$port"; then
    echo "[minio] 对象存储 $MINIO_EP 已可用"
    return 0
  fi

  if ! is_local_host "$host"; then
    echo "[minio] 配置指向远端 $MINIO_EP 且不可达，本机不启动。"
    echo "[minio] 请检查网络/负载；确实需要本机兜底时执行：MINIO_LOCAL=1 $0 start"
    [ "${MINIO_LOCAL:-0}" = "1" ] || return 0
    port=9000
  fi

  if pid_alive "$RUN_DIR/minio.pid"; then
    echo "[minio] 已在运行 (pid $(cat "$RUN_DIR/minio.pid"))"
    return 0
  fi
  local bin="$APP_DIR/middleware/minio"
  if [ ! -x "$bin" ]; then
    echo "[minio] 未找到 $bin，跳过"
    return 1
  fi
  local ak sk
  ak=$(yml_value '^  minio:' access-key)
  sk=$(yml_value '^  minio:' secret-key)
  [ -n "$ak" ] && export MINIO_ROOT_USER="$ak"
  [ -n "$sk" ] && export MINIO_ROOT_PASSWORD="$sk"
  mkdir -p "$APP_DIR/data/minio"
  nohup "$bin" server "$APP_DIR/data/minio" --address ":$port" --console-address ":$((port + 1))" \
    > "$LOG_DIR/minio.log" 2>&1 &
  echo $! > "$RUN_DIR/minio.pid"
  echo "[minio] 启动中 pid $(cat "$RUN_DIR/minio.pid")，端口 $port，日志 $LOG_DIR/minio.log"
}

# ---------------- 后端 ----------------
start_backend() {
  local jar="$APP_DIR/app/exam-server.jar"
  if [ ! -f "$jar" ]; then
    echo "[backend] 未找到 $jar" >&2
    return 1
  fi
  if pid_alive "$RUN_DIR/backend.pid"; then
    echo "[backend] 已在运行 (pid $(cat "$RUN_DIR/backend.pid"))"
    return 0
  fi
  if [ ! -x "$JDK" ]; then
    echo "[backend] 未找到 JDK：$JDK" >&2
    return 1
  fi
  nohup "$JDK" -jar "$jar" \
    --spring.profiles.active=polardb \
    --spring.config.additional-location="file:$APP_DIR/config/" \
    > "$LOG_DIR/exam-server.log" 2>&1 &
  echo $! > "$RUN_DIR/backend.pid"
  echo "[backend] 启动中 pid $(cat "$RUN_DIR/backend.pid")，端口 ${SERVER_PORT:-8088}，日志 $LOG_DIR/exam-server.log"
}

# ---------------- InforSuiteLB ----------------
lb_running() {
  pgrep -f "sbin/InforSuiteLB" >/dev/null 2>&1
}

status_lb() {
  if [ ! -x "$LB_BIN" ]; then
    echo "[lb] 未安装 InforSuiteLB（$LB_HOME 不存在），跳过"
    return 0
  fi
  if lb_running; then
    echo "[lb] InforSuiteLB RUNNING (pid $(pgrep -f 'sbin/InforSuiteLB' | head -1))"
  else
    echo "[lb] InforSuiteLB STOPPED"
  fi
}

start_lb() {
  if [ ! -x "$LB_BIN" ]; then
    echo "[lb] 未找到 $LB_BIN，跳过（反向代理可能由运维统一托管）"
    return 0
  fi
  if lb_running; then
    echo "[lb] 已在运行"
    return 0
  fi
  "$LB_BIN" -p "$LB_HOME" -c "$LB_CONF" start && echo "[lb] 已启动"
}

reload_lb() {
  if [ ! -x "$LB_BIN" ]; then
    echo "[lb] 未找到 $LB_BIN" >&2
    exit 1
  fi
  "$LB_BIN" -p "$LB_HOME" -c "$LB_CONF" -t || { echo "[lb] 配置检查失败，未重载" >&2; exit 1; }
  "$LB_BIN" -p "$LB_HOME" -c "$LB_CONF" -s reload && echo "[lb] 配置已重载"
}

# ---------------- 状态 ----------------
status_all() {
  for f in "$RUN_DIR"/*.pid; do
    [ -f "$f" ] || continue
    if pid_alive "$f"; then
      echo "$(basename "$f" .pid) RUNNING (pid $(cat "$f"))"
    else
      echo "$(basename "$f" .pid) DEAD (stale $f)"
    fi
  done
  status_lb
}

case "${1:-start}" in
  start)
    start_redis
    start_minio
    start_backend
    start_lb
    echo "---- 当前状态 ----"
    status_all
    ;;
  status)
    status_all
    ;;
  reload-lb)
    reload_lb
    ;;
  *)
    echo "Usage: $0 {start|status|reload-lb}" >&2
    exit 1
    ;;
esac
