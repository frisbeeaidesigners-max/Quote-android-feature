# Тактильный отклик (Vibration) — рецепт для android-template

Этот файл — pragmatic-руководство по тому, **какой код реально вибрирует** на наших целевых устройствах (Xiaomi 24117RN76O, MIUI/Android 15) во всех режимах звонка. История проб-и-ошибок в коммитах ветки `feat(swipe-to-reply)`, итог — здесь.

## TL;DR

Чтобы вибрация **гарантированно** срабатывала на тестовом устройстве:

1. Добавить разрешение в `AndroidManifest.xml`:
   ```xml
   <uses-permission android:name="android.permission.VIBRATE" />
   ```

2. Скопировать функцию `performStrongHaptic(context)` из `core/ui/src/main/java/com/example/template/core/ui/hosts/SwipeToReply.kt`:
   ```kotlin
   private fun performStrongHaptic(context: Context) {
       @Suppress("DEPRECATION")
       val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
           ?: return
       val effect = VibrationEffect.createWaveform(
           longArrayOf(5L, 7L, 10L, 7L, 5L),
           intArrayOf(35, 170, 255, 170, 35),
           -1,
       )
       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
           val attrs = android.os.VibrationAttributes.Builder()
               .setUsage(android.os.VibrationAttributes.USAGE_ALARM)
               .build()
           vibrator.vibrate(effect, attrs)
       } else {
           val audioAttrs = android.media.AudioAttributes.Builder()
               .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
               .setUsage(android.media.AudioAttributes.USAGE_ALARM)
               .build()
           @Suppress("DEPRECATION")
           vibrator.vibrate(effect, audioAttrs)
       }
   }
   ```

3. Вызвать `performStrongHaptic(LocalContext.current)` в момент, когда нужен tactile-tick.

Этого достаточно. Дальше — почему именно так, чтобы не приходилось переоткрывать grumble-by-grumble.

## Почему стандартные подходы НЕ работают

| Подход | Что происходит на нашем устройстве |
|---|---|
| `LocalHapticFeedback.current.performHapticFeedback(LongPress)` (Compose) | Не вибрирует. Проходит через `View.performHapticFeedback` → MIUI глушит. |
| `HapticFeedbackType.TextHandleMove` / `Confirm` / прочие из Compose | То же самое. |
| `Vibrator.vibrate(VibrationEffect.EFFECT_HEAVY_CLICK)` (API 29+, без attributes) | Не вибрирует. Без явного usage'а MIUI тоже фильтрует. |
| `Vibrator.vibrate(effect, AudioAttributes(USAGE_TOUCH))` через deprecated overload | Не вибрирует (на API 33+ overload deprecated). |
| `Vibrator.vibrate(effect, VibrationAttributes(USAGE_TOUCH / HARDWARE_FEEDBACK))` API 33+ | Зависит от системной настройки **«вибрация при касании»**. У большинства пользователей выключена → не вибрирует. |
| `... VibrationAttributes(USAGE_NOTIFICATION)` | Работает **только в silent mode**. В normal-ringer-mode вместо вибрации играет рингтон (это поведение нотификаций). |

## Почему `USAGE_ALARM` работает

MIUI применяет per-usage фильтрацию вибраций по системным настройкам:
- `USAGE_TOUCH` / `USAGE_HARDWARE_FEEDBACK` ← опция «вибрация при касании»;
- `USAGE_NOTIFICATION` ← ringer mode (silent / normal);
- `USAGE_ALARM` ← **отдельный канал, который ВСЕГДА вибрирует** (alarms должны будить пользователя независимо от любых настроек).

Семантически это компромисс — мы не alarm. Но прагматически это единственный legal-API путь дать tactile feedback на этом OEM. Длительность короткого pattern'а (≈34мс) делает эффект ощутимым как «щелчок», а не «alarm rattle».

## Параметры pattern'а

```kotlin
VibrationEffect.createWaveform(
    longArrayOf(5L, 7L, 10L, 7L, 5L),       // длительности on/off в мс
    intArrayOf(35, 170, 255, 170, 35),       // амплитуды 0..255
    -1,                                       // -1 = не повторять
)
```

- 5 импульсов с нарастающей-затухающей амплитудой (35 → 170 → 255 → 170 → 35).
- Общая длительность ≈ 34мс — на грани «щелчок», а не «жужжание».
- Амплитуды `< 255` нужны, потому что максимальная амплитуда работает только на моторах с амплитудным контролем (`vibrator.hasAmplitudeControl()`). На обычных моторах амплитуда ≠ 255 интерпретируется как длительный pwm-mode, что и даёт нужное «дрожащее» ощущение даже на ERM-моторах.
- Этот же pattern проверен в `:components/.../audiopanel/AudioPanelView.kt` (там используется для тактильного отклика на seek).

## API-различия (API 26 — это minSdk проекта)

| API | Способ |
|---|---|
| 33+ (Android 13+) | `Vibrator.vibrate(VibrationEffect, VibrationAttributes)` — новый overload, **не deprecated** |
| 26-32 | `Vibrator.vibrate(VibrationEffect, AudioAttributes)` — overload deprecated в API 33, **но всё ещё работает** на старых API; `@Suppress("DEPRECATION")` |

`VibratorManager.defaultVibrator` (API 31+) можно НЕ использовать — `Context.VIBRATOR_SERVICE` через `getSystemService(...)` всё ещё валиден и не deprecated в `ContextCompat`-сценариях.

## Ограничения и предупреждения

- **Зависит от OEM.** На stock Android USAGE_TOUCH работает идиоматичнее. Это руководство ориентировано на MIUI и подобные сильно-кастомизированные оболочки. На «чистом» Pixel'е `USAGE_ALARM` тоже сработает, просто там и `USAGE_TOUCH` сработал бы.
- **Семантический lie.** Linter'ам / accessibility-чекерам наш `USAGE_ALARM` может не понравиться. Это осознанный compromise.
- **Не использовать для длинных patterns.** USAGE_ALARM рассчитан на alarms — если длительность > 200мс, вибрация будет восприниматься как «срабатывающий будильник», что плохо для UX. Держим pattern короткими impulse'ами.
- **AudioPanel-precedent.** Если будет возможность переключить android-components на USAGE_ALARM для AudioPanelView (там сейчас USAGE_NOTIFICATION — работает только в silent), сделай это синхронно — будет один консистентный haptic-канал в приложении.

## Куда применять

Любое место, где нужен короткий tactile-tick на пользовательском действии:
- Threshold-crossing в gesture'е (см. `SwipeToReplyItem.performStrongHaptic`).
- Long-press confirmation.
- Snap-to-anchor в drag'е (slider'е, picker'е).
- Reach-end-of-list / pull-to-refresh activation.
- Validation success/error tick.

Для каждого вызова — просто `performStrongHaptic(LocalContext.current)`. Никаких дополнительных permissions / config'а помимо VIBRATE в манифесте.
