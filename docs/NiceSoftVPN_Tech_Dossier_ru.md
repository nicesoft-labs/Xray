# Внутреннее техническое досье проекта NiceSoft VPN (Android)

> Дата аудита: 20 апреля 2026.
> Формат: инженерный разбор архитектуры, сборки, CI/CD, VPN-цепочки, серверных links, брендинга и рисков.

## 1. Общее назначение проекта

- Это Android VPN-клиент с GUI, который использует Xray-core и tun2socks/TPROXY-подходы для маршрутизации трафика.
- По README проект позиционируется как «simple GUI client for XTLS/Xray-core», но уже ребрендирован в `NiceSoft VPN`.
- Текущая стадия: **брендированный форк/продуктовая обертка поверх Xray-стека**, а не полностью независимый VPN-движок.

### Технологический стек
- Android: Kotlin, AndroidX, ViewBinding, Room, Material.
- VPN: `VpnService` + native `hev-socks5-tunnel` (через JNI) + Go-библиотека `XrayCore.aar`.
- Root/TPROXY режим: `libsu` + бинарник `xrayhelper`.
- Конфигурации: JSON (профили), subscriptions/base64, внутренний merge global-config.

## 2. Текущая структура репозитория

- `app/` — основной Android-модуль (runtime/UI/VPN orchestration).
- `XrayCore/` — Go-обертка для генерации `XrayCore.aar` через gomobile bind.
- `XrayHelper/` — Go-бинарник helper для transparent proxy (submodule, в текущем checkout пуст без init).
- `app/src/main/jni/hev-socks5-tunnel` — native submodule для tun2socks.
- `buildGo.sh`, `buildXrayCore.sh`, `buildXrayHelper.sh` — сборка Go-компонентов.
- `build-xray.sh`, `Dockerfile`, `.github/workflows/release.yml` — CI/CD и релизная цепочка.
- `metadata/` — F-Droid/release-метаданные и changelog.

### Где runtime/build-time/release
- Runtime: `app/src/main/java`, `app/src/main/res`, `app/src/main/assets`.
- Build-time: Gradle scripts + Go build scripts + submodules.
- Release: Docker+GitHub Actions и подпись keystore из secrets.

## 3. Архитектура приложения

### Архитектурная схема (упрощенно)

`Remote endpoint (HTTPS)` → `Link` (Room, LinkDao) → `LinksManagerActivity.refreshLinks()` → `JSON/subscription parser` → `Profile` (Room) → `Settings.selectedProfile` → `TProxyService.getProfile/getConfig` → `XrayCore.start + tun2socks(TProxyStartService)` → `VpnService tunnel`.

### Слои
- **Application/container**: `Xray : Application` создает singleton-доступ к репозиториям.
- **Data layer**: Room (`Config`, `Link`, `Profile`) + Dao + Repository.
- **ViewModel layer**: `ConfigViewModel`, `LinkViewModel`, `ProfileViewModel`.
- **UI layer**: `MainActivity` + дополнительные Activity/Fragment.
- **Service layer**: `TProxyService`, `VpnTileService`, receivers.
- **Infra/helpers**: `ConfigHelper`, `LinkHelper`, `HttpHelper`, `TransparentProxyHelper`.

## 4. Главные точки входа

### 4.1 Application class
- Файл: `app/src/main/java/com/nicesoft/vpn/Xray.kt`
- Класс: `Xray`
- Роль: lazy-инициализация DB и repositories.
- Если ломается: ViewModel не сможет получить хранилище.

### 4.2 MainActivity
- Файл: `app/src/main/java/com/nicesoft/vpn/activity/MainActivity.kt`
- Роль: главный экран, список профилей, tabs по links, connect/disconnect, ping, deep-link import.
- Обновляет UI-состояние через BroadcastReceiver из `TProxyService`.
- Если ломается: пользователь теряет основной UX управления VPN.

### 4.3 TProxyService
- Файл: `app/src/main/java/com/nicesoft/vpn/service/TProxyService.kt`
- Роль: оркестратор VPN lifecycle (start/stop/newConfig/status), конфиг генерация, VpnService tun setup, запуск/останов Xray/tun2socks.
- Если ломается: VPN фактически не поднимется.

