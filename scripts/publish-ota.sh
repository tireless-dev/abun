#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# Abun Android OTA Publisher
# Publishes local Android APK and update metadata to ota.tireless.dev
# ==============================================================================

SERVER_HOST="io.tireless.dev"
SERVER_USER="coco"
REMOTE_DIR="/srv/ota.tireless.dev/abun"
BASE_URL="https://ota.tireless.dev/abun"

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAGING_DIR="${PROJECT_ROOT}/build/ota-staging"

DO_BUILD=false
CUSTOM_APK=""
CHANGELOG=""

print_usage() {
  cat << EOF
Usage: $(basename "$0") [OPTIONS]

Options:
  -b, --build             Build debug APK via Gradle before uploading
  -f, --file <apk_path>   Path to custom APK (defaults to latest debug APK)
  -m, --message <text>    Changelog message (defaults to latest git commit)
  -h, --help              Show this help message

Examples:
  $(basename "$0")                     # Upload currently built APK
  $(basename "$0") -b                  # Build fresh APK and upload
  $(basename "$0") -b -m "Update OTA"  # Build fresh APK with custom changelog
EOF
}

# Parse command line flags
while [[ $# -gt 0 ]]; do
  case "$1" in
    -b|--build)
      DO_BUILD=true
      shift
      ;;
    -f|--file)
      CUSTOM_APK="$2"
      shift 2
      ;;
    -m|--message)
      CHANGELOG="$2"
      shift 2
      ;;
    -h|--help)
      print_usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1"
      print_usage
      exit 1
      ;;
  esac
done

cd "${PROJECT_ROOT}"

# Optional build step
if [[ "${DO_BUILD}" == "true" ]]; then
  BUILD_VERSION_CODE=$(python3 -c "import time; print(int(time.time() // 60))")
  echo "==> Building Android Debug APK (versionCode=${BUILD_VERSION_CODE})..."
  ./gradlew :app:sharedLogic:generateBuildInfo :app:androidApp:assembleDebug -PversionCode="${BUILD_VERSION_CODE}" --no-daemon
fi

# Locate APK
APK_PATH="${CUSTOM_APK}"
if [[ -z "${APK_PATH}" ]]; then
  APK_PATH="${PROJECT_ROOT}/app/androidApp/build/outputs/apk/debug/androidApp-debug.apk"
fi

if [[ ! -f "${APK_PATH}" ]]; then
  echo "Error: APK not found at ${APK_PATH}"
  echo "Hint: Run with -b to build first, or specify an APK with -f <path>"
  exit 1
fi

# Extract version metadata
METADATA_JSON="${PROJECT_ROOT}/app/androidApp/build/outputs/apk/debug/output-metadata.json"
VERSION_CODE=1
VERSION_NAME="0.0.0"

if [[ -f "${METADATA_JSON}" ]]; then
  VERSION_CODE=$(python3 -c "import json; data=json.load(open('${METADATA_JSON}')); print(data.get('elements', [{}])[0].get('versionCode', 1))" 2>/dev/null || echo 1)
  VERSION_NAME=$(python3 -c "import json; data=json.load(open('${METADATA_JSON}')); print(data.get('elements', [{}])[0].get('versionName', '0.0.0'))" 2>/dev/null || echo "0.0.0")
fi

IS_DIRTY=$(git status --porcelain 2>/dev/null | tr -d ' \t\n')
if [[ -n "${IS_DIRTY}" ]]; then
  COMMIT_HASH="wip"
else
  COMMIT_HASH=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
fi

BUILD_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
BUILD_TIME_HUMAN=$(date +"%Y-%m-%d %H:%M:%S %Z")

if [[ -z "${CHANGELOG}" ]]; then
  CHANGELOG=$(git log -1 --pretty=%B 2>/dev/null | tr '\n' ' ' | sed 's/^[ \t]*//;s/[ \t]*$//' || echo "Development build")
fi

FILE_SIZE=$(wc -c < "${APK_PATH}" | tr -d ' ')
SHA256=$(shasum -a 256 "${APK_PATH}" | awk '{print $1}')

VERSIONED_APK_NAME="abun-v${VERSION_NAME}-${VERSION_CODE}-${COMMIT_HASH}.apk"
LATEST_APK_NAME="abun-latest.apk"

# Prepare staging directory
mkdir -p "${STAGING_DIR}/apks"

# Copy to versioned archive, and hardlink to latest
cp "${APK_PATH}" "${STAGING_DIR}/apks/${VERSIONED_APK_NAME}"
ln -f "${STAGING_DIR}/apks/${VERSIONED_APK_NAME}" "${STAGING_DIR}/${LATEST_APK_NAME}"

# Generate latest.json
cat << EOF > "${STAGING_DIR}/latest.json"
{
  "appName": "Abun",
  "applicationId": "dev.tireless.abun",
  "versionCode": ${VERSION_CODE},
  "versionName": "${VERSION_NAME}",
  "commit": "${COMMIT_HASH}",
  "buildTime": "${BUILD_TIME}",
  "changelog": "${CHANGELOG}",
  "downloadUrl": "${BASE_URL}/${LATEST_APK_NAME}",
  "versionedUrl": "${BASE_URL}/apks/${VERSIONED_APK_NAME}",
  "sha256": "${SHA256}",
  "fileSize": ${FILE_SIZE}
}
EOF

