# WebDAV content identity and upgrade guidance

Both editions use the same sync protocol. Local book UUIDs, Room rows, note and
bookmark references, source bindings and reader preference keys remain local.

## Identifying a book without uploading its body

`manifest.json` version 2 accepts an optional `bookIdentities` object. Each key is
a wire book ID and each value contains `sha256`, `format` and `sizeBytes` of the
local book file. These entries do not imply that a downloadable book exists.
Only `books` entries advertise actual uploaded book bodies.

Full sync hashes readable local files with a streaming SHA-256 calculation.
Background progress sync hashes the active book and checks aliases against the
complete local library. File name, storage path and editable shelf title are
excluded from the fingerprint. Any change to file bytes creates a new identity;
this includes changes to encoding or embedded EPUB metadata. Markdown identity
uses the local document bytes, separately from the downloadable Markdown pack.

Existing verified cloud UUIDs are retained. New identities use
`sha256-<lowercase hex digest>` as a stable wire ID. An old progress-only record
under the importing device's UUID can be backfilled by that device on its first
sync. A second device can then match the identity without uploading a body.
Unverifiable legacy records are not matched by title.

Aliases in `WebdavBookIdentityStore` remain installation-local, survive book
deletion for tombstone publication, and are revalidated against readable content.
Independent duplicate local rows are preserved rather than silently collapsed.
An existing cloud-only placeholder may share a verified wire ID with a newly
imported physical copy. State sync keeps the physical descriptor once, merges
all references into that local copy, and retains the old placeholder row; it
does not delete either row or collapse two physical copies.
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
Progress-only sync with book files disabled also defers new remote-only book
rows until the user imports a local copy, even when library organization is on.

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
