#!/usr/bin/env bash
# 发布脚本：把全量包解压目录（staging）发布到在运目录（live）
#
#   全量包解压目录 SRC : /root/fully-deploy/AIExamK3
#   在运目录       APP : /root/AIExamK3
#   负载均衡       LB  : /data/InforSuiteLB （同级的 /data/rds-install 是数据库安装目录，本脚本不碰）
#
# 用法（在测试机 root 下执行）：
#   bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh check      # 发布前检查，不落盘
#   bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh backup     # 备份在运目录（库+包+对象存储本地副本+配置）
#   bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh apply      # 把 staging 同步到 live
#   bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh lb         # 校正 LB 站点配置文件名并测试重载
#   bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh restart    # 停止并重新启动应用进程
#   bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh verify     # 统一入口三端 + SSO + 后端验证
#   bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh all        # check→backup→apply→lb→restart→verify
#
# 入口模型：三端前端 + /api 收敛在统一入口（默认 6003，按路径前缀路由）；
#   6001 是保留的兼容段（/ → /learning-h5/ 跳转、/guolian-jijian/login 单点登录页）；
#   80 是中创 LB 管控平台，与业务无关。
#
# 选项：
#   --yes            all/apply 等步骤不再交互确认
#   --dry-run        只打印要做什么，不改动文件、不重启进程
#   --with-runtime   同时同步 middleware/ 与 data/（仅首次安装或中间件有升级时使用）
set -uo pipefail

APP_DIR="${APP_DIR:-/root/AIExamK3}"
SRC_DIR="${SRC_DIR:-/root/fully-deploy/AIExamK3}"

LB_HOME="${LB_HOME:-/data/InforSuiteLB}"
LB_BIN="$LB_HOME/sbin/InforSuiteLB"
LB_MAIN="$LB_HOME/conf/InforSuiteLB.conf"
LB_SITE_NEW="$LB_HOME/conf/discipline-inspection.conf"
LB_SITE_OLD="$LB_HOME/conf/cra.conf"

# 三端统一入口：6003（按路径前缀路由）；探测不通时回退 6001 兼容段
PORT_ENTRY="${PORT_ENTRY:-6003}"
PORT_ENTRY_ALT="${PORT_ENTRY_ALT:-6001}"
PORT_API="${PORT_API:-8088}"

ACTION=""
ASSUME_YES=0
DRY_RUN=0
WITH_RUNTIME=0

while [ $# -gt 0 ]; do
  case "$1" in
    --yes|-y)       ASSUME_YES=1 ;;
    --dry-run|-n)   DRY_RUN=1 ;;
    --with-runtime) WITH_RUNTIME=1 ;;
    -h|--help)      ACTION="help" ;;
    *)              ACTION="${ACTION:-$1}" ;;
  esac
  shift
done
ACTION="${ACTION:-help}"

say()  { printf '%s\n' "$*"; }
info() { printf '[info] %s\n' "$*"; }
warn() { printf '[warn] %s\n' "$*" >&2; }
err()  { printf '[error] %s\n' "$*" >&2; }
die()  { err "$*"; exit 1; }

run() {
  if [ "$DRY_RUN" = "1" ]; then
    printf '[dry-run] %s\n' "$*"
    return 0
  fi
  "$@"
}

confirm() {
  [ "$ASSUME_YES" = "1" ] && return 0
  [ "$DRY_RUN" = "1" ] && return 0
  printf '%s [y/N] ' "$1"
  read -r ans
  case "$ans" in y|Y|yes|YES) return 0 ;; *) return 1 ;; esac
}

# ---------------------------------------------------------------- 检查
check_paths() {
  [ -d "$SRC_DIR" ] || die "找不到全量包解压目录：$SRC_DIR"
  [ -d "$APP_DIR" ] || die "找不到在运目录：$APP_DIR"
  local s a
  s=$(cd "$SRC_DIR" && pwd); a=$(cd "$APP_DIR" && pwd)
  [ "$s" = "$a" ] && die "staging 与 live 是同一个目录（$s），请检查 SRC_DIR/APP_DIR"
  return 0
}

