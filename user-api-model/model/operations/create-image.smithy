$version: "2"

namespace dev.popaxe.imageforge

@auth([httpBearerAuth])
@http(method: "PUT", uri: "/images", code: 200)
@documentation("Create an image")
operation CreateImage {
    input: CreateImageRequest
    output: CreateImageResponse
    errors: [
        ThrottlingException
        UnauthorizedException
        ForbiddenException
        ConflictException
        InternalServerErrorException
    ]
}

@input
@documentation("A structure representing the properties for creating an image")
structure CreateImageRequest for Image with [AuthenticationParams] {
    @notProperty
    $token

    @required
    @documentation("The name of the image")
    $name

    @required
    @documentation("The hostname of the image")
    @default("localhost")
    $hostname

    @required
    @documentation("The OS version of the image")
    @default("ubuntu-24.04")
    $osVersion

    @required
    @documentation("The packages to install on the image")
    @default([])
    $packagesToInstall

    @required
    @documentation("The timezone of the image")
    @default("UTC")
    $timezone

    @required
    @documentation("The locale of the image")
    $locale

    @required
    @documentation("The type of the image")
    @default("LITE")
    $imageType

    @required
    @documentation("The authentication methods for the image")
    $authenticationMethods

    @documentation("The username for the image")
    @default("pi")
    $username

    @documentation("The password for the image")
    @default("raspberry")
    $password

    @documentation("The SSH public key for the image")
    $sshPublicKey
}

@output
@documentation("The structure representing an image after creation")
structure CreateImageResponse for Image with [ImageMixin] {}
