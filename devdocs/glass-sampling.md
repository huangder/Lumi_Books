# Liquid Glass sampling quality

The Visual & Motion glass section offers Original (1x), Clear (2/3x), Balanced
(1/2x, default) and Performance (1/3x). Missing or unknown values use Balanced.
For compatibility, stored/debug IDs remain `original`, `balanced` (Clear, 2/3x),
`smooth` (Balanced, 1/2x) and `performance`; existing selections retain their scale.
DataStore and the launch-theme mirror both retain the selected value;
nested reader themes inherit it. Other themes and unsupported/e-ink devices
continue to use native-resolution backdrop effects and hide the setting.

`third_party/backdrop` is the licensed 1.0.6 source with a small sampling patch.
It reduces the **effect buffer**, rather than just reducing a source bitmap that
would then be blurred at full resolution. The source display list retains logical
screen coordinates; the effect layer rasterizes at the selected scale and scales
back up. Blur radii and lens shader uniforms are compensated together. Foreground
content, clipping, tints, highlights and shadows remain at native resolution.
Custom menu lens uniforms use the same scale; foreground menu warping stays native.

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
