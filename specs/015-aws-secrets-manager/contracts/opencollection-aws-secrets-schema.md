# Contract: OpenCollection AWS Secrets Manager Schema

## Purpose
Specifies the serialization and deserialization schema for AWS Secrets Manager external secret variables within OpenCollection v1.0.0 documents.

## Schema Definition

### OpenCollection JSON Format
```json
{
  "opencollection": "1.0.0",
  "config": {
    "environments": [
      {
        "name": "Staging",
        "externalSecrets": {
          "type": "aws-secrets-manager",
          "variables": [
            {
              "name": "gateway_client_id",
              "secretName": "local/payment/foo",
              "targetField": "client_id",
              "customFilters": {
                "environment": "{{stage}}"
              },
              "description": {
                "content": "Payment Gateway Client ID"
              },
              "disabled": false
            },
            {
              "name": "gateway_client_secret",
              "secretName": "local/payment/foo",
              "targetField": "client_secret",
              "disabled": false
            }
          ]
        }
      }
    ]
  }
}
```

### OpenCollection YAML Format
```yaml
opencollection: 1.0.0
config:
  environments:
    - name: Staging
      externalSecrets:
        type: aws-secrets-manager
        variables:
          - name: gateway_client_id
            secretName: local/payment/foo
            targetField: client_id
            customFilters:
              environment: "{{stage}}"
            description:
              content: Payment Gateway Client ID
            disabled: false
          - name: gateway_client_secret
            secretName: local/payment/foo
            targetField: client_secret
            disabled: false
```

## Credential Leakage Prohibition Rules

Under no circumstances may the following sensitive values or keys be serialized into OpenCollection files:
- `accessKeyId`, `awsAccessKeyId`, `AWS_ACCESS_KEY_ID`
- `secretAccessKey`, `awsSecretAccessKey`, `AWS_SECRET_ACCESS_KEY`
- `sessionToken`, `awsSessionToken`, `AWS_SESSION_TOKEN`
- Plain text secret payloads (e.g. `client_secret` value `"sk_live_secret_456"`)
- Local or workstation-specific endpoint override URLs (e.g. `http://localhost:4566`)
