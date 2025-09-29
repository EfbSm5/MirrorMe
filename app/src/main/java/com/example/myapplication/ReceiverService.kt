package com.example.myapplication

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
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.net.Socket

class ReceiverService : Service() {

    private var mediaCodec: MediaCodec? = null
    private var surface: Surface? = null
    private var clientSocket: Socket? = null
    private var inputStream: InputStream? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val binder = LocalBinder()

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
            serviceScope.launch {
                connectToSender(senderIp)
            }
        }
        return START_NOT_STICKY
    }

    private fun connectToSender(senderIp: String) {
        try {
            clientSocket = Socket(senderIp, SERVER_PORT)
            inputStream = clientSocket?.getInputStream()
            Handler(Looper.getMainLooper()).post {
                initDecoder()
            }
            startDecoding()
        } catch (e: IOException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, "Failed to connect to sender", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setSurface(surface: Surface) {
        this.surface = surface
    }

    private fun initDecoder() {
        if (surface == null) {
            // Surface is not ready yet, decoder will be initialized when setSurface is called.
            return
        }
        val mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT)
        try {
            mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            mediaCodec?.configure(mediaFormat, surface, null, 0)
            mediaCodec?.start()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun startDecoding() {
        val buffer = ByteArray(1024 * 1024)
        while (isActive(serviceJob)) {
            try {
                val bytes = inputStream?.read(buffer) ?: -1
                if (bytes > 0) {
                    val inputBufferIndex = mediaCodec?.dequeueInputBuffer(10000)
                    if (inputBufferIndex != null && inputBufferIndex >= 0) {
                        val inputBuffer = mediaCodec?.getInputBuffer(inputBufferIndex)
                        inputBuffer?.clear()
                        inputBuffer?.put(buffer, 0, bytes)
                        mediaCodec?.queueInputBuffer(inputBufferIndex, 0, bytes, System.nanoTime() / 1000, 0)
                    }

                    var bufferInfo = MediaCodec.BufferInfo()
                    var outputBufferIndex = mediaCodec?.dequeueOutputBuffer(bufferInfo, 0)
                    while (outputBufferIndex != null && outputBufferIndex >= 0) {
                        mediaCodec?.releaseOutputBuffer(outputBufferIndex, true)
                        outputBufferIndex = mediaCodec?.dequeueOutputBuffer(bufferInfo, 0)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                break
            }
        }
    }

    private fun isActive(job: Job): Boolean {
        return job.isActive
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        try {
            inputStream?.close()
            clientSocket?.close()
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

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
