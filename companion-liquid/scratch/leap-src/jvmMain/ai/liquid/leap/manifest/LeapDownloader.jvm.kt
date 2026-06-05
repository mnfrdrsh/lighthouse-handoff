package ai.liquid.leap.manifest

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.asSource
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.serialization.json.Json

actual fun buildHttpClient(
  json: Json,
  installCache: Boolean,
  followRedirects: Boolean,
  expectSuccess: Boolean,
  disableSslValidation: Boolean,
  connectTimeoutMillis: Long,
  socketTimeoutMillis: Long,
  requestTimeoutMillis: Long,
): HttpClient {
  return HttpClient {
    install(HttpTimeout) {
      this.connectTimeoutMillis = connectTimeoutMillis
      this.socketTimeoutMillis = socketTimeoutMillis
      this.requestTimeoutMillis = requestTimeoutMillis
    }
    install(ContentNegotiation) { json(json) }
    if (installCache) {
      install(HttpCache)
    }
    this.followRedirects = followRedirects
    this.expectSuccess = expectSuccess
  }
}

internal actual val ioDispatcher: CoroutineContext = Dispatchers.IO

internal actual val hasLocalFileSystem: Boolean = true

internal actual fun ByteReadChannel.asKotlinIoSource(): Source = this.asSource().buffered()