### 4.4 LinksManagerActivity
- Файл: `app/src/main/java/com/nicesoft/vpn/activity/LinksManagerActivity.kt`
- Роль: refresh links, HTTP загрузка, парсинг JSON/subscription, синхронизация профилей в БД.
- Если ломается: backend-список серверов не обновляется.

### 4.5 Settings
- Файл: `app/src/main/java/com/nicesoft/vpn/Settings.kt`
- Роль: Single-source конфигурации приложения в SharedPreferences.
- Если ломается: неконсистентность runtime параметров VPN/маршрутов.

## 5. Как проект собирается локально (Linux/Ubuntu)

### 5.1 Минимальные зависимости
1. JDK 21
2. Android SDK Platform 36 + Build Tools 36.0.0
3. Android NDK `28.2.13676358`
4. Go (совместимый с `XrayCore/go.mod`, там заявлено `go 1.26.2`)
5. gomobile
6. Инициализированные git submodules

### 5.2 Пошагово
```bash
git clone <repo-url>
cd Xray
git submodule update --init --recursive

# (опционально) сборка Go-артефактов
./buildGo.sh arm64

# debug
./gradlew -PabiId=2 -PabiTarget=arm64-v8a assembleDebug

# release (нужен keystore /tmp/xray.jks + env vars)
export KS_PASSWORD=...
export KEY_ALIAS=...
export KEY_PASSWORD=...
./gradlew -PabiId=2 -PabiTarget=arm64-v8a assembleRelease
```

### 5.3 Почему может не собраться
- `app/libs` пустой и нет `XrayCore.aar` → unresolved import `XrayCore.XrayCore`.
- Не инициализированы submodules `XrayHelper`, `XrayCore/Xray-core`, `XrayCore/libXray`, `hev-socks5-tunnel`.
- Несовместимая версия Go/gomobile.
- Нет NDK версии из `app/build.gradle.kts`.

## 6. Подробный разбор build-цепочки

### Gradle
- `app/build.gradle.kts`: namespace/applicationId, SDK versions, signing, ABI splits, Java 21, NDK, зависимости.
- `versionCode` рассчитывается из `app/versionCode.txt + abiId`.

### Go chain
- `buildGo.sh`: локально поднимает GOROOT/GOPATH, клонирует Go, билдит toolchain, вызывает `buildXrayCore.sh` и `buildXrayHelper.sh`.
- `buildXrayCore.sh`: gomobile bind → `app/libs/XrayCore.aar`.
- `buildXrayHelper.sh`: go build → `app/src/main/assets/xrayhelper`.

### Docker/CI
- `build-xray.sh`: внутри контейнера ставит Java/SDK/NDK/Go, клонирует **внешний репозиторий** `https://github.com/NiceSoftVPN/android-app.git`, checkout tag, строит release apk.
- `release.yml`: 4 job по ABI + publish job.

## 7. Как устроен XrayCore и почему без него проект не собирается

- Kotlin-код напрямую импортирует `XrayCore.XrayCore` (например MainActivity/ProfileActivity/TProxyService/LinkHelper).
- Этот класс приходит из `XrayCore.aar`, который генерируется gomobile bind.
- Без `app/libs/XrayCore.aar` будут unresolved reference на `XrayCore`.
- `XrayCore/main.go` экспортирует методы `Test/Start/Stop/Version/Json`, которые bridge'ятся в Kotlin.

## 8. Где и как редактируется список серверов

### Ключевые сущности
- `Link` (`database/Link.kt`) — источник серверов: name/address/type/isActive/userAgent.
- `Profile` (`database/Profile.kt`) — конкретный серверный профиль (json-конфиг).
- `LinksManagerActivity.refreshLinks()` — загрузка и синхронизация.

### HTTP GET и парсинг
- HTTP: `HttpHelper.get(link.address, link.userAgent)`.
- JSON-списки: `jsonProfiles()` ожидает JSON array.
- Subscription: `subscriptionProfiles()` декодирует base64 и каждую строку прогоняет через `LinkHelper`.

