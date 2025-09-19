---
id: passwordless-authentication-wtih-webauthn
title: "Securing Your APIs: Passwordless Authentication with WebAuthn"
sidebar_label: "Passwordless Authentication with WebAuthn"
---

## Roles (Who Talks to Whom)

1. Relying Party
2. Webauthn Client (browser)

Relying Party (RP / your server) ↔ Browser (Client) ↔ Authenticator (platform: Touch ID/Windows Hello/Android; or roaming: security key).

```scala
[RP backend] --(options+challenge)--> [Browser] --CTAP--> [Authenticator]
[Authenticator] --(pubkey/attestation or signature)--> [Browser] --(to RP)--> [RP]
```

## Goal

Goal: bind a key pair to a site (RP ID) so later the authenticator proves “it’s me and I’m at the right site” by signing a fresh challenge.

WebAuthn uses public key cryptography; the user's private key never leaves their device.

credentials are scoped to a specific domain => credentials for example.com can’t be used on anothersite.com

it utilizes authenticators:
1. confirming user's identity
2. creating highly secure public-private key pair credentials

seamless experience => users can authenticate without passwords with biometrics, PINs, or security keys or a gesture


##

Signature functionality → How authenticators sign authentication challenges with private keys.
Attestation functionality → How they prove their identity and properties to websites.
Attested → can prove their origin and authenticity,
Scoped → limited to one website/service,
Public key-based → cryptographic, not password-based.


5. Privacy across Relying Parties is maintained.
Claim: Websites cannot learn about credentials you created for other sites.
Explanation: Each Relying Party has a separate “silo” of credentials. For instance:
bank.com cannot ask “does this user have a credential with shopping.com?”
shopping.com cannot detect what authenticators you used with bank.com.

WebAuthn uses asymmetric (public-key) cryptography instead of passwords or SMS texts for registering, authenticating, and multi-factor authentication with websites.



Protection against phishing: An attacker who creates a fake login website can't login as the user because the signature changes with the origin of the website.
Reduced impact of data breaches: Developers don't need to hash the public key, and if an attacker gets access to the public key used to verify the authentication, it can't authenticate because it needs the private key.
Invulnerable to password attacks: Some users might reuse passwords, and an attacker may obtain the user's password for another website (e.g., via a data breach). Also, text passwords are much easier to brute-force than a digital signature.




associating a key with an account
yubico => implements server sides of the protocol => we need to decide do I trust the authenticator and there is lots of little options that you can turn on and off . There is a f 

1. understand passkey web authentication user experience 
2. understand how the web browser api works
3. understand authorization server
4. how to bring all these together


## Core Data Objects

Challenge: RP-generated random bytes (anti-replay).

clientDataJSON: JSON made by the browser; includes type (webauthn.create or .get), challenge (base64url), and origin. RP verifies its SHA-256 and contents.

authenticatorData: Bytes from the authenticator:

rpIdHash = SHA-256(RP ID, e.g., example.com)

flags (notably UP=User Presence, UV=User Verification, AT=Attested credential data present, ED=extensions)

signCount (monotonic counter, best-effort)

optional attestedCredentialData (on registration) and extensions

COSE public key: CBOR-encoded key (e.g., ES256: P-256 ECDSA; others exist).

Signature: Over well-defined bytes (details below).

