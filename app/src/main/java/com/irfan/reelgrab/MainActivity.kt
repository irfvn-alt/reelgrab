package com.irfan.reelgrab

import android.app.Activity
import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {

    // We will replace this after deploying the resolver.
    private val resolverUrl = "https://reelgrab-resolver.onrender.com/resolve"

    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var linkInput: EditText
    private lateinit var status: TextView
    private lateinit var downloadButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 40, 28, 25)
            setBackgroundColor(0xFF10131B.toInt())
        }

        val title = TextView(this).apply {
            text = "REELGRAB"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(0xFFE4B85C.toInt())
        }

        val subtitle = TextView(this).apply {
            text = "INSTAGRAM DOWNLOADER"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFFAAAAAA.toInt())
        }

        linkInput = EditText(this).apply {
            hint = "Paste Instagram link here..."
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF999999.toInt())
            minLines = 2
            setPadding(16, 15, 16, 15)
        }

        val pasteButton = Button(this).apply {
            text = "PASTE LINK"

            setOnClickListener {
                val clipboard =
                    getSystemService(Context.CLIPBOARD_SERVICE)
                        as ClipboardManager

                val copied = clipboard.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(this@MainActivity)
                    ?.toString()

                if (!copied.isNullOrBlank()) {
                    linkInput.setText(copied)
                } else {
                    showStatus("Nothing copied yet.")
                }
            }
        }

        downloadButton = Button(this).apply {
            text = "DOWNLOAD"
            setTextColor(0xFF171717.toInt())
            setBackgroundColor(0xFFE4B85C.toInt())

            setOnClickListener {
                startDownload()
            }
        }

        status = TextView(this).apply {
            text = "Paste an Instagram link to begin."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFFBBBBBB.toInt())
            setPadding(5, 25, 5, 15)
        }

        layout.addView(title)
        layout.addView(subtitle)

        layout.addView(
            Space(this),
            LinearLayout.LayoutParams(1, 35)
        )

        layout.addView(
            linkInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        layout.addView(pasteButton)
        layout.addView(downloadButton)
        layout.addView(status)

        setContentView(layout)

        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!sharedText.isNullOrBlank()) {
            linkInput.setText(sharedText)
        }
    }

    private fun startDownload() {
        val link = linkInput.text.toString().trim()
        val uri = Uri.parse(link)
        val host = uri.host?.lowercase() ?: ""

        val validHost = host in listOf(
            "instagram.com",
            "www.instagram.com",
            "instagr.am",
            "www.instagr.am"
        )

        if (!link.startsWith("https://") || !validHost) {
            showStatus("Invalid Instagram link.")
            return
        }

        if (resolverUrl.contains("YOUR-SERVER-DOMAIN")) {
            showStatus("Resolver server is not connected yet.")
            return
        }

        downloadButton.isEnabled = false
        showStatus("Checking Instagram link...")

        executor.execute {
            try {
                val connection =
                    URL(resolverUrl).openConnection()
                        as HttpURLConnection

                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 30000
                connection.doOutput = true

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                val body = JSONObject()
                    .put("url", link)
                    .toString()

                connection.outputStream.use {
                    it.write(body.toByteArray(Charsets.UTF_8))
                }

                val code = connection.responseCode

                val stream = if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val response = stream.bufferedReader().use {
                    it.readText()
                }

                connection.disconnect()

                val json = JSONObject(response)

                if (code !in 200..299) {
                    throw Exception(
                        json.optString(
                            "detail",
                            "Media not found"
                        )
                    )
                }

                val mediaUrl = json.getString("media_url")
                val title = json.optString(
                    "title",
                    "instagram_media"
                )

                runOnUiThread {
                    saveMedia(mediaUrl, title)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    showStatus(
                        e.message ?: "Download failed."
                    )
                    downloadButton.isEnabled = true
                }
            }
        }
    }

    private fun saveMedia(mediaUrl: String, title: String) {
        try {
            val request =
                DownloadManager.Request(Uri.parse(mediaUrl))

            request.setTitle("ReelGrab")
            request.setDescription("Downloading Instagram media")

            request.setNotificationVisibility(
                DownloadManager.Request
                    .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )

            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_MOVIES,
                "ReelGrab/${safeName(title)}.mp4"
            )

            request.setMimeType("video/mp4")

            val manager =
                getSystemService(DOWNLOAD_SERVICE)
                    as DownloadManager

            manager.enqueue(request)

            showStatus(
                "Download started! Check Movies/ReelGrab."
            )

        } catch (e: Exception) {
            showStatus("Download failed: ${e.message}")
        } finally {
            downloadButton.isEnabled = true
        }
    }

    private fun safeName(name: String): String {
        return name
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(60)
            .ifBlank { "instagram_media" }
    }

    private fun showStatus(message: String) {
        status.text = message
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
