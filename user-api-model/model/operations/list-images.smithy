$version: "2"

namespace dev.popaxe.imageforge

@readonly
@auth([httpBearerAuth])
@http(method: "POST", uri: "/images", code: 200)
@documentation("List a user's images")
@paginated(inputToken: "nextToken", outputToken: "nextToken", pageSize: "pageSize", items: "items")
operation ListImages {
    input: ListImagesRequest
    output: ListImagesResponse
    errors: [
        ThrottlingException
        UnauthorizedException
        ForbiddenException
        InternalServerErrorException
    ]
}

@documentation("The items to include to successfully fetch a page of images")
structure ListImagesRequest with [AuthenticationParams, PaginatedRequest] {}

@documentation("The response from listing images")
structure ListImagesResponse for Image with [PaginatedResponse] {
    items: ImageList
}
