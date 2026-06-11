# IAM Mock

Simple IAM mock service for development. Issues JWTs via Basic Auth.

## Mock Users

| Username   | Password | User-ID | Rolle    |
|------------|----------|---------|----------|
| `patient1` | `pass123` | `00000000-0000-0000-0000-000000000001` | patient |
| `patient2` | `pass123` | `00000000-0000-0000-0000-000000000002` | patient |
| `patient3` | `pass123` | `00000000-0000-0000-0000-000000000003` | patient |
| `patient4` | `pass123` | `00000000-0000-0000-0000-000000000004` | patient |
| `doctor1`  | `pass123` | `10000000-0000-0000-0000-000000000001` | doctor  |

## API

```
POST /authenticate
Authorization: Basic base64(username:password)
```

**Success (200):**
```json
{
  "token": "eyJhbGciOi...",
  "userId": "00000000-0000-0000-0000-000000000001",
  "role": "patient"
}
```

JWT-Payload: `sub` (userId), `role`, `iat`, `exp` (1h). Secret: `iam-mock-secret`.

**Invalid credentials (401):** `{"error": "Invalid credentials"}`

## Quick Start

```bash
docker compose up -d iam-mock --wait
curl -X POST http://localhost:9090/authenticate -u patient1:pass123
```

Der Service ist im compose network als `iam-mock:8080` erreichbar.
