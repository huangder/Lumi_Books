"""Host regression checks, no Android device and no user data in the repository."""
import argparse
import os
from pathlib import Path
import struct
import subprocess
import tempfile

p = argparse.ArgumentParser()
p.add_argument("executable", type=Path)
p.add_argument("--sample", type=Path)
a = p.parse_args()
exe = a.executable.resolve()
fixtures = Path(__file__).resolve().parents[2] / "third_party/libmobi/tests/samples"

def run(source, expected):
    with tempfile.TemporaryDirectory(prefix="lumi-native-out-") as out:
        result = subprocess.run([str(exe), str(source), out], capture_output=True, text=True, timeout=30)
        assert result.returncode == expected, (source.name, result.returncode, result.stdout, result.stderr)
        if not expected:
            subprocess.run([os.sys.executable, str(Path(__file__).with_name("verify_reconstruction.py")), out], check=True)

for name, code in [("sample-unicode-huffdic.mobi", 0), ("sample-unicode-uncompressed.mobi", 0),
                   ("sample-cp1252.mobi", 0), ("sample-drm-v1.mobi", 3),
                   ("sample-drm_pidLTKULBB^5V-v2.mobi", 3), ("sample-invalid-indx.fail", 2)]:
    run(fixtures / name, code)
original = (fixtures / "sample-unicode-huffdic.mobi").read_bytes()
first = struct.unpack_from(">I", original, 78)[0]
with tempfile.TemporaryDirectory(prefix="lumi-native-input-") as tmp:
    target = Path(tmp) / "broken.mobi"
    for size in (0, 1, 77, 78, first + 15):
        target.write_bytes(original[:size]); run(target, 2)
    data = bytearray(original)
    struct.pack_into(">I", data, 78, len(data) + 1)
    target.write_bytes(data); run(target, 2)
    data = bytearray(original)
    struct.pack_into(">I", data, first + 4, 0xFFFFFFFF)
    target.write_bytes(data); run(target, 5)
    data = bytearray(original)
    struct.pack_into(">H", data, first + 12, 1)
    target.write_bytes(data); run(target, 3)
    target.write_bytes(b"\x00" * 78)
    run(target, 4)
    with target.open("wb") as f:
        f.truncate(256 * 1024 * 1024 + 1)
    run(target, 5)
if a.sample:
    run(a.sample, 0)
print("native regression checks passed")
