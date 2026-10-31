# Public and official builds

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

To validate the public edition while the private checkout is present:

```powershell
.\gradlew.bat '-Plumi.oss=true' :app:assembleDebug
```

`PremiumAccess` is the shared capability boundary. The public implementation
does not impose remote-source limits. Edition UI functions provide optional
entries; the public implementations render nothing. The excerpt template
boundary keeps the six base styles public. An unavailable saved style falls
back to the reader style without deleting the stored preferences.

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
