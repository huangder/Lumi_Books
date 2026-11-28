# WebDAV content identity and upgrade guidance

Both editions use the same sync protocol. Local book UUIDs, Room rows, note and
bookmark references, source bindings and reader preference keys remain local.

## Identifying a book without uploading its body

`manifest.json` version 2 accepts an optional `bookIdentities` object. Each key is
a wire book ID and each value contains `sha256`, `format` and `sizeBytes` of the
local book file. These entries do not imply that a downloadable book exists.
Only `books` entries advertise uploaded book bodies; a stale entry may still point to a missing file.

Full sync hashes readable local files with a streaming SHA-256 calculation.
Background progress sync hashes the active book and checks aliases against the
complete local library. File name, storage path and editable shelf title are
excluded from the fingerprint. A file whose bytes differ and that has no synced
history with a cloud entry gets a new identity; this includes changes to
encoding or embedded EPUB metadata. Markdown identity uses the local document
bytes, separately from the downloadable Markdown pack.

## Editing a book that is already synced

Editing a synced book (the TXT editor, find and replace, a Markdown update)
keeps its wire ID. Each installation records, per library, the body hash it
last confirmed equal to the cloud identity (`webdav_book_hashes:<libraryKey>` in
`SyncStateDao`; not synced, not part of backups). On the next sync the local
hash and the cloud identity are compared with that baseline:

- Only the local hash moved: a local edit. The new body is uploaded under a new
  file name, the manifest entry and `bookIdentities` value of the same wire ID
  are replaced in one commit, and the superseded file is deleted afterwards if
  no active entry still references it. A failed upload or commit leaves the
  published entry, file and baseline untouched, so the next sync retries.
- Only the cloud identity moved: another device edited the book. The stale
  local copy is never uploaded, not even to refill a missing body. An
  app-managed TXT file that still equals the baseline is replaced by the
  verified cloud body once the book is neither open nor being read aloud;
  other files (other formats, SAF documents) are kept as they are.
- Both moved: the copies have diverged and the local one forks to a new
  identity, as an unsynced file would. Neither side is overwritten.

Rows are linked to a remote entry only after the manifest that publishes it has
been committed. A client that predates this rule still forks on every edit and
leaves the previous entry behind as a cloud-only copy; the sync status sheet
offers a confirmed cleanup for such copies of TXT books (matched to exactly one
local book by title and author or by uploaded file-name stem).

## Deleting only the cloud copy

"Delete cloud copy only" tombstones the wire ID and unlinks the local row, which
keeps its file and reading data. A tombstoned wire ID blocks further uploads of
that book even after its bytes change. The book details page offers an explicit
upload-again action that lifts the tombstone on the next sync.

Existing verified cloud UUIDs are retained. New identities use
`sha256-<lowercase hex digest>` as a stable wire ID. An old progress-only record
under the importing device's UUID can be backfilled by that device on its first
sync. A second device can then match the identity without uploading a body.
Unverifiable legacy records are not matched by title.

Aliases in `WebdavBookIdentityStore` remain installation-local, survive book
deletion for tombstone publication, and are revalidated against readable content.
Independent duplicate local rows are preserved rather than silently collapsed.
An existing cloud-only placeholder may share a verified wire ID with a newly
imported physical copy. Reconciliation keeps the original book's ID, file and
archive state, merges reading data and shelf membership, and hides the empty
placeholder without deleting its row or publishing a tombstone. A different
wire ID is eligible for merging by content only when the SHA-256, format and
byte count agree with exactly one physical copy and the ID has no available
remote body. Two usable physical copies are never folded together.
When changed content uses another wire ID, obsolete remote rows that collide with
the inverse local mapping are excluded from local state application; the cloud
history is retained.

An unread import or its first default-position open must not overwrite an older
cloud reading position with 0%. Once linked, a later explicit rewind is a normal
reading event and can sync back to another device.

## Selecting progress only

Existing sync choices remain unchanged. Under reading data, the opt-in
`syncReadingProgressOnly` setting sends progress, locator and read status while
excluding local reading sessions, statistics, bookmarks, annotation tags and
notes. Existing remote details and local details are retained. Profile, library
organization and book-file options remain independent; turn those off as well
to sync only reading progress. State snapshots never capture book bodies.
State sync updates existing books only, regardless of the library organization
setting. A new cloud-only item requires book-file sync and a reachable path from
the body manifest. Manual sync checks those paths, repairs missing bodies when
an original is available, and hides unrepairable placeholders locally. Only a
definitive missing response (404/410) counts as a failed existence check;
permission and connection errors do not hide an item. Hidden placeholders
can reappear when their body is restored. This visibility state is local and
does not change the user's own hidden or archived settings or the backup format.

Manifest and legacy reading-data writes use conditional updates and retry on
conflict. If a server has no strong ETag or rejects conditional writes, the
client re-reads and compares the current JSON before writing. That fallback,
and a legacy client that writes without conditions, cannot guarantee atomic
updates across devices. Legacy annotation arrays are merged, not treated as
deletions; deletion is represented by tombstones in the portable state.

## Upgrade notice

The first launch of this rollout records an installation-local state in
`SyncStateDao`. An already-enabled WebDAV configuration gets a pending notice;
a disabled or fresh configuration is marked done without prompting. Opening
sync settings or dismissing the notice acknowledges it. Backups and WebDAV do
not copy this state to another device.

The notice uses `LiquidGlassAlertDialog`, `titleText` and semantic
`AppDialogAction` buttons. It follows app update, remote notice and policy dialogs,
waits for the splash and external import dialog, and is deferred on reader routes.
The primary action opens the existing WebDAV settings rather than changing sync
choices or silently initiating a transfer.

Users should update and manually sync the device with the latest progress first,
then update and sync the remaining devices using identical local book files.
Existing books do not need to be reimported. Legacy clients can read the additive
manifest but may drop new optional fields when rewriting it; participating
devices should all update to use content matching consistently.
