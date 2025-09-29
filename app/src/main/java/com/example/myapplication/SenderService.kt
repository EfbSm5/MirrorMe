package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.*
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

class SenderService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaCodec: MediaCodec? = null
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var clientStream: OutputStream? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var mediaProjectionManager: MediaProjectionManager

    companion object {
        const val ACTION_START = "com.example.myapplication.START_SENDER"
        const val ACTION_STOP = "com.example.myapplication.STOP_SENDER"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        private const val NOTIFICATION_CHANNEL_ID = "SenderServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val SERVER_PORT = 8080
        private const val VIDEO_WIDTH = 720
        private const val VIDEO_HEIGHT = 1280
    }

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Screen Mirroring")
            .setContentText("Sender service is running.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultCode != -1 && resultData != null) {
                    serviceScope.launch {
                        startScreenCapture(resultCode, resultData)
                        startServer()
                    }
                }
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startServer() {
        try {
            serverSocket = ServerSocket(SERVER_PORT)
            clientSocket = serverSocket?.accept()
            clientStream = clientSocket?.getOutputStream()
            mediaCodec?.start()
            serviceScope.launch {
                sendData()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

        val metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(metrics)

        val mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT)
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1000000)
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            mediaCodec?.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = mediaCodec?.createInputSurface()
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                VIDEO_WIDTH, VIDEO_HEIGHT, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, null
            )
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun sendData() {
        val bufferInfo = MediaCodec.BufferInfo()
        while (isActive(serviceJob)) {
            val outputBufferIndex = mediaCodec?.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputBufferIndex != null && outputBufferIndex >= 0) {
                val outputBuffer = mediaCodec?.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null) {
                    val data = ByteArray(bufferInfo.size)
                    outputBuffer.get(data)
                    try {
                        clientStream?.write(data)
                    } catch (e: IOException) {
                        e.printStackTrace()
                        break
                    }
                }
                mediaCodec?.releaseOutputBuffer(outputBufferIndex, false)
            }
        }
    }

    private fun isActive(job: Job): Boolean {
        return job.isActive
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScreenCapture()
        serviceJob.cancel()
        try {
            clientStream?.close()
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun stopScreenCapture() {
        virtualDisplay?.release()
        mediaCodec?.stop()
        mediaCodec?.release()
        mediaProjection?.stop()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Sender Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}

