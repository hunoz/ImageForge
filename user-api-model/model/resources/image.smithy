$version: "2"

namespace dev.popaxe.imageforge

@documentation("The version of the OS to install in the image")
enum OsVersion {
    UBUNTU_20_04 = "ubuntu-20.04"
    UBUNTU_22_04 = "ubuntu-22.04"
    UBUNTU_24_04 = "ubuntu-24.04"
    DEBIAN_10 = "debian-10"
    DEBIAN_11 = "debian-11"
    RASPBIAN_10 = "raspbian-10"
    RASPBIAN_11 = "raspbian-11"
}

@documentation("The authentication method")
enum AuthenticationMethod {
    USERNAME_PASSWORD = "username-password"
    PUBKEY = "pubkey"
}

@documentation("A list of authentication methods")
list AuthenticationMethodList {
    @documentation("An authentication method")
    member: AuthenticationMethod
}

@documentation("The type of image to use")
enum ImageType {
    LITE = "LITE"
    STANDARD = "STANDARD"
    FULL = "FULL"
}

@documentation("The resource defining what an image is and all of its properties")
resource Image {
    identifiers: {
        id: Uuid
    }
    properties: {
        name: NonEmptyString
        hostname: NonEmptyString
        osVersion: OsVersion
        packagesToInstall: NonEmptyStringList
        timezone: NonEmptyString
        locale: NonEmptyString
        imageType: ImageType
        authenticationMethods: AuthenticationMethodList
        username: NonEmptyString
        password: NonEmptyString
        sshPublicKey: NonEmptyString
        createdAt: Timestamp
        createdBy: NonEmptyString
        updatedAt: Timestamp
        updatedBy: NonEmptyString
        generationStatus: ImageGenerationStatus
        location: NonEmptyString
    }
    read: GetImage
    create: CreateImage
    delete: DeleteImage
    update: UpdateImage
    list: ListImages
}

structure ImageBase {
    @documentation("The unique identifier for the image")
    id: Uuid

    @documentation("The name of the image")
    name: NonEmptyString

    @documentation("The hostname of the image")
    hostname: NonEmptyString

    @documentation("The OS version of the image")
    osVersion: OsVersion

    @documentation("The packages to install on the image")
    packagesToInstall: NonEmptyStringList

    @documentation("The timezone of the image")
    timezone: NonEmptyString

    @documentation("The locale of the image")
    locale: NonEmptyString

    @documentation("The type of the image")
    imageType: ImageType

    @documentation("The authentication methods for the image")
    authenticationMethods: AuthenticationMethodList

    @documentation("The username for the image")
    username: NonEmptyString

    @documentation("The password for the image")
    password: NonEmptyString

    @documentation("The SSH public key for the image")
    sshPublicKey: NonEmptyString

    @documentation("When the image was created")
    createdAt: Timestamp

    @documentation("Who created the image")
    createdBy: NonEmptyString

    @documentation("When the image was last updated")
    updatedAt: Timestamp

    @documentation("Who last updated the image")
    updatedBy: NonEmptyString

    @documentation("The current status of the image generation")
    generationStatus: ImageGenerationStatus

    @documentation("The location of the generated image")
    location: NonEmptyString
}

@mixin
@documentation("The resource defining what an image is and all of its properties")
structure ImageMixin {
    @required
    @documentation("The unique identifier for the image")
    id: Uuid

    @required
    @documentation("The name of the image")
    name: NonEmptyString

    @required
    @documentation("The hostname of the image")
    @default("localhost")
    hostname: NonEmptyString

    @required
    @documentation("The OS version of the image")
    @default("ubuntu-24.04")
    osVersion: OsVersion

    @required
    @documentation("The packages to install on the image")
    @default([])
    packagesToInstall: NonEmptyStringList

    @required
    @documentation("The timezone of the image")
    @default("UTC")
    timezone: NonEmptyString

    @required
    @documentation("The locale of the image")
    locale: NonEmptyString

    @required
    @documentation("The type of the image")
    @default("LITE")
    imageType: ImageType

    @required
    @documentation("The authentication methods for the image")
    authenticationMethods: AuthenticationMethodList

    @documentation("The username for the image")
    @default("pi")
    username: NonEmptyString

    @documentation("The password for the image")
    @default("raspberry")
    password: NonEmptyString

    @documentation("The SSH public key for the image")
    sshPublicKey: NonEmptyString

    @required
    @documentation("When the image was created")
    createdAt: Timestamp

    @required
    @documentation("Who created the image")
    createdBy: NonEmptyString

    @required
    @documentation("When the image was last updated")
    updatedAt: Timestamp

    @required
    @documentation("Who last updated the image")
    updatedBy: NonEmptyString

    @required
    @documentation("The current status of the image generation")
    generationStatus: ImageGenerationStatus

    @required
    @documentation("The location of the generated image")
    location: NonEmptyString
}

@documentation("A list of images")
list ImageList {
    @documentation("An image in the list")
    member: ImageBase
}
