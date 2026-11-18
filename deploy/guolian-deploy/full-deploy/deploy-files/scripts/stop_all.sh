#!/usr/bin/env bash
# AIExamK3 目标机一键停止：后端 / Redis / 本机 MinIO 兜底进程（对象存储为远端 OSS）
# 不处理 InforSuiteLB 与数据库；LB 配置重载用 start_all.sh reload-lb
set -u

RUN_DIR="/root/AIExamK3/run"

for f in "$RUN_DIR"/*.pid; do
  [ -f "$f" ] || continue
  pid=$(cat "$f")
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null && echo "stopped $f (pid $pid)" || echo "failed to stop $f"
  else
    echo "already dead $f"
  fi
  rm -f "$f"
done

echo "后端 / Redis / 本机 MinIO 兜底进程已停止。InforSuiteLB、数据库与远端 OSS 不受影响。"
