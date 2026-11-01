window.CM6_ADMIN_ACTIONS={
  "schemaVersion": 1,
  "actions": [
    {
      "id": "ban",
      "name": "Блокировка игрока",
      "scope": "player",
      "permission": "players.moderate",
      "params": {
        "durationMs": {
          "type": "integer",
          "required": false
        },
        "expiresAt": {
          "type": "integer",
          "required": false
        }
      },
      "sourceKind": "site-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "mute",
      "name": "Ограничить общение",
      "scope": "player",
      "permission": "players.moderate",
      "params": {
        "durationMs": {
          "type": "integer",
          "required": false
        },
        "expiresAt": {
          "type": "integer",
          "required": false
        },
        "channel": {
          "type": "string",
          "required": false
        }
      },
      "sourceKind": "cmadmin-voice-plus-adapter-text",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "kick",
      "name": "Отключить от сервера",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "sourceKind": "cmadmin",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "warn",
      "name": "Предупреждение",
      "scope": "player",
      "permission": "players.moderate",
      "params": {
        "durationMs": {
          "type": "integer",
          "required": false
        },
        "expiresAt": {
          "type": "integer",
          "required": false
        }
      },
      "sourceKind": "site-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "freeze",
      "name": "Заморозить игрока",
      "scope": "player",
      "permission": "players.moderate",
      "params": {
        "durationMs": {
          "type": "integer",
          "required": false
        },
        "expiresAt": {
          "type": "integer",
          "required": false
        }
      },
      "sourceKind": "cmadmin",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "unfreeze",
      "name": "Снять заморозку",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "sourceKind": "site-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "jail",
      "name": "Заключить в тюрьму",
      "scope": "player",
      "permission": "players.moderate",
      "params": {
        "durationMs": {
          "type": "integer",
          "required": false
        },
        "expiresAt": {
          "type": "integer",
          "required": false
        }
      },
      "sourceKind": "site-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "unjail",
      "name": "Освободить",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "sourceKind": "site-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "give",
      "name": "Выдать предмет",
      "scope": "player",
      "permission": "inventory.manage",
      "params": {
        "itemId": {
          "type": "string",
          "required": true
        },
        "quantity": {
          "type": "integer",
          "required": true
        }
      },
      "sourceKind": "site-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "inventory.remove",
      "name": "Изъять предмет из инвентаря",
      "scope": "player",
      "permission": "inventory.manage",
      "params": {
        "slot": {
          "type": "integer",
          "required": true
        },
        "quantity": {
          "type": "integer",
          "required": true
        },
        "expectedSnapshotId": {
          "type": "string",
          "required": true
        },
        "expectedItemId": {
          "type": "string",
          "required": true
        }
      },
      "sourceKind": "site-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "ender.remove",
      "name": "Изъять предмет из эндер-сундука",
      "scope": "player",
      "permission": "inventory.manage",
      "params": {
        "slot": {
          "type": "integer",
          "required": true
        },
        "quantity": {
          "type": "integer",
          "required": true
        },
        "expectedSnapshotId": {
          "type": "string",
          "required": true
        },
        "expectedItemId": {
          "type": "string",
          "required": true
        }
      },
      "sourceKind": "site-extension",
      "requiresGameAdapter": true,
      "available": false
    },
    {
      "id": "inventory.restore",
      "name": "Восстановить снимок",
      "scope": "player",
      "permission": "inventory.manage",
      "params": {
        "snapshotId": {
          "type": "string",
          "required": true
        },
        "expectedSnapshotId": {
          "type": "string",
          "required": true
        }
      },
      "sourceKind": "site-extension",
      "requiresGameAdapter": true,
      "available": false
    },
    {
      "id": "heal",
      "name": "Вылечить",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "sourceKind": "cmadmin",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "feed",
      "name": "Восполнить сытость",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "sourceKind": "cmadmin",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "extinguish",
      "name": "Потушить",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "sourceKind": "site-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "effect.clear",
      "name": "Очистить эффекты",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "sourceKind": "cmadmin",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "tp.coords",
      "name": "Телепортировать по координатам",
      "scope": "player",
      "permission": "players.moderate",
      "params": {
        "world": {
          "type": "string",
          "required": true
        },
        "x": {
          "type": "number",
          "required": true
        },
        "y": {
          "type": "number",
          "required": true
        },
        "z": {
          "type": "number",
          "required": true
        }
      },
      "sourceKind": "site-extension",
      "requiresGameAdapter": true,
      "available": false
    },
    {
      "id": "tp.spawn",
      "name": "Телепортировать на спавн",
      "scope": "player",
      "permission": "players.moderate",
      "params": {
        "world": {
          "type": "string",
          "required": false
        }
      },
      "sourceKind": "site-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "tp.bed",
      "name": "Вернуть к кровати",
      "scope": "player",
      "permission": "players.moderate",
      "params": {
        "world": {
          "type": "string",
          "required": false
        }
      },
      "sourceKind": "site-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "gamemode",
      "name": "Изменить режим игры",
      "scope": "player",
      "permission": "players.moderate",
      "params": {
        "mode": {
          "type": "string",
          "required": true
        }
      },
      "sourceKind": "cmadmin",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "fly",
      "name": "Управлять полётом",
      "scope": "player",
      "permission": "players.moderate",
      "params": {
        "enabled": {
          "type": "boolean",
          "required": true
        },
        "durationMs": {
          "type": "integer",
          "required": false
        },
        "expiresAt": {
          "type": "integer",
          "required": false
        }
      },
      "sourceKind": "site-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "god",
      "name": "Управлять неуязвимостью",
      "scope": "player",
      "permission": "players.moderate",
      "params": {
        "enabled": {
          "type": "boolean",
          "required": true
        },
        "durationMs": {
          "type": "integer",
          "required": false
        },
        "expiresAt": {
          "type": "integer",
          "required": false
        }
      },
      "sourceKind": "site-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "money.adjust",
      "name": "Изменить баланс",
      "scope": "player",
      "permission": "economy.manage",
      "params": {
        "currency": {
          "type": "string",
          "required": true
        },
        "mode": {
          "type": "string",
          "required": true
        },
        "amount": {
          "type": "integer",
          "required": true
        }
      },
      "sourceKind": "site-extension",
      "requiresGameAdapter": false,
      "available": false
    },
    {
      "id": "purchase.limit",
      "name": "Настроить личный лимит покупок",
      "scope": "player",
      "permission": "economy.manage",
      "params": {
        "itemId": {
          "type": "string",
          "required": false
        },
        "maximum": {
          "type": "integer",
          "required": false
        },
        "reset": {
          "type": "boolean",
          "required": false
        },
        "remove": {
          "type": "boolean",
          "required": false
        },
        "limitId": {
          "type": "string",
          "required": false
        }
      },
      "sourceKind": "site-extension",
      "requiresGameAdapter": false,
      "available": false
    },
    {
      "id": "sessions.revoke",
      "name": "Завершить сессии сайта",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "sourceKind": "site-extension",
      "requiresGameAdapter": false,
      "available": false
    },
    {
      "id": "fun.title",
      "name": "Показать заголовок",
      "scope": "player",
      "permission": "players.moderate",
      "params": {
        "title": {
          "type": "string",
          "required": true
        },
        "subtitle": {
          "type": "string",
          "required": false
        }
      },
      "sourceKind": "cmadmin",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "fun.sound",
      "name": "Воспроизвести звук",
      "scope": "player",
      "permission": "players.moderate",
      "params": {
        "sound": {
          "type": "string",
          "required": true
        },
        "volume": {
          "type": "number",
          "required": false
        },
        "pitch": {
          "type": "number",
          "required": false
        }
      },
      "sourceKind": "site-extension",
      "requiresGameAdapter": true,
      "available": false
    },
    {
      "id": "fun.firework",
      "name": "Запустить фейерверк",
      "scope": "player",
      "permission": "players.moderate",
      "params": {
        "color": {
          "type": "string",
          "required": false
        },
        "count": {
          "type": "integer",
          "required": false
        }
      },
      "sourceKind": "site-extension",
      "requiresGameAdapter": true,
      "available": false
    },
    {
      "id": "fun.glow",
      "name": "Подсветить игрока",
      "scope": "player",
      "permission": "players.moderate",
      "params": {
        "durationMs": {
          "type": "integer",
          "required": false
        },
        "color": {
          "type": "string",
          "required": false
        }
      },
      "sourceKind": "cmadmin",
      "requiresGameAdapter": true,
      "available": false
    },
    {
      "id": "fun.amethyst",
      "name": "Аметистовый звон",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.anvil",
      "name": "Звук наковальни",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "inventory.armor-off",
      "name": "Снять броню",
      "scope": "player",
      "permission": "inventory.manage",
      "params": {
        "expectedSnapshotId": {
          "type": "string",
          "required": true
        }
      },
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": true
    },
    {
      "id": "fun.bee",
      "name": "Звук пчелы",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.bees",
      "name": "Пчёлы",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.blind",
      "name": "Слепота",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": true
    },
    {
      "id": "effects.burn5",
      "name": "Поджечь на 5 секунд",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": true
    },
    {
      "id": "check-remind",
      "name": "Напомнить о проверке",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "check-stop",
      "name": "Завершить проверку",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "check-stop-return",
      "name": "Завершить проверку и вернуть игрока",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.chickens",
      "name": "Курицы",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.chorus",
      "name": "Эффект хоруса",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "inventory.clear",
      "name": "Очистить инвентарь",
      "scope": "player",
      "permission": "inventory.manage",
      "params": {
        "expectedSnapshotId": {
          "type": "string",
          "required": true
        }
      },
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": true
    },
    {
      "id": "fun.clock",
      "name": "Обратный отсчёт",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.cobweb",
      "name": "Паутина",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.confetti",
      "name": "Конфетти",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.confuse",
      "name": "Дезориентация",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.dark",
      "name": "Затемнение",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.explosion",
      "name": "Безопасный эффект взрыва",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.fakeban",
      "name": "Шуточный экран блокировки",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.fakecredits",
      "name": "Шуточные титры",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.fakeop",
      "name": "Шуточное сообщение об операторе",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.fireworkfake",
      "name": "Звук фейерверка",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.fortune",
      "name": "Печенье судьбы",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.fsb",
      "name": "Шуточная проверка",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "effects.god10",
      "name": "Защитные эффекты на 10 секунд",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": true
    },
    {
      "id": "fun.horn",
      "name": "Козий рог",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "inventory.lock",
      "name": "Временно закрыть инвентарь",
      "scope": "player",
      "permission": "inventory.manage",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "ban.ip",
      "name": "Заблокировать игровой IP",
      "scope": "player",
      "permission": "players.moderate",
      "params": {
        "durationMs": {
          "type": "integer",
          "minimum": 1000,
          "maximum": 31622400000
        },
        "expiresAt": {
          "type": "integer"
        }
      },
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "timed": true,
      "reversible": true,
      "available": true
    },
    {
      "id": "fun.jumpscare",
      "name": "Внезапный звук",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.knockback",
      "name": "Оттолкнуть",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": true
    },
    {
      "id": "fun.launch",
      "name": "Подбросить",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": true
    },
    {
      "id": "fun.levitate",
      "name": "Левитация",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.lightning",
      "name": "Эффект молнии",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": true
    },
    {
      "id": "player.message",
      "name": "Напомнить о правилах",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.nausea",
      "name": "Тошнота",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "nearby",
      "name": "Сведения об окружении",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.note",
      "name": "Ноты",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.panic",
      "name": "Сигнал тревоги",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.paperwork",
      "name": "Шуточная форма",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.phantom",
      "name": "Звук фантома",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.potato",
      "name": "Выдать шуточную картофелину",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.powdersnow",
      "name": "Рыхлый снег",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.pumpkin",
      "name": "Надеть тыкву",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": true
    },
    {
      "id": "fun.rainbow",
      "name": "Радужный заголовок",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "tp.random",
      "name": "Случайно переместить рядом",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": true
    },
    {
      "id": "inventory.repair-hand",
      "name": "Починить предмет в руке",
      "scope": "player",
      "permission": "inventory.manage",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": true
    },
    {
      "id": "fun.scare",
      "name": "Пугающий звук",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "inventory.shuffle-hotbar",
      "name": "Перемешать панель быстрого доступа",
      "scope": "player",
      "permission": "inventory.manage",
      "params": {
        "expectedSnapshotId": {
          "type": "string",
          "required": true
        }
      },
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.slime",
      "name": "Прыжок слизи",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "effects.slow10",
      "name": "Замедлить на 10 секунд",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": true
    },
    {
      "id": "inventory.snapshot",
      "name": "Получить снимок инвентаря",
      "scope": "player",
      "permission": "inventory.manage",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": true
    },
    {
      "id": "fun.sneeze",
      "name": "Чихание панды",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.snowball",
      "name": "Снежок",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.snowcloud",
      "name": "Снежное облако",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.sparkle",
      "name": "Искры",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.spin",
      "name": "Повернуть игрока",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.spy",
      "name": "Звук подзорной трубы",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.squid",
      "name": "Эффект спрута",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.taxpaper",
      "name": "Шуточная налоговая бумага",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.tinyquest",
      "name": "Мини-задание",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.totem",
      "name": "Эффект тотема",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "tp.up10",
      "name": "Переместить на 10 блоков вверх",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": true
    },
    {
      "id": "fun.warden",
      "name": "Звук вардена",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "fun.wind",
      "name": "Порыв ветра",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "requiresGameAdapter": true,
      "sourceKind": "cmadmin",
      "available": false
    },
    {
      "id": "db.optimize",
      "name": "Обслуживание игровой базы данных",
      "scope": "server",
      "permission": "server.manage",
      "params": {},
      "sourceKind": "cmadmin",
      "requiresGameAdapter": true,
      "available": false
    },
    {
      "id": "db.checkpoint",
      "name": "Контрольная точка игровой базы",
      "scope": "server",
      "permission": "server.manage",
      "params": {},
      "sourceKind": "cmadmin",
      "requiresGameAdapter": true,
      "available": false
    },
    {
      "id": "players.snapshot-all",
      "name": "Снимки всех игроков",
      "scope": "server",
      "permission": "server.manage",
      "params": {},
      "sourceKind": "cmadmin",
      "requiresGameAdapter": true,
      "available": false
    },
    {
      "id": "players.heal-all",
      "name": "Вылечить всех игроков",
      "scope": "server",
      "permission": "server.manage",
      "params": {},
      "sourceKind": "cmadmin",
      "requiresGameAdapter": true,
      "available": false
    },
    {
      "id": "players.feed-all",
      "name": "Накормить всех игроков",
      "scope": "server",
      "permission": "server.manage",
      "params": {},
      "sourceKind": "cmadmin",
      "requiresGameAdapter": true,
      "available": false
    },
    {
      "id": "players.unfreeze-all",
      "name": "Снять заморозку со всех",
      "scope": "server",
      "permission": "server.manage",
      "params": {},
      "sourceKind": "cmadmin",
      "requiresGameAdapter": true,
      "available": false
    },
    {
      "id": "players.remind-checks",
      "name": "Напомнить о проверках всем",
      "scope": "server",
      "permission": "server.manage",
      "params": {},
      "sourceKind": "cmadmin",
      "requiresGameAdapter": true,
      "available": false
    },
    {
      "id": "players.save-all",
      "name": "Сохранить всех игроков",
      "scope": "server",
      "permission": "server.manage",
      "params": {},
      "sourceKind": "cmadmin",
      "requiresGameAdapter": true,
      "available": false
    },
    {
      "id": "players.night-vision-all",
      "name": "Ночное зрение всем",
      "scope": "server",
      "permission": "server.manage",
      "params": {},
      "sourceKind": "cmadmin",
      "requiresGameAdapter": true,
      "available": false
    },
    {
      "id": "players.clear-negative-effects-all",
      "name": "Снять отрицательные эффекты у всех",
      "scope": "server",
      "permission": "server.manage",
      "params": {},
      "sourceKind": "cmadmin",
      "requiresGameAdapter": true,
      "available": false
    },
    {
      "id": "world.reset-objects",
      "name": "Сбросить реестр мировых объектов",
      "scope": "server",
      "permission": "server.manage",
      "params": {
        "confirm": {
          "type": "string",
          "enum": [
            "RESET_WORLD_OBJECTS"
          ],
          "required": true
        }
      },
      "sourceKind": "cmadmin-console",
      "requiresGameAdapter": true,
      "privateOnly": false,
      "available": false
    },
    {
      "id": "world.clear-floating-texts",
      "name": "Очистить связанные плавающие надписи",
      "scope": "server",
      "permission": "server.manage",
      "params": {
        "confirm": {
          "type": "string",
          "enum": [
            "CLEAR_FLOATING_TEXTS"
          ],
          "required": true
        }
      },
      "sourceKind": "cmadmin-console",
      "requiresGameAdapter": true,
      "privateOnly": false,
      "available": false
    },
    {
      "id": "game.money.credit",
      "name": "Начислить игровой баланс (EconomyCore)",
      "scope": "player",
      "permission": "economy.manage",
      "params": {
        "currency": {
          "type": "string",
          "enum": [
            "AR",
            "CM"
          ],
          "required": true
        },
        "amount": {
          "type": "integer",
          "minimum": 1,
          "maximum": 1000000000,
          "required": true
        }
      },
      "sourceKind": "cmadmin-console",
      "requiresGameAdapter": true,
      "privateOnly": false,
      "available": false
    },
    {
      "id": "game.audit.ar",
      "name": "Аудит экономики игрового сервера",
      "scope": "server",
      "permission": "logs.view",
      "params": {},
      "sourceKind": "cmadmin-console",
      "requiresGameAdapter": true,
      "privateOnly": false,
      "available": false
    },
    {
      "id": "game.audit.elections",
      "name": "Аудит выборов игрового сервера",
      "scope": "server",
      "permission": "logs.view",
      "params": {},
      "sourceKind": "cmadmin-console",
      "requiresGameAdapter": true,
      "privateOnly": false,
      "available": false
    },
    {
      "id": "rpguard.status",
      "name": "Состояние ограничений команд",
      "scope": "server",
      "permission": "logs.view",
      "params": {},
      "sourceKind": "cmadmin-console",
      "requiresGameAdapter": true,
      "privateOnly": false,
      "available": false
    },
    {
      "id": "rpguard.test",
      "name": "Проверить ограничение команды",
      "scope": "server",
      "permission": "server.manage",
      "params": {
        "probe": {
          "type": "string",
          "required": true
        }
      },
      "sourceKind": "cmadmin-console",
      "requiresGameAdapter": true,
      "privateOnly": false,
      "available": false
    },
    {
      "id": "legacy-votes.status",
      "name": "Состояние прежних выборов",
      "scope": "server",
      "permission": "logs.view",
      "params": {},
      "sourceKind": "cmadmin-console",
      "requiresGameAdapter": true,
      "privateOnly": false,
      "available": false
    },
    {
      "id": "legacy-votes.reset-sidebar",
      "name": "Убрать прежние панели выборов",
      "scope": "server",
      "permission": "server.manage",
      "params": {},
      "sourceKind": "cmadmin-console",
      "requiresGameAdapter": true,
      "privateOnly": false,
      "available": false
    },
    {
      "id": "legacy-votes.clear-glow",
      "name": "Убрать подсветку игроков",
      "scope": "server",
      "permission": "server.manage",
      "params": {},
      "sourceKind": "cmadmin-console",
      "requiresGameAdapter": true,
      "privateOnly": false,
      "available": false
    },
    {
      "id": "legacy-votes.unlock",
      "name": "Закрыть и разблокировать прежние выборы",
      "scope": "server",
      "permission": "server.manage",
      "params": {
        "confirm": {
          "type": "string",
          "enum": [
            "CANCEL_LEGACY_VOTES"
          ],
          "required": true
        }
      },
      "sourceKind": "cmadmin-console",
      "requiresGameAdapter": true,
      "privateOnly": false,
      "available": false
    },
    {
      "id": "election-seal.status",
      "name": "Состояние защиты печати",
      "scope": "server",
      "permission": "logs.view",
      "params": {},
      "sourceKind": "cmadmin-console",
      "requiresGameAdapter": true,
      "privateOnly": false,
      "available": false
    },
    {
      "id": "game.money.adjust",
      "name": "Изменить игровой баланс (EconomyCore)",
      "scope": "player",
      "permission": "economy.manage",
      "params": {
        "currency": {
          "type": "string",
          "enum": [
            "AR",
            "CM"
          ],
          "required": true
        },
        "mode": {
          "type": "string",
          "enum": [
            "credit",
            "debit",
            "set"
          ],
          "required": true
        },
        "amount": {
          "type": "integer",
          "minimum": 0,
          "maximum": 1000000000,
          "required": true
        }
      },
      "sourceKind": "site-v6-bridge-extension",
      "requiresGameAdapter": true,
      "privateOnly": false,
      "available": false
    },
    {
      "id": "kill",
      "name": "Убить игрока",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "sourceKind": "site-v6-bridge-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "unban",
      "name": "Снять блокировку",
      "scope": "player",
      "permission": "players.moderate",
      "params": {},
      "sourceKind": "site-v6-bridge-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "teleport",
      "name": "Телепортировать игрока",
      "scope": "player",
      "permission": "players.moderate",
      "params": {
        "world": {
          "type": "string",
          "required": true,
          "enum": [
            "CopiMine",
            "CopiMine_nether",
            "CopiMine_the_end"
          ]
        },
        "x": {
          "type": "number",
          "required": true
        },
        "y": {
          "type": "number",
          "required": true
        },
        "z": {
          "type": "number",
          "required": true
        }
      },
      "sourceKind": "site-v6-bridge-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "whitelist.add",
      "name": "Добавить в белый список",
      "scope": "player",
      "permission": "server.manage",
      "params": {},
      "sourceKind": "site-v6-bridge-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "whitelist.remove",
      "name": "Убрать из белого списка",
      "scope": "player",
      "permission": "server.manage",
      "params": {},
      "sourceKind": "site-v6-bridge-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "time.day",
      "name": "Включить день",
      "scope": "server",
      "permission": "server.manage",
      "params": {
        "world": {
          "type": "string",
          "required": true,
          "enum": [
            "CopiMine",
            "CopiMine_nether",
            "CopiMine_the_end"
          ]
        }
      },
      "sourceKind": "site-v6-bridge-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "time.night",
      "name": "Включить ночь",
      "scope": "server",
      "permission": "server.manage",
      "params": {
        "world": {
          "type": "string",
          "required": true,
          "enum": [
            "CopiMine",
            "CopiMine_nether",
            "CopiMine_the_end"
          ]
        }
      },
      "sourceKind": "site-v6-bridge-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "weather.clear",
      "name": "Ясная погода",
      "scope": "server",
      "permission": "server.manage",
      "params": {
        "world": {
          "type": "string",
          "required": true,
          "enum": [
            "CopiMine",
            "CopiMine_nether",
            "CopiMine_the_end"
          ]
        }
      },
      "sourceKind": "site-v6-bridge-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "weather.rain",
      "name": "Дождь",
      "scope": "server",
      "permission": "server.manage",
      "params": {
        "world": {
          "type": "string",
          "required": true,
          "enum": [
            "CopiMine",
            "CopiMine_nether",
            "CopiMine_the_end"
          ]
        }
      },
      "sourceKind": "site-v6-bridge-extension",
      "requiresGameAdapter": true,
      "available": true
    },
    {
      "id": "save",
      "name": "Сохранить все миры",
      "scope": "server",
      "permission": "server.manage",
      "params": {},
      "sourceKind": "site-v6-bridge-extension",
      "requiresGameAdapter": true,
      "available": true
    }
  ],
  "excluded": [
    {
      "id": "tpto",
      "reason": "Administrator location is required"
    },
    {
      "id": "tphere",
      "reason": "Administrator location is required"
    },
    {
      "id": "swap",
      "reason": "Administrator location is required"
    },
    {
      "id": "check-start",
      "reason": "Administrator must start the in-game check"
    },
    {
      "id": "arsync",
      "reason": "Moved to EconomyCore"
    },
    {
      "id": "givebook",
      "reason": "Moved to ElectionCore"
    },
    {
      "id": "giveballot",
      "reason": "Moved to ElectionCore"
    },
    {
      "id": "inv",
      "reason": "In-game GUI; use the verified inventory snapshot"
    },
    {
      "id": "ender",
      "reason": "In-game GUI; use the verified ender snapshot"
    }
  ]
};
