# Local icons (temporary)

Сюда кладём SVG, которые ещё не успели смержить в `../icons-library`, но уже нужны
проекту. Файлы из этой папки копируются Gradle-task'ой `syncIconsLocal` в
`app/src/main/assets/icons/` **ПОСЛЕ** `syncIconsFromLibrary` (см.
`app/build.gradle.kts`) — поэтому, если в icons-library случайно есть SVG с тем
же именем, локальная версия его перекроет.

`DSIcon.named(context, "<name>", ...)` находит их так же, как библиотечные.

## Как использовать

1. Кладёшь сюда `<name>.svg`.
2. Билдишь — на `preBuild` SVG копируется в `app/src/main/assets/icons/<name>.svg`.
3. Когда тот же `<name>.svg` появится в `../icons-library/icons/`, удали локальную
   копию отсюда и пересобери.

## В отличие от `assets/icons/`

`assets/icons/` в `.gitignore` и пересоздаётся синком; файлы в `assets-local/icons/`
**трекаются git** — это и есть их роль (носитель временных иконок).

## Текущее содержимое

Слот под `quote-*.svg` — индикатор quote-reply в Text/Media бабблах
(`BubblesView/MediaBubbleView.replyQuoteIcon`, ищет `quote-s`) и пункты меню
quote-picker'а (`quote`, `quote-full`).
