$version: "2"
namespace example.auth

use smithy.api#httpBearerAuth

/// User authentication and authorization service
@httpBearerAuth
service AuthService {
    version: "1.0.0"
    operations: [Login, Logout, GetCurrentUser, RefreshToken]
}

/// Login with username and password
@http(method: "POST", uri: "/auth/login")
operation Login {
    input: LoginInput
    output: LoginOutput
    errors: [InvalidCredentials, AccountLocked]
}

/// Logout the current user
@http(method: "POST", uri: "/auth/logout")
operation Logout {
    input: LogoutInput
    output: LogoutOutput
}

/// Get the currently authenticated user
@http(method: "GET", uri: "/auth/me")
@readonly
@auth([httpBearerAuth])
operation GetCurrentUser {
    input: GetCurrentUserInput
    output: GetCurrentUserOutput
    errors: [Unauthorized]
}

/// Refresh an authentication token
@http(method: "POST", uri: "/auth/refresh")
operation RefreshToken {
    input: RefreshTokenInput
    output: RefreshTokenOutput
    errors: [InvalidToken, TokenExpired]
}

structure LoginInput {
    @required
    @length(min: 3, max: 100)
    username: String

    @required
    @length(min: 8, max: 128)
    password: String

    /// Remember the user for longer session
    rememberMe: Boolean
}

structure LoginOutput {
    @required
    accessToken: AccessToken

    @required
    refreshToken: RefreshTokenValue

    @required
    expiresIn: Integer

    @required
    user: User
}

structure LogoutInput {
    @httpHeader("Authorization")
    authorization: String
}

structure LogoutOutput {}

structure GetCurrentUserInput {
    @httpHeader("Authorization")
    @required
    authorization: String
}

structure GetCurrentUserOutput {
    @required
    user: User
}

structure RefreshTokenInput {
    @required
    refreshToken: RefreshTokenValue
}

structure RefreshTokenOutput {
    @required
    accessToken: AccessToken

    @required
    expiresIn: Integer
}

/// User information
structure User {
    @required
    id: UserId

    @required
    username: String

    @required
    email: Email

    @required
    roles: RoleList

    @required
    createdAt: Timestamp

    lastLoginAt: Timestamp

    @required
    status: UserStatus
}

@pattern("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
string Email

@length(min: 36, max: 36)
string UserId

@length(min: 100, max: 500)
string AccessToken

@length(min: 100, max: 500)
string RefreshTokenValue

list RoleList {
    member: Role
}

enum Role {
    USER
    ADMIN
    MODERATOR
    GUEST
}

enum UserStatus {
    ACTIVE
    INACTIVE
    PENDING
    SUSPENDED
}

/// Invalid credentials error
@error("client")
@httpError(401)
structure InvalidCredentials {
    @required
    message: String

    remainingAttempts: Integer
}

/// Account is locked
@error("client")
@httpError(403)
structure AccountLocked {
    @required
    message: String

    lockedUntil: Timestamp
}

/// User is not authorized
@error("client")
@httpError(401)
structure Unauthorized {
    @required
    message: String
}

/// Token is invalid
@error("client")
@httpError(401)
structure InvalidToken {
    @required
    message: String
}

/// Token has expired
@error("client")
@httpError(401)
structure TokenExpired {
    @required
    message: String

    expiredAt: Timestamp
}
