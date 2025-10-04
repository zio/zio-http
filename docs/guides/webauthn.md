---
id: passwordless-authentication-with-webauthn
title: "Securing Your APIs: Passwordless Authentication with WebAuthn"
sidebar_label: "Passwordless Authentication with WebAuthn"
---

Passwords have long been the cornerstone of online authentication, but they come with significant drawbacks. Users often struggle to create and remember strong passwords, leading to weak security practices such as password reuse and susceptibility to phishing attacks. To address these challenges, the industry is shifting toward passwordless authentication—a model that enhances both security and user experience by eliminating passwords altogether.

At the forefront of this movement are WebAuthn (Web Authentication API) and the CTAP2 standard (Client to Authenticator Protocol 2) under the FIDO2 umbrella. These technologies leverage public-key cryptography and device-based authenticators to provide a robust, phishing-resistant authentication mechanism. WebAuthn is a web standard published by the World Wide Web Consortium (W3C) that enables web clients to communicate with authenticators. CTAP2 defines how a client (like your PC, phone, or browser) communicates with an authenticator. Together, they form the foundation of FIDO2, which enables us to move beyond passwords.

In this article, we are going to explore how to implement passwordless authentication. As our client will be a web browser, we don't need to interact directly with CTAP2; it's handled by the browser under the hood. So we will focus on WebAuthn, which is the web API that browsers expose to web applications, and we will call it to create and use passwordless credentials.

