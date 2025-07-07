$version: "2"

namespace dev.popaxe.imageforge

@mixin
structure GenericException {
    message: NonEmptyString
}

@error("client")
@httpError(404)
structure ResourceNotFoundException with [GenericException] {}

@error("client")
@httpError(401)
structure UnauthorizedException with [GenericException] {}

@error("client")
@httpError(403)
structure ForbiddenException with [GenericException] {}

@error("server")
@retryable
@httpError(500)
structure InternalServerErrorException with [GenericException] {}

@error("client")
@retryable
@httpError(429)
structure ThrottlingException with [GenericException] {}

@error("client")
@httpError(409)
structure ConflictException with [GenericException] {}
