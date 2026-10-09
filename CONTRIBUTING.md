# 贡献

先自己部署 API、把 `ticktick.apiBaseUrl` 指过去，再改代码。没有公共后端可以连。

## 本地检查

```bash
python3 -m pytest backend/tests -q
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

有真机时再跑 `./gradlew connectedDebugAndroidTest`。

产品行为以 `docs/specs/v1-personal-ticktick/` 为准。修复时请补对应单测，并在 `docs/dev-log.md` 记一次命令和退出码。

PR 请保持小而完整：不要提交 `local.properties`、密钥、数据库文件或本机截图。
