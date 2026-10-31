import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import test from 'node:test';

const require = createRequire(import.meta.url);
const domain = require('../frontend/assets/js/domain-v73.js');

const rank = (query, aliases) => domain.rank(query, aliases);

test('поиск прощает опечатку в один символ', () => {
  assert.equal(rank('телепорт', ['телепорт']), 0);
  assert.ok(rank('телеопрт', ['телепорт']) >= 3, 'перестановка букв всё ещё находит');
  assert.ok(rank('телеопрт', ['телепорт']) < Infinity);
  assert.ok(rank('багн', ['бан']) < Infinity, 'пропущенная буква');
  assert.equal(rank('багн', ['заблокировать']), Infinity, 'чужая подпись не должна цепляться');
});

test('поиск понимает неправильную раскладку в обе стороны', () => {
  assert.ok(rank('ntktgjhn', ['телепорт']) < 2);
  assert.ok(rank('телепорт', ['ntktgjhn']) < 2, 'обратное направление тоже');
  assert.ok(rank('buhjrb', ['игроки']) < 2);
});

test('точечные совпадения стоят выше опечаток', () => {
  const exact = rank('бан', ['бан', 'заблокировать']);
  const typo = rank('багн', ['бан']);
  const prefix = rank('бан', ['бан игрока']);
  assert.ok(exact < prefix && prefix < typo);
});

test('короткие запросы не прощают опечаток', () => {
  assert.equal(rank('баа', ['бан']), Infinity, '3 буквы — слишком мало для правки');
});