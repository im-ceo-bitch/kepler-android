# iumrah Android V10 — Release Candidate, 2026-11-14

## Установка

1. Загрузить накопительный ZIP V10 в корень `kepler-android`. Дождаться успешного применения и нового коммита. ZIP не меняет `.github/workflows/`.
2. Запустить существующий `iumrah APK Test Build` (V7, debug applicationId `com.iumrah.beta`). Убедиться, что `assembleDebug` и `testDebugUnitTest` прошли. Установить debug APK для теста.
3. Для подписанной сборки **заменить** `.github/workflows/iumrah-play-release.yml` файлом `docs/release/iumrah-play-release.yml` или отдельным скачанным YAML. Можно скопировать staged workflow через `bash scripts/install-play-release-workflow.sh` локально и закоммитить. Обычный распаковщик эту операцию не выполняет.
4. Проверить секреты и переменные GitHub. Workflow не публикует приложение автоматически.
5. Actions → `iumrah Play Release AAB` → Run workflow → main. Указать versionCode строго больше максимального кода, когда-либо загруженного в Play Console (включая тестовые и черновые релизы). Минимум в исходном проекте — 15; это не подтверждение актуального номера в Console. Выбрать публичный versionName (например, 2.0.4, если соответствует плану релиза).
6. Скачать подписанный APK/AAB из `iumrah-play-...` только после успешного job и появления `RC_VERIFIED.txt`. Для диагностики ошибок — `iumrah-play-diagnostics-...`.

## Обязательная конфигурация релиза

GitHub Secrets (текущие имена; workflow поддерживает старые алиасы из V9):
- `PLAY_UPLOAD_KEYSTORE_BASE64` (или `PLAY_KEYSTORE_BASE64`, `IUMRAH_UPLOAD_KEYSTORE_BASE64`)
- `PLAY_STORE_PASSWORD`, `PLAY_KEY_ALIAS`, `PLAY_KEY_PASSWORD` (или соответствующие `IUMRAH_UPLOAD_*`)

Необязательно, только для удалённых push: `IUMRAH_FIREBASE_API_KEY`, `IUMRAH_FIREBASE_APP_ID`, `IUMRAH_FIREBASE_PROJECT_ID`, `IUMRAH_FIREBASE_SENDER_ID`.

GitHub Actions variable (не секрет): `PLAY_UPLOAD_CERT_SHA256` — отпечаток **upload key certificate**, не Google Play app signing certificate. Взять из Play Console → App integrity. Сборка останавливается, если отпечаток отсутствует или не совпадает. Firebase не является условием сборки. Не создавать новый ключ без необходимости.

## Что проверяет release pipeline

- Source guards, unit tests, Kotlin debug/release compilation, strict release lint, R8 via assembleRelease and bundleRelease.
- No Google Play Billing in source, resolved dependency graph, merged manifest and AAB (сохранено предыдущее правило проекта).
- Действительность подписи **каждой записи** AAB и соответствие upload certificate; подписанный APK через apksigner; фактические applicationId, versionCode, versionName и отсутствие debuggable.
- ELF 16 KB/64-bit gate для обоих артефактов, zipalign APK, PAGE_ALIGNMENT_16K в конфигурации AAB.
- bundletool validate и декодированный манифест AAB: SDK, package, версия, security flags и запрещённые permissions.
- SHA-256 обоих готовых артефактов; диагностика и R8 mapping при ошибках. Никакой автоматической отправки в Play Console.

## Release acceptance (выполняется человеком на реальном устройстве / Play internal testing)

- [ ] Успешный debug APK + unit tests и проверенный signed release AAB/APK.
- [ ] Clean install, upgrade с предыдущего релиза из Play, холодный и тёплый запуск, нет crash/ANR, работа на API 26+ и API 36, 16 KB устройство/эмулятор.
- [ ] 4 языка (RU, UZ Latin, UZ Cyrillic, EN), small/large screens, масштаб текста; сравнение скриншотов с актуальным iOS и устранение расхождений.
- [ ] Поиск рейсов → обратный рейс → отели Мекки и Медины → номер/питание → трансфер → серверная цена → бронирование с quoteProof. Изменение цены требует нового подтверждения. Тестовые брони только с разрешёнными данными и без реальной оплаты.
- [ ] Вход, подтверждение Email/SMS, существующие аккаунты, привязка гостевой брони, ID паломника, выход/вход под другим аккаунтом, документы и статус в Business.
- [ ] Чаты в обе стороны, кеш и непрочитанные, FCM в foreground/background/после закрытия приложения, корректное открытие конкретного чата/брони.
- [ ] Offline/online, плохая сеть, повторный запуск, устаревший ответ, отсутствие повторной отправки бронирования; Nuitee daily cache и 5-min flight detail cache.
- [ ] Umrah Flow: офлайн-тексты, реальные аудиофайлы на едином Worker, 7+7 прогресс; карты и дополнительные сервисы.
- [ ] Play internal testing: store listing, privacy/data safety, account deletion, app access for reviewers, content rating и актуальные требования Console.

## Проверки в локальном окружении V10

- 204 Kotlin production/test files parsed with Kotlin PSI: 0 syntax errors; not Compose/Android type checking.
- 69 JVM assertions using real domain code + minimal DTO stubs; 19 network assertions using fake HTTP transport.
- 15 release tests (actual signed JDK fixture, unsigned, wrong signer, tampered/added payload, version, manifest, mock APK reports).
- Stage 10–25, build environment and offline release source checks; workflow YAML and shell syntax.
- Android SDK/Gradle not present; DNS prevented download of Gradle. No actual APK/AAB built here. Production API, FCM, Play signing/acceptance and visual pixel parity not certified. V10 does not add missing UI or new backend migrations. Standalone manual configurator and some detail screens remain known parity gaps from V8.
