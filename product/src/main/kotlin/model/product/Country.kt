package com.svebrant.model.product

import com.svebrant.configuration.serializers.CountrySerializer
import kotlinx.serialization.Serializable

@Serializable(with = CountrySerializer::class)
enum class Country(
    val code: String,
) {
    SWEDEN("SE"),
    GERMANY("DE"),
    FRANCE("FR"),
    ;

    val displayName: String
        get() =
            this.name
                .lowercase()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                .replace("_", " ")
}
