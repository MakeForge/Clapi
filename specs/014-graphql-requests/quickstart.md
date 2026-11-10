# Quickstart & Verification Guide: GraphQL Requests

**Feature**: `014-graphql-requests`  
**Date**: 2026-11-10  

This guide outlines end-to-end verification scenarios to validate GraphQL request creation, editing, execution, and OpenCollection persistence.

---

## 1. Automated Verification (`qa-tests` & Unit Tests)

### Scenario 1.1: Live GraphQL Execution Against Dummy Server
- **Target**: `DummyApiServer` local endpoint `POST /graphql`
- **Execution Command**:
  ```bash
  ./gradlew :qa-tests:jvmTest --tests "io.github.crustack.clapi.qa.GraphQLRequestE2ETest"
  ```
- **Expectation**:
  - Sends a GraphQL query (`query { users { id name } }`) with variables (`{"limit": 10}`).
  - Successfully receives HTTP 200 with JSON payload containing `{"data": {"echo": ..., "users": [...]}}`.
  - Headers, authentication tokens, and response timing are validated.

### Scenario 1.2: OpenCollection Round-Trip Serialization
- **Target**: `OpenCollectionRoundTripE2ETest`
- **Execution Command**:
  ```bash
  ./gradlew :qa-tests:jvmTest --tests "io.github.crustack.clapi.qa.OpenCollectionRoundTripE2ETest"
  ```
- **Expectation**:
  - Collection document containing `GraphQLRequestItem` (with headers, params, body variants, auth, settings, docs) serializes to JSON.
  - Exported JSON strictly satisfies the OpenCollection v1.0.0 JSON schema.
  - Re-imported object preserves 100% of properties identical to original `GraphQLRequestItem`.

### Scenario 1.3: Core Model Serialization Tests
- **Target**: `core-model` tests
- **Execution Command**:
  ```bash
  ./gradlew :core-model:allTests --tests "*GraphQL*"
  ```
- **Expectation**:
  - Unit tests verifying `GraphQLRequestItemSerializer` polymorphic deserialization for single body and variants array pass cleanly.

---

## 2. Interactive UI Verification

### Scenario 2.1: Creation and Visual Badge
1. Launch Clapi desktop:
   ```bash
   ./gradlew :ui-desktop:run
   ```
2. In the collection sidebar, click `+` on any folder or collection.
3. Select **"New GraphQL Request"**.
4. **Verification**:
   - New item appears in the tree with `"GQL"` badge styled in `#e10098`.
   - Clicking opens `GraphQLRequestEditor` tab with the matching `"GQL"` badge in the tab bar.

### Scenario 2.2: Authoring Query, Variables & Execution
1. In the address bar, enter `{{baseUrl}}/graphql` with method `POST`.
2. In the **Body** sub-tab:
   - In Query editor, enter:
     ```graphql
     query GetUsers($id: ID!) {
       user(id: $id) {
         id
         name
       }
     }
     ```
   - In Variables editor, enter:
     ```json
     {
       "id": "1"
     }
     ```
3. Click the format button to test syntax formatting and fold regions.
4. Click **Send** (or Ctrl+Enter / Cmd+Enter).
5. **Verification**:
   - Status badge shows `200 OK` with latency in ms.
   - Response pane shows formatted JSON response with data.
   - Tab dirty state updates and saves properly with Ctrl+S.
