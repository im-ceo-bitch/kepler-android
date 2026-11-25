# Android — синхронизация с iOS main 7, 25.11.2026

## Основание и область
База Android: kepler-android-main 2.zip + предыдущий накопительный патч пяти блоков. Эталон: kepler-ios-main 7.zip, сравнение с kepler-ios-main 5.zip. Изменено 16 Swift-файлов, добавлено 3. Home исключён по предыдущему указанию: экран, его исходники и новый каталог на Home не перенесены. API полного каталога рейсов добавлен как опциональный контракт, прежние вызовы не меняются. Общие компоненты могут влиять на Home косвенно; скриншотная регрессия не выполнена.

## Перенесено
- Каталог команды и гидов, дата/доступность, портреты, локализованные биографии RU/UZ/UZ-Cyrl, этапы сопровождения, видео и предоставленные сервером отзывы. Не выдумываются цены и отзывы. Доступность не означает подтверждённое назначение.
- TripDraft.guidePreference и booking.customization.preferredGuideID для итоговых экранов официального, ручного и Smart-сценария; выбор автоматически очищает предпочтение. Контакты отображаются из назначенного snapshot, не из публичного каталога. Выбор не создаёт новой платы.
- Account: отдельные карточки Balance, Friends, Rewards, ID, Mobile, Care, промо-анимация гостевого Friends. Кэш бонусов 55 секунд, объединение запросов, изоляция токенов, устаревшее состояние при сетевой ошибке.
- API all=1 и catalogComplete; визуальный Home и глобальный каталог на Home намеренно оставлены прежними.

## Проверки
- Kotlin PSI: 278 файлов, 0 синтаксических ошибок (не Gradle typecheck).
- JVM smoke реальных фрагментов: 6 проверок локализованных биографий, 14 проверок кэша/изоляции аккаунта — PASS.
- Stage 25: PASS (exit 0)
- Проверки V8, V9, V10, block01, block02, block04c05, seamless booking, ios6 parity, public trip merge, global localization/icons — PASS при отдельных запусках.
- Проверка наложения архива и сохранности защищённых путей выполняется упаковщиком.

## Ограничения
Полная сборка Gradle, реальные API и устройства не проверены. Не подтверждено визуальное соответствие, назначение гида и жизненный цикл сохранённого заказа на реальном backend. Документация iOS требует предварительного развёртывания совместимого backend/Business; их архивы не предоставлены. Ручной выбор гида использует sheet, а не точную Swift-навигацию; ID использует существующую карточку вместо точного анимированного iOS-экрана. Liquid Glass и видео используют Android-реализации. Старые отчёты пяти блоков исторические, не доказательство текущей сборки. Не заявляется 100% parity.

## Изменения эталонного iOS
- `IUMRAH_GUIDE_CATALOG_20261125.md`
- `IUMRAH_GUIDE_CATALOG_20261125_IOS_MANIFEST.json`
- `IUMRAH_HOME_DIRECT_FLIGHTS_20261125.md`
- `IUMRAH_PERSONAL_DASHBOARD_20261125.md`
- `IUMRAH_PERSONAL_DASHBOARD_20261125_MANIFEST.json`
- `IUMRAH_TEAM_LOCALIZED_20261125.md`
- `Sources/AppShell/IumrahAccountView.swift`
- `Sources/Core/BookingDraftBuilder.swift`
- `Sources/Core/Components/IumrahSignatureHeaders.swift`
- `Sources/Models/BookingModels.swift`
- `Sources/Models/HotelStorefrontModels.swift`
- `Sources/Models/TripModels.swift`
- `Sources/Services/ChatService.swift`
- `Sources/Services/HotelStorefrontService.swift`
- `Sources/State/IumrahAccountStore.swift`
- `Sources/State/IumrahManualJourneyStore.swift`
- `Sources/Views/Booking/IumrahGuideTransferView.swift`
- `Sources/Views/Guides/IumrahGuideCatalog.swift`
- `Sources/Views/Home/HomeDashboardView.swift`
- `Sources/Views/Home/IumrahFriendsAnimatedCard.swift`
- `Sources/Views/Home/IumrahPersonalDashboardCards.swift`
- `Sources/Views/Home/IumrahPublishedFlightCarousel.swift`
- `Sources/Views/Package/FinalPackageView.swift`
- `Sources/Views/Package/IumrahManualPackageView.swift`
- `Sources/Views/Tabs/HotelsHomeView.swift`
- `Tests/iumrah_dashboard_cache_smoke.py`
- `Tests/iumrah_home_direct_flights_smoke.py`

## Изменения Android сверх прежнего патча
- `app/src/main/java/com/iumrah/beta/core/navigation/AppChromeStore.kt`
- `app/src/main/java/com/iumrah/beta/data/account/IumrahAccountStore.kt`
- `app/src/main/java/com/iumrah/beta/data/chat/ChatService.kt`
- `app/src/main/java/com/iumrah/beta/data/hotel/HotelCatalogService.kt`
- `app/src/main/java/com/iumrah/beta/domain/booking/BookingDraftBuilder.kt`
- `app/src/main/java/com/iumrah/beta/domain/journey/JourneyStore.kt`
- `app/src/main/java/com/iumrah/beta/domain/trip/TripModels.kt`
- `app/src/main/java/com/iumrah/beta/models/booking/BookingSharedModels.kt`
- `app/src/main/java/com/iumrah/beta/models/hotel/HotelStorefrontModels.kt`
- `app/src/main/java/com/iumrah/beta/ui/account/AccountDashboardDelta.kt`
- `app/src/main/java/com/iumrah/beta/ui/account/AccountScreens.kt`
- `app/src/main/java/com/iumrah/beta/ui/account/IumrahRewardsScreen.kt`
- `app/src/main/java/com/iumrah/beta/ui/booking/IumrahGuideTransferScreen.kt`
- `app/src/main/java/com/iumrah/beta/ui/components/IumrahHeaderPills.kt`
- `app/src/main/java/com/iumrah/beta/ui/guides/GuideCatalog.kt`
- `app/src/main/java/com/iumrah/beta/ui/hotels/HotelsScreen.kt`
- `app/src/main/java/com/iumrah/beta/ui/hotels/NuiteeHotelsCatalogScreen.kt`
- `app/src/main/java/com/iumrah/beta/ui/packageflow/FinalPackageScreen.kt`
- `app/src/main/java/com/iumrah/beta/ui/packageflow/ManualPackageScreen.kt`
- `app/src/main/java/com/iumrah/beta/ui/packageflow/NuiteeJourneyReviewScreen.kt`
- `app/src/main/java/com/iumrah/beta/ui/packageflow/PackageReviewChrome.kt`
- `scripts/qa/ios7/test_ios7.py`
- `scripts/verify-brand-block1-final.py`
- `scripts/verify-hotels-storefront-parity.py`
- `scripts/verify-ios-ui-v8.py`
- `scripts/verify-signature-brand-parity.py`
