package me.copimine.dungeon.domain;

import java.util.Objects;

/** One templated enemy group inside a wave (spec OV4.4). */
public record EnemyGroup(MobTemplate template, int count) {

    public EnemyGroup {
        Objects.requireNonNull(template, "template");
        if (count < 1) {
            throw new IllegalArgumentException("Количество мобов должно быть >= 1.");
        }
    }

    public static EnemyGroup of(String templateId, int count) {
        MobTemplate template = MobTemplate.fromId(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Неизвестный шаблон моба: " + templateId);
        }
        return new EnemyGroup(template, count);
    }
}
