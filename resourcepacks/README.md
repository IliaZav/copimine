# CopiMine Resource Pack

Исходники ресурспака лежат в `src/`.

Сборка:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-resourcepack.ps1
```

Результат:

- `build/CopiMineResourcePack.zip`
- `build/CopiMineResourcePack.sha1`

Пак не заменяет глобально ванильные блоки и предметы. Все кастомные модели добавляются только через `CustomModelData` и `assets/copimine/...`.
