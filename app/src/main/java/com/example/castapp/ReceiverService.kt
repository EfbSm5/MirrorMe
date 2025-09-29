package com.example.castapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Surface
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.IOException
import java.net.Socket

class ReceiverService : Service() {

    private var mediaCodec: MediaCodec? = null
    private var surface: Surface? = null
    private var clientSocket: Socket? = null
    private var dataIn: DataInputStream? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val binder = LocalBinder()

    private var csd0: ByteArray? = null
    private var csd1: ByteArray? = null
    private var decoderConfigured = false

    companion object {
        const val EXTRA_SENDER_IP = "senderIp"
        private const val NOTIFICATION_CHANNEL_ID = "ReceiverServiceChannel"
        private const val NOTIFICATION_ID = 2
        private const val SERVER_PORT = 8080
        private const val VIDEO_WIDTH = 720
        private const val VIDEO_HEIGHT = 1280
    }

    inner class LocalBinder : Binder() {
        fun getService(): ReceiverService = this@ReceiverService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Screen Mirroring")
            .setContentText("Receiver service is running.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val senderIp = intent?.getStringExtra(EXTRA_SENDER_IP)
        if (senderIp != null) {
            serviceScope.launch { connectToSender(senderIp) }
        }
        return START_NOT_STICKY
    }

    private fun connectToSender(senderIp: String) {
        try {
            clientSocket = Socket(senderIp, SERVER_PORT)
            dataIn = DataInputStream(clientSocket!!.getInputStream())
            startDecodingLoop()
        } catch (e: IOException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    applicationContext,
                    "Failed to connect to sender",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun setSurface(surface: Surface) {
        this.surface = surface
        initDecoderIfNeeded()
    }

    private fun initDecoderIfNeeded() {
        if (decoderConfigured) return
        val s = surface ?: return
        val sps = csd0 ?: return
        val pps = csd1 ?: return
        try {
            val format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                VIDEO_WIDTH,
                VIDEO_HEIGHT
            )
            format.setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(sps))
            format.setByteBuffer("csd-1", java.nio.ByteBuffer.wrap(pps))
            mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            mediaCodec?.configure(format, s, null, 0)
            mediaCodec?.start()
            decoderConfigured = true
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun startDecodingLoop() {
        serviceScope.launch {
            try {
                while (serviceJob.isActive) {
                    val tag = try {
                        dataIn?.readInt() ?: break
                    } catch (e: IOException) {
                        break
                    }
                    if (tag == -1 || tag == -2) {
                        val len = dataIn?.readInt() ?: break
                        if (len <= 0) continue
                        val buf = ByteArray(len)
                        var r = 0
                        while (r < len) {
                            val n = dataIn?.read(buf, r, len - r) ?: -1
                            if (n <= 0) throw IOException("Stream closed")
                            r += n
                        }
                        if (!decoderConfigured) {
                            if (tag == -1) csd0 = buf else if (tag == -2) csd1 = buf
                            initDecoderIfNeeded()
                        }
                        continue
                    }
                    val len = tag
                    if (len <= 0) continue
                    val frame = ByteArray(len)
                    var read = 0
                    while (read < len) {
                        val n = dataIn?.read(frame, read, len - read) ?: -1
                        if (n <= 0) throw IOException("Stream closed")
                        read += n
                    }
                    val codec = mediaCodec
                    if (codec != null && decoderConfigured) {
                        val inIndex = codec.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val inBuf = codec.getInputBuffer(inIndex)
                            inBuf?.clear()
                            if (inBuf != null && inBuf.capacity() >= len) {
                                inBuf.put(frame)
                                codec.queueInputBuffer(inIndex, 0, len, System.nanoTime() / 1000, 0)
                            } else {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, 0)
                            }
                        }
                        val bufferInfo = MediaCodec.BufferInfo()
                        var outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                        while (outIndex >= 0) {
                            codec.releaseOutputBuffer(outIndex, true)
                            outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(applicationContext, "Receiver disconnected", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        try {
            dataIn?.close()
            clientSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (_: Exception) {
        }
        mediaCodec = null
        surface = null
        csd0 = null
        csd1 = null
        decoderConfigured = false
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Receiver Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
