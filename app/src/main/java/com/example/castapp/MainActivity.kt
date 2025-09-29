package com.example.castapp

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.example.castapp.ui.AppSurface
import com.example.castapp.ui.theme.MyApplicationTheme

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private var receiverService: ReceiverService? = null
    private var isReceiverServiceBound = false

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
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isReceiverServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                AppSurface()
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        if (isReceiverServiceBound) {
            unbindService(serviceConnection)
            isReceiverServiceBound = false
        }
        stopService(Intent(this, SenderService::class.java))
        stopService(Intent(this, ReceiverService::class.java))
    }
}
