#!/usr/bin/env bash

set -euo pipefail

BOOST_VERSION=1.89.0
BOOST_SHA256=67acec02d0d118b5de9eb441f5fb707b3a1cdd884be00ca24b9a73c995511f74

readonly BOOST_VERSION BOOST_SHA256
readonly BOOST_URL="https://github.com/boostorg/boost/releases/download/boost-${BOOST_VERSION}/boost-${BOOST_VERSION}-cmake.tar.xz"

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
archive_path="${repository_root}/production/android/app/boost-${BOOST_VERSION}.tar.xz"

calculate_sha256() {
  local file_path="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${file_path}" | awk '{print $1}'
  else
    shasum -a 256 "${file_path}" | awk '{print $1}'
  fi
}

verify_archive() {
  [[ "$(calculate_sha256 "${archive_path}")" == "${BOOST_SHA256}" ]]
}

if [[ -f "${archive_path}" ]]; then
  if verify_archive; then
    echo "Using verified Boost ${BOOST_VERSION} archive from cache."
    exit 0
  fi
  echo "Discarding cached Boost archive with an invalid SHA-256." >&2
  rm -f "${archive_path}"
fi

temporary_archive="$(mktemp "${archive_path}.part.XXXXXX")"
trap 'rm -f "${temporary_archive}"' EXIT

curl \
  --fail \
  --location \
  --silent \
  --show-error \
  --proto '=https' \
  --tlsv1.2 \
  --connect-timeout 20 \
  --max-time 600 \
  --retry 8 \
  --retry-delay 2 \
  --retry-max-time 300 \
  --retry-all-errors \
  --output "${temporary_archive}" \
  "${BOOST_URL}"

mv "${temporary_archive}" "${archive_path}"
if ! verify_archive; then
  rm -f "${archive_path}"
  echo "Downloaded Boost archive failed SHA-256 verification." >&2
  exit 1
fi

trap - EXIT
echo "Downloaded and verified Boost ${BOOST_VERSION}."
