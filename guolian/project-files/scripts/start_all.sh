#!/bin/bash
# CRA 考试系统一键启动：PolarDB-X(docker) / Redis / MinIO / 后端 / Nginx
BASE=/root/AIExamK3
mkdir -p $BASE/logs $BASE/run $BASE/data/redis

echo "[1/5] PolarDB-X (docker container pxc-tryout-dn-0-Cand-14397) ..."
docker start pxc-tryout-dn-0-Cand-14397 2>/dev/null || echo "  PolarDB-X 容器不存在，请先执行 pxd tryout 部署"

echo "[2/5] Redis ..."
if ! pgrep -f "redis-server.*6379" >/dev/null; then
  nohup /usr/bin/redis-server --port 6379 --bind 127.0.0.1 --dir $BASE/data/redis \
      --appendonly yes --daemonize no > $BASE/logs/redis.log 2>&1 &
  echo $! > $BASE/run/redis.pid
fi

echo "[3/5] MinIO ..."
if ! pgrep -f "minio server" >/dev/null; then
  MINIO_ROOT_USER=minioadmin MINIO_ROOT_PASSWORD=minioadmin \
  nohup $BASE/middleware/minio server $BASE/data/minio \
      --address :9000 --console-address :9001 > $BASE/logs/minio.log 2>&1 &
  echo $! > $BASE/run/minio.pid
fi

echo "[4/5] 后端 exam-server (profile=polardb) ..."
if ! pgrep -f "exam-server.jar" >/dev/null; then
  nohup $BASE/middleware/jdk21/bin/java -jar $BASE/app/exam-server.jar \
      --spring.profiles.active=polardb \
      --spring.config.additional-location=file:$BASE/config/ \
      > $BASE/logs/exam-server.log 2>&1 &
  echo $! > $BASE/run/backend.pid
fi

echo "[5/5] InforSuiteLB ..."
LB_HOME=/data/InforSuiteLB
if [ -x "$LB_HOME/sbin/InforSuiteLB" ]; then
  $LB_HOME/sbin/InforSuiteLB -t -q -p $LB_HOME -c $LB_HOME/conf/InforSuiteLB.conf 2>/dev/null && {
    pgrep InforSuiteLB >/dev/null && $LB_HOME/sbin/InforSuiteLB -s reload -p $LB_HOME -c $LB_HOME/conf/InforSuiteLB.conf \
      || nohup $LB_HOME/sbin/InforSuiteLB -p $LB_HOME -c $LB_HOME/conf/InforSuiteLB.conf > $BASE/logs/infor-suite-lb.log 2>&1 &
  }
else
  echo "  InforSuiteLB 未安装到 $LB_HOME，跳过"
fi

sleep 3
echo "---- 端口状态 ----"
ss -ltnp | grep -E ":(4886|6379|9000|9001|8088|6001|6002|6009|14397)" || ss -ltn | head -20