### Как сменить endpoint
- Из UI: `LinksActivity` → `LinkFormFragment` (ручное редактирование links).
- Жестко: можно зафиксировать адрес в `LinksManagerActivity`/`LinkFormFragment`, убрать поля edit.

### Как отключить ручной ввод links
- Удалить пункты меню `newLink`, `scanQrCode`, `fromClipboard` (`menu_main.xml`, `MainActivity.onOptionsItemSelected`).
- Убрать `LinksActivity` из drawer/menu и/или скрыть entry в `menu_drawer.xml`.
- В `LinkFormFragment` оставить read-only или убрать экран целиком.

### “Только backend сервера”
- Оставить только active links из БД (уже так сделано через `LinkDao.tabs/activeLinks`).
- Запретить создание/редактирование link пользователем (UI restrictions).
- Авто-поддержка одного Link с фиксированным endpoint и `isActive=true` при первом запуске.

## 9. Где редактируется брендинг и внешний вид

- `applicationId`, `namespace`: `app/build.gradle.kts`.
- App name: `strings.xml` (`appName`).
- Иконки/launcher: `AndroidManifest.xml` (`android:icon`), ресурсы drawable/mipmap.
- Tile icon: `VpnTileService.updateTile()` + `AndroidManifest.xml` service icon.
- Notification icon/channel/session name: `TProxyService.createNotification`, `createNotificationChannel`, `startVPN` (`tun.setSession`).
- Deep link scheme: `AndroidManifest.xml` (`nicesoftvpn://import-profile`).
- Action names: константы в `TProxyService` формируются от `BuildConfig.APPLICATION_ID`.
- Имя итогового APK: `build-xray.sh` (`BUILD_NAME="Xray-$RELEASE_TAG-$VERSION_CODE.apk"`).

### Хвосты старого Xray, которые не дочищены
1. `rootProject.name = "Xray"`.
2. `XrayVpnServiceNotification` id канала.
3. Имя apk в CI содержит `Xray-...`.
4. Класс Application называется `Xray`.
5. Package DB name `xray`.
6. Лейблы и строки с `XTLS/Xray-core` как бренд-след.

## 10. Где редактируется логика UI

- Главный экран: `activity_main.xml` + `MainActivity.kt`.
- Список профилей: `RecyclerView` + `ProfileAdapter`.
- Нижняя кнопка start/stop: `toggleButton` в `activity_main.xml` + `onToggleButtonClick`.
- Drawer: `menu_drawer.xml` + `onNavigationItemSelected`.

### Минималистичный UX (практично)
- Удалить drawer entries (`assets/logs/configs/settings/apps-routing/links`), оставить только main.
- Удалить import сценарии (QR/clipboard/manual) из `menu_main.xml` и `MainActivity`.
- Оставить выбор сервера + connect/disconnect.
- В варианте “tap server = connect” вызывать `TProxyService.newConfig/start` прямо в `profileSelect`.

## 11. Как работает VPN-часть

- `TProxyService` стартует по action.
- Берет активный `Profile` по `Settings.selectedProfile`.
- Формирует итоговый json через `ConfigHelper` + валидирует `XrayCore.test`.
- Запускает Xray (`XrayCore.start`) и tun2socks (`TProxyStartService`) при `tun2socks=true`.
- В `VpnService.Builder` выставляет MTU, DNS, маршруты, IPv6, apps routing include/exclude.
- Поддерживает `NEW_CONFIG_SERVICE_ACTION_NAME` для hot-reload профиля.
- Broadcasts: start/stop/status для UI и tile.

## 12. Настройки и их влияние

- Критичные: `selectedProfile`, `tun2socks`, `transparentProxy`, DNS, маршруты, apps routing.
- На автозапуск: `bootAutoStart`, `tproxyAutoConnect`.
- На transparent proxy: `tproxy*`, `hotspotInterface`, `tetheringInterface`.
- Для коммерческого UX часть настроек можно скрыть: ручные DNS, tun MTU/prefix, advanced tproxy.

## 13. База данных и состояние

- Room entities: `Config`, `Link`, `Profile`.
- `Profile.linkId` FK на `Link.id` с `onDelete = CASCADE`.
- `selectedLink/selectedProfile` — в SharedPreferences (не в Room).
- При удалении link каскадно удаляются profiles (DB) + код дополнительно чистит selectedProfile.
- При смене selected profile во время активного VPN вызывается `TProxyService.newConfig()`.

