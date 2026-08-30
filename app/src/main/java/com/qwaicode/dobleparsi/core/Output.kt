/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Made in the open by QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File naming and output paths — ported from src/filenames.js.
 *
 *   Download/Doble Parsi/<name>/<name>.m4a
 *   Download/Doble Parsi/<name>/<name>.srt
 */
object Output {

    const val ROOT_FOLDER = "Doble Parsi"

    private val ILLEGAL = Regex("[\\x00-\\x1f\\x7f<>:\"/\\\\|?*]")
    private val NOTIFICATION_PREFIX = Regex("^\\(\\s*\\d+\\s*\\)\\s*")
    private val LEADING_JUNK = Regex("^[▶►▷•\\s]+")
    private val BRAND_SPLIT = Regex("\\s+[-|–—·•]\\s+")

    private val KNOWN_BRANDS = setOf(
        "youtube", "youtubemusic", "netflix", "vimeo", "twitch", "dailymotion", "aparat",
        "آپارات", "نماوا", "namava", "فیلیمو", "filimo", "telewebion", "تلوبیون",
        "primevideo", "disneyplus", "hulu", "hbomax", "appletv", "spotify", "soundcloud",
        "bilibili", "rutube", "odysee", "rumble", "coursera", "udemy", "edx", "linkedin",
        "facebook", "instagram", "tiktok", "twitter", "reddit", "telegram", "zoom",
        "teams", "meet", "ted", "bbc", "cnn", "aljazeera", "euronews", "chrome", "firefox"
    )

    private fun normalizeBrand(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[\\s._]+"), "").replace(Regex("[™®]"), "")

    /** Media session title to a readable, filesystem safe name. */
    fun cleanTitle(title: String?): String {
        var text = (title ?: "").trim()
        text = NOTIFICATION_PREFIX.replace(text, "")
        text = LEADING_JUNK.replace(text, "")

        repeat(2) {
            val segments = text.split(BRAND_SPLIT)
            if (segments.size < 2) return@repeat
            val last = segments.last().trim()
            if (last.length in 1..32 && KNOWN_BRANDS.contains(normalizeBrand(last))) {
                text = segments.dropLast(1).joinToString(" - ").trim()
            }
        }

        text = ILLEGAL.replace(text, " ").replace(Regex("\\s+"), " ").trim().trim('.')
        if (text.length > 90) text = text.take(90).trim()
        return text
    }

    fun timestampTag(): String =
        SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US).format(Date())

    /**
     * Final folder/file name for a session.
     *
     * Priority: the real media title -> "<player app> <timestamp>" -> "<app>
     * <timestamp>". Only the last two are timestamps, so a proper title always wins.
     */
    fun sessionName(rawTitle: String?, playerLabel: String?, fallbackLabel: String): String {
        val cleaned = cleanTitle(rawTitle)
        if (cleaned.isNotEmpty()) return cleaned
        val player = cleanTitle(playerLabel)
        val prefix = if (player.isNotEmpty()) player else fallbackLabel
        return "$prefix ${timestampTag()}"
    }

    /** True when the name is a timestamp fallback rather than a real video title. */
    fun isFallbackName(name: String): Boolean =
        Regex("\\s\\d{4}-\\d{2}-\\d{2} \\d{2}-\\d{2}$").containsMatchIn(name)

    private fun relativePath(sessionName: String): String =
        "${Environment.DIRECTORY_DOWNLOADS}/$ROOT_FOLDER/$sessionName/"

    private fun insert(context: Context, sessionName: String, fileName: String, mime: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(sessionName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }

    fun publish(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        try {
            context.contentResolver.update(uri, values, null, null)
        } catch (_: Throwable) {
        }
    }

    class PendingFile(val uri: Uri, val descriptor: ParcelFileDescriptor)

    /** Opens an .m4a target for the muxer. */
    fun createAudio(context: Context, sessionName: String): PendingFile? {
        val uri = insert(context, sessionName, "$sessionName.m4a", "audio/mp4") ?: return null
        val fd = try {
            context.contentResolver.openFileDescriptor(uri, "rw")
        } catch (_: Throwable) {
            null
        } ?: return null
        return PendingFile(uri, fd)
    }

    /**
     * Moves/renames an already created entry, used when the real video title only
     * becomes known after the session started.
     */
    fun rename(context: Context, uri: Uri, newSessionName: String, newFileName: String): Boolean {
        val resolver = context.contentResolver
        val both = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newFileName)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(newSessionName))
        }
        if (runCatching { resolver.update(uri, both, null, null) > 0 }.getOrDefault(false)) return true

        // Some OEM providers refuse DISPLAY_NAME and RELATIVE_PATH in one update.
        val movedFolder = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(newSessionName))
        }
        val renamedFile = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newFileName)
        }
        val moved = runCatching { resolver.update(uri, movedFolder, null, null) > 0 }
            .getOrDefault(false)
        val renamed = runCatching { resolver.update(uri, renamedFile, null, null) > 0 }
            .getOrDefault(false)
        return moved || renamed
    }

    fun writeText(context: Context, sessionName: String, fileName: String, mime: String, body: String): Uri? {
        val uri = insert(context, sessionName, fileName, mime) ?: return null
        try {
            context.contentResolver.openOutputStream(uri, "w")?.use { stream ->
                stream.write(body.toByteArray(Charsets.UTF_8))
                stream.flush()
            }
        } catch (_: Throwable) {
            return null
        }
        publish(context, uri)
        return uri
    }
}
