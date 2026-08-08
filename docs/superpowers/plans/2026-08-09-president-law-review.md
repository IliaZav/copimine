# План: законы президента в GUI, сайте и livebar

## 1. RED-контракты

- Расширить `tests/test_requested_gameplay_fixes.py` проверками, что активное меню
  президента читает pending laws, открывает книгу и маршрутизирует approve/reject.
- Добавить проверки livebar: ветка `PRESIDENT_TERM` не может входить в candidate-page
  cycle и должна выводить published laws.
- Расширить `admin-web/scripts/election_rp_contract_test.py` ожиданиями backend route,
  law fields, frontend panel/book/review handlers.
- Добавить контракт, что mandate branch AdminPlus не вызывает legacy announcement.
- Запустить новые проверки до реализации и убедиться, что они падают по ожидаемым
  отсутствующим маркерам.

## 2. Игровой ElectionCore

- В `openRpPresidentMenu` загрузить pending laws активного срока.
- В `renderRpPresidentMenu` добавить карточки законов и действия просмотра.
- Реализовать detail/book rendering по образцу application book.
- Добавить approve/reject actions через существующий `reviewLaw` с refresh snapshot и
  возвратом в President GUI.
- Сохранить все существующие проверки доступа и подтверждения.
- Зафиксировать, что livebar в `PRESIDENT_TERM` всегда использует presidential branch.

## 3. Веб-админка

- В `election_detail_sync` вернуть опубликованные и pending законы активного срока.
- Добавить транзакционный `review_president_law_sync` с теми же лимитами/cooldown,
  проверкой активного срока, аудитом и panel event.
- Добавить admin-only POST endpoint с sensitive confirmation.
- В `cabinet-runtime.js` отрисовать секцию законов в election admin panel, книжное
  окно и approve/reject handlers с обновлением данных.

## 4. Legacy mandate cleanup

- Удалить устаревший AdminPlus side effect для `president_mandate`, не меняя обработчик
  ElectionCore.
- Проверить bytecode собранного AdminPlus, что строка/вызов announcement отсутствует
  из mandate interaction path.

## 5. Проверка и поставка

- Запустить все относящиеся contract/validator tests и frontend selftests.
- Собрать ElectionCore/AdminPlus JAR, проверить содержимое и SHA-256.
- Выполнить Paper-remapped проверку.
- Перед загрузкой сделать серверный backup, загрузить только проверенные артефакты,
  перезапустить `copimine-minecraft`, проверить remote SHA-256 и startup logs.
- Закоммитить только task changes и отправить branch в origin; пользовательские dirty
  файлы не добавлять.
