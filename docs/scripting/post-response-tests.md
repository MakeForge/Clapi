---
title: Post-Response Testing & Assertions
description: Writing automated tests against HTTP status codes, headers, response times, and JSON payloads in Clapi.
---

# Post-Response Testing & Assertions

<p class="badges">
  <span class="badge badge-protocol">Scripting & Testing</span>
</p>

## Overview

In the **Tests** tab, you can write automated assertions that validate whether the received response meets your API contract. Tests execute immediately upon response arrival, and the results appear in the **Tests** sub-tab in the response viewer.

---

## Writing Assertions

Clapi provides a BDD/Chai-compatible assertion syntax:

```javascript
// Test HTTP status
clapi.test("Status code is 200 OK", function () {
  clapi.expect(clapi.response.status).to.equal(200);
});

// Test response time / latency
clapi.test("Response time is under 500ms", function () {
  clapi.expect(clapi.response.responseTime).to.be.below(500);
});

// Test response headers
clapi.test("Content-Type header is JSON", function () {
  clapi.expect(clapi.response.headers.get("Content-Type")).to.include("application/json");
});

// Test JSON payload structure
clapi.test("User object contains required fields", function () {
  const data = JSON.parse(clapi.response.body);
  clapi.expect(data).to.have.property("id");
  clapi.expect(data.email).to.match(/^[^\s@]+@[^\s@]+\.[^\s@]+$/);
  clapi.expect(data.roles).to.be.an("array").that.includes("admin");
});
```

---

## Extracting Response Tokens into Variables

You can extract tokens from a response body and store them directly into your active environment to authenticate subsequent requests:

```javascript
const response = JSON.parse(clapi.response.body);
if (response.accessToken) {
  clapi.environment.set("authToken", response.accessToken);
  console.log("Updated authToken in active environment");
}
```
