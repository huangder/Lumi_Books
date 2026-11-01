"""Verify native reconstructed resources independently of the Android importer."""
import argparse
import pathlib
import re
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("directory", type=pathlib.Path)
parser.add_argument("--images", type=int)
args = parser.parse_args()
root = args.directory.resolve()
ns = {"o": "http://www.idpf.org/2007/opf"}
package = ET.parse(root / "content.opf").getroot()
manifest = {e.attrib["id"]: e for e in package.findall("o:manifest/o:item", ns)}
spine = package.findall("o:spine/o:itemref", ns)
assert spine, "empty spine"
images = 0
for item in manifest.values():
    target = (root / item.attrib["href"]).resolve()
    assert target.is_relative_to(root) and target.is_file(), item.attrib["href"]
for ref in spine:
    item = manifest[ref.attrib["idref"]]
    text = (root / item.attrib["href"]).read_text(encoding="utf-8")
    assert "kindle:embed" not in text and "kindle:flow" not in text
    sources = re.findall(r'<(?:img|image)\b[^>]*?\b(?:src|(?:xlink:)?href)\s*=\s*["\']([^"\']+)', text, re.I)
    images += len(sources)
    for source in sources:
        target = (root / source.split("#")[0]).resolve()
        assert target.is_relative_to(root) and target.is_file(), source
if args.images is not None:
    assert images == args.images, (images, args.images)
print(f"manifest={len(manifest)} spine={len(spine)} image_occurrences={images}")
print("spine_order=" + ",".join(manifest[r.attrib["idref"]].attrib["href"] for r in spine))
