/* Fills the static error page from the HTTP status passed by the backend. */
(function () {
  'use strict';
  var body = document.body;
  var param = Number(new URLSearchParams(location.search).get('code'));
  var fromBody = Number(body.getAttribute('data-code'));
  var code = fromBody >= 400 && fromBody < 600 ? fromBody : (param >= 400 && param < 600 ? param : 404);
  var incident = body.getAttribute('data-incident') || new URLSearchParams(location.search).get('incident') || '';
  if (incident.indexOf('{{') === 0) incident = '';
  var copy = {
    400: ['Некорректный запрос', 'Страница потерялась между чанками', 'Браузер отправил данные, которые сервер не смог прочитать. Обновите страницу и попробуйте снова.'],
    401: ['Нужно войти', 'Для этой страницы нужен аккаунт', 'Войдите в аккаунт CopiMine — и мы вернём вас туда, куда вы шли.'],
    403: ['Доступ закрыт', 'Эта дверь заперта', 'У аккаунта нет прав на этот раздел. Если доступ должен быть — напишите в поддержку.'],
    404: ['Страница не найдена', 'Страница потерялась между чанками', 'Такой страницы нет. Возможно, ссылка устарела или в адресе опечатка.'],
    405: ['Действие недоступно', 'Так с этой страницей нельзя', 'Ссылка требует другого действия. Вернитесь на главную и попробуйте оттуда.'],
    409: ['Данные устарели', 'Кто-то успел раньше', 'Состояние изменилось, пока страница была открыта. Обновите её и повторите действие.'],
    413: ['Файл слишком большой', 'Не влезло в сундук', 'Сервер не принял такой объём данных. Уменьшите файл и попробуйте снова.'],
    418: ['Пасхалка', 'Я чайник', 'Код придумали 1 апреля 1998 года. Сервер кофе не варит, но страницы отдаёт исправно.'],
    422: ['Проверьте данные', 'Форма не прошла проверку', 'Некоторые поля заполнены неверно. Вернитесь назад и проверьте их.'],
    429: ['Слишком много запросов', 'Не так быстро', 'Мы ненадолго ограничили частоту запросов. Подождите минуту и повторите.'],
    500: ['Ошибка на сервере', 'Сервер споткнулся о блок', 'Мы уже получили сигнал и разбираемся. Попробуйте обновить страницу через минуту.'],
    502: ['Сервис не ответил', 'Мост между мирами не отвечает', 'Один из внутренних сервисов не ответил. Обычно это проходит за пару минут.'],
    503: ['Сервис недоступен', 'Мир на техническом перерыве', 'Сервис временно недоступен — скорее всего, идёт обновление. Загляните чуть позже.']
  };
  var entry = copy[code] || (code >= 500
    ? ['Ошибка на сервере', 'Сервер споткнулся о блок', 'Запрос не удалось выполнить. Попробуйте обновить страницу через минуту.']
    : ['Ошибка запроса', 'Что-то пошло не так', 'Запрос не удалось выполнить. Вернитесь на главную и попробуйте снова.']);
  document.title = code + ' — ' + entry[0] + ' · CopiMine';
  body.setAttribute('data-code', String(code));
  document.querySelectorAll('[data-error-code]').forEach(function (node) {
    node.textContent = String(code);
    node.setAttribute('data-code', String(code));
  });
  document.querySelector('[data-error-label]').textContent = code === 418 ? 'Пасхалка' : 'Ошибка ' + code;
  document.querySelector('[data-error-title]').textContent = entry[1];
  document.querySelector('[data-error-text]').textContent = entry[2];
  var note = document.querySelector('.err-incident');
  if (incident && code >= 500) {
    note.hidden = false;
    document.querySelector('[data-error-incident]').textContent = incident;
  }
  if (code >= 400 && code < 500 && code !== 418) document.querySelector('[data-support]').hidden = false;
  if (code >= 500) document.querySelector('[data-error-retry]').hidden = false;
  if (history.length > 1) document.querySelector('[data-error-back]').hidden = false;
  document.querySelector('[data-error-retry]').addEventListener('click', function () { location.reload(); });
  document.querySelector('[data-error-back]').addEventListener('click', function () { history.back(); });
})();
