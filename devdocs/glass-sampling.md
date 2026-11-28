# Liquid Glass sampling quality

| Preset | Frosted surfaces | Progressive blur over live content | Tab prism |
| --- | ---: | ---: | ---: |
| Original | 1x | 1x | 1x |
| Clear | 1/2x | 2/3x | 1x |
| Balanced (default) | 1/3x | 1/2x | 1x |
| Performance | 1/3x | 1/3x | 1/3x |

Stored/debug IDs remain `original`, `balanced` (Clear), `smooth` (Balanced)
and `performance`. Missing or unknown values use Balanced. DataStore and the
launch-theme mirror retain the selection, and nested themes inherit it. The
frame-time governor can lower the frosted and progressive scales in Clear
and Balanced. The prism uses the selected preset. Other themes and
unsupported/e-ink devices hide the setting.

`third_party/backdrop` is the licensed 1.0.6 source with a small sampling patch.
It reduces the **effect buffer**, rather than just reducing a source bitmap that
would then be blurred at full resolution. The source display list retains logical
screen coordinates; the effect layer rasterizes at the selected scale and scales
back up. Blur radii and lens shader uniforms are compensated together. Foreground
content, clipping, tints, highlights and shadows remain at native resolution.
Custom menu lens uniforms use the same scale; foreground menu warping stays native.
Progressive blur can use the sampled layer when its clear edge reveals matching live
content. Composites that supply the complete backdrop use native resolution.

Activity push transitions reuse toolbar snapshots in a half-resolution effect
layer while the animated blur radius exceeds 2 dp, with the radius compensated
for the reduced buffer. The layer returns to native resolution near the handoff.

Window snapshots keep their existing 1440px long-edge cap, then apply the selected
quality. Native EPUB captures render directly to the selected bitmap dimensions.
Neither path changes the logical viewport, screen origin or frozen-overlay lease
policy. Quality changes invalidate an unfrozen snapshot even when the page stays
the same. Database and portable backup/WebDAV schemas are unchanged.

## Validation

Local regression tests: `GlassSamplingGeometryTest`,
`LiquidGlassSamplingPersistenceTest`, `LiquidGlassSamplingSettingTest`,
`GlassSnapshotLifecycleTest` and `SettingsPreferenceScreensTest`. Also run the
existing glass controls, menu geometry and render regression tests in both official
and OSS local test configurations, and compile both editions before installation.
Never run connected Android tests or install test APKs.

The debug Activities `GlassControlEdgePreviewActivity`,
`GlassDialogPreviewActivity`, `MenuMotionPreviewActivity`,
`ReaderGlassMenuPreviewActivity`, and `EpubPerformanceActivity` accept a
`samplingQuality` string extra (`original`, `balanced`, `smooth`, `performance`). The EPUB
fixture also needs its existing `glass=true` option. These overrides do not save
preferences.

For device QA, compare the same scene, transparency, theme, orientation and
refresh rate in all four qualities: scrolling bookshelf with bottom tabs,
opening/morphing menus, dialogs including native-window snapshots, TXT/native
EPUB/raster reader overlays, and Premium report/tag/export pages. Check small
foreground text, edge/highlight sharpness, snapshot alignment after rotation and
IME offsets, stale frames, and opening/closing animation stability. Compare frame
time percentiles and missed frames over repeated identical interactions; reduced
pixel count alone does not establish a frame-rate improvement.
