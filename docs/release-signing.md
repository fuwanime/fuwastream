# Release signing

Every public FuwaStream build must be signed with the same permanent key. Android refuses
to update an installed app whose certificate changed, so the CI build fails instead of
falling back to the debug key.

## Permanent certificate

- Alias: `fuwanime`
- SHA-256: `1C:DB:C5:6C:56:C6:DB:A6:8F:BF:74:C0:DD:E3:7B:42:8B:6F:A9:EC:57:FE:6E:E1:31:6F:D7:00:83:76:75:44`
- Validity: until 2059

The keystore itself is **not** committed. It is stored in the GitHub Actions secrets of
`LT-SYAII/fuwastream`:

| Secret | Purpose |
| --- | --- |
| `KEYSTORE_BASE64` | Base64 of the `.jks` file |
| `KEYSTORE_PASSWORD` | Store and key password |
| `KEY_ALIAS` | `fuwanime` |

## Local release build

```bash
export KEYSTORE_PATH=/secure/path/fuwanime-release.jks
export KEYSTORE_PASSWORD='…'
export KEY_ALIAS=fuwanime
gradle assembleRelease bundleRelease
```

Without those variables the release task fails with a clear message; debug builds are
unaffected.

## One-time migration from the old debug-signed APK

Builds up to `2.0.0-build.1` were signed with the runner's debug key. Android cannot update
them in place:

1. Open **Cadangkan sekarang** in Settings and save a backup to a folder.
2. Uninstall the old FuwaStream once.
3. Install the new APK from the release page.
4. Restore the backup.

All later releases keep the same certificate, so updates work normally again.
