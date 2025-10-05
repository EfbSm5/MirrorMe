package com.example.castapp

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

class SenderService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaCodec: MediaCodec? = null
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var dataOut: DataOutputStream? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var mediaProjectionManager: MediaProjectionManager

    companion object {
        const val ACTION_START = "com.example.castapp.START_SENDER"
        const val ACTION_STOP = "com.example.castapp.STOP_SENDER"
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
        mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
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
            ACTION_START -> startScreenSharing(intent)
            ACTION_STOP -> stopScreenSharing()
        }
        return START_STICKY
    }

    private fun startScreenSharing(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData!!)

        setupVirtualDisplay()
        setupMediaCodec()
        startSocketServer()
    }

    private fun setupVirtualDisplay() {
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(metrics)

        val inputSurface = mediaCodec?.createInputSurface()
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "SenderDisplay",
            VIDEO_WIDTH,
            VIDEO_HEIGHT,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            inputSurface,
            null,
            null
        )
    }

    private fun setupMediaCodec() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, 6000000)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodec?.start()
    }

    private fun startSocketServer() {
        serviceScope.launch {
            try {
                serverSocket = ServerSocket(SERVER_PORT)
                clientSocket = serverSocket?.accept()
                dataOut = DataOutputStream(clientSocket?.getOutputStream())

                val bufferInfo = MediaCodec.BufferInfo()
                while (true) {
                    val outputBufferId = mediaCodec?.dequeueOutputBuffer(bufferInfo, 10000) ?: -1
                    if (outputBufferId >= 0) {
                        val encodedData = mediaCodec?.getOutputBuffer(outputBufferId)
                        val chunk = ByteArray(bufferInfo.size)
                        encodedData?.get(chunk)
                        dataOut?.writeInt(bufferInfo.size)
                        dataOut?.write(chunk)
                        mediaCodec?.releaseOutputBuffer(outputBufferId, false)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun stopScreenSharing() {
        serviceScope.launch {
            try {
                dataOut?.close()
                clientSocket?.close()
                serverSocket?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        mediaCodec?.stop()
        mediaCodec?.release()
        virtualDisplay?.release()
        mediaProjection?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScreenCapture()
        serviceJob.cancel()
        try {
            dataOut?.close()
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

    override fun onBind(intent: Intent?): IBinder? = null

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
