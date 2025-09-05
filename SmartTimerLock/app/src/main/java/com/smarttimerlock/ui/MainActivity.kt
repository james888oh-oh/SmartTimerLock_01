package com.smarttimerlock.ui

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.*
import android.os.*
import android.provider.Settings
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.smarttimerlock.R
import com.smarttimerlock.admin.MyDeviceAdminReceiver
import com.smarttimerlock.databinding.ActivityMainBinding
import com.smarttimerlock.service.TimerService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminName: ComponentName

    private val timerBroadcast = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TimerService.ACTION_TICK) {
                val remain = intent.getLongExtra(TimerService.EXTRA_REMAIN_MS, 0L)
                binding.statusText.text = "남은 시간: ${remain/60000}분 ${((remain%60000)/1000)}초"
            } else if (intent?.action == TimerService.ACTION_FINISH) {
                binding.statusText.text = "완료"
                try {
                    stopLockTask()
                } catch (_: Exception) { }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("smart_timer_lock", MODE_PRIVATE)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminName = ComponentName(this, MyDeviceAdminReceiver::class.java)

        updateUiState()

        binding.savePinBtn.setOnClickListener { savePin() }
        binding.enableAdminBtn.setOnClickListener { requestAdmin() }
        binding.startBtn.setOnClickListener { startLockAndTimer() }
        binding.stopBtn.setOnClickListener { promptParentPinThenStop() }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(TimerService.ACTION_TICK)
            addAction(TimerService.ACTION_FINISH)
        }
        registerReceiver(timerBroadcast, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(timerBroadcast)
    }

    private fun updateUiState() {
        val hasPin = prefs.contains("pin")
        binding.pinSetupGroup.visibility = if (hasPin) android.view.View.GONE else android.view.View.VISIBLE
        binding.mainGroup.visibility = if (hasPin) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun savePin() {
        val pin = binding.pinInput.text.toString().trim()
        val confirm = binding.pinConfirm.text.toString().trim()
        if (pin.length != 4 || !pin.all { it.isDigit() }) {
            toast("PIN은 4자리 숫자입니다.")
            return
        }
        if (pin != confirm) {
            toast("PIN이 일치하지 않습니다.")
            return
        }
        prefs.edit().putString("pin", pin).apply()
        toast("PIN이 저장되었습니다.")
        updateUiState()
    }

    private fun requestAdmin() {
        if (dpm.isAdminActive(adminName)) {
            toast("이미 관리자 권한이 활성화되어 있습니다.")
            return
        }
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminName)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "타이머 종료 시 자동으로 잠금 화면으로 전환하기 위해 필요합니다.")
        }
        startActivity(intent)
    }

    private fun startLockAndTimer() {
        val minutesStr = binding.minutesInput.text.toString().trim()
        val minutes = minutesStr.toLongOrNull()
        if (minutes == null || minutes <= 0) {
            toast("분 단위로 올바른 시간을 입력하세요.")
            return
        }

        // Try to start screen pinning (Lock Task). If not permitted, system will ask for confirmation (Android feature).
        try {
            startLockTask()
            toast("화면이 고정되었습니다. 타이머 시작!")
        } catch (e: Exception) {
            // Guide user to enable Screen Pinning in settings.
            AlertDialog.Builder(this)
                .setTitle("화면 고정 안내")
                .setMessage("설정 > 보안 > 화면 고정 기능을 켠 후, 다시 시작을 눌러주세요.")
                .setPositiveButton("설정 열기") { _, _ ->
                    startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                }
                .setNegativeButton("닫기", null)
                .show()
        }

        // Start foreground service timer
        val svc = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_START
            putExtra(TimerService.EXTRA_DURATION_MS, minutes * 60_000L)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc)
        } else {
            startService(svc)
        }
    }

    private fun promptParentPinThenStop() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("부모 PIN 입력")
            .setView(input)
            .setPositiveButton("확인") { _, _ ->
                val pin = prefs.getString("pin", null)
                if (pin == input.text.toString().trim()) {
                    stopTimerAndUnlock()
                } else {
                    toast("PIN이 올바르지 않습니다.")
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun stopTimerAndUnlock() {
        val svc = Intent(this, TimerService::class.java).apply { action = TimerService.ACTION_STOP }
        stopService(svc)
        try { stopLockTask() } catch (_: Exception) {}
        toast("타이머가 중지되었습니다.")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
