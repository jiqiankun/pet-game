package com.petgame.battle;

import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.model.BattleSide;
import com.petgame.battle.model.BattleUnit;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.*;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 战斗引擎测试夹具。
 * <p>
 * 以程序化配置构建 GameConfigRegistry（不依赖 Spring 与 YAML），
 * 便于对伤害链路、行动顺序、状态结算做确定性断言。
 */
public final class BattleTestFixtures {

    private BattleTestFixtures() {
    }

    /**
     * 构建测试用配置注册中心。
     *
     * @param critRate 暴击率（0 = 测试中禁用暴击，1.0 = 必定暴击）
     */
    public static GameConfigRegistry buildRegistry(double critRate) {
        SystemRuleConfig system = new SystemRuleConfig();
        system.setCritRate(critRate);
        system.setCritMultiplierMin(1.4);
        system.setCritMultiplierMax(2.0);

        GameElementsConfig elements = new GameElementsConfig();
        elements.setElements(List.of(element("WATER"), element("FIRE"), element("METAL")));
        elements.setAdvantages(List.of(advantage("WATER", "FIRE"), advantage("FIRE", "METAL")));

        SkillsConfig skills = new SkillsConfig();
        skills.setSkills(List.of(
                skill("SKILL_HIT", "WATER", "DAMAGE", "ENEMY_SINGLE", 10, Map.of("STRENGTH", 1.0), 0, 1.0, 0),
                skill("SKILL_FIRE_HIT", "FIRE", "DAMAGE", "ENEMY_SINGLE", 10, Map.of("STRENGTH", 1.0), 0, 1.0, 0),
                skill("SKILL_WEAK", "NONE", "DAMAGE", "ENEMY_SINGLE", 0, Map.of(), 0, 1.0, 0),
                skill("SKILL_CD", "WATER", "DAMAGE", "ENEMY_SINGLE", 5, Map.of(), 2, 1.0, 0),
                skill("SKILL_CHARGE", "WATER", "DAMAGE", "ENEMY_SINGLE", 10, Map.of(), 0, 1.0, 1),
                skill("SKILL_AOE", "WATER", "DAMAGE", "ENEMY_ALL", 5, Map.of(), 0, 1.0, 0),
                skill("SKILL_BIG", "NONE", "DAMAGE", "ENEMY_SINGLE", 1000, Map.of(), 0, 1.0, 0),
                skill("SKILL_HEAL", "WATER", "HEAL", "SELF", 10, Map.of("SPIRIT", 1.0), 0, 1.0, 0),
                skill("SKILL_WAIT", "NONE", "NONE", "SELF", 0, Map.of(), 0, 1.0, 0)
        ));

        StatusesConfig statuses = new StatusesConfig();
        StatusEffectConfig burn = new StatusEffectConfig();
        burn.setId("BURN");
        burn.setName("灼烧");
        burn.setCategory("DOT");
        burn.setDefaultDuration(2);
        burn.setDotPercent(0.06);
        StatusEffectConfig silence = new StatusEffectConfig();
        silence.setId("SILENCE");
        silence.setName("沉默");
        silence.setCategory("CONTROL");
        silence.setDefaultDuration(2);
        silence.setSilence(true);
        StatusEffectConfig taunt = new StatusEffectConfig();
        taunt.setId("TAUNT");
        taunt.setName("嘲讽");
        taunt.setCategory("BUFF");
        taunt.setDefaultDuration(2);
        taunt.setTaunt(true);
        statuses.setStatuses(List.of(burn, silence, taunt));

        SkillsConfig withPassives = skills;
        PassiveSkillConfig unyielding = new PassiveSkillConfig();
        unyielding.setId("PASSIVE_UNYIELDING");
        unyielding.setName("不屈");
        unyielding.setTrigger("ON_HIT_TAKEN");
        unyielding.setEffectType("SURVIVE_LETHAL");
        unyielding.setMaxTriggerPerBattle(1);
        withPassives.setPassives(List.of(unyielding));

        try {
            GameConfigRegistry registry = new GameConfigRegistry(null, null);
            setField(registry, "systemRules", system);
            setField(registry, "elementsConfig", elements);
            setField(registry, "skillsConfig", withPassives);
            setField(registry, "statusesConfig", statuses);

            Map<String, GameElementConfig> elementIndex = new LinkedHashMap<>();
            for (GameElementConfig elem : elements.getElements()) {
                elementIndex.put(elem.getId(), elem);
            }
            setField(registry, "elementIndex", elementIndex);
            setField(registry, "advantageIndex", new java.util.HashSet<>(
                    List.of("WATER|FIRE", "FIRE|METAL")));

            Map<String, SkillConfig> skillIndex = new LinkedHashMap<>();
            for (SkillConfig skill : withPassives.getSkills()) {
                skillIndex.put(skill.getId(), skill);
            }
            setField(registry, "skillIndex", skillIndex);

            Map<String, StatusEffectConfig> statusIndex = new LinkedHashMap<>();
            for (StatusEffectConfig status : statuses.getStatuses()) {
                statusIndex.put(status.getId(), status);
            }
            setField(registry, "statusIndex", statusIndex);

            Map<String, PassiveSkillConfig> passiveIndex = new LinkedHashMap<>();
            for (PassiveSkillConfig passive : withPassives.getPassives()) {
                passiveIndex.put(passive.getId(), passive);
            }
            setField(registry, "passiveIndex", passiveIndex);
            return registry;
        } catch (Exception e) {
            throw new IllegalStateException("测试夹具构建失败", e);
        }
    }

