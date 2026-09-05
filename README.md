# Simple E2EE Chat

A minimal ID-based one-to-one end-to-end encrypted text messenger.

## v1 scope
- Create a unique Messenger ID
- Find another user by ID
- Exchange public keys
- Encrypt message text on the client
- Relay ciphertext through the server
- Decrypt only on the recipient client
- Android APK build through GitHub Actions
- No calls, video, status, stories, groups, or media in v1

## Important: Android backend URL

The Android installable build is configured for the HTTPS backend URL:

`https://simple-e2ee-chat-api-sajju8378.onrender.com`

The previous `http://10.0.2.2:8080` address is only useful when an Android emulator is talking to a server running on the development computer. It is **not** the correct address for a physical phone.

## Deploy the backend

This repository includes `render.yaml` for a Render web-service deployment. Render supports free web services for testing/hobby use, and the service receives a public HTTPS `onrender.com` URL. Free services can sleep after inactivity and their local filesystem is ephemeral, so the included JSON store is for prototype/testing only, not production.

After deployment, verify:

`https://simple-e2ee-chat-api-sajju8378.onrender.com/health`

The response should contain `"ok":true`.

## Security note

This repository is an engineering prototype, not a production-secure messenger. The v1 transport uses client-side authenticated encryption and key lookup. A later hardening phase should add a formally specified asynchronous session protocol, key verification, replay protection, device management, encrypted local persistence, abuse controls, rate limiting, durable server storage, and independent security review before public production use.

## Planned platforms
- Web client for development and testing
- Android application
- Backend relay/key directory

## Repository layout
```text
apps/web/          web client
apps/android/      Android app
server/            API and relay server
crypto/            client crypto/protocol helpers
docs/              architecture and threat model
.github/workflows/ CI and Android build
render.yaml        backend deployment configuration
```
