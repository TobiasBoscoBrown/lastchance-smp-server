#!/usr/bin/env bash
set -euo pipefail

# Usage: ./build.sh [All|DeathsReporter|SessionsReporter|DownedGate|KickOnDeath|KillReporter|RandomSpawn]
TARGET="${1:-All}"

# Resolve server root = parent of this script directory
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SERVER_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
PLUGINS_DIR="${SERVER_ROOT}/plugins"
SERVER_JAR="${SERVER_ROOT}/server.jar"
LIB_ROOT="${SERVER_ROOT}/libraries"

# Find tools
if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/javac" ]]; then
  JAVAC="${JAVA_HOME}/bin/javac"
  JAR="${JAVA_HOME}/bin/jar"
else
  JAVAC="javac"
  JAR="jar"
fi

# Check prerequisites
if [[ ! -f "${SERVER_JAR}" ]]; then
  echo "ERROR: Cannot find ${SERVER_JAR}. Place your Paper server.jar there first." >&2
  exit 1
fi
mkdir -p "${PLUGINS_DIR}"

# Build classpath (colon-separated on Unix)
CP="${SERVER_JAR}"
if [[ -d "${LIB_ROOT}" ]]; then
  while IFS= read -r -d '' J; do
    CP+="${CP:+:}${J}"
  done < <(find "${LIB_ROOT}" -type f -name '*.jar' -print0)
fi

build_one() {
  local name="$1"
  local module_dir="${SCRIPT_DIR}/${name}"
  local src_dir="${module_dir}/src/main/java"
  local res_dir="${module_dir}/src/main/resources"
  local build_dir="${module_dir}/build/classes"
  local out_jar_module="${module_dir}/build/${name}.jar"
  local out_jar_server="${PLUGINS_DIR}/${name}.jar"

  if [[ ! -d "${src_dir}" ]]; then
    echo "SKIP: ${name} has no sources at ${src_dir}" >&2
    return 0
  fi

  echo "Building ${name} ..."
  rm -rf "${build_dir}" && mkdir -p "${build_dir}"

  # Collect sources
  mapfile -t sources < <(find "${src_dir}" -type f -name '*.java')
  if [[ ${#sources[@]} -eq 0 ]]; then
    echo "ERROR: No Java sources in ${src_dir}" >&2
    return 1
  fi

  # Compile
  "${JAVAC}" -encoding UTF-8 --release 21 -cp "${CP}" -d "${build_dir}" "${sources[@]}"

  # Package jar
  rm -f "${out_jar_module}" "${out_jar_server}"
  if [[ -d "${res_dir}" ]]; then
    # Include plugin.yml and config.yml if present
    if [[ -f "${res_dir}/plugin.yml" && -f "${res_dir}/config.yml" ]]; then
      "${JAR}" --create --file "${out_jar_module}" -C "${build_dir}" . -C "${res_dir}" plugin.yml -C "${res_dir}" config.yml
    elif [[ -f "${res_dir}/plugin.yml" ]]; then
      "${JAR}" --create --file "${out_jar_module}" -C "${build_dir}" . -C "${res_dir}" plugin.yml
    else
      "${JAR}" --create --file "${out_jar_module}" -C "${build_dir}" .
    fi
  else
    "${JAR}" --create --file "${out_jar_module}" -C "${build_dir}" .
  fi

  # Install into server plugins folder
  cp "${out_jar_module}" "${out_jar_server}"
  echo "Built and copied ${name}.jar to ${out_jar_server}"
}

case "${TARGET}" in
  All|all)
    build_one "DeathsReporter"
    build_one "SessionsReporter"
    build_one "DownedGate"
    build_one "KickOnDeath"
    build_one "KillReporter"
    build_one "RandomSpawn"
    ;;
  DeathsReporter|SessionsReporter|DownedGate|KickOnDeath|KillReporter|RandomSpawn)
    build_one "${TARGET}"
    ;;
  *)
    echo "Usage: $0 [All|DeathsReporter|SessionsReporter|DownedGate|KickOnDeath|KillReporter|RandomSpawn]" >&2
    exit 2
    ;;
esac
