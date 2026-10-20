#!/usr/bin/env bash
# ================================================================
# Autumn Gateway Dashboard — RBAC 一键自举（MySQL 8.0+）
#
# 作用：新库 / 第一次使用权限体系时，按正确顺序执行全部 RBAC 脚本，
#       执行完即可登录并使用权限管理功能（自举）。
#
# 用法：
#   # 方式 A：本机有 mysql 客户端
#   ./bootstrap-mysql.sh -h 192.168.0.102 -P 3306 -u root -p 123456 autumn-security
#
#   # 方式 B：MySQL 跑在 Docker 容器里（容器内执行，无需本机装客户端）
#   ./bootstrap-mysql.sh --docker mysql -u root -p 123456 autumn-security
#
# 幂等：所有脚本都可重复执行，跑第二次不会产生重复数据，
#       也不会覆盖用户已改过的密码（V2 的 ON DUPLICATE KEY UPDATE 故意不含 password 列）。
#
# 环境变量可覆盖：DB_HOST / DB_PORT / DB_USER / DB_PASS / DB_NAME / DOCKER_CONTAINER
# ================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RES_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"   # src/main/resources

DB_HOST="${DB_HOST:-192.168.0.102}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-123456}"
DB_NAME="${DB_NAME:-autumn-security}"
DOCKER_CONTAINER="${DOCKER_CONTAINER:-mysql}"
USE_DOCKER=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --docker) USE_DOCKER=1; DOCKER_CONTAINER="${2:-mysql}"; shift 2 ;;
    -h|--host) DB_HOST="$2"; shift 2 ;;
    -P|--port) DB_PORT="$2"; shift 2 ;;
    -u|--user) DB_USER="$2"; shift 2 ;;
    -p|--password) DB_PASS="$2"; shift 2 ;;
    -d|--database) DB_NAME="$2"; shift 2 ;;
    --help) sed -n '2,25p' "$0"; exit 0 ;;
    *) echo "未知参数: $1（用 --help 查看用法）" >&2; exit 1 ;;
  esac
done

# 按顺序执行的脚本清单
SCRIPTS=(
  "${RES_DIR}/schema-basic-mysql.sql"
  "${SCRIPT_DIR}/V1__rbac_schema_mysql.sql"
  "${SCRIPT_DIR}/V2__rbac_init_data_mysql.sql"
  "${SCRIPT_DIR}/V3__rbac_reset_password_mysql.sql"
)

run_sql() {
  local file="$1"
  if [[ "${USE_DOCKER}" -eq 1 ]]; then
    docker exec -i "${DOCKER_CONTAINER}" \
      mysql -u"${DB_USER}" -p"${DB_PASS}" "${DB_NAME}" < "${file}"
  else
    mysql -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" -p"${DB_PASS}" "${DB_NAME}" < "${file}"
  fi
}

echo "==> RBAC 自举：库 ${DB_NAME}（${USE_DOCKER:+docker 容器 ${DOCKER_CONTAINER}}${USE_DOCKER:-${DB_HOST}:${DB_PORT}}）"

# 先备份（有 mysqldump 就备份，没有就跳过并提示）
if [[ "${USE_DOCKER}" -eq 1 ]]; then
  BACKUP="/tmp/backup-${DB_NAME}-$(date +%Y%m%d%H%M%S).sql.gz"
  if docker exec "${DOCKER_CONTAINER}" \
       mysqldump --single-transaction --no-tablespaces -u"${DB_USER}" -p"${DB_PASS}" "${DB_NAME}" 2>/dev/null | gzip > "${BACKUP}"; then
    echo "==> 已备份到 ${BACKUP}"
  else
    echo "==> 备份失败（权限或 mysqldump 不可用），继续前请确认已手动备份" >&2
  fi
fi

for f in "${SCRIPTS[@]}"; do
  if [[ ! -f "${f}" ]]; then
    echo "!! 脚本不存在，跳过：${f}" >&2
    continue
  fi
  echo "   执行 $(basename "${f}") ..."
  run_sql "${f}"
done

echo "==> 执行一致性检查 ..."
run_sql "${SCRIPT_DIR}/verify-mysql.sql"

echo "==> 自举完成。初始账号："
echo "     admin   / Autumn#Admin@2026   （超级管理员，admin=1，全权限直通）"
echo "     auditor / Autumn#User@2026    （审计员，只读，用于验证非管理员视角）"
echo "     ⚠️ 上线前务必改掉这两个密码。"
