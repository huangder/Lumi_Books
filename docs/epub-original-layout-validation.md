# Original EPUB Layout Validation

Date: 2026-10-07

## Implementation

- Three regular page WebViews and at most one boundary chapter warm WebView.
- Prepared chapter handoff with request, configuration and layout validation; idle scheduling and memory-pressure cleanup.
- Session-scoped converted-document LRU limited to 16 MiB; oversized documents are excluded and concurrent preparation is coalesced.
- Curl snapshots are versioned and prepared outside touch startup. Paint-only configuration avoids pagination; repeated visual commands are suppressed.
- Night paper and readable text replace large publisher backgrounds without inverting media. The original-layout theme suite preserves publisher colors and backgrounds, including at night; overridden inline styles and priorities are restored.
- Existing workspace changes, including spacing settings, were retained.

## Completed Device Samples

Device: 24129PN74C, Android 17. Isolated debug fixture: synthetic text EPUB with long and short chapters. Each completed instrumented mode had 120 turns: 105 within chapters and 15 across chapters; all 120 reported prepared-page hits.

| Transition | Within-chapter first frame P95 | Cross-chapter first frame P95 | Maximum cross-animation frame gap |
| --- | ---: | ---: | ---: |
| Slide | 13.69 ms | 17.37 ms | 43 ms |
| Curl | 15.36 ms | 24.56 ms | 43 ms |
| Scroll animation | 11.65 ms | 19.65 ms | 42 ms |

Curl snapshot P95 was 70.14 ms across 184 captures. Snapshot work has moved out of synchronous touch startup, but remains material work. Frame gaps indicate occasional dropped frames.

These timings start at the programmatically invoked turn handler and end at the first animation draw. They do not measure hardware touch latency or fully settled page latency. No pre-change baseline or same-device native-layout control was completed, so no before/after improvement percentage is available. Cold loads were not represented in these completed samples.

Fade completed 120 automated inputs but has no first-frame instrumentation. None sampling was stopped at the user's request. Image/tutorial fixtures, continuous scrolling, the complete gesture/error/rotation/memory regression matrix and device theme screenshots were not completed. The scroll row above is a pagination animation, not continuous scrolling.

## Verification

- Relevant cache, transformer, warm-chapter, live rendering, navigation, lifecycle, initial-load recovery, queued-turn, curl-input and turn-context JVM tests passed.
- Browser theme regression passed 44 checks, including publisher style restoration, night backgrounds, paint-only page geometry and font-triggered pagination.
- Browser text alignment/spacing (437), image spacing (80), annotation taps (136), underlines (80), comic navigation and image gesture checks passed.
- `:app:compileDebugAndroidTestKotlin` passed. Only debug APKs were installed; no connected tests, test APKs, uninstall or data clearing were performed.
- The earlier full JVM run had 9 failures and 2 skipped tests out of 1698. Two transformer expectations were corrected and passed on rerun. Seven failures remain outside the edited EPUB rendering areas; their preexisting status has not been established:
  - `ContinuousScrollReaderTest.comicImagePixelsUpdateWithoutMovingOrResizingThePage`
  - `ReaderImageAdjustmentsTest.preferencesSurviveNewReaderAndStayScopedToBook`
  - `ReaderPunctuationLayoutTest.continuousAndPagedInkAreIdenticalWithOneLayout`, with and without `[33]`
  - `ReaderPunctuationLayoutTest.indentedParagraphsKeepBothModeInkInsideReaderMargins`, with and without `[33]`
  - `TxtTitlePageRenderingTest.chapterTitleStaysLeftAlignedInEveryAlignmentMode`

Raw build and device logs are local ignored artifacts under `app/build/epub-optimization-*.log` and `app/build/epub-performance/epub-text/`. The debug fixture does not write real reading progress or preferences. Sampling was stopped when requested and the fixture Activity was no longer running.
