#!/usr/bin/env bash
# 生成测试机发布源码：主工程 code/ + 测试机差异基线 guolian/test/project-files/code/
# 目标目录：deploy/guolian-deploy/full-deploy/source-code/code
# 规则：
#   1. 只从主工程同步源码，测试机配置/门户页由差异基线覆盖；
#   2. 本机专用内容（local profile、Mock、本地门户、本地脚本、工具目录）不进测试机源码；
#   3. 生成后自动校验测试机配置与单点登录为真实公服链路，校验不通过直接失败。
# 用法：./sync-test-source.sh
set -euo pipefail

CODE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$(cd "$CODE_DIR/.." && pwd)"
OVERLAY="$ROOT/guolian/test/project-files/code"
CONFIG="$ROOT/guolian/test/project-files/config/application-polardb.yml"
DEST="${DEST:-$ROOT/deploy/guolian-deploy/full-deploy/source-code/code}"

APP_YML="$DEST/exam-server/src/main/resources/application.yml"
SSO_PAGE="$DEST/sso-portal/index.html"
DEST_CONFIG="$DEST/config/application-polardb.yml"

[ -d "$ROOT/code" ] || { echo "未找到主工程 $ROOT/code"; exit 1; }
[ -d "$OVERLAY" ] || { echo "未找到测试机差异基线 $OVERLAY"; exit 1; }
[ -f "$CONFIG" ] || { echo "未找到测试机外置配置 $CONFIG"; exit 1; }

echo "主工程     : $CODE_DIR"
echo "差异基线   : $OVERLAY"
echo "输出目录   : $DEST"
mkdir -p "$DEST"

echo "==> 同步主工程源码"
rsync -a --delete \
  --exclude '/target/' \
  --exclude '/node_modules/' \
  --exclude '/dist/' \
  --exclude '/logs/' \
  --exclude '/run/' \
  --exclude '/tools/' \
  --exclude '/local-portal/' \
  --exclude '/scripts/local_gateway.py' \
  --exclude '/scripts/start_all_local.sh' \
  --exclude '/scripts/stop_all_local.sh' \
  --exclude '/exam-integration/src/main/java/com/group/exam/integration/portal/' \
  --exclude '/exam-integration/src/main/java/com/group/exam/integration/client/MockGuolianTicketClient.java' \
  --exclude '/exam-server/src/main/resources/db/data-local-portal-accounts-mysql.sql' \
  "$CODE_DIR/" "$DEST/"

echo "==> 清理本机专用内容与编译产物"
rm -rf "$DEST/tools" "$DEST/local-portal" \
  "$DEST/exam-integration/src/main/java/com/group/exam/integration/portal" \
  "$DEST/exam-integration/src/main/java/com/group/exam/integration/client/MockGuolianTicketClient.java" \
  "$DEST/exam-server/src/main/resources/db/data-local-portal-accounts-mysql.sql" \
  "$DEST/scripts/local_gateway.py" "$DEST/scripts/start_all_local.sh" "$DEST/scripts/stop_all_local.sh"
find "$DEST" -type d \( -name target -o -name node_modules -o -name dist -o -name logs -o -name run \) \
  -prune -exec rm -rf {} +

echo "==> 覆盖测试机差异文件"
rsync -a "$OVERLAY/" "$DEST/"

echo "==> 写入测试机外置配置"
mkdir -p "$DEST/config"
cp "$CONFIG" "$DEST_CONFIG"

echo "==> 校验"
ERRORS=0
ok()   { echo "  [ OK ] $*"; }
fail() { echo "  [FAIL] $*"; ERRORS=$((ERRORS + 1)); }

grep -q '^[[:space:]]*active:[[:space:]]*local' "$APP_YML" \
  && fail "application.yml 仍激活 local profile" \
  || ok "未激活 local profile（不启用 Mock 单点登录）"

grep -q '10.64.16.14:4886' "$APP_YML" \
  && ok "数据库指向测试机 10.64.16.14:4886" \
  || fail "数据库地址不是测试机 10.64.16.14:4886"

grep -q 'host:[[:space:]]*10.64.16.29' "$APP_YML" \
  && ok "Redis 指向测试机 10.64.16.29" \
  || fail "Redis 地址不是测试机 10.64.16.29"

grep -q '10.10.250.100:8333' "$APP_YML" \
  && ok "对象存储指向 OSS 10.10.250.100:8333" \
  || fail "对象存储不是 OSS 10.10.250.100:8333"

grep -q '10.64.251.169:4000' "$APP_YML" \
  && ok "AI 生题指向内网网关 10.64.251.169:4000" \
  || fail "AI 生题未指向内网网关 10.64.251.169:4000"

grep -q '10.20.8.8:32389' "$APP_YML" && grep -q 'client-id:[[:space:]]*guolian-jijian' "$APP_YML" \
  && ok "国联公服接入配置存在（10.20.8.8:32389 / guolian-jijian）" \
  || fail "缺少国联公服接入配置"

[ -f "$SSO_PAGE" ] && grep -q 'integration/ticket/login' "$SSO_PAGE" && ! grep -q 'api/v1/portal' "$SSO_PAGE" \
  && ok "门户单点登录页走真实公服接口" \
  || fail "门户单点登录页缺失或仍走本地 Mock 接口"

[ ! -e "$DEST/local-portal" ] \
  && ok "未包含本机模拟门户 local-portal/" \
  || fail "仍包含本机模拟门户 local-portal/"

[ ! -e "$DEST/exam-integration/src/main/java/com/group/exam/integration/client/MockGuolianTicketClient.java" ] \
  && ok "未包含 MockGuolianTicketClient" \
  || fail "仍包含 MockGuolianTicketClient"

[ ! -d "$DEST/exam-integration/src/main/java/com/group/exam/integration/portal" ] \
  && ok "未包含本地门户 portal/ 包" \
  || fail "仍包含本地门户 portal/ 包"

[ -d "$DEST/exam-workflow" ] \
  && ok "已包含 exam-workflow 模块" \
  || fail "缺少 exam-workflow 模块"

diff -q "$CONFIG" "$DEST_CONFIG" >/dev/null \
  && ok "外置配置与测试机基线一致" \
  || fail "外置配置与测试机基线不一致"

if find "$DEST" -type d \( -name target -o -name node_modules -o -name dist \) | grep -q .; then
  fail "包含编译产物或依赖目录（target/node_modules/dist）"
else
  ok "不含编译产物与依赖目录"
fi

echo
if [ "$ERRORS" -ne 0 ]; then
  echo "校验失败：$ERRORS 项，未通过"
  exit 1
fi
echo "校验通过：测试机源码已生成到 $DEST"
