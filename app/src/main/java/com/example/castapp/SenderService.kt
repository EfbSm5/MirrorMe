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

            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startServer() {
        try {
            serverSocket = ServerSocket(SERVER_PORT)
            clientSocket = serverSocket?.accept()
            dataOut = DataOutputStream(clientSocket?.getOutputStream())
            mediaCodec?.start()
            serviceScope.launch { sendDataLoop() }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

        val metrics = DisplayMetrics()
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)

        val mediaFormat =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT)
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1_500_000)
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        mediaFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
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

    private fun sendDataLoop() {
        val codec = mediaCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        try {
            while (serviceJob.isActive) {
                when (val index = codec.dequeueOutputBuffer(bufferInfo, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Send CSD (SPS/PPS) so receiver can configure decoder
                        val fmt = codec.outputFormat
                        val csd0 = fmt.getByteBuffer("csd-0")
                        val csd1 = fmt.getByteBuffer("csd-1")
                        if (csd0 != null && csd0.remaining() > 0) {
                            val sps = ByteArray(csd0.remaining())
                            csd0.get(sps)
                            dataOut?.writeInt(-1) // tag for csd-0
                            dataOut?.writeInt(sps.size)
                            dataOut?.write(sps)
                            dataOut?.flush()
                        }
                        if (csd1 != null && csd1.remaining() > 0) {
                            val pps = ByteArray(csd1.remaining())
                            csd1.get(pps)
                            dataOut?.writeInt(-2) // tag for csd-1
                            dataOut?.writeInt(pps.size)
                            dataOut?.write(pps)
                            dataOut?.flush()
                        }
                    }

                    else -> {
                        if (index >= 0) {
                            val outputBuffer = codec.getOutputBuffer(index)
                            if (outputBuffer != null && bufferInfo.size > 0) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                val frame = ByteArray(bufferInfo.size)
                                outputBuffer.get(frame)
                                dataOut?.writeInt(frame.size)
                                dataOut?.write(frame)
                                dataOut?.flush()
                            }
                            codec.releaseOutputBuffer(index, false)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            // Socket disconnected or other IO issue: stop gracefully
            e.printStackTrace()
        }
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
