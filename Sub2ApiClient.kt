package top.ss2a.widget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class Sub2ApiClient(private val dataStore: WidgetDataStore) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * 使用管理员账号和密码一键登录，获取并保存 JWT Token
     */
    suspend fun loginAdmin(account: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        val serverUrl = cleanBaseUrl(dataStore.serverUrl)
        val loginUrl = "$serverUrl/api/v1/auth/login"

        val payload = JSONObject().apply {
            put("email", account.trim())
            put("password", password)
        }.toString()

        var lastError = "无法连接登录接口"

        for (url in loginEndpoints) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .addHeader("Accept", "application/json")
                    .build()

                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        val token = extractTokenFromJson(body)
                        if (token.isNotBlank()) {
                            dataStore.adminAccount = account
                            dataStore.adminPassword = password
                            dataStore.adminToken = token
                            return@withContext Result.success(token)
                        } else {
                            lastError = "登录成功但响应中未找到有效 Token"
                        }
                    } else {
                        val errObj = runCatching { JSONObject(body) }.getOrNull()
                        val msg = errObj?.optString("message") ?: "HTTP ${resp.code}"
                        lastError = msg
                    }
                }
            } catch (e: Exception) {
                lastError = e.message ?: "登录网络异常"
            }
        }
        return@withContext Result.failure(Exception(lastError))
    }

    /**
     * 拉取 Sub2API 仪表盘全站统计数据，支持 401 自动无感重登
     */
    suspend fun fetchDashboardStats(allowAutoRelogin: Boolean = true): Sub2DashboardData = withContext(Dispatchers.IO) {
        val serverUrl = dataStore.serverUrl.trimEnd('/')
        var token = dataStore.adminToken

        // 若无 Token 但有账号密码，先尝试登录
        if (token.isBlank() && dataStore.adminAccount.isNotBlank() && dataStore.adminPassword.isNotBlank()) {
            val loginResult = loginAdmin(dataStore.adminAccount, dataStore.adminPassword)
            if (loginResult.isSuccess) {
                token = loginResult.getOrNull().orEmpty()
            }
        }

        if (token.isBlank()) {
            return@withContext Sub2DashboardData(
                isSuccess = false,
                errorMessage = "未配置 Admin Token，请登录或手动填入"
            )
        }
        // Sub2API 官方管理员仪表盘接口
        val statsUrl = "$serverUrl/api/v1/admin/dashboard/stats"

        var lastError = "无法连接至 Sub2API 服务"
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTimeStr = timeFormat.format(Date())

        try {
            val request = Request.Builder()
                .url(statsUrl)
                .addHeader("Authorization", if (token.startsWith("Bearer ")) token else "Bearer $token")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    // 尝试自动使用保存的账号密码重新获取 Token
                    if (allowAutoRelogin && dataStore.adminAccount.isNotBlank() && dataStore.adminPassword.isNotBlank()) {
                        val reloginResult = loginAdmin(dataStore.adminAccount, dataStore.adminPassword)
                        if (reloginResult.isSuccess) {
                            return@withContext fetchDashboardStats(allowAutoRelogin = false)
                        }
                    }
                    return@withContext Sub2DashboardData(
                        isSuccess = false,
                        errorMessage = "Token 校验失败 (HTTP ${response.code})，请重新登录"
                    )
                }

                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val parsed = parseDashboardJson(bodyStr, currentTimeStr)
                    dataStore.saveLatestStats(parsed)
                    return@withContext parsed
                } else {
                    val msg = parseErrorMsg(bodyStr, response.code)
                    lastError = msg
                }
            }
        } catch (e: Exception) {
            lastError = e.message ?: "网络连接异常"
        }
            }
        }

        // 若网络失败，回退返回缓存数据并带上错误标记
        val cached = dataStore.getCachedStats()
        return@withContext cached.copy(
            isSuccess = false,
            errorMessage = lastError
        )
    }

    private fun extractTokenFromJson(jsonStr: String): String {
        return runCatching {
            val root = JSONObject(jsonStr)
            val dataObj = root.optJSONObject("data") ?: root
            val directToken = dataObj.optString("token")
            if (directToken.isNotBlank()) return@runCatching directToken
            val accessToken = dataObj.optString("access_token")
            if (accessToken.isNotBlank()) return@runCatching accessToken
            dataObj.optString("jwt")
        }.getOrDefault("")
    }

    private fun parseSub2DashboardJson(jsonString: String, timeStr: String): Sub2DashboardData {
        val root = JSONObject(jsonString)
        // 兼容数据直接在顶层或包装在 data / result 内部
        val target = when {
            root.has("data") && root.get("data") is JSONObject -> root.getJSONObject("data")
            root.has("result") && root.get("result") is JSONObject -> root.getJSONObject("result")
            else -> root
        }

        // 解析今日金额/消耗
        val costValue = getDoubleFlexible(target, listOf("today_cost", "today_amount", "daily_cost", "cost", "total_cost"))
        val costFormatted = "$${DecimalFormat("0.00").format(costValue)}"

        // 解析今日请求数
        val requestsValue = getLongFlexible(target, listOf("today_requests", "today_request_count", "requests", "total_requests"))

        // 解析今日 Token 数量
        val tokensValue = getLongFlexible(target, listOf("today_tokens", "total_tokens", "tokens"))
        val tokensFormatted = formatTokenCount(tokensValue)

        // 解析用户数与 Key 数
        val usersCount = getIntFlexible(target, listOf("user_count", "total_users", "active_users"))
        val keysCount = getIntFlexible(target, listOf("key_count", "total_keys", "active_keys"))

        return Sub2DashboardData(
            todayCost = costFormatted,
            todayRequests = requestsValue,
            todayTokens = tokensFormatted,
            activeUsers = usersCount,
            activeKeys = keysCount,
            lastUpdateTime = timeStr,
            isSuccess = true,
            errorMessage = ""
        )
    }

    private fun getDoubleFlexible(json: JSONObject, keys: List<String>): Double {
        for (k in keys) {
            if (json.has(k)) {
                return json.optDouble(k, 0.0)
            }
        }
        return 0.0
    }

    private fun getLongFlexible(json: JSONObject, keys: List<String>): Long {
        for (k in keys) {
            if (json.has(k)) {
                return json.optLong(k, 0L)
            }
        }
        return 0L
    }

    private fun getIntFlexible(json: JSONObject, keys: List<String>): Int {
        for (k in keys) {
            if (json.has(k)) {
                return json.optInt(k, 0)
            }
        }
        return 0
    }

    private fun formatTokenCount(tokens: Long): String {
        return when {
            tokens >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", tokens / 1_000_000_000.0)
            tokens >= 1_000_000 -> String.format(Locale.US, "%.2fM", tokens / 1_000_000.0)
            tokens >= 1_000 -> String.format(Locale.US, "%.1fK", tokens / 1_000.0)
            else -> tokens.toString()
        }
    }
}ng.format(Locale.US, "%.2fM", tokens / 1_000_000.0)
            tokens >= 1_000 -> String.format(Locale.US, "%.1fK", tokens / 1_000.0)
            else -> tokens.toString()
        }
    }
}            tokens >= 1_000 -> String.format(Locale.US, "%.1fK", tokens / 1_000.0)
            else -> tokens.toString()
        }
    }
}ng.format(Locale.US, "%.2fM", tokens / 1_000_000.0)
            tokens >= 1_000 -> String.format(Locale.US, "%.1fK", tokens / 1_000.0)
            else -> tokens.toString()
        }
    }
}