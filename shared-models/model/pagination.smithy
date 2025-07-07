$version: "2"

namespace dev.popaxe.imageforge

enum SortOrder {
    ASC = "ASC"
    DESC = "DESC"
}

@input
@mixin
@documentation("A request shape to pass to any pagination-based operation")
structure PaginatedRequest {
    @required
    @documentation("The order in which to sort items")
    sortOrder: SortOrder = "ASC"

    @documentation("The number of items to return in a single request")
    pageSize: NonZeroPositiveInteger = 10

    @documentation("The starting point of the next page")
    nextToken: NonEmptyString
}

@output
@mixin
@documentation("A response shape that defines all properties except for the one containing the items")
structure PaginatedResponse {
    @documentation("The next token which will be used to get the next page of results. If token is null, no more items are left")
    nextToken: NonEmptyString

    @required
    @documentation(
        """
            If there is a next page, this will be true and nextToken will not be null.
            If there is not a next page, this will be false and nextToken will be null.
        """
    )
    hasNextPage: Boolean
}
