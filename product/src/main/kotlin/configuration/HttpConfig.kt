package com.svebrant.configuration

import com.svebrant.configuration.serializers.ObjectIdSerializer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.bson.types.ObjectId

fun Application.configureServerHttp() {
    install(Compression)
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
                serializersModule =
                    SerializersModule {
                        contextual(ObjectId::class, ObjectIdSerializer)
                    }
            },
        )
    }
}

fun Application.configureHttpClient(bearerToken: String): HttpClient =
    HttpClient(CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = false
                },
            )
        }
        install(Auth) {
            bearer {
                loadTokens {
                    BearerTokens(bearerToken, bearerToken)
                }
            }
        }
    }

fun configureHttpClient(bearerToken: String): HttpClient =
    HttpClient(CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = false
                },
            )
        }
        install(Auth) {
            bearer {
                loadTokens {
                    BearerTokens(bearerToken, bearerToken)
                }
            }
        }
    }
