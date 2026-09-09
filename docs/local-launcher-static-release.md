# Локальный release-комплект Launcher

Этот комплект предназначен для проверки локального релиза и последующей
замены только статической web-раздачи. Production сейчас не используется.

## Что входит

- `CopiMineLauncher-1.0.3-static-site-20260824.tar.gz` — самодостаточная статическая раздача сайта,
  Launcher, metadata, manifest и установщиков;
- `scripts/replace_copimine_static_launcher_release.sh` — безопасная замена
  через версионированную папку и symlink `current`;
- старые релизы не удаляются, поэтому rollback выполняется переключением
  symlink на прежнюю папку.

Скрипт намеренно не знает о Paper, Minecraft world, player-data, AuthMe или
database. Он откажется работать, если package/archive или target path похожи
на игровой или persistent-data путь.

## Проверка на сервере после его доступности

Сначала передайте архив в отдельный временный каталог static-инфраструктуры и
проверьте его SHA-256. Затем выполните от имени пользователя web-раздачи:

```bash
sha256sum CopiMineLauncher-1.0.3-static-site-20260824.tar.gz
tar -tzf CopiMineLauncher-1.0.3-static-site-20260824.tar.gz >/dev/null

bash scripts/replace_copimine_static_launcher_release.sh \
  --archive /srv/copimine-upload/CopiMineLauncher-1.0.3-static-site-20260824.tar.gz \
  --web-root /srv/copimine-web \
  --current-link /srv/copimine-web/current \
  --release-id launcher-1.0.3-local-20260824 \
  --reload-nginx
```

Nginx должен заранее смотреть в `/srv/copimine-web/current`. Скрипт не
перезапускает Paper и не вызывает `systemctl restart`, `reload`, `kill`,
`rsync --delete` или удаление старых релизов. Если `nginx -t` или reload
завершится ошибкой, текущий symlink возвращается на прежнюю цель.

Перед реальным запуском этой команды нужно отдельно проверить именно путь
web-root и конфигурацию nginx. Наличие SSH-доступа само по себе не является
разрешением менять игровой production-сервер.