check_staging() {
  local bad=0
  [ -f "$SRC_DIR/app/exam-server.jar" ] || { warn "缺少 app/exam-server.jar"; bad=1; }
  local pair d base html
  for pair in admin:/mgmt/ user:/learning-web/ h5:/learning-h5/; do
    d="${pair%%:*}"; base="${pair##*:}"
    html="$SRC_DIR/web/$d/index.html"
    if [ ! -f "$html" ]; then
      warn "缺少 web/$d/index.html"; bad=1; continue
    fi
    if grep -q "$base" "$html"; then
      info "web/$d base 正确：$base"
    else
      warn "web/$d 的 index.html 未使用 $base（会白屏）：$(grep -oE 'src="[^"]*"' "$html" | head -1)"
      bad=1
    fi
  done
  [ -f "$SRC_DIR/config/application-polardb.yml" ] || { warn "缺少 config/application-polardb.yml"; bad=1; }
  [ -f "$SRC_DIR/scripts/start_all.sh" ] || { warn "缺少 scripts/start_all.sh"; bad=1; }
  [ -f "$SRC_DIR/scripts/deploy_release.sh" ] || warn "staging 里没有 deploy_release.sh（不影响发布，脚本可从本包运行）"
  [ -f "$SRC_DIR/web/sso/index.html" ] || info "包内无 web/sso/index.html（SSO 页不由三端构建产出，apply 会保留在运版本）"
  return $bad
}

check_live() {
  local f
  info "在运目录进程状态（pid 文件）："
  for f in "$APP_DIR"/run/*.pid; do
    [ -f "$f" ] || continue
    if kill -0 "$(cat "$f")" 2>/dev/null; then
      info "  $(basename "$f" .pid) RUNNING (pid $(cat "$f"))"
    else
      info "  $(basename "$f" .pid) DEAD (stale)"
    fi
  done
  [ -d "$APP_DIR/run" ] || warn "在运目录没有 run/（可能尚未启动过）"
  if [ -f "$APP_DIR/web/sso/index.html" ]; then
    info "SSO 入口页存在：web/sso/index.html"
  else
    warn "在运目录缺 web/sso/index.html（/guolian-jijian/login 会 404）"
  fi
  df -h "$APP_DIR" | tail -1 | awk '{printf "[info] 磁盘可用 %s / %s\n", $4, $2}'
}

check_lb() {
  if [ -x "$LB_BIN" ]; then
    info "InforSuiteLB 可执行：$LB_BIN"
  else
    warn "未找到 $LB_BIN（LB 是否装在 $LB_HOME？）"
  fi
  [ -f "$LB_MAIN" ] && info "LB 主配置：$LB_MAIN" || warn "未找到 LB 主配置 $LB_MAIN"
  if [ -f "$LB_SITE_NEW" ]; then
    info "LB 站点配置：$LB_SITE_NEW"
  elif [ -f "$LB_SITE_OLD" ]; then
    warn "LB 站点配置仍是旧名：$LB_SITE_OLD（执行 apply 后跑一次 lb 子命令即可改名）"
  else
    warn "未找到 LB 站点配置（$LB_SITE_NEW / $LB_SITE_OLD）"
  fi
}

check_config_diff() {
  local s="$SRC_DIR/config/application-polardb.yml" a="$APP_DIR/config/application-polardb.yml"
  if [ ! -f "$s" ]; then warn "staging 无配置"; return 0; fi
  if [ ! -f "$a" ]; then warn "live 无配置，apply 时会被写入"; return 0; fi
  if cmp -s "$s" "$a"; then
    info "配置一致：config/application-polardb.yml"
  else
    warn "配置不一致（apply 不会覆盖 live 配置，只生成 .new 供比对）："
    diff -u "$a" "$s" 2>&1 | sed -n '1,40p' >&2 || true
  fi
}

cmd_check() {
  say "================ 发布前检查 ================"
  say "staging : $SRC_DIR"
  say "live    : $APP_DIR"
  say "LB home : $LB_HOME"
  check_paths
  local bad=0
  check_staging || bad=1
  check_config_diff
  check_live
  check_lb
  if [ "$bad" = "1" ]; then
    err "检查未通过：请先修正上面的告警，再执行 apply"
    return 1
  fi
  info "检查通过"
}

# ---------------------------------------------------------------- 备份
cmd_backup() {
  say "================ 备份在运目录 ================"
  local s="$APP_DIR/scripts/backup.sh"
  if [ -f "$s" ]; then
    info "调用 $s"
    run bash "$s" || die "备份失败，终止发布"
  else
    local t
    t="/root/AIExamK3-backup/$(date +%Y%m%d_%H%M%S)"
    warn "live 没有 backup.sh，退化为目录打包：$t"
    run mkdir -p "$t"
    run tar -czf "$t/app-package.tar.gz" -C "$APP_DIR" app config web scripts middleware README.md 2>/dev/null
    [ -d "$APP_DIR/data/minio/exam-files" ] && run tar -czf "$t/minio-data.tar.gz" -C "$APP_DIR/data/minio" exam-files
    info "已打包到 $t（数据库请用 $APP_DIR/scripts/backup.sh 或 mysql.sh 单独导出）"
  fi
}

# ---------------------------------------------------------------- 同步
has_rsync() { command -v rsync >/dev/null 2>&1; }

sync_dir() {  # sync_dir <相对路径> <是否删除多余文件 0|1> [rsync 排除模式]
  local rel="$1" del="${2:-0}" excl="${3:-}"
  local s="$SRC_DIR/$rel" d="$APP_DIR/$rel"
  [ -e "$s" ] || return 0
  run mkdir -p "$d"
  if has_rsync; then
    local opts=(-a)
    [ "$del" = "1" ] && opts+=(--delete)
    [ -n "$excl" ] && opts+=("--exclude=$excl")
    run rsync "${opts[@]}" "$s"/ "$d"/
  else
    # 无 rsync 时用「移出→清空→回填」模拟 --exclude，避免误删被排除的目录
    local keep=""
    if [ -n "$excl" ]; then
      local name="${excl#/}"; name="${name%/}"
      if [ -d "$d/$name" ]; then
        keep=$(mktemp -d)
        run cp -a "$d/$name" "$keep/$name"
      fi
    fi
    [ "$del" = "1" ] && { info "无 rsync，先清空 $rel"; run rm -rf "${d:?}"; run mkdir -p "$d"; }
    run cp -a "$s"/. "$d"/
    if [ -n "$keep" ]; then
      run cp -a "$keep/." "$d"/ 2>/dev/null || true
      rm -rf "$keep"
      local kn="${excl#/}"; kn="${kn%/}"
      info "已保留 $rel/$kn"
    fi
  fi
  info "已同步 $rel"
}

cmd_apply() {
  say "================ 同步 staging → live ================"
  check_paths
  confirm "将把 $SRC_DIR 发布到 $APP_DIR，继续？" || die "已取消"

  run mkdir -p "$APP_DIR"
  # 运行数据与中间件默认不动；首次安装时用 --with-runtime
  if [ "$WITH_RUNTIME" = "1" ]; then
    sync_dir middleware 0
    sync_dir data 0
  else
    info "跳过 middleware/ 与 data/（需要时加 --with-runtime）"
  fi

  sync_dir app 0
  sync_dir web 1 "/sso/"   # --delete 清旧 hash 静态文件，但保留 LB alias 的 web/sso
  sync_dir scripts 1        # --delete：同时清掉已废弃的 nginx-discipline-inspection.conf
  sync_dir docs 0

  # SSO 入口页由 LB 直接 alias，不由三端构建产出，包内通常没有
  if [ -f "$SRC_DIR/web/sso/index.html" ]; then
    info "已同步包内 web/sso/index.html"
  elif [ -f "$APP_DIR/web/sso/index.html" ]; then
    info "包内无 web/sso，已保留在运的 web/sso/index.html"
  else
    warn "web/sso/index.html 在包内与在运目录都不存在：/guolian-jijian/login 会 404"
    warn "  需要从门户侧获取该页面后放到 $APP_DIR/web/sso/index.html"
  fi
  if [ -f "$SRC_DIR/README.md" ]; then
    run cp -p "$SRC_DIR/README.md" "$APP_DIR/README.md"
    info "已更新 README.md"
  fi

  # 配置：不覆盖在运配置，避免把现场调整冲掉
  local sc="$SRC_DIR/config/application-polardb.yml" ac="$APP_DIR/config/application-polardb.yml"
  if [ -f "$sc" ]; then
    run mkdir -p "$APP_DIR/config"
    if [ ! -f "$ac" ]; then
      run cp -p "$sc" "$ac"; info "live 无配置，已写入 config/application-polardb.yml"
    elif cmp -s "$sc" "$ac"; then
      info "配置一致，未改动 config/application-polardb.yml"
    else
      run cp -p "$sc" "$ac.new"
      warn "配置不一致：已生成 config/application-polardb.yml.new（未覆盖在运配置）"
      warn "差异见上方 check 输出；确认后手工执行："
      warn "  cp $ac.new $ac"
    fi
  fi

  if [ "$DRY_RUN" = "0" ]; then
    chmod +x "$APP_DIR"/scripts/*.sh 2>/dev/null || true
    [ -d "$APP_DIR/web" ] && chmod -R a+rX "$APP_DIR/web" 2>/dev/null || true
    info "已设置脚本可执行位、web 目录读权限"
  fi
}

# ---------------------------------------------------------------- LB
cmd_lb() {
  say "================ InforSuiteLB ================"
  if [ ! -x "$LB_BIN" ]; then
    err "未找到 $LB_BIN，跳过（LB 可能由运维统一管理）"
    return 1
  fi
  if [ -f "$LB_SITE_OLD" ] && [ ! -f "$LB_SITE_NEW" ]; then
    info "站点配置改名为 discipline-inspection.conf"
    run mv "$LB_SITE_OLD" "$LB_SITE_NEW"
    if grep -rn "cra\.conf" "$LB_HOME/conf/" 2>/dev/null | grep -v '\.bak'; then
      err "主配置仍引用 cra.conf（见上），请先把 include 改为 discipline-inspection.conf，尚未重载"
      return 1
    fi
  elif [ -f "$LB_SITE_NEW" ]; then
    info "站点配置已是 discipline-inspection.conf"
  else
    warn "未找到站点配置，仅测试主配置"
  fi

  info "测试配置：$LB_BIN -p $LB_HOME -c $LB_MAIN -t"
  run "$LB_BIN" -p "$LB_HOME" -c "$LB_MAIN" -t || { err "配置测试失败，未重载"; return 1; }
  info "重载配置：$LB_BIN -p $LB_HOME -c $LB_MAIN -s reload"
  run "$LB_BIN" -p "$LB_HOME" -c "$LB_MAIN" -s reload || { err "重载失败"; return 1; }
  info "LB 已重载"
}

# ---------------------------------------------------------------- 重启
cmd_restart() {
  say "================ 重启应用进程 ================"
  [ -f "$APP_DIR/scripts/stop_all.sh" ] || die "缺少 $APP_DIR/scripts/stop_all.sh"
  [ -f "$APP_DIR/scripts/start_all.sh" ] || die "缺少 $APP_DIR/scripts/start_all.sh"
  confirm "将停止并重启 $APP_DIR 下的后端/Redis（对象存储为远端 OSS，LB 与数据库不动），继续？" || die "已取消"
  run bash "$APP_DIR/scripts/stop_all.sh"
  sleep 2
  run bash "$APP_DIR/scripts/start_all.sh"
}

# ---------------------------------------------------------------- 验证
probe() {  # probe <名称> <url>
  local name="$1" url="$2" out code ctype
  if ! command -v curl >/dev/null 2>&1; then
    warn "无 curl，跳过 $name"
    return 0
  fi
  out=$(curl -s -o /dev/null -m 5 -w '%{http_code} %{content_type}' "$url" 2>/dev/null) || out="000 -"
  code="${out%% *}"; ctype="${out#* }"
  if [ "$code" = "200" ] && printf '%s' "$ctype" | grep -qi 'text/html'; then
    info "$name OK  $url → $code $ctype"
  else
    warn "$name 异常  $url → $code $ctype"
    return 1
  fi
}

check_base_in_page() {  # check_base_in_page <名称> <url> <期望base>
  local name="$1" url="$2" base="$3" hit
  command -v curl >/dev/null 2>&1 || return 0
  hit=$(curl -s -m 5 "$url" 2>/dev/null | grep -oE '(src|href)="[^"]*"' | head -1)
  case "$hit" in
    *"$base"*) info "$name 首页引用带 base：$hit" ;;
    *)         warn "$name 首页引用缺少 $base：${hit:-<空>}" ;;
  esac
}

# 探测统一入口实际监听的端口：先 6003，不通再试 6001 兼容段
detect_entry_port() {
  local p
  command -v curl >/dev/null 2>&1 || { echo "$PORT_ENTRY"; return 0; }
  for p in "$PORT_ENTRY" "$PORT_ENTRY_ALT"; do
    [ -n "$p" ] || continue
    if [ "$(curl -s -o /dev/null -m 3 -w '%{http_code}' "http://127.0.0.1:$p/mgmt/" 2>/dev/null)" = "200" ]; then
      echo "$p"; return 0
    fi
  done
  echo "$PORT_ENTRY"
}

cmd_verify() {
  say "================ 验证 ================"
  local bad=0 port
  port=$(detect_entry_port)
  if [ "$port" != "$PORT_ENTRY" ]; then
    warn "统一入口 $PORT_ENTRY 无响应，改用兼容段 $port 验证"
  fi
  info "统一入口端口：$port"

  probe "管理端"  "http://127.0.0.1:$port/mgmt/"          || bad=1
  probe "用户端"  "http://127.0.0.1:$port/learning-web/"  || bad=1
  probe "H5"      "http://127.0.0.1:$port/learning-h5/"   || bad=1
  check_base_in_page "管理端" "http://127.0.0.1:$port/mgmt/" "/mgmt/"
  check_base_in_page "用户端" "http://127.0.0.1:$port/learning-web/" "/learning-web/"
  check_base_in_page "H5"     "http://127.0.0.1:$port/learning-h5/" "/learning-h5/"

  # SSO 入口：LB alias 到 web/sso/index.html，缺失只告警（不影响三端）
  if command -v curl >/dev/null 2>&1; then
    local sso
    sso=$(curl -sI -m 5 -o /dev/null -w '%{http_code} %{content_type}' "http://127.0.0.1:$port/guolian-jijian/login" 2>/dev/null) || sso="000 -"
    case "$sso" in
      200*text/html*) info "SSO 入口 OK  /guolian-jijian/login → $sso" ;;
      *)              warn "SSO 入口异常  /guolian-jijian/login → $sso（检查 web/sso/index.html）" ;;
    esac
  fi

  local api_ok=0
  if command -v curl >/dev/null 2>&1; then
    local code
    code=$(curl -s -o /dev/null -m 5 -w '%{http_code}' "http://127.0.0.1:$PORT_API/api/v1/health/db" 2>/dev/null)
    if [ "$code" = "200" ]; then
      info "后端 /api/v1/health/db OK"
      api_ok=1
    else
      warn "后端 /api/v1/health/db → ${code:-000}"
      bad=1
    fi
  fi

  if [ "$api_ok" = "1" ] && [ -f "$APP_DIR/scripts/verify_api.py" ] && command -v python3 >/dev/null 2>&1; then
    info "运行 verify_api.py（可选，耗时较长）"
    run python3 "$APP_DIR/scripts/verify_api.py" --base "http://127.0.0.1:$PORT_API/api/v1" || warn "verify_api.py 未全通过，请看输出"
  fi

  # start_all.sh 内写死了 /root/AIExamK3，只有 live 就是该路径时 status 才有意义
  if [ "$APP_DIR" = "/root/AIExamK3" ] && [ -f "$APP_DIR/scripts/start_all.sh" ]; then
    run bash "$APP_DIR/scripts/start_all.sh" status
  fi

  [ "$bad" = "0" ] && info "验证通过" || { err "存在异常项，请检查上面的 [warn]"; return 1; }
}

# ---------------------------------------------------------------- 帮助 / 入口
usage() {
  cat <<'EOF'
发布脚本：staging（全量包解压目录）→ live（在运目录）

  bash deploy_release.sh check     发布前检查（不改动任何文件）
  bash deploy_release.sh backup    备份在运目录（数据库/应用包/对象存储本地副本/配置）
  bash deploy_release.sh apply     同步 app/web/scripts/docs/README，配置不覆盖
  bash deploy_release.sh lb        校正 LB 站点配置文件名并测试、重载
  bash deploy_release.sh restart   停止并重启后端/Redis（LB 与数据库不动）
  bash deploy_release.sh verify    三端入口 + 首页 base + 后端健康检查
  bash deploy_release.sh all       check → backup → apply → lb → restart → verify

选项：
  --yes            不交互确认
  --dry-run        只打印动作，不落盘、不重启
  --with-runtime   同时同步 middleware/ 与 data/（首次安装或中间件升级）

默认路径（可用环境变量覆盖）：
  SRC_DIR=/root/fully-deploy/AIExamK3
  APP_DIR=/root/AIExamK3
  LB_HOME=/data/InforSuiteLB

端口（可用环境变量覆盖）：
  PORT_ENTRY=6003        三端统一入口（/mgmt/ /learning-web/ /learning-h5/ /api/）
  PORT_ENTRY_ALT=6001    兼容段，6003 不通时用于验证
  PORT_API=8088          后端（默认直连，不经 LB）
EOF
}

case "$ACTION" in
  check)   cmd_check ;;
  backup)  cmd_backup ;;
  apply)   cmd_apply ;;
  lb)      cmd_lb ;;
  restart) cmd_restart ;;
  verify)  cmd_verify ;;
  all)
    cmd_check || exit 1
    cmd_backup || exit 1
    cmd_apply || exit 1
    cmd_lb || exit 1
    cmd_restart || exit 1
    cmd_verify || exit 1
    ;;
  help|-h|--help) usage ;;
  *) err "未知动作：$ACTION"; usage; exit 1 ;;
esac
