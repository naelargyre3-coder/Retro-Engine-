package com.retroengine.app

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ApkRepacker {

    private class Counting(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }

    private fun isSignatureEntry(name: String): Boolean {
        if (!name.startsWith("META-INF/")) return false
        val upper = name.uppercase()
        return upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") ||
            upper.endsWith(".EC") || upper.endsWith("MANIFEST.MF")
    }

    // Copie l'APK en remplaçant ou ajoutant des fichiers, en retirant les anciennes signatures.
    fun repack(
        input: File,
        output: File,
        replace: Map<String, ByteArray>,
        add: Map<String, ByteArray>,
        skip: (String) -> Boolean = { false }
    ) {
        val counter = Counting(BufferedOutputStream(FileOutputStream(output), 64 * 1024))
        ZipOutputStream(counter).use { zos ->
            ZipFile(input).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    val name = e.name
                    if (e.isDirectory || isSignatureEntry(name) || skip(name)) continue
                    val data = replace[name] ?: zip.getInputStream(e).use { it.readBytes() }
                    writeEntry(zos, counter, name, data, e.method == ZipEntry.STORED)
                }
            }
            for ((name, data) in add) writeEntry(zos, counter, name, data, false)
        }
    }

    private fun writeEntry(
        zos: ZipOutputStream,
        counter: Counting,
        name: String,
        data: ByteArray,
        stored: Boolean
    ) {
        val entry = ZipEntry(name)
        if (stored) {
            entry.method = ZipEntry.STORED
            entry.size = data.size.toLong()
            entry.compressedSize = data.size.toLong()
            val crc = CRC32()
            crc.update(data)
            entry.crc = crc.value
            // Alignement sur 4 octets, comme zipalign
            val nameLen = name.toByteArray(Charsets.UTF_8).size
            val r = ((counter.count + 30 + nameLen) % 4).toInt()
            val pad = (4 - r) % 4
            if (pad != 0) {
                val extra = ByteArray(pad + 4)
                extra[0] = 0x35
                extra[1] = 0xD9.toByte()
                extra[2] = pad.toByte()
                extra[3] = 0
                entry.extra = extra
            }
        } else {
            entry.method = ZipEntry.DEFLATED
        }
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }
}