## 14. Обновление проекта

- Обновлять зависимости через `libs.versions.toml` и AGP/Kotlin/Room совместимо.
- XrayCore/libXray: обновление submodules + пересборка `XrayCore.aar`.
- Go toolchain должен совпадать с требованиями `XrayCore/go.mod`.
- SDK/NDK синхронно обновлять с `app/build.gradle.kts` и CI скриптом.

### Безопасная стратегия обновления
1. Обновить submodules в отдельной ветке.
2. Собрать `buildXrayCore.sh` и `buildXrayHelper.sh` локально.
3. Прогнать debug/release assemble по ABI.
4. Проверить start/stop/newConfig и import links.
5. Только потом обновлять CI Docker image/toolchain.

## 15. CI/CD и release

- Триггер: push tag (`release.yml`).
- 4 отдельные docker-сборки по ABI.
- Keystore передается через secrets (`KS_FILE` base64 + пароли).
- Release body подтягивается из `metadata/en-US/changelogs/<version>.txt`.

### Спорные/опасные места CI
1. `build-xray.sh` клонирует внешний repo, а не использует текущий workspace (риск рассинхрона).
2. Скрипт удаляет `gradle-wrapper.jar` перед сборкой.
3. Используется rolling Debian forky/trixie (нестабильность среды).
4. Имена артефактов с legacy `Xray-...`.

## 16. Технический долг и проблемные места

### Что хорошо
- Четко разделены Link/Profile/Config и есть Room migrations.
- Есть hot-reload конфига при смене профиля.
- Есть поддержка разных ABI и release automation.

### Что недоделано/рискованно
- Сильная связка с submodules и Go toolchain.
- Partial rebranding (имена/идентификаторы).
- Большой advanced UI для массового пользователя (сложно для продукта).
- Root/transparent режим с чувствительными shell-командами.

## 17. Практические сценарии доработки

1. **Один фиксированный backend endpoint**:
   - Автосоздавать один Link при первом запуске, скрыть Links UI.
   - Файлы: `MainActivity`, `LinksManagerActivity`, `LinkFormFragment`, `menu_drawer.xml`, `menu_main.xml`.
2. **Только backend servers list**:
   - Запрет ручного import + only refresh from endpoint.
3. **Без ручного импорта конфигов**:
   - Удалить QR/clipboard/newProfile пункты и соответствующую логику.
4. **Без drawer и лишних экранов**:
   - Очистить `menu_drawer.xml` + `onNavigationItemSelected`.
5. **Одна кнопка connect/disconnect**:
   - Уже есть `toggleButton`, можно скрыть edit/delete профиля.
6. **Клик по серверу сразу подключает**:
   - После `settings.selectedProfile = profile.id` вызывать `TProxyService.start/newConfig`.
7. **Платный API + токен**:
   - Добавить auth header в `HttpHelper.get` (Bearer token из защищенного хранилища).
8. **Категории/страны**:
   - Расширить `Profile`/`Link` schema + фильтрацию UI tabs/sections.

## 18. Рекомендованный roadmap

### MVP-ready сейчас
- Базовый VPN connect/disconnect, links refresh, profile selection, release pipeline.

### Для production
1. Дочистить ребрендинг (id/channel/apk names/rootProject).
2. Упростить UX до продуктового сценария (убрать инженерные экраны).
3. Зафиксировать backend протокол и схему аутентификации.
4. Стабилизировать CI (build from current SHA, pinned base images).
5. Добавить smoke/instrumentation тесты подключения.

### Для безопасного развития
- Ввести архитектурные контракты: backend schema versioning, config validation suite, rollback strategy.
- Разделить product flavor и developer flavor.

---

## Примечания и предположения

- **Предположение:** так как submodules в текущей среде не инициализированы (пустые директории), часть реальной логики `XrayHelper`/`hev-socks5-tunnel` не была полностью просмотрена.
- **Предположение:** заявленный `go 1.26.2` может быть внутренним/будущим тулчейном; требуется верификация в целевом CI-окружении.
