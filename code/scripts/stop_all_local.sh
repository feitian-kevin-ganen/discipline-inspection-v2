#!/usr/bin/env bash
# AIExamK3 本机一键停止：只停本目录 run/ 下记录的进程，不影响其他项目
set -u

APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RUN_DIR="$APP_DIR/run"

for name in gateway backend; do
  f="$RUN_DIR/$name.pid"
  if [ -f "$f" ]; then
    pid=$(cat "$f")
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null
      for _ in 1 2 3 4 5 6 7 8 9 10; do
        kill -0 "$pid" 2>/dev/null || break
        sleep 0.5
      done
      kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null
      echo "[$name] 已停止 (pid $pid)"
    else
      echo "[$name] 未在运行 (stale pid $pid)"
    fi
    rm -f "$f"
  else
    echo "[$name] 无 pid 文件，跳过"
  fi
done
