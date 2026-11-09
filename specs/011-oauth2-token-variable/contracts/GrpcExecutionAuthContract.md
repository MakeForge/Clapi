# Contract: gRPC Execution with Dynamic Auth Variables

**Module**: `ui-shared` & `core-network`

---

## 1. gRPC Metadata Token Resolution Contract

When a gRPC request is executed from `RequestExecutor.kt`:

1. **Text Collection**:
   - `textsToScan` includes:
     - `effectiveUrl`
     - `effectiveMessageBody`
     - All `effectiveMetadata` keys and values
     - `tab.protoFilePath`
     - `tab.grpcMethod`
     - All OAuth2 configuration fields from tab or inherited auth (URL, client ID, client secret, username, password, scope)
2. **Dynamic Variable Resolution**:
   - `dynamicVariableRegistry.resolveDynamicVariables(textsToScan, dynamicVarContext)` executes prior to building the gRPC call request.
   - Any dynamic variable (e.g. `_auth.oauth2.accessToken`) is resolved and merged into `mergedScopedVars`.
3. **Metadata Interpolation**:
   - In `resolveGrpcCallRequest(...)`, `mergedScopedVars` is passed into `VariableResolver.resolve(...)` for all enabled metadata key/value pairs.
   - For a metadata entry:
     - Key: `authorization`
     - Value: `Bearer {{_auth.oauth2.accessToken}}`
     - Resulting outgoing metadata entry: `authorization: Bearer <resolved_access_token>`.
4. **Transport Invariant**:
   - The dynamic variable placeholder MUST NEVER appear unresolved in the outgoing gRPC wire metadata. If resolution fails or OAuth2 is disabled, execution halts before calling `grpcClient.executeUnary(...)`.
