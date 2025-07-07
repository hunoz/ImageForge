$version: "2"

namespace dev.popaxe.imageforge

use aws.api#service
use aws.protocols#restJson1
use smithy.api#httpBearerAuth
use smithy.framework#ValidationException
use smithy.protocols#rpcv2Cbor

@title("User API")
@restJson1
@rpcv2Cbor
@service(sdkId: "user")
@httpBearerAuth
@auth([httpBearerAuth])
service UserApi {
    version: "2024-08-23"
    operations: [
        GetAuthenticationInformation
    ]
    resources: [
        Image
    ]
    errors: [
        ValidationException
    ]
}
