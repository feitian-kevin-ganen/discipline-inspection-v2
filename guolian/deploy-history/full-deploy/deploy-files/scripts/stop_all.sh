#!/usr/bin/env bash
# AIExamK3 目标机一键停止脚本（默认保留 Nginx 与 PolarDB-X 容器）
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

echo "后端 / Redis / MinIO 已停止。Nginx 与 PolarDB-X 容器保持运行。"
