#!/usr/bin/env bash
# 在线考试系统 一键启动脚本：后端(8088) + PC管理端(6000/6009) + H5移动端(6001) + PC用户端(6002)
#   + 原生小程序预览(6003) + H5套壳小程序预览(6004)
set -u
CODE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$CODE_DIR/logs"
PID_DIR="$CODE_DIR/run"
mkdir -p "$LOG_DIR" "$PID_DIR"

start_backend() {
  local jar
  jar=$(ls "$CODE_DIR"/exam-server/target/exam-server*.jar 2>/dev/null | grep -v sources | grep -v original | head -1)
  if [ -z "$jar" ]; then echo "[backend] 未找到 jar，请先 mvn package"; return 1; fi
  if [ -f "$PID_DIR/backend.pid" ] && kill -0 "$(cat "$PID_DIR/backend.pid")" 2>/dev/null; then
    echo "[backend] 已在运行 (pid $(cat "$PID_DIR/backend.pid"))"; return 0
  fi
  nohup java -jar "$jar" > "$LOG_DIR/exam-server.log" 2>&1 &
  echo $! > "$PID_DIR/backend.pid"
  echo "[backend] 启动中 pid $(cat "$PID_DIR/backend.pid")，日志 $LOG_DIR/exam-server.log"
}

start_front() {
  local name="$1" dir="$2" port="$3"
  if [ -f "$PID_DIR/$name.pid" ] && kill -0 "$(cat "$PID_DIR/$name.pid")" 2>/dev/null; then
    echo "[$name] 已在运行 (pid $(cat "$PID_DIR/$name.pid"))"; return 0
  fi
  if [ ! -d "$dir/node_modules" ]; then
    echo "[$name] 安装依赖..."; (cd "$dir" && npm install --no-audit --no-fund >> "$LOG_DIR/$name.log" 2>&1) || { echo "[$name] npm install 失败"; return 1; }
  fi
  (cd "$dir" && nohup npm run dev -- --host 0.0.0.0 --port "$port" --strictPort > "$LOG_DIR/$name.log" 2>&1 & echo $! > "$PID_DIR/$name.pid")
  echo "[$name] 启动中 pid $(cat "$PID_DIR/$name.pid")，端口 $port，日志 $LOG_DIR/$name.log"
}

start_static() {
  local name="$1" dir="$2" port="$3"
  if [ -f "$PID_DIR/$name.pid" ] && kill -0 "$(cat "$PID_DIR/$name.pid")" 2>/dev/null; then
    echo "[$name] 已在运行 (pid $(cat "$PID_DIR/$name.pid"))"; return 0
  fi
  nohup python3 -m http.server "$port" --bind 0.0.0.0 --directory "$dir" > "$LOG_DIR/$name.log" 2>&1 &
  echo $! > "$PID_DIR/$name.pid"
  echo "[$name] 启动中 pid $(cat "$PID_DIR/$name.pid")，端口 $port，目录 $dir"
}

case "${1:-start}" in
  start)
    start_backend
    start_front admin-web "$CODE_DIR/exam-admin-web" 6000
    # 上游网络拦截了 6000 端口（X11 常用端口），加开 6009 作为管理端外部访问入口
    start_front admin-web-6009 "$CODE_DIR/exam-admin-web" 6009
    start_front h5-web "$CODE_DIR/exam-h5-web" 6001
    start_front user-web "$CODE_DIR/exam-user-web" 6002
    # 小程序版本（静态预览页；原生代码需微信开发者工具导入对应项目目录）
    [ -d "$CODE_DIR/exam-miniprogram/preview" ] && start_static miniprogram "$CODE_DIR/exam-miniprogram/preview" 6003
    [ -d "$CODE_DIR/exam-h5miniprogram/preview" ] && start_static h5miniprogram "$CODE_DIR/exam-h5miniprogram/preview" 6004
    ;;
  stop)
    for f in "$PID_DIR"/*.pid; do
      [ -f "$f" ] && kill "$(cat "$f")" 2>/dev/null && rm -f "$f" && echo "stopped $f"
    done
    # npm 包装进程退出后 vite 子进程可能残留，按模块路径补杀
    pkill -f 'exam-admin-web/node_modules' 2>/dev/null
    pkill -f 'exam-h5-web/node_modules' 2>/dev/null
    pkill -f 'exam-user-web/node_modules' 2>/dev/null
    pkill -f 'exam-server/target' 2>/dev/null
    ;;
  status)
    for f in "$PID_DIR"/*.pid; do
      [ -f "$f" ] && { kill -0 "$(cat "$f")" 2>/dev/null && echo "$f RUNNING ($(cat "$f"))" || echo "$f DEAD"; }
    done
    ;;
esac
