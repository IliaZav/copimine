# CopiMine: полная замена `opt/copimine` и восстановление на Ubuntu

Этот сценарий нужен, когда надо полностью заменить проект на сервере новой актуальной версией.

Что входит в полную замену:

1. Полная замена папки `/opt/copimine`.
2. Полная замена jar-плагинов, resource pack, сайта, конфигов и клиентских файлов внутри `opt`.
3. Отдельный backup и restore PostgreSQL.
4. Перезапуск сервисов Minecraft, admin-web и связанных фоновых процессов.

Важно:

- Архив `opt/copimine` не заменяет PostgreSQL сам по себе.
- База данных должна бэкапиться и восстанавливаться отдельно.
- Если нужна именно полная миграция состояния, делайте и замену `opt`, и restore PostgreSQL dump.

## Что подготовить на исходной машине

1. Актуальный архив папки `opt/copimine`.
2. PostgreSQL dump базы CopiMine.
3. Доступ `sudo` на сервере.

## 1. Backup перед заменой на сервере

```bash
sudo systemctl stop copimine-admin || true
sudo systemctl stop copimine-minecraft || true
sudo systemctl stop copimine-discord || true

sudo mkdir -p /opt/copimine-backups
TS="$(date +%Y%m%d-%H%M%S)"

sudo cp -a /opt/copimine "/opt/copimine-backups/copimine-opt-$TS"
sudo -u postgres pg_dump -Fc copimine > "/opt/copimine-backups/copimine-db-$TS.dump"
```

## 2. Полная замена папки `/opt/copimine`

Предположим, архив уже загружен на сервер в `/home/<user>/copimine-upload/`.

```bash
cd /home/<user>/copimine-upload
unzip -q CopiMine-opt-full-*.zip -d extracted

sudo rm -rf /opt/copimine.new
sudo mkdir -p /opt/copimine.new
sudo cp -a extracted/. /opt/copimine.new/

sudo rm -rf /opt/copimine.old
if [ -d /opt/copimine ]; then
  sudo mv /opt/copimine /opt/copimine.old
fi
sudo mv /opt/copimine.new /opt/copimine
```

## 3. Полный restore базы из dump

Если база должна быть заменена полностью:

```bash
sudo systemctl stop copimine-admin || true
sudo systemctl stop copimine-minecraft || true
sudo systemctl stop copimine-discord || true

sudo -u postgres dropdb --if-exists copimine
sudo -u postgres createdb -O copimine copimine
sudo -u postgres pg_restore -d copimine /path/to/copimine-db.dump
```

Если нужно сохранить текущую базу и только обновить файлы, этот шаг пропускается.

## 4. Права и исполняемые файлы

```bash
sudo chown -R copimine:copimine /opt/copimine 2>/dev/null || true
sudo chown -R qwerty:qwerty /opt/copimine 2>/dev/null || true

sudo find /opt/copimine -type d -exec chmod 755 {} \;
sudo find /opt/copimine -type f -exec chmod 644 {} \;

sudo chmod +x /opt/copimine/admin-web/start.sh 2>/dev/null || true
sudo chmod +x /opt/copimine/admin-web/backend/*.py 2>/dev/null || true
```

Используйте того системного пользователя, под которым реально работают ваши сервисы.

## 5. Проверка критичных путей

Проверьте, что на месте:

- `/opt/copimine/minecraft/server/plugins`
- `/opt/copimine/admin-web`
- `/opt/copimine/resourcepacks/build/CopiMineResourcePack.zip`
- `/opt/copimine/minecraft/server/server.properties`
- `/opt/copimine/admin-web/.env`

## 6. Перезапуск сервисов

```bash
sudo systemctl daemon-reload
sudo systemctl restart copimine-admin || true
sudo systemctl restart copimine-minecraft || true
sudo systemctl restart copimine-discord || true
```

## 7. Быстрая проверка после запуска

```bash
sudo systemctl status copimine-admin --no-pager -l | sed -n '1,30p'
sudo systemctl status copimine-minecraft --no-pager -l | sed -n '1,30p'
tail -n 200 /opt/copimine/minecraft/server/logs/latest.log
```

Что проверить в логах:

1. `CopiMineEconomyCore` загрузился без SQL ошибок.
2. `CopiMineElectionCore` загрузился без ошибок schema/column.
3. `CopiMineNarcotics` загрузился и не отключился.
4. `CopiMineWorldCore` загрузился.
5. `TAB` не ругается на broken YAML.

## 8. Если нужен откат

```bash
sudo systemctl stop copimine-admin || true
sudo systemctl stop copimine-minecraft || true
sudo systemctl stop copimine-discord || true

sudo rm -rf /opt/copimine
sudo mv /opt/copimine.old /opt/copimine

sudo systemctl restart copimine-admin || true
sudo systemctl restart copimine-minecraft || true
sudo systemctl restart copimine-discord || true
```

Restore базы при откате:

```bash
sudo -u postgres dropdb --if-exists copimine
sudo -u postgres createdb -O copimine copimine
sudo -u postgres pg_restore -d copimine /opt/copimine-backups/copimine-db-<timestamp>.dump
```
