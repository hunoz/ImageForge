$version: "2"

namespace dev.popaxe.imageforge

@readonly
@auth([httpBearerAuth])
@http(method: "GET", uri: "/images/{id}", code: 200)
@documentation("Get an image by its ID")
operation GetImage {
    input: GetImageRequest
    output: GetImageResponse
    errors: [
        ThrottlingException
        ResourceNotFoundException
        UnauthorizedException
        ForbiddenException
        InternalServerErrorException
    ]
}

@input
structure GetImageRequest for Image with [AuthenticationParams] {
    @notProperty
    $token

    @required
    @httpLabel
    @documentation("The ID of the image")
    $id
}

@output
structure GetImageResponse for Image with [ImageMixin] {}
