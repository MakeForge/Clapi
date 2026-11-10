# RTK — Token-Optimized CLI

**rtk** is a CLI proxy that filters and compresses command outputs, saving 60-90% tokens.

## Rule

Always prefix shell commands with `rtk`:

```bash
# Instead of:              Use:
git status                 rtk git status
git log -10                rtk git log -10
cargo test                 rtk cargo test
docker ps                  rtk docker ps
kubectl get pods           rtk kubectl pods
```

## Build & Test Execution Rule
Whenever you run Gradle commands (such as tests, builds, linting, or formatting checks), **always omit the `./` prefix and always prefix with `rtk`** so output is token-filtered.

Examples:
- Instead of `./gradlew test` -> Use `rtk gradlew test`
- Instead of `./gradlew :app:testDebugUnitTest` -> Use `rtk gradlew :app:testDebugUnitTest`
- Instead of `./gradlew ktfmtCheck` -> Use `rtk gradlew ktfmtCheck`
- Never use raw `./gradlew` unless debugging command output issues.
- 
## Meta commands (use directly)

```bash
rtk gain              # Token savings dashboard
rtk gain --history    # Per-command savings history
rtk discover          # Find missed rtk opportunities
rtk proxy <cmd>       # Run raw (no filtering) but track usage
```

## IntelliJ MCP
You can utilize connected IntelliJ MCP to e.g. easily search through external library code, find files more easily. Leverage it whenever useful.