$version: "2"

namespace dev.popaxe.imageforge

@pattern("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$")
@length(min: 1, max: 128)
string Uuid
