#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

echo "=== Building Zenoti Extension (Scala.js) ==="
sbt buildExtension

echo ""
echo "=== Repackaging extension ==="
python3 - <<'EOF'
import zipfile, os
base = os.path.join(os.path.dirname(__file__), '..')
ext_dir = os.path.abspath(base)
zip_path = os.path.join(ext_dir, '..', 'zenoti-extension.zip')

with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as z:
    for root, dirs, files in os.walk(ext_dir):
        dirs[:] = [d for d in dirs if d not in ('scala', '__pycache__')]
        for f in files:
            if f.endswith(('.zip', '.md')):
                continue
            fp = os.path.join(root, f)
            arcname = 'zenoti-extension/' + os.path.relpath(fp, ext_dir)
            z.write(fp, arcname)
            print('  added:', arcname)

print('Done:', zip_path)
EOF
