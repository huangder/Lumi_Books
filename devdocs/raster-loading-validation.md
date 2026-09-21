# PDF / CBZ loading validation

## Implemented policy

- A reader session owns priority-scheduled native work. PDF has one worker; CBZ has two,
  with at most one speculative job and one large decode active at a time.
- Physical page dimensions are read once per session. Equal output specifications share
  work across quality labels; completed work survives cancellation of an individual UI consumer.
- Actual list/pager offsets and transformed pan/zoom values reset the 160 ms settle timer.
  While moving, visible pages request previews. Settled pages request the selected quality.
- Directional prefetch covers two physical pages ahead and one behind, completing spreads.
  Only the nearest adjacent page/spread gets normal-quality prefetch after settling.
- After 800 ms idle, the current page and two pages either side are saved as unannotated,
  unzoomed normal-quality PNGs. Spreads are completed. Exit only saves already available images.
- Resume PNG limits are 64 MiB per book, 192 MiB total and three recent books. Clear cache,
  source invalidation, corruption, interrupted writes and superseded sessions invalidate writes.
  Unreliable source fingerprints are not reused across processes. Existing source files,
  reading progress and annotations are not stored in this disposable cache.

## Local checks

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*Raster*Test' --tests '*PdfZoomGestureTest' --tests '*CbzSpreadPlannerTest' --tests '*ReaderCacheStoreTest'
.\gradlew.bat :app:assembleDebug :app:compileDebugAndroidTestKotlin
```

Never run connected tests or install instrumentation APKs on the user's device.
For device verification, install the debug application once with `:app:installDebug`.

## Device comparison protocol

Use the same device, book, initial page, reading mode, quality, thermal state and gesture
sequence on the baseline and candidate. Use the existing 41-page large-image CBZ fixture,
a text/vector PDF and a scanned PDF. Perform five repetitions each of forward rapid
scrolling, reverse scrolling, distant jumps, and exit/reopen. Repeat in horizontal and
vertical paging; include landscape spreads and right-to-left comics.

The debug `RasterLoad` log tag records `queueMs`, `decodeMs`, bitmap bytes, memory/disk hits,
and `draw ... stage=first|full elapsedMs=...`. Draw latency starts when a physical page enters
the viewport and ends at its first Compose image draw; it is not GPU presentation latency.
Pages traversed without a draw must be reported separately, not counted as zero latency.
Use Perfetto `Raster.decode.<page>` slices for native work, `dumpsys gfxinfo` for missed
frames, and periodic `dumpsys meminfo com.huangder.lumibooks` samples for peak memory.
Do not clear user app data to create a cold run; use a not-yet-read region or dedicated fixture.

```powershell
& 'F:\SDK\platform-tools\adb.exe' logcat -v brief -s RasterLoad:D '*:S'
& 'F:\SDK\platform-tools\adb.exe' shell dumpsys gfxinfo com.huangder.lumibooks
& 'F:\SDK\platform-tools\adb.exe' shell dumpsys meminfo com.huangder.lumibooks
```

Report p50/p95 separately for first image and selected quality, plus native decode count,
cache hits, missed-frame rate and peak memory. Never infer a device speedup from unit tests.
Instrumentation was not present in the baseline, so comparable first/full draw measurements
require the same measurement hooks on that baseline or an external frame-capture method.

Manual regressions: zoomed two-axis pan and release inertia; tap to open the menu while
zoomed; two-finger navigation in annotation mode; correct annotation coordinates after
preview replacement; mode/quality switches; cover-alone spreads; clear cache then reopen;
background/exit during native decode and immediately reenter the same book.

## Measurement status

Local verification on 2026-09-20: 357 reader/cache tests across 62 suites passed with no
failures or skips. Debug assembly and Android-test Kotlin compilation passed. A single
`:app:installDebug` update succeeded on the connected 24129PN74C (Android 17); no test APK
was installed and application data was not cleared.

No matched baseline/candidate device performance results have been collected yet. The
connected phone was being used in another application, so this change does not claim a
percentage speedup or completion of the manual gesture matrix.
