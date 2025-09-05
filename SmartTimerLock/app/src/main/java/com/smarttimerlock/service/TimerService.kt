package com.smarttimerlock.service

import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.*
import android.os.Build
import android.os.CountDownTimer
import androidx.core.app.NotificationCompat
import com.smarttimerlock.R
import com.smarttimerlock.admin.MyDeviceAdminReceiver
import com.smarttimerlock.ui.MainActivity

class TimerService : Service() {

    companion object {
        const val CHANNEL_ID = "smart_timer_lock_channel"
        const val NOTI_ID = 1001

        const val ACTION_START = "com.smarttimerlock.action.START"
        const val ACTION_STOP = "com.smarttimerlock.action.STOP"
        const val ACTION_TICK = "com.smarttimerlock.action.TICK"
        const val ACTION_FINISH = "com.smarttimerlock.action.FINISH"

        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_REMAIN_MS = "remain_ms"
    }

    private var timer: CountDownTimer? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTI_ID, buildNotification("타이머 준비"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val duration = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
                startTimer(duration)
            }
            ACTION_STOP -> {
                stopTimer()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startTimer(durationMs: Long) {
        stopTimer()
        timer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                notify("남은 시간: ${millisUntilFinished/60000}분")
                sendBroadcast(Intent(ACTION_TICK).putExtra(EXTRA_REMAIN_MS, millisUntilFinished))
            }

            override fun onFinish() {
                notify("완료됨 - 잠금 전환")
                // Try lockNow via DevicePolicyManager
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = ComponentName(this@TimerService, MyDeviceAdminReceiver::class.java)
                if (dpm.isAdminActive(admin)) {
                    dpm.lockNow()
                }
                sendBroadcast(Intent(ACTION_FINISH))
                stopSelf()
            }
        }.start()
        notify("타이머 시작")
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
        notify("타이머 중지")
    }

    private fun notify(text: String) {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("스마트 타이머 잠금")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_lock_timer)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTI_ID, notif)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Smart Timer Lock", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("스마트 타이머 잠금")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_lock_timer)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
