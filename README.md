# Simple E2EE Chat

A minimal ID-based one-to-one end-to-end encrypted text messenger.

## Current v1: two-phone chat
- Create a unique Messenger ID on each phone
- Copy/share the ID with the other person
- Find the other user by ID
- Exchange public keys through the relay
- Encrypt message text on the sender phone
- Relay only the encrypted message envelope
- Decrypt the message on the recipient phone
- Poll for new messages every 3 seconds
- No calls, video, status, stories, groups, or media yet

## Deploy the backend

The Android app needs a public HTTPS backend for real phones. This repository includes `render.yaml` for a Render web-service deployment.

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/sajju8378/simple-e2ee-chat)

After deployment, verify the service health endpoint:

`https://simple-e2ee-chat-api-sajju8378.onrender.com/health`

The response should contain `"ok":true`.

The Android installable build is configured for:

`https://simple-e2ee-chat-api-sajju8378.onrender.com`

The old `http://10.0.2.2:8080` address is only for an Android emulator talking to a server on the development computer. It is not the correct address for a physical phone.

Render's free web service tier is suitable for testing/hobby use. Free services can sleep after inactivity and their local filesystem is ephemeral, so the current JSON store is a prototype/testing datastore, not production persistence.

## Two-phone test

1. Install the APK on **Phone A**.
2. Create a new account with a display name and an 8+ character password.
3. The app generates an `E2E-XXXXXXXX` Messenger ID and keeps the private key on that phone.
4. Tap **Copy my ID** and send the ID to Phone B.
5. Install the same APK on **Phone B** and create a different account.
6. On either phone, enter the other phone's Messenger ID and tap **Find friend & open chat**.
7. Send a message with **Send securely**.
8. The recipient phone polls the server and decrypts the message locally.

For testing, use two different accounts/IDs. The current prototype intentionally does not restore an account's private key to a second device.

## Android APK

GitHub Actions builds the Android debug APK and also runs a backend `/health` smoke test before the Android build. The artifact is named `simple-e2ee-chat-debug-apk`.

## Security note

This repository is an engineering prototype, not a production-secure messenger. The v1 transport uses client-side authenticated encryption and key lookup. A later hardening phase should add a formally specified asynchronous session protocol, key verification, replay protection, device management, encrypted local persistence, abuse controls, rate limiting, durable server storage, and independent security review before public production use.

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
