# Threat model (v1)

## Assets
- User private identity keys
- Message plaintext
- Public identity keys and user IDs
- Encrypted message envelopes

## Trust assumptions
- The operating system and client runtime are not compromised.
- Cryptographic primitives and libraries are correctly implemented.
- The backend may be curious or compromised and therefore must not be trusted with plaintext.

## v1 protections
- Plaintext encryption occurs client-side.
- Private keys are never sent to the backend.
- Message transport uses authenticated encryption.

## Known gaps before production
- A complete asynchronous session protocol is not yet specified.
- Key authenticity must be verified to protect against active key-directory attacks.
- Replay protection and message ordering require explicit protocol state.
- Local persistence must be encrypted.
- Account/device recovery needs a carefully defined security model.
- Server metadata can still reveal traffic patterns unless additional privacy mechanisms are added.

This document is a design starting point, not a security audit or guarantee.