# Generate mobile-friendly web download page
cat << EOF > "${STAGING_DIR}/index.html"
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Abun OTA 下载</title>
  <style>
    :root {
      --bg: #0f172a;
      --card-bg: #1e293b;
      --text: #f8fafc;
      --text-muted: #94a3b8;
      --primary: #38bdf8;
      --primary-hover: #0ea5e9;
      --accent: #22c55e;
      --border: #334155;
    }
    body {
      margin: 0;
      padding: 24px 16px;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      background: var(--bg);
      color: var(--text);
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 100vh;
      box-sizing: border-box;
    }
    .card {
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 20px;
      padding: 32px 24px;
      max-width: 420px;
      width: 100%;
      box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.5);
      text-align: center;
    }
    .badge {
      display: inline-block;
      padding: 4px 12px;
      font-size: 12px;
      font-weight: 600;
      color: var(--primary);
      background: rgba(56, 189, 248, 0.1);
      border: 1px solid rgba(56, 189, 248, 0.2);
      border-radius: 999px;
      margin-bottom: 12px;
    }
    h1 {
      margin: 0 0 8px 0;
      font-size: 28px;
      font-weight: 700;
    }
    .version {
      font-size: 15px;
      color: var(--text-muted);
      margin-bottom: 24px;
    }
    .info-box {
      background: rgba(15, 23, 42, 0.6);
      border-radius: 12px;
      padding: 16px;
      text-align: left;
      font-size: 13px;
      margin-bottom: 24px;
      border: 1px solid var(--border);
    }
    .info-row {
      display: flex;
      justify-content: space-between;
      margin-bottom: 8px;
    }
    .info-row:last-child {
      margin-bottom: 0;
    }
    .info-label {
      color: var(--text-muted);
    }
    .info-val {
      color: var(--text);
      font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    }
    .changelog {
      margin-top: 12px;
      padding-top: 10px;
      border-top: 1px dashed var(--border);
      color: var(--text);
      word-break: break-word;
    }
    .btn {
      display: block;
      width: 100%;
      box-sizing: border-box;
      padding: 14px 20px;
      background: var(--primary);
      color: #0f172a;
      font-size: 16px;
      font-weight: 700;
      text-decoration: none;
      border-radius: 12px;
      transition: background 0.2s, transform 0.1s;
    }
    .btn:active {
      transform: scale(0.98);
      background: var(--primary-hover);
    }
    .meta-links {
      margin-top: 20px;
      font-size: 12px;
      color: var(--text-muted);
    }
    .meta-links a {
      color: var(--primary);
      text-decoration: none;
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="badge">OTA 测试发布</div>
    <h1>Abun</h1>
    <div class="version">v${VERSION_NAME} (Build ${VERSION_CODE})</div>

    <div class="info-box">
      <div class="info-row">
        <span class="info-label">Git Commit</span>
        <span class="info-val">${COMMIT_HASH}</span>
      </div>
      <div class="info-row">
        <span class="info-label">更新时间</span>
        <span class="info-val">${BUILD_TIME_HUMAN}</span>
      </div>
      <div class="info-row">
        <span class="info-label">文件大小</span>
        <span class="info-val">$(( FILE_SIZE / 1024 / 1024 )) MB</span>
      </div>
      <div class="changelog">
        <span class="info-label">更新说明：</span><br>
        ${CHANGELOG}
      </div>
    </div>

    <a href="./${LATEST_APK_NAME}" class="btn">立即下载 APK</a>

    <div class="meta-links">
      <a href="./latest.json" target="_blank">查看 latest.json</a> · 
      <a href="./apks/" target="_blank">历史版本归档</a>
    </div>
  </div>
</body>
</html>
EOF

echo "==> Ensuring remote directory exists..."
ssh "${SERVER_USER}@${SERVER_HOST}" "mkdir -p '${REMOTE_DIR}/apks'"

echo "==> Uploading artifacts to ${SERVER_USER}@${SERVER_HOST}:${REMOTE_DIR}..."
rsync -avzH --progress "${STAGING_DIR}/" "${SERVER_USER}@${SERVER_HOST}:${REMOTE_DIR}/"

echo "==> Pruning remote archive to keep only the latest 3 versions..."
ssh "${SERVER_USER}@${SERVER_HOST}" "
  if [ -d '${REMOTE_DIR}/apks' ]; then
    cd '${REMOTE_DIR}/apks'
    count=\$(ls -1t abun-*.apk 2>/dev/null | wc -l)
    if [ \"\${count}\" -gt 3 ]; then
      ls -1t abun-*.apk 2>/dev/null | tail -n +4 | xargs -r rm -f
    fi
  fi
"

# Also prune local staging archive to keep only latest 3
if [[ -d "${STAGING_DIR}/apks" ]]; then
  (
    cd "${STAGING_DIR}/apks"
    local_count=$(ls -1t abun-*.apk 2>/dev/null | wc -l)
    if [[ "${local_count}" -gt 3 ]]; then
      ls -1t abun-*.apk 2>/dev/null | tail -n +4 | xargs rm -f || true
    fi
  )
fi

echo ""
echo "================================================================="
echo "✅ OTA 发布成功！"
echo "🌐 手机网页访问与下载: ${BASE_URL}/"
echo "📦 APK 直接下载链接:   ${BASE_URL}/${LATEST_APK_NAME}"
echo "📄 检查更新接口:       ${BASE_URL}/latest.json"
echo "================================================================="
