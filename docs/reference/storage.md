# Хранение данных

- Файл: `files/journal.json` во внутренней памяти приложения. В сеть данные уходят только в чат с ботом Telegram, если [синхронизация](../how-to/sync-telegram.md) включена. Токен бота хранится в этом же файле.
- Запись атомарная: сначала `journal.json.tmp`, потом переименование.
- Резервное копирование Android выключено (`allowBackup=false`), поэтому при удалении приложения дневник пропадает.

## Схема

```json
{
  "profile": {
    "bodyweightKg": 82.0,
    "discipline": "Bench",
    "rank": "Second",
    "goal": "Peak",
    "daysPerWeek": 2,
    "correctionPercent": 0.5,
    "natural": true,
    "manualMaxes": { "Bench": 110.0 },
    "programId": "maximum",
    "programStartEpochDay": 20387
  },
  "sessions": [
    {
      "id": 1,
      "epochDay": 20387,
      "rawText": "жим 100 на 5 по 3, было тяжело",
      "entries": [
        { "exercise": "Bench", "customName": null, "sets": [ { "weightKg": 100.0, "reps": 5, "sets": 3 } ] }
      ],
      "feeling": "Hard",
      "telegramMessageId": 57,
      "telegramParts": []
    }
  ],
  "telegram": {
    "token": "123456789:AA…",
    "botId": 123456789,
    "botUsername": "my_diary_bot",
    "chatId": 987654321,
    "lastUpdateId": 70451234,
    "pendingDeletes": [],
    "lastSyncEpochSecond": 1793000000
  }
}
```

`epochDay` и `programStartEpochDay` — число дней с 1 января 1970. `programId` — `maximum`, `hypertrophy` или `null`. Незнакомые поля при чтении пропускаются, поэтому старые версии файла читаются новыми версиями приложения.

Поля синхронизации с Telegram:

| Поле | Значение |
|---|---|
| `sessions[].telegramMessageId` | номер копии записи в чате с ботом; `null` — запись ещё не отправлена |
| `sessions[].telegramParts` | сообщения боту (`messageId`, `text`), из которых собрана запись; пусто для записей из приложения |
| `telegram.token` | токен бота; пустая строка — синхронизация выключена |
| `telegram.botId`, `botUsername` | бот, к которому подключались последним |
| `telegram.chatId` | чат владельца: первый личный чат, написавший боту |
| `telegram.lastUpdateId` | последнее обработанное событие Bot API |
| `telegram.pendingDeletes` | сообщения бота, которые надо удалить, потому что запись удалена в приложении |
| `telegram.lastSyncEpochSecond` | время последней успешной синхронизации, секунды с 1970 года |
