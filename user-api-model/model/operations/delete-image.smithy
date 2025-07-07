$version: "2"

namespace dev.popaxe.imageforge

@auth([httpBearerAuth])
@idempotent
@http(method: "DELETE", uri: "/images/{id}", code: 204)
@documentation("Delete an image")
operation DeleteImage {
    input: DeleteImageRequest
    errors: [
        ThrottlingException
        UnauthorizedException
        ForbiddenException
        InternalServerErrorException
    ]
}

@documentation("The structure defining what parameters are needed to delete an image")
structure DeleteImageRequest for Image with [AuthenticationParams] {
    @httpLabel
    @required
    $id

    @notProperty
    $token
}
