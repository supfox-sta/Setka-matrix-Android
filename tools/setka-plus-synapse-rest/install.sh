#!/bin/bash
# Copies setka_plus.py into Synapse site-packages.
# Patches __init__.py and homeserver.py — see README.md for manual steps if this script misses.

set -euo pipefail

VENV="${1:-/opt/venvs/matrix-synapse}"
PYVER="$(basename "$(ls -d "$VENV/lib/python"*)")"
SITE="$VENV/lib/$PYVER/site-packages"
DIR="$(cd "$(dirname "$0")" && pwd)"

cp "$DIR/setka_plus.py" "$SITE/synapse/rest/client/setka_plus.py"
cp "$DIR/setka_channels.py" "$SITE/synapse/rest/client/setka_channels.py"
echo "Copied setka_plus.py and setka_channels.py"

if [ ! -f /etc/matrix-synapse/setka_plus.yaml ]; then
  cp "$DIR/setka_plus.yaml.example" /etc/matrix-synapse/setka_plus.yaml
  echo "Created /etc/matrix-synapse/setka_plus.yaml — edit it!"
fi

echo ""
echo "Now patch manually (see README.md):"
echo "  1. $SITE/synapse/rest/__init__.py"
echo "  2. $SITE/synapse/app/homeserver.py"
echo "  3. Remove modules: setka_plus from homeserver.yaml"
echo "  4. systemctl restart matrix-synapse"
