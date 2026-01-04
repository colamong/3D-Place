param(
  [Parameter(Mandatory=$true)][string]$Service,   # 예: bff, user, media ...
  [string]$Account = "970103397792",
  [string]$Region  = "ap-northeast-2",
  [string]$Prefix  = "colombus",
  [string]$DockerfilePath,                        # 기본: services/<Service>/Dockerfile (없으면 services/auth/Dockerfile 재사용)
  [string]$Tag
)

$ErrorActionPreference = "Stop"
if (-not $PSScriptRoot) { $PSScriptRoot = (Get-Location).Path }

# Tag
if (-not $Tag -or $Tag -eq "") {
  try { $Tag = (git rev-parse --short HEAD) 2>$null } catch { $Tag = $null }
  if (-not $Tag) { $Tag = (Get-Date -Format "yyyyMMddHHmmss") }
}

# Paths
$EcrHost = "$Account.dkr.ecr.$Region.amazonaws.com"
$Repo    = "$Prefix/$Service"
$EcrRepo = "$EcrHost/$Repo"

if (-not $DockerfilePath -or $DockerfilePath -eq "") {
  $DockerfilePath = Join-Path $PSScriptRoot ("services/{0}/Dockerfile" -f $Service)
}

# 최소 구조 확인(서비스 모듈이 실제로 있는지)
$SvcGradle = Join-Path $PSScriptRoot ("services/{0}/build.gradle.kts" -f $Service)
$SvcSrc    = Join-Path $PSScriptRoot ("services/{0}/src" -f $Service)
if (!(Test-Path $SvcGradle)) { throw "Gradle 스크립트 없음: $SvcGradle" }
if (!(Test-Path $SvcSrc))    { throw "소스 경로 없음: $SvcSrc" }

# 아티팩트/캐시 디렉터리
$ART   = Join-Path $PSScriptRoot "images"
$CACHE = Join-Path $PSScriptRoot ".buildx-cache"
New-Item -ItemType Directory -Force -Path $ART,$CACHE | Out-Null
$TAR = Join-Path $ART ("{0}-{1}.tar" -f $Service,$Tag)

Write-Host "=== $($Service.ToUpper()) build → TAR 저장 ==="
Write-Host ("Repo: {0}" -f $EcrRepo)
Write-Host ("Tag : {0}" -f $Tag)
Write-Host ("DF  : {0}" -f $DockerfilePath)
Write-Host ("TAR : {0}" -f $TAR)
Write-Host ""

# ECR 리포 보장
try {
  aws ecr describe-repositories --repository-names $Repo --region $Region 1>$null
} catch {
  aws ecr create-repository --repository-name $Repo --region $Region | Out-Null
}

# buildx 보장
$builderName = "colombus-builder"
try { docker buildx inspect $builderName | Out-Null } catch { docker buildx create --name $builderName --use | Out-Null }

$env:DOCKER_BUILDKIT = "1"
$stdOut = Join-Path $ART ("{0}-build-{1}.out.log" -f $Service,$Tag)
$stdErr = Join-Path $ART ("{0}-build-{1}.err.log" -f $Service,$Tag)

# ===== docker buildx 실행(안정화) =====
$env:DOCKER_BUILDKIT = "1"

# buildx 인자 구성
$cmd = @(
  "buildx","build","--progress","plain",
  "--network","host","--platform","linux/arm64","--provenance=false",
  "--cache-from",("type=local,src={0}" -f $CACHE),
  "--cache-to",  ("type=local,dest={0},mode=max" -f $CACHE),
  "-t",("{0}:{1}" -f $EcrRepo,$Tag),
  "-t",("{0}:latest" -f $EcrRepo),
  "-f",$DockerfilePath,
  "--output",("type=docker,dest={0}" -f $TAR),
  $PSScriptRoot
)

# 0) 방어 코드: $cmd 비어있으면 즉시 실패
if (-not $cmd -or $cmd.Count -eq 0) {
  throw "내부 오류: docker ArgumentList(cmd)가 비어있음"
}

# 1) stdout/stderr 경로
$stdOut = Join-Path $ART ("{0}-build-{1}.out.log" -f $Service,$Tag)
$stdErr = Join-Path $ART ("{0}-build-{1}.err.log" -f $Service,$Tag)
$log    = Join-Path $ART ("{0}-build-{1}.log"     -f $Service,$Tag)

# 2) Start-Process는 문자열 한 줄을 권장 → 배열을 안전하게 한 줄로 변환
#    (인자에 공백 있을 수 있으니 각 토큰에 따옴표 추가)
function Quote-Arg([string]$s) {
  if ($s -match '\s' -or $s -match '[;&|<>]') { return '"' + ($s -replace '"','\"') + '"' }
  return $s
}
$argLine = ($cmd | ForEach-Object { Quote-Arg $_ }) -join ' '

# 3) 실행
$proc = Start-Process -FilePath "docker" -ArgumentList $argLine `
  -NoNewWindow -Wait -PassThru `
  -RedirectStandardOutput $stdOut `
  -RedirectStandardError  $stdErr

# 4) 종료코드 확인 + 로그 병합
$exit = $proc.ExitCode
Get-Content $stdOut, $stdErr | Set-Content -Encoding UTF8 $log

if ($exit -ne 0) {
  Write-Host "`n$Service 빌드 실패. 마지막 100줄 ↓"
  Get-Content $log -Tail 100 | Write-Host
  throw "$Service 빌드 실패 (exit=$exit)"
}

Write-Host "`n[OK] $Service TAR 저장 완료 → $TAR"
Write-Host "[LOG] 전체 로그 → $log"

# 푸시 가이드
Write-Host ""
Write-Host "푸시:"
Write-Host ("  docker load -i `"{0}`"" -f $TAR)
Write-Host ("  aws ecr get-login-password --region {0} | docker login --username AWS --password-stdin {1}" -f $Region,$EcrHost)
Write-Host ("  docker push {0}:{1}" -f $EcrRepo,$Tag)
Write-Host ("  docker push {0}:latest" -f $EcrRepo)

# 이미지 로드
docker load -i $TAR | Out-Null

# ECR 로그인
aws ecr get-login-password --region $Region | docker login --username AWS --password-stdin $EcrHost | Out-Null

# 푸시 (태그/Latest)
docker push ("{0}:{1}" -f $EcrRepo,$Tag) | Out-Null
docker push ("{0}:latest" -f $EcrRepo) | Out-Null