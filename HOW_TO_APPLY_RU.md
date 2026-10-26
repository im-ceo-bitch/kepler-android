# Установка iumrah Android cumulative signature v4 (26.10.2026)

Это **единый патч от первоначального Android-репозитория**, объединяющий все изменения v1-v3 и новый чёрно-белый фирменный стиль из предоставленного iOS-архива.

1. Открыть основной Android GitHub-репозиторий, ветку `main`.
2. `Add file` -> `Upload files` -> загрузить целый ZIP **iumrah-android-update-20261026-v4.zip**, НЕ распаковывая.
3. Commit. Workflow `iumrah Android CI` автоматически распакует пути от корня проекта и удалит ZIP после применения.
4. Проверить workflow apply-update, затем отдельно запустить workflow `iumrah Android CI` вручную (`workflow_dispatch`) для настоящей сборки APK и unit tests (само применение ZIP APK не собирает).

Важно: v1, v2 и v3 ДО v4 не устанавливать. iOS репозиторий не изменялся. Полный тест визуального соответствия и реальных бронирований ещё предстоит.

Документ проверки: `docs/parity/IOS_ANDROID_ONE_TO_ONE_ROADMAP_RU.md`.
