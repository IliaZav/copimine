# CopiMine: выпуск и установка

## Локальная проверка

Из каталога `opt/copimine` запустите:

```powershell
python -m py_compile admin-web/backend/main.py
node --check admin-web/frontend/assets/js/cabinet-runtime.js
node --check admin-web/frontend/assets/js/admin/commerce-pages.js
powershell -NoProfile -ExecutionPolicy Bypass -File tests/RunCopiMineValidators.ps1
```

Сборка полного пакета:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\package_full_release.ps1 `
  -ProjectRoot (Resolve-Path .).Path
```

## Отправка на Ubuntu

Замените путь на имя свежего архива из каталога `release`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\windows\send_copimine_release.ps1 `
  -Archive D:\Desktop\Copimine\release\copimine-opt-full-YYYY-MM-DD-HHMMSS.tar.gz
```

После успешной проверки контрольной суммы на сервере установщик запускается так:

```bash
sudo bash /home/qwerty/copimine-upload/install_release.sh \
  /home/qwerty/copimine-upload/copimine-opt-full-YYYY-MM-DD-HHMMSS.tar.gz
```

Установщик сохраняет резервную копию. `--wipe-worlds` и восстановление дампа не
включены по умолчанию: используйте их только после отдельной проверки резервной
копии и согласования вайпа.

Проверка после установки:

```bash
sudo bash /home/qwerty/copimine-upload/collect_deploy_diagnostics.sh
sudo systemctl status copimine-admin-web copimine-minecraft --no-pager
curl -fsS http://127.0.0.1:8090/api/health
```

## Что важно

- Новая версия интерфейса отдаётся с запретом устаревшего кэша для HTML, CSS и JS.
- Ресурспак и модпак собираются в пакете; URL ресурспака берётся из манифеста.
- Сброс казны выполняется только из админки подтверждённым действием и оставляет
  запись аудита. Реальные покупки по-прежнему зачисляют доход в казну.
- Рецепты после сохранения применяются командой `cmnarcotics reload`; перезапуск
  сервера не нужен, если RCON доступен.
- Создание лавки из `/adminhub` проверяет блок и уникальность случайного пятибуквенного
  кода в PostgreSQL. Переименование доступно в управлении блоками и командой
  `/cmartifacts shop rename <код> <название>`.
