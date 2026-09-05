# Simple E2EE Chat

A minimal ID-based one-to-one end-to-end encrypted text messenger.

## Scope (v1)
- Create a unique messenger ID
- Find another user by ID
- Exchange public keys
- Encrypt message text on the client
- Relay ciphertext through the server
- Decrypt only on the recipient client
- No calls, video, status, stories, groups, or media in v1

## Security note
This repository is an engineering prototype, not a production-secure messenger. The v1 transport uses client-side authenticated encryption and key lookup. A later hardening phase should add a formally specified asynchronous session protocol, key verification, replay protection, device management, encrypted local persistence, abuse controls, and independent security review before public production use.

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
```
