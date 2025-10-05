package com.example.castapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Binder
import android.os.IBinder
import android.view.Surface
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
        val notification =
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID).setContentTitle("Screen Mirroring")
                .setContentText("Receiver service is running.").setSmallIcon(R.mipmap.ic_launcher)
                .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val senderIp = intent?.getStringExtra(EXTRA_SENDER_IP)
        if (senderIp != null) {
            startSocketClient(senderIp)
        } else {
            CastApp.showMsg("Sender IP not provided")
        }
        return START_STICKY
    }

    private fun startSocketClient(senderIp: String) {
        serviceScope.launch {
            try {
                clientSocket = Socket(senderIp, SERVER_PORT)
                dataIn = DataInputStream(clientSocket?.getInputStream())

                setupMediaCodec()
                receiveAndDecodeFrames()
            } catch (e: IOException) {
                e.printStackTrace()
                CastApp.showMsg("Failed to connect to sender")
            }
        }
    }

    private fun setupMediaCodec() {
        val format =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT)
        format.setByteBuffer("csd-0", csd0?.let { java.nio.ByteBuffer.wrap(it) })
        format.setByteBuffer("csd-1", csd1?.let { java.nio.ByteBuffer.wrap(it) })

        mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        mediaCodec?.configure(format, surface, null, 0)
        mediaCodec?.start()
        decoderConfigured = true
    }

    private fun receiveAndDecodeFrames() {
        val bufferInfo = MediaCodec.BufferInfo()
        try {
            while (true) {
                val frameSize = dataIn?.readInt() ?: break
                val frameData = ByteArray(frameSize)
                dataIn?.readFully(frameData)

                val inputBufferId = mediaCodec?.dequeueInputBuffer(10000) ?: -1
                if (inputBufferId >= 0) {
                    val inputBuffer = mediaCodec?.getInputBuffer(inputBufferId)
                    inputBuffer?.clear()
                    inputBuffer?.put(frameData)
                    mediaCodec?.queueInputBuffer(
                        inputBufferId,
                        0,
                        frameSize,
                        System.nanoTime() / 1000,
                        0
                    )
                }

                val outputBufferId = mediaCodec?.dequeueOutputBuffer(bufferInfo, 10000) ?: -1
                if (outputBufferId >= 0) {
                    mediaCodec?.releaseOutputBuffer(outputBufferId, true)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
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
        mediaCodec?.stop()
        mediaCodec?.release()
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

    fun setSurface(surface: Surface) {
        this.surface = surface
        if (decoderConfigured) {
            mediaCodec?.setOutputSurface(surface)
        }
    }
}
