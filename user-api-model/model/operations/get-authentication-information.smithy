$version: "2"

namespace dev.popaxe.imageforge

@readonly
@http(method: "GET", uri: "/auth/info")
@optionalAuth
@documentation("Get the authentication information for the client to obtain an ID token")
operation GetAuthenticationInformation {
    output: AuthenticationInformation
    errors: [
        InternalServerErrorException
        ThrottlingException
    ]
}

@documentation("The structure defining what information is necessary for a client to authenticate")
structure AuthenticationInformation {
    @documentation("The authorize url of the identity provider (e.g. https://example.com/authorize")
    authorizeUrl: NonEmptyString

    @documentation("The token url of the identity provider that can exchange a code for a token (e.g. https://example.com/oauth/token")
    tokenUrl: NonEmptyString

    @documentation("The client ID to send to the identity provider")
    clientId: NonEmptyString

    @documentation("The PKCE code verifier to send to the identity provider")
    verifier: NonEmptyString
}
