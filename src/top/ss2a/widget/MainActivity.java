package top.ss2a.widget;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText etServerUrl;
    private EditText etAdminAccount;
    private EditText etAdminPassword;
    private EditText etAdminToken;
    private EditText etRefreshInterval;
    private Button btnQuickLogin;
    private Button btnTestConnection;
    private Button btnSaveConfig;
    private ProgressBar progressBar;
    private TextView tvTestResult;
    
    // 顶部小组件实时效果预览卡片
    private TextView tvPreviewMode;
    private TextView tvPreviewTime;
    private TextView tvPreviewCost;
    private TextView tvPreviewRequests;
    private TextView tvPreviewTokens;

    // 快捷切换药丸
    private Button btn10s;
    private Button btn30s;
    private Button btn60s;
    private Button btn300s;

    private WidgetDataStore dataStore;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dataStore = new WidgetDataStore(this);

        etServerUrl = findViewById(R.id.et_server_url);
        etAdminAccount = findViewById(R.id.et_admin_account);
        etAdminPassword = findViewById(R.id.et_admin_password);
        etAdminToken = findViewById(R.id.et_admin_token);
        etRefreshInterval = findViewById(R.id.et_refresh_interval);
        btnQuickLogin = findViewById(R.id.btn_quick_login);
        btnTestConnection = findViewById(R.id.btn_test_connection);
        btnSaveConfig = findViewById(R.id.btn_save_config);
        progressBar = findViewById(R.id.progress_bar);
        tvTestResult = findViewById(R.id.tv_test_result);

        tvPreviewMode = findViewById(R.id.tv_preview_mode);
        tvPreviewTime = findViewById(R.id.tv_preview_time);
        tvPreviewCost = findViewById(R.id.tv_preview_cost);
        tvPreviewRequests = findViewById(R.id.tv_preview_requests);
        tvPreviewTokens = findViewById(R.id.tv_preview_tokens);

        btn10s = findViewById(R.id.btn_chip_10s);
        btn30s = findViewById(R.id.btn_chip_30s);
        btn60s = findViewById(R.id.btn_chip_60s);
        btn300s = findViewById(R.id.btn_chip_300s);

        etServerUrl.setText(dataStore.getServerUrl());
        etAdminAccount.setText(dataStore.getAdminAccount());
        etAdminPassword.setText(dataStore.getAdminPassword());
        etAdminToken.setText(dataStore.getAdminToken());
        etRefreshInterval.setText(String.valueOf(dataStore.getRefreshIntervalSeconds()));

        btnQuickLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleQuickLogin();
            }
        });

        btnTestConnection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleTestConnection();
            }
        });

        btnSaveConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleSaveConfig();
            }
        });

        setupIntervalChips();
        updatePreviewCard(dataStore.getCachedStats());

        // 确保后台服务与保活调度运行
        Sub2RealtimeService.start(this);
        Sub2WidgetProvider.scheduleAutoAlarm(this);
        Sub2JobService.schedulePeriodicJob(this);
    }

    private void updatePreviewCard(Sub2DashboardData data) {
        if (data == null) return;
        if (tvPreviewMode != null) {
            tvPreviewMode.setText("SS2A · " + (data.modeTitle != null ? data.modeTitle : "全站监控"));
        }
        if (tvPreviewTime != null) {
            tvPreviewTime.setText("更新于 " + (data.lastUpdateTime != null ? data.lastUpdateTime : "--:--"));
        }
        if (tvPreviewCost != null) {
            tvPreviewCost.setText(data.todayCost != null ? data.todayCost : "$0.00");
        }
        if (tvPreviewRequests != null) {
            tvPreviewRequests.setText("请求: " + data.todayRequests + " 次");
        }
        if (tvPreviewTokens != null) {
            tvPreviewTokens.setText("Token: " + (data.todayTokens != null ? data.todayTokens : "0"));
        }
    }

    private void saveIntervalFromInput() {
        try {
            String s = etRefreshInterval.getText().toString().trim();
            if (!s.isEmpty()) {
                int sec = Integer.parseInt(s);
                dataStore.setRefreshIntervalSeconds(sec);
            }
        } catch (Exception ignored) {}
    }

    private void handleQuickLogin() {
        final String rawUrl = etServerUrl.getText().toString().trim();
        final String account = etAdminAccount.getText().toString().trim();
        final String password = etAdminPassword.getText().toString().trim();

        if (rawUrl.isEmpty()) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }
        if (account.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入账号邮箱和密码", Toast.LENGTH_SHORT).show();
            return;
        }

        saveIntervalFromInput();
        final String cleanUrl = Sub2ApiClient.cleanBaseUrl(rawUrl);
        etServerUrl.setText(cleanUrl);
        dataStore.setServerUrl(cleanUrl);
        dataStore.setAdminAccount(account);
        dataStore.setAdminPassword(password);

        progressBar.setVisibility(View.VISIBLE);
        tvTestResult.setVisibility(View.VISIBLE);
        tvTestResult.setTextColor(Color.parseColor("#BAC2DE"));
        tvTestResult.setText("正在向服务器登录验证并获取 Token...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Sub2ApiClient client = new Sub2ApiClient(dataStore);
                    final String token = client.loginAdmin(account, password);
                    final Sub2DashboardData res = client.fetchDashboardStats(false);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            if (token != null && !token.isEmpty()) {
                                etAdminToken.setText(token);
                                if (res.isSuccess) {
                                    tvTestResult.setTextColor(Color.parseColor("#34D399"));
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("登录成功！Token 已自动绑定\n");
                                    sb.append("• 监控模式: ").append(res.modeTitle).append("\n");
                                    sb.append("• 今日消费: ").append(res.todayCost).append("\n");
                                    sb.append("• 今日请求: ").append(res.todayRequests).append(" 次\n");
                                    sb.append("• 今日 Tokens: ").append(res.todayTokens).append("\n");
                                    sb.append("• 极速模式: 每 ").append(dataStore.getRefreshIntervalSeconds()).append(" 秒自动轮询已就绪！");
                                    tvTestResult.setText(sb.toString());
                                    Toast.makeText(MainActivity.this, "登录成功！已启动秒级极速实时刷新", Toast.LENGTH_SHORT).show();
                                    updatePreviewCard(res);
                                    Sub2WidgetProvider.updateAllWidgets(MainActivity.this, res);
                                    Sub2RealtimeService.start(MainActivity.this);
                                    Sub2RealtimeService.syncNow(MainActivity.this);
                                } else {
                                    tvTestResult.setTextColor(Color.parseColor("#FBBF24"));
                                    tvTestResult.setText("登录成功但获取数据遇到问题: " + res.errorMessage);
                                }
                            } else {
                                tvTestResult.setTextColor(Color.parseColor("#F87171"));
                                tvTestResult.setText("登录失败：未收到有效 Token");
                                Toast.makeText(MainActivity.this, "登录失败，请检查账号密码", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            tvTestResult.setTextColor(Color.parseColor("#F87171"));
                            tvTestResult.setText("登录失败: " + e.getMessage());
                            Toast.makeText(MainActivity.this, "登录失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void handleTestConnection() {
        final String rawUrl = etServerUrl.getText().toString().trim();
        final String token = etAdminToken.getText().toString().trim();

        if (rawUrl.isEmpty()) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }

        saveIntervalFromInput();
        final String cleanUrl = Sub2ApiClient.cleanBaseUrl(rawUrl);
        etServerUrl.setText(cleanUrl);
        dataStore.setServerUrl(cleanUrl);
        if (!token.isEmpty()) {
            dataStore.setAdminToken(token);
        }

        progressBar.setVisibility(View.VISIBLE);
        tvTestResult.setVisibility(View.VISIBLE);
        tvTestResult.setTextColor(Color.parseColor("#BAC2DE"));
        tvTestResult.setText("正在连接 Sub2API 统计接口...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Sub2ApiClient client = new Sub2ApiClient(dataStore);
                    final Sub2DashboardData res = client.fetchDashboardStats(true);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            if (res.isSuccess) {
                                StringBuilder sb = new StringBuilder();
                                sb.append("连接成功！\n");
                                sb.append("• 监控模式: ").append(res.modeTitle).append("\n");
                                sb.append("• 今日消费: ").append(res.todayCost).append("\n");
                                sb.append("• 今日请求: ").append(res.todayRequests).append(" 次\n");
                                sb.append("• 今日 Tokens: ").append(res.todayTokens).append("\n");
                                sb.append("• 更新时间: ").append(res.lastUpdateTime).append("\n");
                                sb.append("• 极速模式: 每 ").append(dataStore.getRefreshIntervalSeconds()).append(" 秒自动轮询已就绪！");
                                tvTestResult.setTextColor(Color.parseColor("#34D399"));
                                tvTestResult.setText(sb.toString());
                                Toast.makeText(MainActivity.this, "连接成功！已同步至桌面小组件", Toast.LENGTH_SHORT).show();
                                updatePreviewCard(res);
                                Sub2WidgetProvider.updateAllWidgets(MainActivity.this, res);
                                Sub2RealtimeService.start(MainActivity.this);
                                Sub2RealtimeService.syncNow(MainActivity.this);
                            } else {
                                tvTestResult.setTextColor(Color.parseColor("#F87171"));
                                tvTestResult.setText("连接失败: " + res.errorMessage);
                                Toast.makeText(MainActivity.this, "连接失败: " + res.errorMessage, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            tvTestResult.setTextColor(Color.parseColor("#F87171"));
                            tvTestResult.setText("测试异常: " + e.getMessage());
                            Toast.makeText(MainActivity.this, "连接异常: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void handleSaveConfig() {
        String rawUrl = etServerUrl.getText().toString().trim();
        String account = etAdminAccount.getText().toString().trim();
        String password = etAdminPassword.getText().toString().trim();
        String token = etAdminToken.getText().toString().trim();

        saveIntervalFromInput();
        String cleanUrl = Sub2ApiClient.cleanBaseUrl(rawUrl);
        etServerUrl.setText(cleanUrl);
        dataStore.setServerUrl(cleanUrl);
        dataStore.setAdminAccount(account);
        dataStore.setAdminPassword(password);
        dataStore.setAdminToken(token);

        Toast.makeText(this, "配置已保存！极速实时同步服务已启动", Toast.LENGTH_SHORT).show();
        Sub2DashboardData cached = dataStore.getCachedStats();
        updatePreviewCard(cached);
        Sub2WidgetProvider.updateAllWidgets(this, cached);
        Sub2RealtimeService.start(this);
        Sub2RealtimeService.syncNow(this);
        Sub2WidgetProvider.scheduleAutoAlarm(this);
        Sub2JobService.schedulePeriodicJob(this);
    }

    private void setupIntervalChips() {
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                if (id == R.id.btn_chip_10s) etRefreshInterval.setText("10");
                else if (id == R.id.btn_chip_30s) etRefreshInterval.setText("30");
                else if (id == R.id.btn_chip_60s) etRefreshInterval.setText("60");
                else if (id == R.id.btn_chip_300s) etRefreshInterval.setText("300");
                updateChipStates();
            }
        };

        if (btn10s != null) btn10s.setOnClickListener(listener);
        if (btn30s != null) btn30s.setOnClickListener(listener);
        if (btn60s != null) btn60s.setOnClickListener(listener);
        if (btn300s != null) btn300s.setOnClickListener(listener);

        etRefreshInterval.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updateChipStates();
            }
        });

        updateChipStates();
    }

    private void updateChipStates() {
        String val = etRefreshInterval.getText().toString().trim();
        highlightChip(btn10s, "10".equals(val));
        highlightChip(btn30s, "30".equals(val));
        highlightChip(btn60s, "60".equals(val));
        highlightChip(btn300s, "300".equals(val));
    }

    private void highlightChip(Button btn, boolean selected) {
        if (btn == null) return;
        if (selected) {
            btn.setBackgroundResource(R.drawable.bg_chip_selected);
            btn.setTextColor(Color.parseColor("#34D399"));
        } else {
            btn.setBackgroundResource(R.drawable.bg_badge_btn);
            btn.setTextColor(Color.parseColor("#CBD5E1"));
        }
    }

    @Override
    public void onBackPressed() {
        // 按返回键时保留任务栈退至后台，避免进程被直接 finish，确保后台常驻服务丝滑轮询
        moveTaskToBack(true);
    }
}
