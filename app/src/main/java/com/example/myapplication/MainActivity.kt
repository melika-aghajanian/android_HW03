package com.example.myapplication

import com.example.myapplication.ui.theme.MyApplicationTheme
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.*
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit
import android.provider.Settings
import android.bluetooth.BluetoothAdapter
import android.util.Log
import android.os.Handler
import android.os.Looper
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.File

class MainActivity : ComponentActivity() {
    private var isInternetConnected by mutableStateOf(false)
    private lateinit var connectivityManager: ConnectivityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        InternetStatus(isInternetConnected)
                        LogList() // Display log list
                    }
                }
            }
        }

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        registerInternetBroadcastReceiver()
        scheduleAirplaneBluetoothCheck()
    }

    @Composable
    fun InternetStatus(isConnected: Boolean) {
        val status = if (isConnected) "Connected" else "Disconnected"
        Text(
            text = "Internet Status: $status",
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }

    private fun registerInternetBroadcastReceiver() {
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(internetReceiver, filter)
    }

    private val internetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                isInternetConnected = isInternetConnected(context)
                val message = if (isInternetConnected) "Internet connected" else "Internet disconnected"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isInternetConnected(context: Context?): Boolean {
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo?.isConnectedOrConnecting ?: false
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(internetReceiver)
    }

    private fun scheduleAirplaneBluetoothCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<AirplaneBluetoothWorker>(
            repeatInterval = 2,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "AirplaneBluetoothCheck",
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicWorkRequest
        )
    }

    @Composable
    fun LogList() {
        val logs = readLogsFromFile(this)
        LazyColumn {
            items(logs) { log ->
                LogItem(log)
            }
        }
    }

    @Composable
    fun LogItem(log: String) {
        Text(text = log)
    }

    private fun readLogsFromFile(context: Context): List<String> {
        val logs = mutableListOf<String>()
        try {
            val file = File(context.filesDir, AirplaneBluetoothWorker.LOG_FILE_NAME)
            if (file.exists()) {
                file.forEachLine { line ->
                    logs.add(line)
                }
            }
        } catch (e: Exception) {
            Log.e(AirplaneBluetoothWorker.TAG, "Error reading log file: ${e.message}")
        }
        return logs
    }
}

class AirplaneBluetoothWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    companion object {
        const val TAG = "airplane_worker"
        const val LOG_FILE_NAME = "log.txt"
    }

    private val LOG_INTERVAL_MS = TimeUnit.MINUTES.toMillis(2)
    private var timer: Timer? = null

    override fun doWork(): Result {
        val currentTimeMillis = System.currentTimeMillis()
        Log.i(TAG, "Work started at: $currentTimeMillis")

        // Start periodic logging task
        startLoggingTask()

        return Result.success()
    }

    private fun startLoggingTask() {
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                logStatus()
            }
        }, 0, LOG_INTERVAL_MS)
    }

    private fun logStatus() {
        val isAirplaneModeOn = Settings.Global.getInt(
            applicationContext.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0
        ) != 0
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val isBluetoothOn = bluetoothAdapter?.isEnabled ?: false

        val logMessage = "Timestamp: ${getCurrentTimeStamp()} | Airplane Mode: ${if (isAirplaneModeOn) "ON" else "OFF"} | Bluetooth: ${if (isBluetoothOn) "ON" else "OFF"}"
        Log.i(TAG, logMessage)

        // Write log to file
        writeLogToFile(logMessage)
    }

    private fun getCurrentTimeStamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun writeLogToFile(logMessage: String) {
        try {
            val file = File(applicationContext.filesDir, LOG_FILE_NAME)
            val fos = FileOutputStream(file, true)
            fos.write("$logMessage\n".toByteArray())
            fos.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to file: ${e.message}")
        }
    }

    override fun onStopped() {
        super.onStopped()
        timer?.cancel()
    }
}