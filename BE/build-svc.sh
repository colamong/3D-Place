#!/usr/bin/env bash
set -euo pipefail

#####################################
# 기본 파라미터 / 기본값
#####################################
SERVICE=""
ACCOUNT="970103397792"
REGION="ap-northeast-2"
PREFIX="colombus"
DOCKERFILE_PATH=""
TAG=""

usage() {
  cat <<EOF
Usage: $0 --service <name> [options]

Required:
  -s, --service        서비스 이름 (예: bff, user, media ...)

Options:
  -a, --account        AWS Account ID (default: $ACCOUNT)
  -r, --region         AWS Region (default: $REGION)
  -p, --prefix         ECR 리포 prefix (default: $PREFIX)
  -f, --dockerfile     Dockerfile 경로 (default: services/<service>/Dockerfile)
  -t, --tag            태그 (기본: git short SHA, 실패 시 yyyymmddHHMMSS)
  -h, --help           이 도움말 출력
EOF
}

#####################################
# 인자 파싱
#####################################
while [[ $# -gt 0 ]]; do
  case "$1" in
    -s|--service)
      SERVICE="$2"
      shift 2
      ;;
    -a|--account)
      ACCOUNT="$2"
      shift 2
      ;;
    -r|--region)
      REGION="$2"
      shift 2
      ;;
    -p|--prefix)
      PREFIX="$2"
      shift 2
      ;;
    -f|--dockerfile)
      DOCKERFILE_PATH="$2"
      shift 2
      ;;
    -t|--tag)
      TAG="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ -z "$SERVICE" ]]; then
  echo "ERROR: --service 는 필수입니다." >&2
  usage
  exit 1
fi

#####################################
# 경로 / 태그 / 변수 설정
#####################################
# PowerShell의 $PSScriptRoot 대응: 스크립트 파일이 위치한 디렉토리
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Tag 없으면 git → 실패 시 날짜
if [[ -z "${TAG}" ]]; then
  if TAG=$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null); then
    :
  else
    TAG="$(date +%Y%m%d%H%M%S)"
  fi
fi

ECR_HOST="${ACCOUNT}.dkr.ecr.${REGION}.amazonaws.com"
REPO="${PREFIX}/${SERVICE}"
ECR_REPO="${ECR_HOST}/${REPO}"

if [[ -z "${DOCKERFILE_PATH}" ]]; then
  DOCKERFILE_PATH="${ROOT}/services/${SERVICE}/Dockerfile"
fi

# 최소 구조 확인
SVC_GRADLE="${ROOT}/services/${SERVICE}/build.gradle.kts"
SVC_SRC="${ROOT}/services/${SERVICE}/src"

if [[ ! -f "${SVC_GRADLE}" ]]; then
  echo "Gradle 스크립트 없음: ${SVC_GRADLE}" >&2
  exit 1
fi
if [[ ! -d "${SVC_SRC}" ]]; then
  echo "소스 경로 없음: ${SVC_SRC}" >&2
  exit 1
fi

# 아티팩트 / 캐시 디렉토리
ART="${ROOT}/images"
CACHE="${ROOT}/.buildx-cache"
mkdir -p "${ART}" "${CACHE}"

TAR="${ART}/${SERVICE}-${TAG}.tar"
STDOUT_LOG="${ART}/${SERVICE}-build-${TAG}.out.log"
STDERR_LOG="${ART}/${SERVICE}-build-${TAG}.err.log"
FULL_LOG="${ART}/${SERVICE}-build-${TAG}.log"

echo "=== ${SERVICE^^} build → TAR 저장 ==="
echo "Repo : ${ECR_REPO}"
echo "Tag  : ${TAG}"
echo "DF   : ${DOCKERFILE_PATH}"
echo "TAR  : ${TAR}"
echo

#####################################
# ECR 리포지토리 존재 보장
#####################################
if ! aws ecr describe-repositories --repository-names "${REPO}" --region "${REGION}" >/dev/null 2>&1; then
  aws ecr create-repository --repository-name "${REPO}" --region "${REGION}" >/dev/null
fi

#####################################
# buildx builder 보장
#####################################
BUILDER_NAME="colombus-builder"
if ! docker buildx inspect "${BUILDER_NAME}" >/dev/null 2>&1; then
  docker buildx create --name "${BUILDER_NAME}" --use >/dev/null
fi

export DOCKER_BUILDKIT=1

#####################################
# docker buildx 실행 (로그 분리)
#####################################
set +e
docker buildx build \
  --progress plain \
  --network host \
  --platform linux/arm64 \
  --provenance=false \
  --cache-from "type=local,src=${CACHE}" \
  --cache-to "type=local,dest=${CACHE},mode=max" \
  -t "${ECR_REPO}:${TAG}" \
  -t "${ECR_REPO}:latest" \
  -f "${DOCKERFILE_PATH}" \
  --output "type=docker,dest=${TAR}" \
  "${ROOT}" \
  >"${STDOUT_LOG}" 2>"${STDERR_LOG}"

EXIT_CODE=$?
cat "${STDOUT_LOG}" "${STDERR_LOG}" > "${FULL_LOG}"
set -e

if [[ ${EXIT_CODE} -ne 0 ]]; then
  echo
  echo "${SERVICE} 빌드 실패. 마지막 100줄 ↓"
  tail -n 100 "${FULL_LOG}" || true
  echo
  echo "전체 로그: ${FULL_LOG}"
  exit "${EXIT_CODE}"
fi

echo
echo "[OK] ${SERVICE} TAR 저장 완료 → ${TAR}"
echo "[LOG] 전체 로그 → ${FULL_LOG}"
echo

#####################################
# 푸시 가이드 출력
#####################################
echo "푸시 명령 예시:"
echo "  docker load -i \"${TAR}\""
echo "  aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ECR_HOST}"
echo "  docker push ${ECR_REPO}:${TAG}"
echo "  docker push ${ECR_REPO}:latest"
echo

#####################################
# 실제 docker load + ECR 로그인 + push
#####################################
echo "[STEP] docker load"
docker load -i "${TAR}" >/dev/null

echo "[STEP] ECR 로그인"
aws ecr get-login-password --region "${REGION}" | docker login --username AWS --password-stdin "${ECR_HOST}" >/dev/null

echo "[STEP] docker push ${ECR_REPO}:${TAG}"
docker push "${ECR_REPO}:${TAG}"

