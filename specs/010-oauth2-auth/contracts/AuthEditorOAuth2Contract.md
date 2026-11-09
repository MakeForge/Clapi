# Contract: Auth Editor OAuth2 UI

## Component: `AuthEditor` (`ui-shared`)

### 1. Auth Type Selection
- The auth type selector chips MUST include `OAuth 2.0` (`AuthType.OAUTH2`).
- Clicking `OAuth 2.0` sets `tab.authType = AuthType.OAUTH2` and marks the tab dirty.

### 2. Form Controls
When `tab.authType == AuthType.OAUTH2`, the editor renders:

1. **Flow Type Selector**:
   - Chips or dropdown for `Client Credentials` (`OAuth2Flow.CLIENT_CREDENTIALS`) and `Password` (`OAuth2Flow.RESOURCE_OWNER_PASSWORD_CREDENTIALS`).
2. **Access Token URL**:
   - Text field supporting variable autocomplete and resolution.
3. **Client ID**:
   - Text field supporting variable autocomplete.
4. **Client Secret**:
   - Masked password field with show/hide toggle supporting variable autocomplete.
5. **Client Authentication Placement**:
   - Radio/chips for `Basic Auth Header` (`basic_auth_header`) vs. `Request Body` (`body`).
6. **Scope (Optional)**:
   - Text field for space-delimited OAuth2 scopes.
7. **Resource Owner Credentials (Only visible when Password flow is selected)**:
   - **Username**: Text field supporting variables.
   - **Password**: Masked password field with show/hide toggle supporting variables.

### 3. Token Testing & Inspection Section
Below credential inputs, a dedicated testing section is rendered:
- **"Fetch Tokens" Button**:
  - Triggers asynchronous token acquisition using the currently configured parameters and active variable layers.
  - While fetching, disables the button and displays a progress indicator.
  - On error, displays a red banner or message with the error description.
- **Copyable Token Displays**:
  - **Access Token**: Read-only text field with a Copy icon button. Clicking copy writes token to clipboard.
  - **Refresh Token**: Read-only text field with Copy icon button (visible if returned).
  - **Identity Token (ID Token)**: Read-only text field with Copy icon button (visible if returned).
- **Token Status**:
  - Displays expiration state (e.g., "Active (expires in 4m 32s)" or "Expired" or "No active token").