    /** 构建战斗单位（存活、未上场）。 */
    public static BattleUnit unit(String id, String element, int maxHp, int strength,
                                  int spirit, int defense, int resistance, int speed,
                                  String... skillIds) {
        BattleUnit unit = new BattleUnit();
        unit.setUnitId(id);
        unit.setName(id);
        unit.setElement(element);
        unit.setLevel(1);
        unit.setMaxHp(maxHp);
        unit.setCurrentHp(maxHp);
        unit.setStrength(strength);
        unit.setSpirit(spirit);
        unit.setDefense(defense);
        unit.setResistance(resistance);
        unit.setSpeed(speed);
        unit.getSkillIds().addAll(List.of(skillIds));
        return unit;
    }

    /** 将单位设为上场并指定位置。 */
    public static BattleUnit active(BattleUnit unit, int position) {
        unit.setActive(true);
        unit.setPosition(position);
        return unit;
    }

    /** 构建战斗上下文。 */
    public static BattleContext context(long seed, List<BattleUnit> playerUnits, List<BattleUnit> enemyUnits) {
        BattleContext ctx = new BattleContext("BATTLE_TEST", seed);
        BattleSide player = new BattleSide("PLAYER");
        player.getUnits().addAll(playerUnits);
        BattleSide enemy = new BattleSide("ENEMY");
        enemy.getUnits().addAll(enemyUnits);
        ctx.setPlayerSide(player);
        ctx.setEnemySide(enemy);
        return ctx;
    }

    // ---- 内部辅助 ----

    private static GameElementConfig element(String id) {
        GameElementConfig elem = new GameElementConfig();
        elem.setId(id);
        elem.setName(id);
        return elem;
    }

    private static ElementAdvantageConfig advantage(String attacker, String defender) {
        ElementAdvantageConfig adv = new ElementAdvantageConfig();
        adv.setAttacker(attacker);
        adv.setDefender(defender);
        return adv;
    }

    private static SkillConfig skill(String id, String element, String effectType, String target,
                                     double baseValue, Map<String, Double> scaling,
                                     int cooldown, double accuracy, int chargeTurns) {
        SkillConfig skill = new SkillConfig();
        skill.setId(id);
        skill.setName(id);
        skill.setElement(element);
        skill.setDamageType("PHYSICAL");
        skill.setEffectType(effectType);
        skill.setTarget(target);
        skill.setBaseValue(baseValue);
        skill.getScaling().putAll(scaling);
        skill.setCooldown(cooldown);
        skill.setAccuracy(accuracy);
        skill.setChargeTurns(chargeTurns);
        return skill;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
