# Хранение данных

- Файл: `files/journal.json` во внутренней памяти приложения. В сеть он уходит только на ваш Яндекс Диск, если включён [бэкап](../how-to/backup-yandex-disk.md).
- Запись атомарная: сначала `journal.json.tmp`, потом переименование.
- Резервное копирование Android выключено (`allowBackup=false`). Без бэкапа на Яндекс Диск Дневник пропадает при удалении приложения.
- Вход на Диск (логин, пароль приложения, время и отпечаток последней копии) лежит отдельно, в `shared_prefs/backup.xml`, и в копию не попадает.

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
    "programStartEpochDay": 20387,
    "diet": {
      "phase": "Deficit",
      "phaseStartEpochDay": 20380,
      "sex": "Male",
      "bodyFatPercent": 18.0,
      "heightCm": null,
      "age": null,
      "activity": "Moderate",
      "deficitPercent": 15,
      "calorieCorrectionPercent": 0,
      "correctedEpochDay": null,
      "refeedDays": [20387]
    }
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
  ],
  "weights": [ { "epochDay": 20387, "kg": 81.6 } ]
}
```

`epochDay`, `programStartEpochDay`, `phaseStartEpochDay` и `refeedDays` — числа дней с 1 января 1970. `diet.phase` — `Off`, `Deficit`, `Break` или `Maintenance`; правила питания — в [справочнике](flexible-diet.md). `programId` — `maximum`, `hypertrophy` или `null`. Незнакомые поля при чтении пропускаются, поэтому старые версии файла читаются новыми версиями приложения.

## Бэкап на Яндекс Диске

| Файл | Что это |
|---|---|
| `kurmish-fitness/journal.json` | последняя копия, её берёт кнопка «Восстановить» |
| `kurmish-fitness/history/journal-ГГГГ-ММ-ДД.json` | последняя копия за каждый день |

Формат копий тот же, что у `journal.json` на телефоне.
