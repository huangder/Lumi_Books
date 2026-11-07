# Original EPUB spreads

Original book layout uses two independent WebView leaves when the existing
two-page setting is enabled and the live window is at least 600dp wide and
landscape or near-square. Ink, continuous scrolling, vertical scrolling, and
other formats retain their existing paths. The experimental native CSS renderer
is bypassed only while an EPUB spread is active.

`EpubSpreadTurnHost` owns three spread slots (six live WebViews, no warm slot).
Each leaf is laid out at `(width - 16dp) / 2`; independent documents retain
their publisher backgrounds, fonts, links, and footnotes. Both leaves must pass
their layout, compositor, and draw fences before a group commits. Whole groups
use the shared slide, curl, wave, fade, and no-animation implementations. Slide,
curl, wave, and fade track the finger before release, then complete or bounce
back using the controller's distance and velocity rules. A slow adjacent group
resumes at the held finger position only after both leaves are ready. Cancelled
gestures discard that pending position. No-animation keeps its instant switch.

Curl and wave snapshots capture each WebView at its actual leaf dimensions and
compose the pixels into one spread. Each capture owns a short-lived hardware
renderer; sharing one renderer between WebViews can replay retained compositor
constraints and vertically compress the text. Close the renderer after copying
its pixels. One reusable leaf bitmap is released with the host. On Android 10+
do not replace a failed hardware capture with software WebView drawing, whose
viewport geometry can also differ. Glass backdrops use the same composition.

`EpubSpreadPlanner` requests only missing adjacent chapter counts. Text pages
pair across chapter boundaries. Entry, jump, and resize anchor a new sequence;
forward/backward turns preserve that sequence. Pages remain indexed individually,
and the visible pair is reported separately from its focused progress locator.
Selection is local to one leaf; its chapter, page, and window coordinates travel
with the shared selection event. TTS can read both visible pages without moving
the pair when only its focus changes.

Fixed pages respect package and item spread hints, explicit page sides, centered
pages, declared covers, and publisher viewport dimensions. An unmarked first
chapter is not assumed to be a cover. Unmarked landscape design pages are shown
alone. Fixed and reflow pages never share a group. Hints and logical-to-spine
mapping are exposed through a defaulted `BookRenderSession.pagePresentation`
method; database and backup schemas are unchanged.

## Validation

Run local tests for `EpubSpread*`, `EpubPagePresentationTest`,
`EpubPresentationMetadataTest`, `EpubNavigationTest`, `BookLayoutRestorePointTest`,
and `EpubDocumentTransformerTest` in both editions. Validate actual WebView
pagination on a device because Robolectric does not execute reader JavaScript.
Never install an instrumented test APK.

The existing debug-only `EpubPerformanceActivity` accepts `spread=true`,
`landscape=true`, and a zero-based `chapter`. With `book_title` it reads an
existing library book without writing its preferences or saved position. This
uses the production spread host and permits phone-based tablet-size screenshots
without changing the user's reader settings. Restore any temporary `wm size`
and `wm density` overrides after capture.
