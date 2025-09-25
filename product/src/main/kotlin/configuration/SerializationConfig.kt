package com.svebrant.configuration

import com.svebrant.configuration.serializers.ObjectIdSerializer
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.bson.types.ObjectId

val objectIdModule =
    SerializersModule {
        contextual(ObjectId::class, ObjectIdSerializer)
    }

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
                serializersModule = objectIdModule
            },
        )
    }
}
