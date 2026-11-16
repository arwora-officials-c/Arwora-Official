package com.arwora.utils

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Pure Kotlin implementation of Android Debug Bridge (ADB) binary wire protocol
 * framing, RSA-2048 key management, Android RSAPublicKey struct serialization,
 * and AUTH token signing.
 */
object NativeAdbProtocol {

    const val A_SYNC = 0x434e5953
    const val A_CNXN = 0x4e584e43
    const val A_OPEN = 0x4e45504f
    const val A_OKAY = 0x59414b4f
    const val A_CLSE = 0x45534c43
    const val A_WRTE = 0x45545257
    const val A_AUTH = 0x48545541

    const val A_VERSION = 0x01000000
    const val MAX_PAYLOAD = 256 * 1024

    const val AUTH_TOKEN = 1
    const val AUTH_SIGNATURE = 2
    const val AUTH_RSAPUBLICKEY = 3

    private const val HEADER_SIZE = 24
    private const val RSA_KEY_BITS = 2048
    private const val RSA_NUM_BYTES = 256
    private const val RSA_NUM_WORDS = 64

    private val SHA1_DIGEST_INFO_PREFIX = byteArrayOf(
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
        0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    )

    data class AdbPacket(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AdbPacket) return false
            return command == other.command &&
                arg0 == other.arg0 &&
                arg1 == other.arg1 &&
                data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = command
            result = 31 * result + arg0
            result = 31 * result + arg1
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    fun calculateChecksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) {
            sum += (b.toInt() and 0xFF)
        }
        return sum
    }

    fun encodePacket(
        command: Int,
        arg0: Int,
        arg1: Int,
        data: ByteArray = ByteArray(0)
    ): ByteArray {
        val checksum = calculateChecksum(data)
        val magic = command xor -1
        val buffer = ByteBuffer.allocate(HEADER_SIZE + data.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(command)
        buffer.putInt(arg0)
        buffer.putInt(arg1)
        buffer.putInt(data.size)
        buffer.putInt(checksum)
        buffer.putInt(magic)
        if (data.isNotEmpty()) {
            buffer.put(data)
        }
        return buffer.array()
    }

    fun writePacket(
        out: OutputStream,
        command: Int,
        arg0: Int,
        arg1: Int,
        data: ByteArray = ByteArray(0)
    ) {
        out.write(encodePacket(command, arg0, arg1, data))
        out.flush()
    }

    fun readPacket(input: InputStream): AdbPacket {
        val headerBytes = readFully(input, HEADER_SIZE)
        val header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        val command = header.getInt()
        val arg0 = header.getInt()
        val arg1 = header.getInt()
        val dataLength = header.getInt()
        val dataCheck = header.getInt()
        val magic = header.getInt()

        require(magic == (command xor -1)) {
            "Invalid ADB header magic: 0x${magic.toString(16)} for cmd 0x${command.toString(16)}"
        }
        require(dataLength in 0..MAX_PAYLOAD) {
            "Invalid ADB payload length: $dataLength"
        }

        val payload = if (dataLength > 0) readFully(input, dataLength) else ByteArray(0)
        val actualChecksum = calculateChecksum(payload)
        require(actualChecksum == dataCheck) {
            "ADB payload checksum mismatch: expected=$dataCheck actual=$actualChecksum"
        }
        return AdbPacket(command, arg0, arg1, payload)
    }

    fun decodePacketBytes(bytes: ByteArray): AdbPacket {
        return readPacket(ByteArrayInputStream(bytes))
    }

    private fun readFully(input: InputStream, count: Int): ByteArray {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buffer, offset, count - offset)
            if (read < 0) {
                throw EOFException("Unexpected end of ADB stream ($offset/$count bytes read)")
            }
            offset += read
        }
        return buffer
    }

    fun loadOrCreateKeyPair(keyDir: File): KeyPair {
        if (!keyDir.exists()) {
            keyDir.mkdirs()
        }
        val privateFile = File(keyDir, "adbkey")
        val publicFile = File(keyDir, "adbkey.pub")

        if (privateFile.exists() && publicFile.exists()) {
            try {
                val keyFactory = KeyFactory.getInstance("RSA")
                val privBytes = Base64.decode(privateFile.readText(), Base64.DEFAULT)
                val pubBytes = Base64.decode(publicFile.readText(), Base64.DEFAULT)
                val privKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))
                return KeyPair(pubKey, privKey)
            } catch (_: Exception) {
                // Regenerate corrupted keypair below
            }
        }

        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(RSA_KEY_BITS)
        val pair = generator.generateKeyPair()

        try {
            privateFile.writeText(Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP))
            publicFile.writeText(Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP))
        } catch (_: Exception) {
            // Non-fatal in test/read-only environments; still return valid in-memory pair
        }

        return pair
    }

    /**
     * Serializes an RSA-2048 public key into Android ADB's 524-byte RSAPublicKey struct:
     * - uint32 len = 64
     * - uint32 n0inv = -N^(-1) mod 2^32
     * - uint32 n[64] (little-endian modulus words)
     * - uint32 rr[64] (little-endian (2^2048)^2 mod N words)
     * - uint32 exponent
     */
    fun encodeAndroidRsaPublicKeyStruct(publicKey: RSAPublicKey): ByteArray {
        val modulus = publicKey.modulus
        val exponent = publicKey.publicExponent

        val r32 = BigInteger.ONE.shiftLeft(32)
        val n0 = modulus.mod(r32)
        val n0Inv = r32.subtract(n0.modInverse(r32)).mod(r32)

        val r = BigInteger.ONE.shiftLeft(RSA_NUM_BYTES * 8)
        val rr = r.multiply(r).mod(modulus)

        val out = ByteBuffer.allocate(4 + 4 + RSA_NUM_WORDS * 4 + RSA_NUM_WORDS * 4 + 4)
            .order(ByteOrder.LITTLE_ENDIAN)

        out.putInt(RSA_NUM_WORDS)
        out.putInt(n0Inv.toLong().toInt())

        writeLittleEndianWords(out, modulus, RSA_NUM_WORDS)
        writeLittleEndianWords(out, rr, RSA_NUM_WORDS)

        out.putInt(exponent.toLong().toInt())
        return out.array()
    }

    fun formatAdbPublicKeyPayload(
        publicKey: RSAPublicKey,
        comment: String = "arwora@android"
    ): ByteArray {
        val structBytes = encodeAndroidRsaPublicKeyStruct(publicKey)
        val base64 = Base64.encodeToString(structBytes, Base64.NO_WRAP)
        val line = "$base64 $comment\u0000"
        return line.toByteArray(Charsets.UTF_8)
    }

    fun signToken(token: ByteArray, privateKey: PrivateKey): ByteArray {
        val digestInfo = ByteArrayOutputStream(SHA1_DIGEST_INFO_PREFIX.size + token.size).apply {
            write(SHA1_DIGEST_INFO_PREFIX)
            write(token)
        }.toByteArray()

        val signature = Signature.getInstance("NONEwithRSA")
        signature.initSign(privateKey)
        signature.update(digestInfo)
        return signature.sign()
    }

    private fun writeLittleEndianWords(buffer: ByteBuffer, value: BigInteger, words: Int) {
        val mask32 = BigInteger.valueOf(0xFFFFFFFFL)
        var remaining = value
        for (i in 0 until words) {
            val word = remaining.and(mask32).toLong().toInt()
            buffer.putInt(word)
            remaining = remaining.shiftRight(32)
        }
    }
}
