package com.retroengine.app

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AxmlPatcher {

    private const val CHUNK_XML = 0x0003
    private const val CHUNK_STRING_POOL = 0x0001
    private const val UTF8_FLAG = 0x100

    fun patchStrings(data: ByteArray, transform: (String) -> String): ByteArray {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.getShort(0).toInt() == CHUNK_XML) { "Manifeste Android invalide." }
        val xmlHeaderSize = buf.getShort(2).toInt() and 0xFFFF
        val poolStart = xmlHeaderSize
        require(buf.getShort(poolStart).toInt() == CHUNK_STRING_POOL) {
            "Table de chaînes du manifeste introuvable."
        }
        val poolHeaderSize = buf.getShort(poolStart + 2).toInt() and 0xFFFF
        val poolSize = buf.getInt(poolStart + 4)
        val stringCount = buf.getInt(poolStart + 8)
        val styleCount = buf.getInt(poolStart + 12)
        val flags = buf.getInt(poolStart + 16)
        val stringsStart = buf.getInt(poolStart + 20)
        require(styleCount == 0) { "Styles non pris en charge dans le manifeste." }
        val utf8 = (flags and UTF8_FLAG) != 0

        val strings = ArrayList<String>(stringCount)
        for (i in 0 until stringCount) {
            val offset = buf.getInt(poolStart + poolHeaderSize + i * 4)
            strings.add(readString(buf, poolStart + stringsStart + offset, utf8))
        }

        val offsets = IntArray(stringCount)
        val body = ByteArrayOutputStream()
        for (i in 0 until stringCount) {
            offsets[i] = body.size()
            writeString(body, transform(strings[i]), utf8)
        }
        while (body.size() % 4 != 0) body.write(0)

        val newStringsStart = poolHeaderSize + stringCount * 4
        val newPoolSize = newStringsStart + body.size()
        val restStart = poolStart + poolSize
        val restLen = data.size - restStart
        val total = xmlHeaderSize + newPoolSize + restLen

        val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        out.put(data, 0, xmlHeaderSize)
        out.put(data, poolStart, poolHeaderSize)
        for (o in offsets) out.putInt(o)
        out.put(body.toByteArray())
        out.put(data, restStart, restLen)
        out.putInt(4, total)
        out.putInt(xmlHeaderSize + 4, newPoolSize)
        out.putInt(xmlHeaderSize + 20, newStringsStart)
        return out.array()
    }

    private fun readString(buf: ByteBuffer, pos: Int, utf8: Boolean): String {
        var p = pos
        if (utf8) {
            var charLen = buf.get(p).toInt() and 0xFF
            p++
            if ((charLen and 0x80) != 0) {
                charLen = ((charLen and 0x7F) shl 8) or (buf.get(p).toInt() and 0xFF)
                p++
            }
            var byteLen = buf.get(p).toInt() and 0xFF
            p++
            if ((byteLen and 0x80) != 0) {
                byteLen = ((byteLen and 0x7F) shl 8) or (buf.get(p).toInt() and 0xFF)
                p++
            }
            val bytes = ByteArray(byteLen)
            for (i in 0 until byteLen) bytes[i] = buf.get(p + i)
            return String(bytes, Charsets.UTF_8)
        }
        var len = buf.getShort(p).toInt() and 0xFFFF
        p += 2
        if ((len and 0x8000) != 0) {
            len = ((len and 0x7FFF) shl 16) or (buf.getShort(p).toInt() and 0xFFFF)
            p += 2
        }
        val sb = StringBuilder(len)
        for (i in 0 until len) sb.append(buf.getShort(p + i * 2).toInt().toChar())
        return sb.toString()
    }

    private fun writeString(out: ByteArrayOutputStream, s: String, utf8: Boolean) {
        if (utf8) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            writeLen8(out, s.length)
            writeLen8(out, bytes.size)
            out.write(bytes)
            out.write(0)
        } else {
            val len = s.length
            if (len > 0x7FFF) {
                writeShort(out, 0x8000 or (len shr 16))
                writeShort(out, len and 0xFFFF)
            } else {
                writeShort(out, len)
            }
            for (c in s) writeShort(out, c.code)
            writeShort(out, 0)
        }
    }

    private fun writeLen8(out: ByteArrayOutputStream, v: Int) {
        if (v > 0x7F) {
            out.write(0x80 or (v shr 8))
            out.write(v and 0xFF)
        } else {
            out.write(v)
        }
    }

    private fun writeShort(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }
}
