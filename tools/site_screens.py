"""Make bare phone-screen images for the website's CSS phone mockups.

docs/media/*.webp screenshots (1291x2751) already include a device frame,
bezel and punch-hole camera. The homepage draws its own phone in CSS, so this
crops each image to the display area and paints out the camera hole.

    python tools/site_screens.py        # writes docs/media/screen/*.webp
"""
from pathlib import Path

import cv2
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "docs" / "media"
OUT = SRC / "screen"
SIZE = (1291, 2751)
CROP = (42, 42, 1238, 2708)        # display area inside the mockup bezel
CAMERA = (640, 117, 42)            # centre x, centre y, radius to repaint (source pixels)


def main() -> None:
    OUT.mkdir(exist_ok=True)
    for src in sorted(SRC.glob("*.webp")):
        im = Image.open(src).convert("RGB")
        if im.size != SIZE:
            continue
        rgb = np.array(im)
        mask = np.zeros(rgb.shape[:2], np.uint8)
        cv2.circle(mask, CAMERA[:2], CAMERA[2], 255, -1)
        bgr = cv2.inpaint(cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR), mask, 9, cv2.INPAINT_TELEA)
        out = Image.fromarray(cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)).crop(CROP)
        out.save(OUT / src.name, "WEBP", quality=86, method=6)
        print(src.name, out.size)


if __name__ == "__main__":
    main()
