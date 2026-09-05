# Architecture

## v1 goal
A simple one-to-one text messenger using a user-facing ID. Message plaintext and private keys stay on the client.

## Flow
1. Client creates a local identity key pair.
2. Client registers an ID plus public identity material with the backend.
3. Sender looks up the recipient ID and gets public material.
4. Sender encrypts plaintext locally using authenticated encryption.
5. Backend stores/relays only the encrypted envelope and routing metadata.
6. Recipient fetches the envelope and decrypts locally.

## Important boundary
The backend is not a message decryption service. It must never receive client private keys or plaintext message bodies.

## v1 limitations
The first implementation is intentionally simpler than Signal. It should not be marketed as a fully audited Signal-equivalent protocol. The hardening roadmap includes an asynchronous authenticated key-agreement protocol, ratcheting, key verification, replay protection, device lifecycle management, encrypted local databases, and security audit.

## Android strategy
The Android app will use the same backend API and protocol package. Private identity material will be stored using Android secure storage. APK builds will be produced by GitHub Actions once the Android project is added.
