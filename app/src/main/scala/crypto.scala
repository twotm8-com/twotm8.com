package twotm8

import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import javax.crypto.Mac

def sha256(plaintext: String): String =
  val digest = MessageDigest.getInstance("SHA-256")
  val hashBytes = digest.digest(plaintext.getBytes(StandardCharsets.UTF_8))
  hashBytes.map("%02x".format(_)).mkString

def hmac(plaintext: String, key: String): String =
  val algorithm = "HmacSHA256"
  val secretKeySpec =
    new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algorithm)

  val mac = Mac.getInstance(algorithm)
  mac.init(secretKeySpec)

  val hmacBytes = mac.doFinal(plaintext.getBytes(StandardCharsets.UTF_8))

  Base64.getEncoder.encodeToString(hmacBytes)
end hmac
