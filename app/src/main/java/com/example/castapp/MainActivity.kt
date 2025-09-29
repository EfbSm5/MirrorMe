package com.example.castapp

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.IBinder
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.example.castapp.ui.AppSurface
import com.example.castapp.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private var receiverService: ReceiverService? = null
    private var isReceiverServiceBound = false
    private var pendingSurface: Surface? = null

    private val mediaProjectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val intent = Intent(this, SenderService::class.java).apply {
                action = SenderService.ACTION_START
                putExtra(SenderService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(SenderService.EXTRA_RESULT_DATA, result.data)
            }
            startForegroundService(intent)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ReceiverService.LocalBinder
            receiverService = binder.getService()
            isReceiverServiceBound = true
            pendingSurface?.let { surface ->
                receiverService?.setSurface(surface)
                pendingSurface = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isReceiverServiceBound = false
            receiverService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val myIp = getIpAddress()
        val gatewayIp = getGatewayIpAddress()
        setContent {
            MyApplicationTheme {
                AppSurface(
                    myIp = myIp,
                    gatewayIp = gatewayIp,
                    onStartSend = {
                        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                    },
                    onStartReceive = { surface ->
                        // Start and bind receiver service, pass gateway IP so it can connect
                        val startIntent = Intent(this, ReceiverService::class.java).apply {
                            putExtra(ReceiverService.EXTRA_SENDER_IP, gatewayIp)
                        }
                        startForegroundService(startIntent)
                        bindService(startIntent, serviceConnection, BIND_AUTO_CREATE)
                        if (isReceiverServiceBound) {
                            receiverService?.setSurface(surface)
                        } else {
                            pendingSurface = surface
                        }
                    },
                    onStopReceive = {
                        pendingSurface = null
                        if (isReceiverServiceBound) {
                            unbindService(serviceConnection)
                            isReceiverServiceBound = false
                        }
                        stopService(Intent(this, ReceiverService::class.java))
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingSurface = null
        if (isReceiverServiceBound) {
            unbindService(serviceConnection)
            isReceiverServiceBound = false
        }
        stopService(Intent(this, SenderService::class.java))
        stopService(Intent(this, ReceiverService::class.java))
    }
}
