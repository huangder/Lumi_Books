# Public and official builds

## Integrate before building

The public checkout and `app/src/premium` are two separate Git repositories.
Committing in one does not commit the other. A Git worktree is another checkout,
not a shared working directory: edits in another worktree are not build inputs.
The Gradle tasks use only the selected checkout and its nested Premium checkout.

Use one designated integration checkout for device builds. Each feature task
must report its public and Premium paths, branches and commits, then integrate
both sides there. Review overlapping changes instead of copying whole folders.
Before installation, list relevant worktrees and confirm every requested feature
is present in the integration checkout, including untracked source and resources.
Do not treat an unchanged Premium `main`, a successful build or an identical
version name as proof that all feature work has been integrated.

Keep the public branch based on the independently buildable public history.
Never merge pre-split Premium history into it. Keep private implementations,
credentials and activation distributions out of the public index. Record the
compatible public commit in the private repository and retain a local build
record with both commit IDs, edition, APK path and verification results.

Shared changes require official and OSS compilation checks. Install only the
new official debug APK for ordinary device validation; OSS validation output
must not replace it. A local commit is a saved version; pushing, merging a PR
and publishing an APK are separate actions that require user authorization.

The public checkout builds the base edition with the existing Gradle tasks:

```powershell
.\gradlew.bat :app:assembleDebug
```

An authorized developer can clone the private edition repository into
`app/src/premium`. When `app/src/premium/java` exists, the build includes its
Java/Kotlin, resources, assets, manifest and tests. The directory is ignored by
the public repository. It has its own Git history; commit and push its changes
from inside that directory. Never force-add it to the public index.

```powershell
git clone --branch main https://github.com/huangder/Lumi_Books-premium.git app/src/premium
```

Build outputs are separate: `app/build/official` and `app/build/oss`. This keeps
KSP/Hilt generated declarations from leaking across edition switches. APK files
are under the selected directory's `outputs/apk/debug/` folder.

Concurrent validation from one checkout can use `'-Plumi.buildTag=reader-fix'`
to place outputs under `app/build/<edition>/reader-fix`. Give each concurrent
build its own `--project-cache-dir` as well, so Gradle's previous-output cleanup
cannot remove another build's generated files. The tag accepts letters, digits,
underscores and hyphens; ordinary builds keep the paths above.

To validate the public edition while the private checkout is present:

```powershell
.\gradlew.bat '-Plumi.oss=true' :app:assembleDebug
```

`PremiumAccess` is the shared capability boundary. The public implementation
does not impose remote-source limits. Edition UI functions provide optional
entries; the public implementations render nothing. The excerpt template
boundary keeps the six base styles public. An unavailable saved style falls
back to the reader style without deleting the stored preferences.

`EditionWidgetRefresh` starts optional widget observers through the selected
edition's Hilt binding; OSS supplies an empty implementation. Optional widget
providers, manifests, resources and tests belong in the private checkout. Shared
visible-book queries and compare-and-set position updates remain public so both
editions use the same library records. External readers can explicitly request
a reload of the persisted position without allowing stale background saves to
overwrite it. Cover Flow selection mode disables its management actions.

The official APK keeps its existing behavior: reading reports and advanced
excerpts can be previewed without unlocking; export is gated. Tag management
checks access at entry. Neither edition changes the application ID, Room schema,
migrations, backup format or WebDAV data format. License preferences remain
excluded from backups.

Code visibility and distribution licensing are separate. LICENSE governs the
public source; third-party terms still apply. An official distribution needs
appropriate rights for the base code as well as the private implementation.
Adding a module or a NOTICE statement does not override GPL obligations.

Do not install test APKs or run connected Android tests. Use local JVM tests,
compilation and packaging checks; install only the debug app when requested.

Reader changes involving large Compose lambdas also need a fresh minified release
build: JVM tests and debug builds do not exercise R8/D8's release register allocation.
Keep page content, overlays and sheets in separate composable slots instead of
capturing all reader state in one nested glass-host lambda. To check the resulting DEX with
ART without installing an APK or touching app data, run `tools/verify-reader-dex.ps1`
with `-Apk` and `-Mapping` pointing to that build's release APK and mapping.txt.
Set `ANDROID_HOME` and `JAVA_HOME`; use `-Serial` if several devices are connected.
The tool checks that the APK and mapping have the same R8 ID before loading the
reader classes. This checks class verification, not rendering or reading behavior.
`ReaderDexArgumentBudgetTest` also guards generated JVM method signatures against
growing back toward the failing near-limit capture size; it does not replace ART.
