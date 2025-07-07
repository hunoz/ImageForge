$version: "2"

namespace dev.popaxe.imageforge

@auth([httpBearerAuth])
@http(method: "PATCH", uri: "/images/{id}", code: 200)
@documentation("Update an image")
operation UpdateImage {
    input: UpdateImageRequest
    output: UpdateImageResponse
    errors: [
        ThrottlingException
        UnauthorizedException
        ForbiddenException
        ResourceNotFoundException
        InternalServerErrorException
    ]
}

@input
@documentation("The structure defining what parameters are needed to update a workspace")
structure UpdateImageRequest for Image with [AuthenticationParams] {
    @notProperty
    $token

    @httpLabel
    @required
    $id

    @documentation("The name of the image")
    $name

    @documentation("The hostname of the image")
    $hostname

    @documentation("The OS version of the image")
    $osVersion

    @documentation("The packages to install on the image")
    $packagesToInstall

    @documentation("The timezone of the image")
    $timezone

    @documentation("The locale of the image")
    $locale

    @documentation("The type of the image")
    $imageType

    @documentation("The authentication methods for the image")
    $authenticationMethods

    @documentation("The username for the image")
    $username

    @documentation("The password for the image")
    $password

    @documentation("The SSH public key for the image")
    $sshPublicKey
}

@output
structure UpdateImageResponse for Image with [ImageMixin] {}
