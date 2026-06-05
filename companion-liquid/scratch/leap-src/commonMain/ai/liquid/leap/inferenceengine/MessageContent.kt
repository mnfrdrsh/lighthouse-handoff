package ai.liquid.leap.inferenceengine

import kotlinx.io.bytestring.ByteString

sealed class MessageContent {
  class StringContent(val text: String) : MessageContent()

  class JpegContent(val jpegByteString: ByteString) : MessageContent()

  class WavContent(val wavByteString: ByteString) : MessageContent()
}
