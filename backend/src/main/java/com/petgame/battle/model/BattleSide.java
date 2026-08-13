package com.petgame.battle.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 战斗一方（队伍）。
 * <p>
 * 最多携带 6 只（standardBattleSlots 上场，其余候补）。
 * 全部宠物失去战斗能力即战斗失败。
 */
@Data
public class BattleSide {

    /** 阵营标识：PLAYER / ENEMY。 */
    private String side;

    /** 全部单位（含候补），按队伍位置顺序。 */
    private List<BattleUnit> units = new ArrayList<>();

    public BattleSide(String side) {
        this.side = side;
    }

    /** 存活且在场的单位。 */
    public List<BattleUnit> getActiveAliveUnits() {
        List<BattleUnit> result = new ArrayList<>();
        for (BattleUnit unit : units) {
            if (unit.isActive() && unit.isAlive()) {
                result.add(unit);
            }
        }
        return result;
    }

    /** 存活的候补单位（可换宠/补位）。 */
    public List<BattleUnit> getBenchAliveUnits() {
        List<BattleUnit> result = new ArrayList<>();
        for (BattleUnit unit : units) {
            if (!unit.isActive() && unit.isAlive()) {
                result.add(unit);
            }
        }
        return result;
    }

    /** 全部单位是否失去战斗能力。 */
    public boolean isAllDefeated() {
        return units.stream().noneMatch(BattleUnit::isAlive);
    }

    /** 全部单位是否均已退出战斗（倒下或被捕捉，阶段 5 捕捉）。 */
    public boolean isAllGone() {
        return units.stream().noneMatch(u -> u.isAlive() && !u.isCaptured());
    }

    /** 按战斗内 ID 查找单位（含候补）。 */
    public BattleUnit findUnit(String unitId) {
        for (BattleUnit unit : units) {
            if (unit.getUnitId().equals(unitId)) {
                return unit;
            }
        }
        return null;
    }

    /** 查找指定位置的在场单位。 */
    public BattleUnit findActiveAtPosition(int position) {
        for (BattleUnit unit : units) {
            if (unit.isActive() && unit.getPosition() == position) {
                return unit;
            }
        }
        return null;
    }
}
