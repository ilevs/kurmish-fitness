# Хранение данных

- Файл: `files/journal.json` во внутренней памяти приложения. Доступа к сети у приложения нет, разрешений оно не просит.
- Запись атомарная: сначала `journal.json.tmp`, потом переименование.
- Резервное копирование Android выключено (`allowBackup=false`), поэтому при удалении приложения журнал пропадает.

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
      "feeling": "Hard"
    }
  ]
}
```

`epochDay` и `programStartEpochDay` — число дней с 1 января 1970. `programId` — `maximum`, `hypertrophy` или `null`. Незнакомые поля при чтении пропускаются, поэтому старые версии файла читаются новыми версиями приложения.
