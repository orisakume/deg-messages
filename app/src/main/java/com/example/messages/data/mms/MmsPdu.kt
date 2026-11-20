package com.example.messages.data.mms

import java.io.ByteArrayOutputStream
import java.util.UUID

/** MMS 1.2 Send.req headers and a WSP multipart/related body with a SMIL presentation. */
internal fun composeMmsPdu(address: String, caption: ByteArray, picture: MmsAttachment): ByteArray = Wsp().apply {
    write(byteArrayOf(0x8c.toByte(), 0x80.toByte(), 0x98.toByte())) // message type, Send.req, transaction ID
    text("T" + UUID.randomUUID().toString().replace("-", ""))
    write(0x8d); write(0x92) // MMS version 1.2
    write(0x89); write(1); write(0x81) // From: insert-address token
    write(0x97) // To: encoded-string-value
    val recipient = Wsp().apply { write(0xea); text("$address/TYPE=PLMN") }.toByteArray()
    valueLength(recipient.size); write(recipient)
    write(0x8a); write(0x80) // X-Mms-Message-Class: personal
    write(0x8f); write(0x81) // X-Mms-Priority: normal
    write(0x84) // Content-Type
    val related = Wsp().apply {
        write(0xb3) // application/vnd.wap.multipart.related
        write(0x8a); text("<presentation>") // start
        write(0x89); text("application/smil") // type
    }.toByteArray()
    valueLength(related.size); write(related)

    uintvar(if (caption.isEmpty()) 2 else 3)
    val captionElement = if (caption.isNotEmpty()) """<text src="caption.txt" region="Text"/>""" else ""
    val element = if (picture.isVideo) "video" else "img"
    val smil = """<smil><head><layout><root-layout width="320" height="320"/><region id="Image" width="320" height="260"/><region id="Text" top="260" width="320" height="60"/></layout></head><body><par dur="${picture.durationMillis.coerceAtLeast(1000)}ms"><$element src="${picture.filename}" region="Image"/>$captionElement</par></body></smil>"""
    part("application/smil", "presentation.smil", "presentation", smil.toByteArray(Charsets.UTF_8))
    part(picture.mime, picture.filename, "attachment", picture.bytes)
    if (caption.isNotEmpty()) part("text/plain", "caption.txt", "caption", caption)
}.toByteArray()

/** Reads the MMS Send.conf fields a carrier returns through SmsManager. */
internal fun acceptedMmsResponse(response: ByteArray): Boolean {
    val input = java.io.ByteArrayInputStream(response)
    var isSendConfirmation = false
    var isAccepted = false
    fun skipText(): Boolean {
        while (true) {
            when (input.read()) {
                -1 -> return false
                0 -> return true
            }
        }
    }
    while (input.available() > 0) {
        when (input.read()) {
            0x8c -> isSendConfirmation = input.read() == 0x81
            0x92 -> isAccepted = input.read() == 0x80
            0x8d -> if (input.read() < 0x80) return false
            0x98, 0x8b -> if (!skipText()) return false  // transaction ID or message ID
            0x93 -> { // optional encoded response text
                input.mark(1)
                val first = input.read()
                if (first in 1..30) {
                    if (input.skip(first.toLong()) != first.toLong()) return false
                } else {
                    input.reset()
                    if (!skipText()) return false
                }
            }
            else -> return isSendConfirmation && isAccepted
        }
    }
    return isSendConfirmation && isAccepted
}

private class Wsp : ByteArrayOutputStream() {
    fun text(value: String) {
        write(value.toByteArray(Charsets.UTF_8))
        write(0)
    }

    fun valueLength(length: Int) {
        if (length <= 30) write(length) else { write(31); uintvar(length) }
    }

    fun uintvar(number: Int) {
        var shift = 0
        while (shift < 28 && number ushr (shift + 7) != 0) shift += 7
        while (shift > 0) {
            write(((number ushr shift) and 0x7f) or 0x80)
            shift -= 7
        }
        write(number and 0x7f)
    }

    fun part(mime: String, name: String, contentId: String, data: ByteArray) {
        val headers = Wsp().apply {
            val type = Wsp().apply {
                text(mime)
                write(0x85); text(name) // name parameter
                if (mime == "text/plain") { write(0x81); write(0xea) } // UTF-8
            }.toByteArray()
            valueLength(type.size); write(type)
            write(0xc0); write(0x22); text("<$contentId>")
            write(0x8e); text(name)
        }.toByteArray()
        uintvar(headers.size); uintvar(data.size); write(headers); write(data)
    }
}
