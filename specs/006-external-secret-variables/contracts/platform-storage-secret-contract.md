# Contract: PlatformStorage Secret Storage Extensions

## API Contract

Located in `ui-shared/platform/PlatformApi.kt` and platform actuals (`PlatformApi.desktop.kt`).

```kotlin
package io.github.crustack.clapi.ui.shared.platform

expect object PlatformStorage {
    // Existing plaintext storage operations
    fun putString(key: String, value: String)
    fun getString(key: String): String?
    fun remove(key: String)

    // New encrypted storage operations backed by ksafe
    /**
     * Stores [value] securely encrypted using AES-GCM and platform-native key custody.
     * Guaranteed safe for tokens, passphrases, and credentials.
     */
    fun putSecretString(key: String, value: String)

    /**
     * Retrieves and decrypts the secret previously stored under [key].
     * Returns null if the key does not exist or was cleared.
     */
    fun getSecretString(key: String): String?
}
```

## Security & Operational Guarantees

1. **Confidentiality**: All values stored via `putSecretString` are encrypted using AES-GCM via `ksafe` (`eu.anifantakis:ksafe:3.3.0`).
2. **Key Custody**: Secret encryption keys are anchored into platform key vaults:
   - Linux: FreeDesktop Secret Service (GNOME Keyring / KWallet) with 0700 file fallback if no keyring daemon is present.
   - macOS: Apple Keychain.
   - Windows: DPAPI (Data Protection API).
3. **Synchronous Direct Semantics**: Calls to `putSecretString` and `getSecretString` execute synchronously using `ksafe.putDirect` and `ksafe.getDirect`, preventing thread deadlocks and aligning with `PlatformStorage` conventions.
4. **Clean Removal**: Calling `PlatformStorage.remove(key)` removes entries from both plaintext preferences and `ksafe` encrypted storage.
