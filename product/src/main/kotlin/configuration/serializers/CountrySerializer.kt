package com.svebrant.configuration.serializers

import com.svebrant.model.Country
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object CountrySerializer : KSerializer<Country> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Country", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Country,
    ) {
        encoder.encodeString(value.displayName)
    }

    override fun deserialize(decoder: Decoder): Country {
        val name = decoder.decodeString()
        return Country.entries.find { it.displayName.equals(name, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown country: $name")
    }
}
