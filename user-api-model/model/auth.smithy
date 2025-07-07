$version: "2"

namespace dev.popaxe.imageforge

@mixin
@documentation("The params passed as input for the authentication process to succeed")
structure AuthenticationParams {
    @httpHeader("X-Token")
    @required
    @documentation("The ID token of the user")
    token: NonEmptyString
}
