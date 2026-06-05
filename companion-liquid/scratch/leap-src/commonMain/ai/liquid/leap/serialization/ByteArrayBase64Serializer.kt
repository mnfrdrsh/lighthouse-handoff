package ai.liquid.leap.serialization

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Custom serializer for ByteArray that encodes/decodes using Base64.
 *
 * This is used for binary data like images and audio in chat messages, allowing them to be
 * transmitted as strings in JSON format.
 */
@OptIn(ExperimentalEncodingApi::class)
object ByteArrayBase64Serializer : KSerializer<ByteArray> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("ByteArrayBase64", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: ByteArray) {
    val base64 = Base64.Default.encode(value)
    encoder.encodeString(base64)
  }

  override fun deserialize(decoder: Decoder): ByteArray {
    val base64 = decoder.decodeString()
    return Base64.Default.decode(base64)
  }
}
