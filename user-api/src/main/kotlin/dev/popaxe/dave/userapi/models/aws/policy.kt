package dev.popaxe.dave.userapi.models.aws

import com.google.gson.annotations.SerializedName

data class Statement(
    @SerializedName("Effect") val effect: String,
    @SerializedName("Action") val actions: MutableList<String>,
    @SerializedName("Resource") val resources: MutableList<String>,
)

data class FederatedPermissionsPolicyDocument(
    @SerializedName("Version") val version: String,
    @SerializedName("Statement") val statement: MutableList<Statement>,
)
