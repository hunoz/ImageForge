$version: "2"

namespace dev.popaxe.imageforge

@pattern("^[ a-zA-Z0-9_:-]{1,256}$")
@length(min: 1, max: 256)
string AlphaNumericString

list AlphaNumericStrings {
    member: AlphaNumericString
}

@pattern("^\\S+$")
@length(min: 1)
string NonEmptyString

list NonEmptyStringList {
    member: NonEmptyString
}
