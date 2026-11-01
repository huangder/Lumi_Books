# libmobi v0.12 JNI conversion

The native module vendors upstream libmobi tag `v0.12`, commit
`85dcfe803fc2a21020ddcf15c3eb66b93d388add`, under its LGPL-3.0-or-later
license. See `third_party/libmobi/licenses/NOTICE.txt` and the adjacent license
texts. The library is a separate `libmobi.so`; no libmobi code is merged into
the application JNI library. Encryption code is excluded at build time.

The JNI entrypoint is
`com.huangder.lumibooks.util.kindle.KindleNative.reconstruct(String, String)`.
The output path must already be a private empty directory. Success creates
`content.opf`, `partNNNNN.*`, `flowNNNNN.*`, and `resourceNNNNN.*` directly at
its root. A failed conversion removes its partial files. The method chooses the KF8 side of hybrid files and
accepts KF8 plus classic MOBI/PalmDOC compression variants (including
HUFF/CDIC). Replica print, dictionaries, and encrypted inputs are rejected.
Kotlin assembles the EPUB container and handles ZIP packaging.

Integer results are stable across the JNI and host entrypoints:

| Code | Meaning |
| --- | --- |
| 0 | Success |
| 1 | Input/output failure or output directory not empty |
| 2 | Malformed or truncated MOBI/PDB |
| 3 | Encrypted input (DRM unsupported) |
| 4 | Unsupported MOBI subtype or compression |
| 5 | Input/resource/allocation limit exceeded |
| 6 | Reconstruction error or missing OPF/main markup |

The core checks PDB record offsets before libmobi parses them. Limits are 256 MiB
input, 64 MiB declared/decompressed text, 64 MiB per output part, 512 MiB total
output and native allocations, 20,000 output files, and two million native
allocation operations. Allocation requests are capped at 128 MiB. Output names
are generated locally from numeric IDs; reconstructed source names are never
used as paths. Files are created exclusively and without following symlinks.

Android NDK r28 and CMake 3.22.1 are pinned in `app/build.gradle.kts`. Build
configures `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`; both JNI and libmobi
ELF LOAD segments use 16 KiB maximum page alignment. The complete vendored source
and its notices are shipped as app assets to satisfy LGPL source availability.

For host checks, configure `app/src/main/cpp/CMakeLists.txt` with MinGW CMake,
then run `tools/kindle/test_native.py <path-to-kindle_reconstruct.exe>`. It
covers upstream HUFF/CDIC, uncompressed and DRM fixtures plus malformed/bounds
cases. An optional privately supplied book path adds an end-to-end sample check.
`tools/kindle/verify_reconstruction.py` validates OPF manifest references,
spine order, local image paths and the absence of unexpanded Kindle flow links.
No user book is stored in the repository.