This guide explores the implementation of WebAuthn using the [Yubico WebAuthn library](https://github.com/Yubico/java-webauthn-server), demonstrating both passkey registration and authentication flows. The implementation showcases how modern web applications can eliminate passwords while enhancing both security and user experience.

## Requirements

To follow along with this guide, you will need the following tools and libraries:

1. Latest version of Google Chrome, Mozilla Firefox, Microsoft Edge, or Apple Safari
2. A FIDO2-compliant authenticator (e.g., YubiKey)
3. Yubico's WebAuthn library

Please add the following dependency to your build.sbt file:

```scala
libraryDependencies += "com.yubico" % "webauthn-server-core"        % "2.7.0"
libraryDependencies += "com.yubico" % "webauthn-server-attestation" % "2.7.0"
```

## Key Concepts

Before diving into the details of how WebAuthn works and the implementation details, let's define some key concepts and terminology:

1. **Relying Party (RP)**: The server or service that wants to authenticate the user and is responsible for generating challenges and verifying responses.
2. **Client (User Agent)**: The web browser or application that mediates between the Relying Party and the Authenticator using the WebAuthn API.
3. **Authenticator**: A device or software that proves your identity by generating or storing credentials (like cryptographic keys, passwords, or biometric data).
   - Platform authenticator: Built into the device (e.g., Touch ID, Windows Hello, Android biometrics)
   - Roaming authenticator: External devices that can be used across multiple platforms (e.g., YubiKey, FIDO2 USB Keys)
4. **Challenge**: A unique, server-generated random value sent to the authenticator to be signed, ensuring **the freshness of each authentication attempt** (preventing replay attacks) and allowing the relying party to **confirm the response truly comes from the rightful account owner's authenticator that has the right private key**.
5. **User Presence**: A flag that the relying party can request to ensure that the user is physically present during the authentication process (e.g., by touching the security key or using biometrics).
6. **User Verification**: A flag that the relying party can request to ensure that the user has been verified by the authenticator (e.g., using a PIN or biometric verification). It is more stringent than user presence, which only requires a simple action like touching the authenticator.
7. **Credential ID**: A unique identifier for a credential (public/private key pair) created by the authenticator during registration. The relying party uses this ID to look up the associated public key during authentication.

## FIDO2: The Umbrella Project

FIDO2 is an umbrella project that encompasses two main components: **WebAuthn** and **CTAP2**. Together, they enable passwordless authentication using public-key cryptography and device-based authenticators.

WebAuthn is part of the FIDO standard, which is published by the World Wide Web Consortium (W3C). It is a JavaScript API that enables web clients to interact with authenticators. It has two main operations:

- **[`navigator.credentials.create()`](https://developer.mozilla.org/en-US/docs/Web/API/CredentialsContainer/create)**: Takes registration options and asks the authenticator to create a new credential (a pair of private and public keys).
- **[`navigator.credentials.get()`](https://developer.mozilla.org/en-US/docs/Web/API/CredentialsContainer/get)**: Takes authentication options and asks the authenticator to sign a given challenge with the private key.

We will discuss these operations in more detail later.

CTAP2 is a client-to-authenticator protocol that defines how a client (like your PC, phone, or browser) communicates with an authenticator. As we are going to write a web application, we don't need to interact directly with CTAP2; it's handled by the browser under the hood.

## Why and How WebAuthn is Secure

WebAuthn's security architecture represents a paradigm shift from password-based authentication, addressing fundamental vulnerabilities that have plagued online security for decades. In this guide, we aren't going to cover security in depth, but the key aspects that make WebAuthn a robust and secure authentication mechanism are:

1. At its core, WebAuthn uses asymmetric cryptography, where each user has a unique public/private key pair. The private key remains securely stored on the user's device (the authenticator), while the public key is registered with the service (the relying party). This design ensures that even if a service's database is compromised, attackers cannot derive the private keys, rendering stolen data useless for authentication.
2. During authentication, the server issues a unique, random challenge that the authenticator must sign using its private key. This challenge-response mechanism ensures that only the legitimate user can authenticate, as the private key never leaves the device. Additionally, the challenge is unique for each authentication attempt, preventing replay attacks.
3. WebAuthn is architecturally designed to be phishing-resistant through origin binding. When you want to log in with WebAuthn, the authenticator checks the website's origin against the origin you registered with. If there is a subtle difference that the human eye might not notice, the authenticator will refuse to sign the challenge. This mechanism effectively thwarts phishing attacks, as attackers cannot trick users into authenticating on fraudulent sites.

## Discoverable and Non-Discoverable Credentials

Let's define what discoverable and non-discoverable credentials are:

1. **Non-discoverable credentials**: These credentials require the user to provide a username or identifier during the authentication process. When the user attempts to log in, they must first enter their username, which the relying party uses to look up the associated credentials. The relying party asks the browser to use them in the authentication process. In this type of credential, the authenticator may not know which credential to use until the relying party tells it. Please note that sometimes non-resident keys are also called non-discoverable credentials; this is because these types of credentials are not required to be stored on the authenticator devices. Instead, it is the responsibility of the relying party to store them. They only have an internal mechanism to derive the private key from their master secret and the credential information that the relying party provides.
2. **Discoverable Credentials**: These credentials can be used without the user providing a username or identifier. The authenticator itself stores the user information alongside the credential, allowing it to identify and use the correct credential during authentication. This enables a more seamless and user-friendly experience, as users can authenticate without needing to remember the username. During authentication, the authenticator prompts the user to select from a list of available credentials. These types of credentials are also called resident keys because they are stored on the authenticator device itself.

There is another term you may hear in this context: passkey. Passkeys are discoverable credentials that can be synced across devices through providers such as Google Password Manager, iCloud Keychain, and Windows Hello. So they are not device-bound and can be used from any device.

In this guide, we will cover both types of credentials and demonstrate how to implement them using the Yubico WebAuthn library.

## Registration and Authentication Flows

After learning the key concepts, we are ready to explore the registration and authentication flows, but in the simplest form.

## Implementation Overview

Based on the registration and authentication flows, the client and server sides each have distinct responsibilities:

1. Client-side (Browser) is responsible for communicating between the Relying Party (your server) and the Authenticator to:
     - Create new credentials (public/private key pair)
     - Sign challenges to prove the authenticator possesses the private key
2. Server-side is responsible for:
   - Generating a challenge
   - Verifying the registration and authentication responses from the client
   - Storing and managing user credentials (public keys)
   - Managing user sessions

## Registration Flow Implementation

The **registration flow** involves the following steps:

1. **Client (Browser)** requests registration options from the **Relying Party** (your server).
2. **Relying Party** generates a challenge and sends registration options to the **Client**.
3. **Client** invokes the authenticator to create a new credential (public/private key pair).
4. **Authenticator** creates the credential and returns it to the **Client**.
5. **Client** sends the credential to the **Relying Party**.
6. **Relying Party** verifies the credential and stores the public key.
7. If verification is successful, the **Relying Party** associates the public key with the user's account.
8. **Relying Party** confirms successful registration to the **Client**.
9. **Client** confirms successful registration to the User.

To manage the registration process, we define a `WebAuthnService` trait with two methods: `startRegistration` and `finishRegistration`:

```scala
trait WebAuthnService {
  def startRegistration(request: RegistrationStartRequest): IO[String, RegistrationStartResponse]
  def finishRegistration(request: RegistrationFinishRequest): IO[String, RegistrationFinishResponse]
}
```

1. The `startRegistration` method is responsible for generating public key creation options to be sent to the client. The client uses these options to prompt the authenticator to create a new credential.
2. The `finishRegistration` method is responsible for verifying the response from the client and storing the public key associated with the user to complete the registration process.

Each of these two methods corresponds to an API endpoint:

1. `POST /api/webauthn/registration/start`: This endpoint initiates the registration process by generating and returning the public key creation options to the client.
2. `POST /api/webauthn/registration/finish`: This endpoint completes the registration process by verifying the response from the client.


Let's start by implementing the start and finish routes for the registration flow.

### Registration Routes

#### 1. Start Route

The start endpoint is responsible for generating the registration options and sending them to the client:

```scala
val registrationStartRoute =
  Method.POST / "api" / "webauthn" / "registration" / "start"  -> handler { (req: Request) =>
    for {
      request  <- req.body.to[RegistrationStartRequest]
      response <- webauthn.startRegistration(request.username)
    } yield Response.json(response.toJson)
  }
```

It takes a `RegistrationStartRequest` object containing the username, and then it calls the `startRegistration` method of the `WebAuthnService` and returns a `PublicKeyCredentialCreationOptions` object containing the registration options. The `RegistrationStartRequest` and `RegistrationStartResponse` types are defined as follows:

```scala
case class RegistrationStartRequest(username: String)

type RegistrationStartResponse =
   com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
```

1. **`RegistrationStartRequest`** contains the username of the user who wants to register a new credential.
2. **`RegistrationStartResponse`** is a type alias of `PublicKeyCredentialCreationOptions` from the Yubico WebAuthn library, which contains the public key credential options that the client needs to create a new credential.

What are registration options? They are all the information the client needs to create a new key pair credential. It includes the challenge, relying party information, user information, and other parameters.

To deserialize the request body into a `RegistrationStartRequest` object, we need to define a JSON codec for it:

```scala
import zio.json._

object RegistrationStartRequest {
   implicit val codec: JsonCodec[RegistrationStartRequest] = DeriveJsonCodec.gen
}
```

As the `RegistrationStartResponse` data type is a type alias of `PublicKeyCredentialCreationOptions`, it supports JSON serialization out of the box. We can directly convert it to JSON using the `PublicKeyCredentialCreationOptions#toJson` method.


This route is responsible for generating the [`PublicKeyCredentialCreationOptions`](https://developer.mozilla.org/en-US/docs/Web/API/PublicKeyCredentialCreationOptions) which looks like this:

```json
{
  "rp": {
    "name": "WebAuthn Demo",
    "id": "localhost"
  },
  "user": {
    "name": "john",
    "displayName": "john",
    "id": "OTk4NDdkZjItZTRkNy00N2YyLTgxNTEtZTljY2FhODhkZTZl"
  },
  "challenge": "KvgPsSdLEyhLaB9r9m3EQs6UyunnPHopdGmM1G2SlE4",
  "pubKeyCredParams": [
    {
      "alg": -8,
      "type": "public-key"
    },
    {
      "alg": -7,
      "type": "public-key"
    },
    {
      "alg": -257,
      "type": "public-key"
    }
  ],
  "timeout": 60000,
  "hints": [],
  "authenticatorSelection": {
    "requireResidentKey": true,
    "residentKey": "required",
    "userVerification": "required"
  },
  "attestation": "none",
  "extensions": {}
}
```

Here is an overview of each field:

1. **`rp`**: Contains information about the relying party (your server), including its name and ID (domain). The `rp.id` must match the domain you want the credential scoped to (no scheme, no port), e.g., `example.com` or `login.example.com`. If you set it to `login.example.com`, the credential will not work on `example.com` or `shop.example.com`. Conversely, if you set it to `example.com`, the credential will work on all subdomains. The `rp.name` is a human-readable name for the relying party, which is shown to the user in the authenticator UI.
2. **`user`**: Contains information about the user, including their username, display name, and a unique identifier (ID). The `user.id` is a server-generated unique identifier for the user, encoded as a base64url string. Please note that the "user id" is also called "user handle" in the WebAuthn specification, so they are the same thing. The `user.name` is the username of the user, and the `user.displayName` is a human-readable name for the user, which is shown to the user in the authenticator UI.
3. **`challenge`**: A server-generated random value (base64url-encoded) that the authenticator must sign to prove possession of the private key.
4. **`pubKeyCredParams`**: An array of objects specifying the acceptable public key algorithms for the credential. Each object contains an `alg` field (COSE algorithm identifier) and a `type` field (always "public-key").
5. **`timeout`**: The time (in milliseconds) that the client should wait for the user to complete the registration process.
6. **`hints`**: An array of strings that provide hints to the client (browser) about which type of authenticator to use and what the order of our preference is.
7. **`authenticatorSelection`**: An object that specifies criteria for selecting the authenticator. It includes:
   - `requireResidentKey`: A boolean indicating whether a resident key (discoverable credential) is required.
   - `residentKey`: A string indicating the requirement level for resident keys ("required", "preferred", or "discouraged").
   - `userVerification`: A string indicating the requirement level for user verification ("required", "preferred", or "discouraged").
8. **`attestation`**: Defines the attestation conveyance preference, which indicates how the relying party wants to receive attestation information from the authenticator. It can be "none", "indirect", or "direct". In this example, we set it to "none" to simplify the process and avoid dealing with attestation verification.
9. **`extensions`**: An object that can contain additional extension data for the registration process. In this example, we leave it empty.

#### 2. Finish Route

After the client sends the registration start request and receives the registration options, it invokes the authenticator to create a new credential. The authenticator returns the credential to the client, which then sends it to the relying party to finish the registration ceremony. So we have to implement the finish endpoint to handle this request.

The finish endpoint is responsible for verifying the response from the client and storing the public key:

```scala
val registrationFinishRoute =
  Method.POST / "api" / "webauthn" / "registration" / "finish" -> handler { (req: Request) =>
    for {
      body    <- req.body.asString
      request <- ZIO.fromEither(body.fromJson[RegistrationFinishRequest])
      result  <- webauthn.finishRegistration(request).orElseFail {
        Response(
          status = Status.InternalServerError,
          body = Body.fromString(s"Registration failed!"),
        )
      }
    } yield Response(body = Body.from(result))
  }
```

It takes a `RegistrationFinishRequest` object containing the response from the client, then it calls the `finishRegistration` method of the `WebAuthnService`, and returns a `RegistrationFinishResponse` object indicating whether the registration was successful. The `RegistrationFinishRequest` and `RegistrationFinishResponse` types are defined as follows:

```scala
case class RegistrationFinishRequest(
  username: String,
  userhandle: String,
  publicKeyCredential: PublicKeyCredential[AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs],
)

case class RegistrationFinishResponse(
  success: Boolean,
  credentialId: String,
)
```

1. The `RegistrationFinishRequest` contains a username, user handle, and the `PublicKeyCredential` object returned by the authenticator. The `PublicKeyCredential` is a generic type that takes two type parameters: the response type and the extension outputs type. In this case, we use `AuthenticatorAttestationResponse` as the response type and `ClientRegistrationExtensionOutputs` as the extension outputs type.
2. The `RegistrationFinishResponse` contains a success flag and the credential ID of the newly created credential.

:::note
Please note that we have two types of authenticator responses:
1. `AuthenticatorAttestationResponse`: Used during the **registration** ceremony and contains the attestation object and client data JSON.
2. `AuthenticatorAssertionResponse`: Used during the **authentication** ceremony and contains the authenticator data, client data JSON, signature, and user handle.

As we are implementing the registration flow, we use `AuthenticatorAttestationResponse`.
:::

Here is an example response from the authenticator after the client prompts the authenticator to create a new credential:

```json
{
  "username": "milad",
  "userhandle": "cfaf4026-21e7-4bfb-909c-9b514fc52ac4",
  "publicKeyCredential": {
    "authenticatorAttachment": "cross-platform",
    "clientExtensionResults": {},
    "id": "xil3bQiqYNMnsTD6JV6LZA",
    "rawId": "xil3bQiqYNMnsTD6JV6LZA",
    "response": {
      "attestationObject": "o2NmbXRkbm9uZWdhdHRTdG10oGhhdXRoRGF0YViUSZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2NdAAAAAOqbjWZNAR0hPOS2tIy1ddQAEMYpd20IqmDTJ7Ew-iVei2SlAQIDJiABIVgggd6fPJYYYdbHuIwo_F3NhNtQS0NkK71IEN_hasDbLxUiWCAfUA-DngJiyOy2X2Ze4qWp6zIZA2wG5ymfS3zBMHd8VA",
      "authenticatorData": "SZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2NdAAAAAOqbjWZNAR0hPOS2tIy1ddQAEMYpd20IqmDTJ7Ew-iVei2SlAQIDJiABIVgggd6fPJYYYdbHuIwo_F3NhNtQS0NkK71IEN_hasDbLxUiWCAfUA-DngJiyOy2X2Ze4qWp6zIZA2wG5ymfS3zBMHd8VA",
      "clientDataJSON": "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIiwiY2hhbGxlbmdlIjoiSzg2RUZVMTdMSzB5LXFMOVh0dDJYLXdyMnVaaUtxaC13bFJtRlpUWGxGdyIsIm9yaWdpbiI6Imh0dHA6Ly9sb2NhbGhvc3Q6ODA4MCIsImNyb3NzT3JpZ2luIjpmYWxzZX0",
      "publicKey": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEgd6fPJYYYdbHuIwo_F3NhNtQS0NkK71IEN_hasDbLxUfUA-DngJiyOy2X2Ze4qWp6zIZA2wG5ymfS3zBMHd8VA",
      "publicKeyAlgorithm": -7,
      "transports": [
        "hybrid",
        "internal"
      ]
    },
    "type": "public-key"
  }
}
```

It contains the public key credential created by the authenticator, along with the username and user handle. Using this information, the server can verify the credential and store the public key associated with the user.

To be able to serialize and deserialize the `RegistrationFinishRequest` and `RegistrationFinishResponse` types to and from JSON, we need to define JSON codecs for them.

For the `RegistrationFinishRequest`, we define a JSON decoder as below:

```scala
object RegistrationFinishRequest {
  implicit val decoder: JsonDecoder[RegistrationFinishRequest] =
    JsonDecoder[Json].mapOrFail { o =>
      for {
        u   <- o.get(JsonCursor.field("username")).flatMap(_.as[String])
        uh  <- o.get(JsonCursor.field("userhandle")).flatMap(_.as[String])
        pkc <- o
          .get(JsonCursor.field("publicKeyCredential"))
          .map(_.toString())
          .map(PublicKeyCredential.parseRegistrationResponseJson)
      } yield RegistrationFinishRequest(u, uh, pkc)
    }
}
```

As the `PublicKeyCredential` type has built-in JSON parsing support in the Yubico WebAuthn library, instead of reinventing the wheel, we can use the `PublicKeyCredential.parseRegistrationResponseJson` method to parse the `publicKeyCredential` field from the JSON object.

For the `RegistrationFinishResponse`, we can define a JSON codec straightforwardly as below:

```scala
object RegistrationFinishResponse {
  implicit val codec: JsonCodec[RegistrationFinishResponse] = DeriveJsonCodec.gen
}
```

It helps us to directly convert the `RegistrationFinishResponse` object to the JSON format when returning it in the response.

### WebAuthn Service

The `WebAuthnService` interface is responsible for handling the core logic of the registration and authentication flow. In this section, we will focus on implementing the registration flow, which includes the `startRegistration` and `finishRegistration` methods:

```scala
trait WebAuthnService {
   def startRegistration(request: RegistrationStartRequest): IO[String, RegistrationStartResponse]
   def finishRegistration(request: RegistrationFinishRequest): IO[String, RegistrationFinishResponse]
}
```

1. The `startRegistration` method is responsible for generating public key creation options to be sent to the client. The client uses these options to prompt the authenticator to create a new credential.
2. The `finishRegistration` method is responsible for verifying the response from the client and storing the public key associated with the user to complete the registration process.

Let's start by implementing the `startRegistration` method.

#### 1. Start Registration

To implement the `startRegistration` method, we should perform the following steps:

1. Check if the user exists in our system. If not, we create a new user with the given username.
2. Generate the registration options (`PublicKeyCredentialCreationOptions`) using the Yubico WebAuthn library.
3. Store the registration options somewhere (e.g., in-memory map or database) so that we can retrieve them later when the client sends the response to finish the registration ceremony.

Now we are ready to implement the `WebAuthnService`:

```scala
class WebAuthnServiceImpl(
  userService: UserService,
  pendingRegistrations: Ref[Map[UserHandle, RegistrationStartResponse]],
) extends WebAuthnService {
  private val relyingPartyIdentity: RelyingPartyIdentity = ???
  private val relyingParty        : RelyingParty         = ???
  
  private def userIdentity(userId: String, username: String): UserIdentity = ???

  override def startRegistration(request: RegistrationStartRequest): UIO[RegistrationStartResponse] =
    userService
      .getUser(request.username)
      .orElse {
        val user = User(UUID.randomUUID().toString, request.username, Set.empty)
        userService.addUser(user).as(user)
      }
      .orDieWith(_ => new IllegalStateException("Unexpected status in registration flow!"))
      .flatMap { user =>
        val creationOptions = generateCreationOptions(relyingPartyIdentity, userIdentity(user.userHandle, request.username));
        pendingRegistrations.update(_.updated(user.userHandle, creationOptions)).as(creationOptions)
      }
      
  override def finishRegistration(
    request: RegistrationFinishRequest,
  ): ZIO[Any, String, RegistrationFinishResponse] = ???
}
```

The `startRegistration` method first checks if the user exists in the system. If not, it creates a new user with the given username. It generates a random user ID (user handle) for the new user. Then it generates the registration options by calling the `generateCreationOptions` method. The `generateCreationOptions` takes two parameters: `RelyingPartyIdentity` and `UserIdentity`. The `RelyingPartyIdentity` contains information about the relying party (your server), and the `UserIdentity` contains information about the user.

We used the `generateCreationOptions` helper method to create the `PublicKeyCredentialCreationOptions` object, which can be defined as follows:

```scala
def generateCreationOptions(
  relyingPartyIdentity: RelyingPartyIdentity,
  userIdentity: UserIdentity,
  timeout: Duration = 1.minutes,
): PublicKeyCredentialCreationOptions = {
  PublicKeyCredentialCreationOptions
    .builder()
    .rp(relyingPartyIdentity)
    .user(userIdentity)
    .challenge(generateChallenge())
    .pubKeyCredParams(
      List(
        PublicKeyCredentialParameters.EdDSA,
        PublicKeyCredentialParameters.ES256,
        PublicKeyCredentialParameters.RS256,
      ).asJava,
    )
    .authenticatorSelection(
      AuthenticatorSelectionCriteria
        .builder()
        .residentKey(ResidentKeyRequirement.REQUIRED)
        .userVerification(UserVerificationRequirement.REQUIRED)
        .build(),
    )
    .attestation(AttestationConveyancePreference.NONE)
    .timeout(timeout.toMillis)
    .build()
}
```

The `generateChallenge` function generates a unique challenge using a secure random number generator:

```scala
object Crypto {
  val secureRandom: SecureRandom = new SecureRandom()
}

def generateChallenge(): ByteArray = {
  val bytes = new Array[Byte](32)
  Crypto.secureRandom.nextBytes(bytes)
  new ByteArray(bytes)
}
```

The `RelyingPartyIdentity` can be defined as follows:

```scala
val relyingPartyIdentity: RelyingPartyIdentity =
  RelyingPartyIdentity
    .builder()
    .id("localhost")
    .name("WebAuthn Demo")
    .build()
```

It specifies the relying party ID (domain) and name. Similarly, we have to generate the `UserIdentity` object based on the user information:

```scala
def userIdentity(userId: String, username: String): UserIdentity =
  UserIdentity
    .builder()
    .name(username)
    .displayName(username)
    .id(new ByteArray(userId.getBytes())) 
    .build()
```

After creating credential creation options, before returning it to the client, we need to store it somewhere so that we can retrieve it later when the client sends the response to finish the registration ceremony. To keep track of the registration attempts, we store it in an in-memory map (`pendingRegistrations`) with the user handle as the key.

#### 2. Finish Registration

Now, let's see how to implement the `finishRegistration` method in the `WebAuthnService`. In this method, we have to perform the following steps:

1. Find the corresponding pending registration options for the user handle provided in the request. Why? Because we need to verify the response from the client against the original challenge and options that were sent to the client during the registration start step.
2. To verify the received response, we use the `RelyingParty#finishRegistration` method from the Yubico WebAuthn library. This method takes a `FinishRegistrationOptions` object, which contains the original registration options and the response from the client. It performs all the necessary validations and returns a `RegistrationResult` object.
3. If the registration is successful and the user is verified, we store the public key and other relevant information in our user service. This enables us to authenticate the user in future login attempts.
4. Finally, we return a `RegistrationFinishResponse` object, which contains a success flag and the credential ID.

Here is our implementation of the `finishRegistration` method:

```scala
class WebAuthnServiceImpl(
  userService: UserService,
  pendingRegistrations: Ref[Map[UserHandle, RegistrationStartResponse]],
) extends WebAuthnService {
  private val relyingPartyIdentity: RelyingPartyIdentity = ???
  private val relyingParty: RelyingParty = ???
  private def userIdentity(userId: String, username: String): UserIdentity = ???

  override def finishRegistration(
    request: RegistrationFinishRequest,
  ): IO[String, RegistrationFinishResponse] =
    for {
      creationOptions <- pendingRegistrations.get
        .map(_.get(request.userhandle))
        .some
        .orElseFail(s"no registration request found for ${request.username} username")
      result = relyingParty.finishRegistration(
        FinishRegistrationOptions
          .builder()
          .request(creationOptions)
          .response(request.publicKeyCredential)
          .build(),
      )
      _ <- userService
        .addCredential(
          userHandle = request.userhandle,
          credential = UserCredential(
            userHandle = creationOptions.getUser.getId,
            credentialId = result.getKeyId.getId,
            publicKeyCose = result.getPublicKeyCose,
            signatureCount = result.getSignatureCount,
          ),
        )
        .orElseFail(s"${request.username} user not found!")
      _        <- pendingRegistrations.update(_.removed(request.userhandle))
    } yield {
      RegistrationFinishResponse(
        success = true,
        credentialId = result.getKeyId.getId.getBase64Url,
      )
    }
}
```

The `finishRegistration` method uses the RP (Relying Party) instance. It can be created as follows:

```scala
val relyingPartyIdentity: RelyingPartyIdentity =
  RelyingPartyIdentity
    .builder()
    .id("localhost")
    .name("WebAuthn Demo")
    .build()

val relyingParty: RelyingParty =
  RelyingParty
    .builder()
    .identity(relyingPartyIdentity)
    .credentialRepository(new InMemoryCredentialRepository(userService))
    .origins(Set("http://localhost:8080").asJava)
    .build()
```

To create a `RelyingParty`, we need to provide the `RelyingPartyIdentity`, a `CredentialRepository`, and a set of allowed origins. The `CredentialRepository` is an interface that the Yubico WebAuthn library uses to look up credentials during the authentication process. We will implement it later. Before that, let's see how we can manage users and their credentials.

The important part about both the registration and authentication processes is the persistence of user information and credentials. To manage users and their credentials, we define the `UserService` that supports the following operations:

```scala
trait UserService {
  def getUser(username: String): IO[UserNotFound, User]
  def getUserByHandle(userHandle: String): IO[UserNotFound, User]
  def addUser(user: User): IO[UserAlreadyExists, Unit]
  def addCredential(userHandle: String, credential: UserCredential): IO[UserNotFound, Unit]
  def getCredentialById(credentialId: String): UIO[Set[UserCredential]]
}
```

Where the `User` and `UserCredential` types are defined as follows:

```scala
case class User(
  userHandle: String,
  username: String,
  credentials: Set[UserCredential],
)

case class UserCredential(
  userHandle: ByteArray,
  credentialId: ByteArray,
  publicKeyCose: ByteArray,
  signatureCount: Long,
)
```

The `UserCredential` type represents a public key credential extracted from the `RegistrationResult` object returned by `RelyingParty#finishRegistration`. It contains the user handle, credential ID, public key in COSE format, and signature count and we will store it in our user service.

The `UserServiceError` type is defined as follows:

```scala
sealed trait UserServiceError
object UserServiceError {
  case class UserNotFound(username: String)      extends UserServiceError
  case class UserAlreadyExists(username: String) extends UserServiceError
}
```

As the implementation of `UserService` is not the main focus of this guide, we will provide a simple in-memory implementation:

```scala
case class UserServiceLive(users: Ref[Map[String, User]]) extends UserService {

  override def getUser(username: String): IO[UserNotFound, User] =
    users.get.flatMap { userMap =>
      ZIO.fromOption(userMap.get(username)).orElseFail(UserNotFound(username))
    }

  override def addUser(user: User): IO[UserAlreadyExists, Unit] =
    users.get.flatMap { userMap =>
      ZIO.when(userMap.contains(user.username)) {
        ZIO.fail(UserAlreadyExists(user.username))
      } *> users.update(_.updated(user.username, user))
    }

  override def addCredential(userHandle: String, credential: UserCredential): IO[UserNotFound, Unit] =
    users.get.flatMap { userMap =>
      ZIO.fromOption(userMap.values.find(_.userHandle == userHandle)).orElseFail(UserNotFound(userHandle)).flatMap {
        user =>
          val updatedCredentials = user.credentials + credential
          val updatedUser        = user.copy(credentials = updatedCredentials)
          users.update(_.updated(user.username, updatedUser))
      }
    }

  override def getCredentialById(credentialId: String): IO[Nothing, Set[UserCredential]] =
    users.get.map { userMap =>
      userMap.values
        .flatMap(_.credentials)
        .filter(_.credentialId.getBytes.sameElements(credentialId.getBytes))
        .toSet
    }

  override def getUserByHandle(userHandle: String): IO[UserNotFound, User] =
    users.get.flatMap { userMap =>
      ZIO.fromOption(userMap.values.find(_.userHandle == userHandle)).orElseFail(UserNotFound(userHandle))
    }

}
```

These methods allow us to manage users and their credentials, and also help us to implement the `CredentialRepository` interface required by the Yubico WebAuthn library. Let's see how we can implement it:

```scala
import zio._
import com.yubico.webauthn._
import com.yubico.webauthn.data._
import example.auth.webauthn.model.UserCredential

import java.util
import java.util.Optional
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters.RichOption

class InMemoryCredentialRepository(userService: UserService) extends CredentialRepository {
  override def getCredentialIdsForUsername(username: String): util.Set[PublicKeyCredentialDescriptor] =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run {
        userService
          .getUser(username)
          .map(_.credentials)
          .orElseSucceed(Set.empty)
          .map { _.map { cred =>
              PublicKeyCredentialDescriptor
                .builder()
                .id(cred.credentialId)
                .build()
            }
          }
          .map(_.toSet)
          .map(_.asJava)
      }.getOrThrow()
    }

  override def getUserHandleForUsername(username: String): Optional[ByteArray] =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run {
        userService
          .getUser(username)
          .map { user =>
            new ByteArray(user.userHandle.getBytes())
          }
          .option
      }.getOrThrow()
    }.toJava

  override def getUsernameForUserHandle(userHandle: ByteArray): Optional[String] = 
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run {
        userService.getUserByHandle(new String(userHandle.getBytes)).map(_.username).option
      }.getOrThrow()
    }.toJava

  override def lookup(credentialId: ByteArray, userHandle: ByteArray): Optional[RegisteredCredential] = 
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run {
        userService
          .getUserByHandle(new String(userHandle.getBytes))
          .flatMap { user =>
            val credOpt = user.credentials.find(_.credentialId == credentialId)
            credOpt match {
              case Some(cred) => ZIO.succeed(cred)
              case None       => ZIO.fail(new Exception("Credential not found"))
            }
          }
          .map(toRegisteredCredential)
          .option
      }.getOrThrow()
    }.toJava

  override def lookupAll(credentialId: ByteArray): util.Set[RegisteredCredential] =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run {
        userService.getCredentialById(new String(credentialId.getBytes))
      }.getOrThrow().map(toRegisteredCredential).asJava
    }

  private def toRegisteredCredential(cred: UserCredential): RegisteredCredential =
    RegisteredCredential
      .builder()
      .credentialId(cred.credentialId)
      .userHandle(cred.userHandle)
      .publicKeyCose(cred.publicKeyCose)
      .signatureCount(cred.signatureCount)
      .build()
}
```

In this implementation, we use the `UserService` to look up users and their credentials. As we are touching the boundary between ZIO and Java, we need to use `Unsafe.unsafe` and `Runtime.default.unsafe.run` to run ZIO effects and get the results.

### Client-Side Implementation

Let's start by implementing the registration flow by writing the client-side registration form:

```html
<label for="passkey-username">Choose a Username</label>
<input type="text" id="passkey-username" placeholder="e.g., john" autocomplete="username"/>
<button onclick="registerPasskey()">Create Passkey</button>
```

After the user enters a username and clicks the "Create Passkey" button, the `registerPasskey` function is called. This function retrieves the username from the input field and initiates the registration process by calling the `performRegistration` function:

```javascript
async function registerPasskey() {
  const username = document.getElementById("passkey-username").value;
  if (!username) {
    alert("Please enter a username to register")
    return;
  }
  
  const success = await performRegistration({ username });

  if (success) {
    document.getElementById("passkey-username").value = "";
    alert("Passkey registered successfully!");
  }
}
```

The `performRegistration` function handles the entire registration flow:

```javascript
async function performRegistration({ username }) {
  try {
    const requestBody = { username };
    const startRes = await fetch("/api/webauthn/registration/start", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(requestBody),
    });

    if (!startRes.ok) throw new Error("Failed to start registration");
    const serverOptions = await startRes.json();

    const credential = await navigator.credentials.create({
      publicKey: PublicKeyCredential.parseCreationOptionsFromJSON(serverOptions),
    });

    const credentialJSON = credential.toJSON();

    const credentialForServer = {
      username: username,
      userhandle: atob(serverOptions.user.id),
      publicKeyCredential: credentialJSON,
    };

    const finishRes = await fetch("/api/webauthn/registration/finish", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(credentialForServer),
    });
    
    let resBody = await finishRes.json()
    if (!finishRes.ok) throw new Error("Failed to finish registration");
    
    alert("Passkey registered successfully! You can now sign in using your passkey.");
    return true;
  } catch (err) {
    alert("Failed to register passkey: " + err.message);
    return false;
  }
}
```

The `performRegistration` function performs the following steps:

1. Sends a `POST` request to the `/api/webauthn/registration/start` endpoint with the username to initiate the registration process. The server responds with registration options.
2. Parses the returned options using `PublicKeyCredential.parseCreationOptionsFromJSON`, then calls `navigator.credentials.create()` to prompt the authenticator to generate a new credential.
3. Converts the created credential to JSON using the `toJSON()` method.
4. Sends another `POST` request to the `/api/webauthn/registration/finish` endpoint with the username, user handle, and serialized credential to complete the registration.
5. If registration succeeds, it notifies the user with a success message and returns `true`. If any step fails, it alerts the user with an error message and returns `false`.

## Authentication Flow Implementation

After successful registration, the user can authenticate using their passkey. The **authentication flow** involves the following steps:

1. **Client** (Browser) requests authentication options from the **Relying Party** (your server).
2. **Relying Party** generates a challenge and sends authentication options to the **Client**.
3. **Client** invokes the **Authenticator** to sign the challenge to prove that the authenticator possesses the private key.
4. **Authenticator** signs the challenge and returns the signature to the **Client**.
5. **Client** sends the signature to the **Relying Party**.
6. **Relying Party** verifies the signature using the stored public key that is associated with the user (this public key was stored during the registration flow).
7. If the signature is valid, the Relying Party confirms successful authentication to the Client.

The authentication process is similar to the registration process and involves two main steps: starting the authentication ceremony and finishing it. Let's add them to the `WebAuthnService` interface:

```scala
trait WebAuthnService {
  def startRegistration(request: RegistrationStartRequest): IO[String, RegistrationStartResponse]
  def finishRegistration(request: RegistrationFinishRequest): IO[String, RegistrationFinishResponse]
 
  // two new operations
  def startAuthentication(request: AuthenticationStartRequest): IO[String, AuthenticationStartResponse]
  def finishAuthentication(request: AuthenticationFinishRequest): IO[String, AuthenticationFinishResponse]
}
```

1. The `startAuthentication` method is responsible for generating an assertion request to be sent to the client. This request contains a challenge and other options required for authentication. The client uses this request to prompt the authenticator to sign the challenge.
2. The `finishAuthentication` method is responsible for verifying the authenticator response received from the client. It checks the signature provided by the authenticator against the stored public key associated with the user. If the signature is valid, it confirms successful authentication.

Each of these two methods corresponds to an API endpoint:

1. `POST /api/webauthn/authentication/start`: This endpoint initiates the authentication process by generating and returning the assertion request to the client.
2. `POST /api/webauthn/authentication/finish`: This endpoint completes the authentication process by verifying the response from the client.

### Authentication Routes

Recall that we discussed two types of registrations and authentications: with username and without username. The username-less authentication is more user-friendly and seamless, but it requires the authenticator to support resident keys (discoverable credentials). In this guide, we will implement the authentication flow that supports both types of authentication.

#### 1. Start Route

The start route is responsible for generating the assertion request to be sent to the client. This request contains a challenge and other options required for authentication. The client uses this request to prompt the authenticator to sign the challenge:

```scala
val authenticationStartRoute =
  Method.POST / "api" / "webauthn" / "authentication" / "start"  -> handler { (req: Request) =>
    for {
      request  <- req.body.to[AuthenticationStartRequest]
      response <- webauthn.startAuthentication(request)
    } yield Response.json(response.toJson)
  }
```

The start route takes a JSON object of type `AuthenticationStartRequest` and returns a JSON object of type `AuthenticationStartResponse`:

```scala
case class AuthenticationStartRequest(username: Option[String])
type AuthenticationStartResponse = AssertionRequest
```

1. **`AuthenticationStartRequest`**: We made the `username` field of `AuthenticationStartRequest` optional to support both types of authentication flows (with username and without username). For username-less authentication, we will send `null` or omit the `username` field in the JSON object.
2. **`AuthenticationStartResponse`**: It is a type alias for `AssertionRequest` from the Yubico WebAuthn library, which contains `PublicKeyCredentialRequestOptions` and optional username and user handle.

To be able to deserialize the incoming request to its corresponding model, i.e., `AuthenticationStartRequest`, we have to write a codec for it:

```scala
object AuthenticationStartRequest {
  implicit val codec: JsonCodec[AuthenticationStartRequest] = DeriveJsonCodec.gen
}
```

The start route responds with an `AuthenticationStartResponse` object, which is a type alias for the `AssertionRequest` data type from the Yubico WebAuthn library. This contains `PublicKeyCredentialRequestOptions` and optional username and user handle. To convert the `AuthenticationStartResponse` to JSON format, we can use the built-in `AssertionRequest#toJson` method. So we do not need to write a custom codec for `AuthenticationStartResponse`.

Here is an example response of type `AuthenticationStartResponse` from the server if no username is provided in the request:

```json
{
  "publicKeyCredentialRequestOptions": {
    "challenge": "nrihUD006_f5FP75E3Ntq5Up136pvAXpYm_nThFVJdY",
    "timeout": 60000,
    "hints": [],
    "rpId": "localhost",
    "userVerification": "required",
    "extensions": {}
  }
}
```

It contains the `publicKeyCredentialRequestOptions` field, which is of type `PublicKeyCredentialRequestOptions`. This object contains all the necessary information for the client to prompt the authenticator to sign the challenge:

1. **challenge**: A unique challenge generated by the server to prevent replay attacks. The client must sign this challenge using the private key associated with the credential that is stored in the authenticator. This proves that the client possesses the private key.
2. **timeout**: The time in milliseconds that the client has to complete the authentication process.
3. **rpId**: The relying party identifier, which is typically the domain of the website.
4. **userVerification**: Indicates the level of user verification required. In this case, it is set to "required", meaning the authenticator must verify the user's identity (e.g., via biometric or PIN).

If we send a username in the `AuthenticationStartRequest`, the server will generate an assertion request specific to that user. This means that the server will look up the user's credentials and include them in the assertion request. This is useful for scenarios where the user wants to authenticate with a specific username. Besides the `publicKeyCredentialRequestOptions` field, it also includes the `username` or `userhandle` field in the response. Here is an example response when a username (e.g., `john`) is provided in the request:

```json
{
  "publicKeyCredentialRequestOptions": {
    "challenge": "G4ARnC_LUO8u5EM5bS2BVUc8jB3zhB1vM-6-9pPn1us",
    "timeout": 60000,
    "hints": [],
    "rpId": "localhost",
    "allowCredentials": [
      {
        "type": "public-key",
        "id": "y8gtZxKJg_55WumHTKD_dA"
      },
      {
        "type": "public-key",
        "id": "wX_IO_8fBxNT5-CyeHY9gg"
      }
    ],
    "userVerification": "required",
    "extensions": {}
  },
  "username": "john"
}
```

The `publicKeyCredentialRequestOptions` field contains an additional field called `allowCredentials`. This field is a list of credentials that the server recognizes for the specified user. The client will use one of these credentials to authenticate. The `username` field contains the username provided in the request.

#### 2. Finish Route

On the client side, after receiving the assertion request from the server, the client prompts the authenticator to sign the challenge. This is done using the `navigator.credentials.get()` method provided by the WebAuthn API.

Modern browsers have built-in support for parsing the JSON object directly, using the [`PublicKeyCredential.parseRequestOptionsFromJSON`](https://developer.mozilla.org/en-US/docs/Web/API/PublicKeyCredential/parseRequestOptionsFromJSON_static) method. So we can easily parse the received assertion request and convert it to [`PublicKeyCredentialRequestOptions`](https://developer.mozilla.org/en-US/docs/Web/API/PublicKeyCredentialRequestOptions), which is required by the [`navigator.credentials.get()`](https://developer.mozilla.org/en-US/docs/Web/API/CredentialsContainer/get) method.

After prompting the authenticator and getting the assertion response, the client creates a JSON of type `AuthenticationFinishRequest` and sends it to the server to finish the authentication process using the following endpoint:

```scala
val authenticationFinishRoute =
  Method.POST / "api" / "webauthn" / "authentication" / "finish" -> handler { (req: Request) =>
    for {
      body    <- req.body.asString
      request <- ZIO.fromEither(body.fromJson[AuthenticationFinishRequest])
      result  <- webauthn
        .finishAuthentication(request)
        .orElseFail(
          Response(
            status = Status.Unauthorized,
            body = Body.fromString(s"Authentication failed!"),
          ),
        )
    } yield Response(body = Body.from(result))
  }
```

The finish route takes a JSON object of type `AuthenticationFinishRequest` and returns a JSON object of type `AuthenticationFinishResponse`:

```scala
case class AuthenticationFinishRequest(
  username: Option[String], // Optional for discoverable passkeys
  publicKeyCredential: PublicKeyCredential[AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs],
)

case class AuthenticationFinishResponse(
  success: Boolean,
  username: String,
)
```

1. **`AuthenticationFinishRequest`**: We made the `username` field of `AuthenticationFinishRequest` optional to support both types of authentication flows (with username and without username). For username-less authentication, we will send `null` or omit the `username` field in the JSON object. The `publicKeyCredential` field is of type `PublicKeyCredential` from the Yubico WebAuthn library, which contains the assertion response from the authenticator.
2. **`AuthenticationFinishResponse`**: It contains a success flag and the username of the authenticated user.

Here is an example of the JSON object of type `AuthenticationFinishRequest` sent to the server when no username is provided in the request:

```json
{
  "username" : null, 
  "publicKeyCredential": {
    "authenticatorAttachment": "cross-platform",
    "clientExtensionResults": {},
    "id": "6XXK_FdqGAvpwXpHRTg-jQ",
    "rawId": "6XXK_FdqGAvpwXpHRTg-jQ",
    "response": {
      "authenticatorData": "SZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2MdAAAAAA",
      "clientDataJSON": "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0IiwiY2hhbGxlbmdlIjoiMXRZTUJOMlFiMVd2a0RBMWFvaE5ReHJhc1BSZ2VlTU1wZTlQWHFYamhGVSIsIm9yaWdpbiI6Imh0dHA6Ly9sb2NhbGhvc3Q6ODA4MCIsImNyb3NzT3JpZ2luIjpmYWxzZX0",
      "signature": "MEUCIDAL9bj4PdhzuKzVwNpxWShcAxskB3NzZUeAmfNAeo35AiEAmfr6d5RO3zflLBZy0MuCnzw7OyhwggM6PEogdU7fO70"
    },
    "type": "public-key"
  }
}
```

The `publicKeyCredential` field contains the assertion response from the authenticator, which includes the authenticator data, client data JSON, and signature. This information is used by the server to verify the authenticity of the authentication attempt. If the authentication is username-based, the `username` field will contain the username provided by the client.

To be able to deserialize the `AuthenticationFinishRequest` type from JSON, we need to define a JSON decoder for it. Here is how we can define the decoder:

```scala
object AuthenticationFinishRequest {
  implicit val decoder: JsonDecoder[AuthenticationFinishRequest] =
    JsonDecoder[Json].mapOrFail { o =>
      for {
        u   <-
          Right(
            o.get(JsonCursor.field("username"))
              .toOption
              .flatMap(_.as[String].toOption),
          )
        pkc <- o
          .get(JsonCursor.field("publicKeyCredential"))
          .map(_.toString())
          .map(PublicKeyCredential.parseAssertionResponseJson)
      } yield AuthenticationFinishRequest(u, pkc)
    }
}
```

We use the `PublicKeyCredential.parseAssertionResponseJson` function from the Yubico WebAuthn library to parse the `publicKeyCredential` field from the JSON object.

After receiving the `AuthenticationFinishRequest` from the client, the server needs to verify the assertion response. After verifying the response, the server sends back an `AuthenticationFinishResponse` object to the client, which indicates whether the authentication was successful. The `AuthenticationFinishResponse` type is defined as follows:

```scala
case class AuthenticationFinishResponse(
  success: Boolean,
  username: String,
)
```

Its JSON codec can be defined straightforwardly as below:

```scala
object AuthenticationFinishResponse {
  implicit val codec: JsonCodec[AuthenticationFinishResponse] = DeriveJsonCodec.gen
}
```

Now that we have defined the routes and data types for the authentication flow, let's implement the logic in the `WebAuthnService`.

### WebAuthn Service

The `WebAuthnService` interface is responsible for handling the core logic of the WebAuthn authentication flow. In the registration flow, we implemented the `startRegistration` and `finishRegistration` methods. Now, for the authentication flow, we need to implement the `startAuthentication` and `finishAuthentication` methods:

```scala
trait WebAuthnService {
  def startRegistration(request: RegistrationStartRequest): IO[String, RegistrationStartResponse]
  def finishRegistration(request: RegistrationFinishRequest): IO[String, RegistrationFinishResponse]

  // new methods
  def startAuthentication(request: AuthenticationStartRequest): IO[String, AuthenticationStartResponse]
  def finishAuthentication(request: AuthenticationFinishRequest): IO[String, AuthenticationFinishResponse]
}
```

#### 1. Start Authentication

The `startAuthentication` method is responsible for generating the assertion request to be sent to the client. This request contains a challenge and other options required for authentication. The client uses this request to prompt the authenticator to sign the challenge.

To implement this method, we need to perform the following steps:

1. Generate an assertion request using the `RelyingParty#startAssertion` method from the Yubico WebAuthn library. This method takes a `StartAssertionOptions` object, which contains options for the assertion request, such as the username (if provided), user verification requirement, and timeout, and returns an `AssertionRequest` object. The `AssertionRequest` is another name for `AuthenticationStartResponse`. So we can directly return it as the response of this method.
2. Before returning the assertion request, we have to store the generated assertion request in the `pendingAuthentications` map using the challenge as the key. This allows us to retrieve the original assertion request later when verifying the response from the client.

Let's implement the `startAuthentication` method:

```scala
type Challenge = String

class WebAuthnServiceImpl(
  userService: UserService,
  pendingRegistrations: Ref[Map[UserHandle, RegistrationStartResponse]],
  pendingAuthentications: Ref[Map[Challenge, AuthenticationStartResponse]],
) extends WebAuthnService {
  private val relyingPartyIdentity: RelyingPartyIdentity = ???
  private val relyingParty: RelyingParty = ???
  private def userIdentity(userId: String, username: String): UserIdentity = ???

  override def startRegistration(request: RegistrationStartRequest): ZIO[Any, Nothing, RegistrationStartResponse] = ???

  override def finishRegistration(
    request: RegistrationFinishRequest,
  ): IO[String, RegistrationFinishResponse] = ???

  override def startAuthentication(
    request: AuthenticationStartRequest,
  ): ZIO[Any, Nothing, AuthenticationStartResponse] = {
    val assertion = generateAssertionRequest(relyingParty, request.username)
    val challenge = assertion.getPublicKeyCredentialRequestOptions.getChallenge.getBase64Url; 
    pendingAuthentications.update(_.updated(challenge, assertion)).as(assertion)
  }

  override def finishAuthentication(
    request: AuthenticationFinishRequest,
  ): IO[String, AuthenticationFinishResponse] = ???
}
```

To generate an assertion request, we need to consider whether a username is provided in the request. If a username is provided, we generate an assertion request specific to that user. If no username is provided, we generate a username-less assertion request suitable for discoverable passkeys:

```scala
def generateAssertionRequest(
  relyingParty: RelyingParty,
  username: Option[String],
  timeout: Duration = 1.minutes,
): AssertionRequest = {
  // Create assertion request
  username match {
    case Some(user) if user.nonEmpty =>
      // Username-based authentication
      relyingParty.startAssertion(
        StartAssertionOptions
          .builder()
          .username(user)
          .userVerification(UserVerificationRequirement.REQUIRED)
          .timeout(timeout.toMillis)
          .build(),
      )

    case _ =>
      // Username-less authentication for discoverable passkeys
      relyingParty.startAssertion(
        StartAssertionOptions
          .builder()
          .userVerification(UserVerificationRequirement.REQUIRED)
          .timeout(timeout.toMillis)
          .build(),
      )
  }
}
```

The `RelyingParty#startAssertion` method generates an assertion object by performing the following steps:

1. Creates a unique challenge.
2. If a username is provided, retrieves the user’s credentials and adds their credential IDs to the `allowCredentials` field of the `PublicKeyCredentialRequestOptions`.
3. Sets the user verification requirement to `REQUIRED`, ensuring that the authenticator verifies the user’s identity.
4. Defines a timeout value for the authentication process.

Finally, the generated assertion request is stored in the `pendingAuthentications` map using the challenge as the key. This allows us to retrieve the original assertion request later when verifying the response from the client.

#### 2. Finish Authentication

After the client receives the assertion request from the server, it prompts the authenticator to sign the challenge. The client then sends the assertion response back to the server to finish the authentication process. The `finishAuthentication` method is responsible for verifying the response from the client.

To implement the `finishAuthentication` method, we need to perform the following steps:
1. Retrieve the challenge from the `publicKeyCredential` provided in the request.
2. Look up the original assertion request from the `pendingAuthentications` map using the challenge.
3. Call the `RelyingParty#finishAssertion` method to verify the response from the client. This method takes the original assertion request and the public key credential response from the client, performs all the necessary validations, and returns an `AssertionResult` that contains the result of the authentication attempt.
4. Before returning the result of authentication, we have to remove the corresponding pending assertion request from the `pendingAuthentications` map using the challenge as the key.
5. If the authentication is successful, we return an `AuthenticationFinishResponse` object, which contains a success flag and the username associated with the authenticated credential.

Now, let's implement the `finishAuthentication` method in the `WebAuthnService`:

```scala
class WebAuthnServiceImpl(
  userService: UserService,
  pendingRegistrations: Ref[Map[UserHandle, RegistrationStartResponse]],
  pendingAuthentications: Ref[Map[Challenge, AuthenticationStartResponse]],
) extends WebAuthnService {
  private val relyingPartyIdentity: RelyingPartyIdentity = ???
  private val relyingParty: RelyingParty = ???

  private def userIdentity(userId: String, username: String): UserIdentity = ???

  override def startRegistration(
    request: RegistrationStartRequest
  ): ZIO[Any, Nothing, RegistrationStartResponse] = ???

  override def finishRegistration(
    request: RegistrationFinishRequest,
  ): IO[String, RegistrationFinishResponse] = ???

  override def startAuthentication(
    request: AuthenticationStartRequest,
  ): ZIO[Any, Nothing, AuthenticationStartResponse] = ???

  override def finishAuthentication(
    request: AuthenticationFinishRequest,
  ): IO[String, AuthenticationFinishResponse] =
    for {
      challenge <- ZIO
        .succeed(request.publicKeyCredential.getResponse.getClientData.getChallenge.getBase64Url)
      assertionRequest <- pendingAuthentications.get
        .map(_.get(challenge))
        .some
        .orElseFail(s"The ${challenge} not found in pending authentication requests!")
      assertion =
        relyingParty.finishAssertion(
          FinishAssertionOptions
            .builder()
            .request(assertionRequest)
            .response(request.publicKeyCredential)
            .build(),
        )
      _ <- pendingAuthentications.update(_.removed(challenge))
    } yield AuthenticationFinishResponse(
      success = assertion.isSuccess,
      username = assertion.getUsername,
    )

}
```

Now that we have implemented the `finishAuthentication` method, the authentication flow is complete. The server can now handle both username-less and username-based authentication requests. We are ready to implement the client-side code to interact with these endpoints.

### Client-Side Implementation

The client-side authentication form for username-less authentication looks like this:

```html
<button class="passkey-login-btn" onclick="authenticatePasskey()">Sign In with Passkey</button>
```

For username-based authentication, we can have a simple form like this:

```html
<input type="text" id="username-login" placeholder="Enter your username" />
<button class="username-login-btn" onclick="authenticateWithUsername()">Sign In</button>
```

Both `authenticatePasskey()` and `authenticateWithUsername()` functions call a common function `performAuthentication` to handle the authentication process. Here is how we can implement these functions:

```javascript
async function authenticatePasskey() {
  await performAuthentication({ username: null, isPasskey: true });
}

async function authenticateWithUsername() {
  const username = document.getElementById("username-login").value;
  if (!username) {
    alert("Please enter your username to sign in")
    return;
  }
  await performAuthentication({ username, isPasskey: false });
}
```

The `performAuthentication` function handles the entire authentication flow:

```javascript
async function performAuthentication({ username, isPasskey }) {
  try {
    const requestBody = { username: username };

    const startResponse = await fetch("/api/webauthn/authentication/start", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(requestBody),
    });

    if (!startResponse.ok) {
      throw new Error("Failed to start authentication");
    }

    const serverOptions = await startResponse.json();
    const publicKeyOptions = serverOptions.publicKeyCredentialRequestOptions;

    const assertion = await navigator.credentials.get({
      publicKey: PublicKeyCredential.parseRequestOptionsFromJSON(publicKeyOptions),
    });

    const assertionJSON = assertion.toJSON();

    const assertionForServer = {
      username: username,
      publicKeyCredential: assertionJSON,
    };

    const finishResponse = await fetch("/api/webauthn/authentication/finish", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(assertionForServer),
    });

    const result = await finishResponse.json();

    if (!finishResponse.ok || !result.success) {
      throw new Error(result.message || "Failed to finish authentication");
    }

    // Extract username from result for passkey flow
    const authenticatedUser = result.username;
    
    alert("Authentication successful for user: " + authenticatedUser);
    return true;
  } catch (error) {
    alert("Authentication error: " + error);
    return false;
  }
}
```

1. It sends a `POST` request to the `/api/webauthn/authentication/start` endpoint with the username (or `null` for username-less authentication) to start the authentication process. The server responds with the assertion request.
2. It parses the assertion request and calls the `navigator.credentials.get()` method to prompt the authenticator to sign the challenge.
3. It serializes the assertion response to JSON format using the `toJSON()` method.
4. It sends a `POST` request to the `/api/webauthn/authentication/finish` endpoint with the username and the serialized assertion to finish the authentication process.
5. If the authentication is successful, it alerts the user with the authenticated username.

## Running Demo

To run the demo application that uses the code we implemented above, execute the following commands in your terminal:

```bash
git clone git@github.com:zio/zio-http.git
cd zio-http/
sbt zioHttpExample/runMain example.auth.webauthn.WebAuthnServer
```

After running these commands, open `http://localhost:8080` in your browser. You can now test the registration and authentication flows using both username-based and discoverable passkeys.

## Conclusion

In this guide, we explored the full WebAuthn flow: generating challenges, creating credentials, verifying attestations and assertions, and integrating a browser client that supports both username-based and discoverable (passkey) sign-ins. The result is a phishing-resistant authentication system built on public-key cryptography, offering a smoother user experience than one-time codes or SMS.

Using **WebAuthn with ZIO HTTP**, we implemented a complete passwordless authentication solution that supports both registration and authentication ceremonies, handling discoverable credentials (passkeys) as well as traditional username-based flows.

WebAuthn mitigates the fundamental weaknesses of password-based authentication by leveraging asymmetric cryptography. Private keys never leave the authenticator device, eliminating credential databases as high-value targets. Origin binding prevents phishing, while the challenge–response mechanism ensures each authentication attempt is unique, non-replayable, and tied to the legitimate credential owner.

Finally, when transitioning from development to production, ensure that your WebAuthn setup is correctly configured. WebAuthn requires **HTTPS** for all communications (with the exception of `localhost` during development) and a properly configured relying party identifier (`rp.id`) that matches your production domain.
