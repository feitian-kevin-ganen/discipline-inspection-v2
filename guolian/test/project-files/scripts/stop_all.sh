#!/bin/bash
# CRA 考试系统停止脚本
BASE=/root/AIExamK3
for name in backend minio redis; do
  if [ -f $BASE/run/$name.pid ]; then
    kill $(cat $BASE/run/$name.pid) 2>/dev/null && echo "stopped $name ($(cat $BASE/run/$name.pid))"
    rm -f $BASE/run/$name.pid
  fi
done
pkill -f "exam-server.jar" 2>/dev/null

# 停止中创 InforSuiteLB
LB_HOME=/data/InforSuiteLB
if [ -x "$LB_HOME/sbin/InforSuiteLB" ]; then
  $LB_HOME/sbin/InforSuiteLB -s stop -p $LB_HOME -c $LB_HOME/conf/InforSuiteLB.conf 2>/dev/null || true
fi

# PolarDB-X 容器默认保持运行；如需停止：
#   docker stop pxc-tryout-dn
echo done
