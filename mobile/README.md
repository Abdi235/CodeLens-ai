# SecureAI Mobile

React Native (Expo) client for the SecureAI Spring Boot API.

## Prerequisites

- Node.js 20+
- Expo CLI (`npx expo`)
- Android emulator, iOS simulator, or Expo Go on a device
- SecureAI backend running (Docker Compose or local Spring Boot on port 8080)

## Configure API URL

Set the backend URL before starting Expo:

```bash
# Windows PowerShell
$env:EXPO_PUBLIC_API_URL="http://localhost:8080"

# macOS/Linux
export EXPO_PUBLIC_API_URL=http://localhost:8080
```

For a physical device, use your machine's LAN IP (e.g. `http://192.168.1.10:8080`).

## Run

```bash
cd mobile
npm install
npx expo start
```

## Features

- Register / sign in (JWT)
- Submit repository for analysis (`POST /api/analysis`)
- Poll job status (`GET /api/analysis/{jobId}`)
- View findings and remediation (`GET /api/analysis/{jobId}/results`)
