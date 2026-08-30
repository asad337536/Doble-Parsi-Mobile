/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Made in the open by QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.media

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.provider.Settings
import android.service.notification.NotificationListenerService

/**
 * Notification listener, only there so the app is allowed to read the active media
 * session. That session is where the *real* name of what is playing lives — the
 * Android equivalent of reading mediaSession / og:title inside the page.
 */
class MediaTitleService : NotificationListenerService()

object MediaTitle {

    fun isEnabled(context: Context): Boolean {
        val flat = Settings.Secure
            .getString(context.contentResolver, "enabled_notification_listeners")
            .orEmpty()
        if (flat.isBlank()) return false
        return flat.split(':').any { entry ->
            ComponentName.unflattenFromString(entry)?.packageName == context.packageName
        }
    }

    private fun controllers(context: Context): List<MediaController> {
        if (!isEnabled(context)) return emptyList()
        return try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            manager.getActiveSessions(ComponentName(context, MediaTitleService::class.java))
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun titleOf(controller: MediaController): String? {
        val metadata = controller.metadata ?: return null
        val title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        return title?.takeIf { it.isNotBlank() }
    }

    private fun isPlaying(controller: MediaController): Boolean =
        controller.playbackState?.state == PlaybackState.STATE_PLAYING

    /** Title of whatever is playing right now, or null. Playing sessions win. */
    fun current(context: Context): String? {
        val all = controllers(context)
        return all.firstOrNull { isPlaying(it) }?.let { titleOf(it) }
            ?: all.asSequence().mapNotNull { titleOf(it) }.firstOrNull()
    }

    /** Label of the app that owns the playing session, e.g. "YouTube". */
    fun currentAppLabel(context: Context): String? {
        val controller = controllers(context).firstOrNull { isPlaying(it) }
            ?: controllers(context).firstOrNull()
            ?: return null
        val packageName = controller.packageName ?: return null
        return try {
            val manager = context.packageManager
            manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * Keeps watching the media session for the whole session.
 *
 * Reading the title only once, at the moment Start is pressed, was the reason the
 * files ended up called "Doble Parsi 2026-08-29 18-51": the user starts the app
 * first and only then opens YouTube, so at start time there is no media session at
 * all. The watcher polls while dubbing and the final name is decided when the
 * session ends — exactly what the Chrome extension does when it re-reads the page
 * title before downloading.
 */
class MediaTitleWatcher(private val context: Context) {

    private companion object {
        const val POLL_MS = 900L
    }

    private var handler: Handler? = null
    private var firstTitle: String? = null
    private var lastTitle: String? = null
    private var appLabel: String? = null

    private val poll = object : Runnable {
        override fun run() {
            sample()
            handler?.postDelayed(this, POLL_MS)
        }
    }

    fun start(handler: Handler) {
        this.handler = handler
        sample()
        handler.postDelayed(poll, POLL_MS)
    }

    fun stop() {
        handler?.removeCallbacks(poll)
        handler = null
    }

    fun sample() {
        val title = MediaTitle.current(context)?.trim()?.takeIf { it.isNotEmpty() }
        if (title != null) {
            if (firstTitle == null) firstTitle = title
            lastTitle = title
        }
        if (appLabel == null) appLabel = MediaTitle.currentAppLabel(context)
    }

    /** Best known title: the one that was playing longest, else the latest. */
    fun bestTitle(): String? = firstTitle ?: lastTitle

    fun playerLabel(): String? = appLabel
}
