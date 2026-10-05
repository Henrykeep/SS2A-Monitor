package top.ss2a.widget.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import top.ss2a.widget.R
import top.ss2a.widget.data.Sub2ApiClient
import top.ss2a.widget.data.WidgetDataStore
import top.ss2a.widget.widget.Sub2WidgetProvider
import top.ss2a.widget.widget.WidgetUpdateWorker

class MainActivity : AppCompatActivity() {

    private lateinit var dataStore: WidgetDataStore
    private lateinit var apiClient: Sub2ApiClient

    private lateinit var etServerUrl: EditText
    private lateinit var etAdminAccount: EditText
    private lateinit var etAdminPassword: EditText
    private lateinit var btnQuickLogin: Button
    private lateinit var etAdminToken: EditText
    private lateinit var rgInterval: RadioGroup
    private lateinit var btnTest: Button
    private lateinit var btnSave: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvTestResult: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dataStore = WidgetDataStore(this)
        apiClient = Sub2ApiClient(dataStore)

        initViews()
        loadSavedConfig()
    }

    private fun initViews() {
        etServerUrl = findViewById(R.id.et_server_url)
        etAdminAccount = findViewById(R.id.et_admin_account)
        etAdminPassword = findViewById(R.id.et_admin_password)
        btnQuickLogin = findViewById(R.id.btn_quick_login)
        etAdminToken = findViewById(R.id.et_admin_token)
        rgInterval = findViewById(R.id.rg_interval)
        btnTest = findViewById(R.id.btn_test_connection)
        btnSave = findViewById(R.id.btn_save_config)
        progressBar = findViewById(R.id.progress_bar)
        tvTestResult = findViewById(R.id.tv_test_result)

        btnQuickLogin.setOnClickListener {
            handleQuickLogin()
        }

        btnTest.setOnClickListener {
            testApiConnection()
        }

        btnSave.setOnClickListener {
            saveConfigAndSync()
        }
    }

    private fun loadSavedConfig() {
        etServerUrl.setText(dataStore.serverUrl)
        etAdminAccount.setText(dataStore.adminAccount)
        etAdminPassword.setText(dataStore.adminPassword)
        etAdminToken.setText(dataStore.adminToken)

        when (dataStore.refreshIntervalMinutes) {
            30L -> rgInterval.check(R.id.rb_30m)
            60L -> rgInterval.check(R.id.rb_60m)
            else -> rgInterval.check(R.id.rb_15m)
        }
    }

    private fun handleQuickLogin() {
        val url = etServerUrl.text.toString().trim()
        val account = etAdminAccount.text.toString().trim()
        val password = etAdminPassword.text.toString()

        if (url.isBlank()) {
            Toast.makeText(this, "请输入站点域名", Toast.LENGTH_SHORT).show()
            return
        }
        if (account.isBlank() || password.isBlank()) {
            Toast.makeText(this, "请输入管理员账号和密码", Toast.LENGTH_SHORT).show()
            return
        }

        dataStore.serverUrl = url
        progressBar.visibility = View.VISIBLE
        btnQuickLogin.isEnabled = false

        lifecycleScope.launch {
            val result = apiClient.loginAdmin(account, password)
            progressBar.visibility = View.GONE
            btnQuickLogin.isEnabled = true

            if (result.isSuccess) {
                val token = result.getOrNull().orEmpty()
                etAdminToken.setText(token)
                Toast.makeText(this@MainActivity, "🎉 登录成功，Token 已自动绑定！", Toast.LENGTH_SHORT).show()
                // 登录成功后直接自动触发一次测试连接
                testApiConnection()
            } else {
                Toast.makeText(this@MainActivity, "登录失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun testApiConnection() {
        val url = etServerUrl.text.toString().trim()
        val token = etAdminToken.text.toString().trim()

        if (url.isBlank()) {
            Toast.makeText(this, "请输入站点域名", Toast.LENGTH_SHORT).show()
            return
        }
        if (token.isBlank()) {
            Toast.makeText(this, "请输入管理员 Admin Token", Toast.LENGTH_SHORT).show()
            return
        }

        // 临时赋值用于测试
        dataStore.serverUrl = url
        dataStore.adminToken = token

        progressBar.visibility = View.VISIBLE
        tvTestResult.visibility = View.GONE
        btnTest.isEnabled = false

        lifecycleScope.launch {
            val result = apiClient.fetchDashboardStats()
            progressBar.visibility = View.GONE
            btnTest.isEnabled = true
            tvTestResult.visibility = View.VISIBLE

            if (result.isSuccess) {
                tvTestResult.setTextColor(0xFF10B981.toInt())
                tvTestResult.text = buildString {
                    append("✅ 连接成功！当前 Sub2API 统计：\n")
                    append("• 今日消耗：${result.todayCost}\n")
                    append("• 今日请求数：${result.todayRequests} 次\n")
                    append("• 今日 Tokens：${result.todayTokens}\n")
                    if (result.activeUsers > 0) append("• 用户数：${result.activeUsers} | Key 数：${result.activeKeys}")
                }
            } else {
                tvTestResult.setTextColor(0xFFEF4444.toInt())
                tvTestResult.text = "❌ 连接失败：${result.errorMessage}"
            }
        }
    }

    private fun saveConfigAndSync() {
        val url = etServerUrl.text.toString().trim()
        val token = etAdminToken.text.toString().trim()

        val interval = when (rgInterval.checkedRadioButtonId) {
            R.id.rb_30m -> 30L
            R.id.rb_60m -> 60L
            else -> 15L
        }

        dataStore.serverUrl = url
        dataStore.adminToken = token
        dataStore.refreshIntervalMinutes = interval

        // 注册定时任务
        WidgetUpdateWorker.schedulePeriodicSync(this, interval)

        // 触发一次即时拉取并同步小组件
        lifecycleScope.launch {
            val stats = apiClient.fetchDashboardStats()
            Sub2WidgetProvider.updateAllWidgets(this@MainActivity, stats)
            Toast.makeText(this@MainActivity, "配置已保存，已更新桌面小组件！", Toast.LENGTH_LONG).show()
        }
    }
}