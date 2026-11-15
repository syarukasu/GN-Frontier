#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
exec python3 GN-Frontier-Setup/setup.py "$@"
