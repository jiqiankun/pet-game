package com.petgame.pet.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.common.BusinessException;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.config.model.SkillConfig;
import com.petgame.config.model.SystemRuleConfig;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.domain.PetPanelStats;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.entity.PlayerPetSkillEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.pet.mapper.PlayerPetSkillMapper;
import com.petgame.pokedex.service.PokedexService;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 宠物服务（阶段 4）。
 * <p>
 * 编排 {@link PetGrowthService} 与数据库持久化，提供宠物详情查询、升级、加点、洗点、技能装配能力。
 * <p>
 * 关键约束：
 * <ul>
 *   <li>所有战斗经验统一进入玩家公共经验池（{@link PlayerEntity#getExpPool()}），不直接发给宠物。</li>
 *   <li>升级消耗经验池；经验不足时阻止升级。</li>
 *   <li>升级、加点、洗点不允许在战斗过程中操作（前端进入战斗时禁用入口）。</li>
 *   <li>升级或生命加点只增加最大 HP，不恢复当前 HP（需求 §17 核心规则）。</li>
 *   <li>洗点第一阶段免费（需求 §21），返还全部已分配自由点数。</li>
 *   <li>技能装备槽位 1~4，最多装备 4 个主动技能参战（需求 §24）。</li>
 * </ul>
 */
@Service
public class PetService {

    private static final Logger log = LoggerFactory.getLogger(PetService.class);

    private final PlayerMapper playerMapper;
    private final PlayerPetMapper playerPetMapper;
    private final PlayerPetSkillMapper playerPetSkillMapper;
    private final PetGrowthService growthService;
    private final GameConfigRegistry registry;
    private final PokedexService pokedexService;

    public PetService(PlayerMapper playerMapper,
                      PlayerPetMapper playerPetMapper,
                      PlayerPetSkillMapper playerPetSkillMapper,
                      PetGrowthService growthService,
                      GameConfigRegistry registry,
                      PokedexService pokedexService) {
        this.playerMapper = playerMapper;
        this.playerPetMapper = playerPetMapper;
        this.playerPetSkillMapper = playerPetSkillMapper;
        this.growthService = growthService;
        this.registry = registry;
        this.pokedexService = pokedexService;
    }

    // ==================== 宠物详情 ====================

    /**
     * 查询宠物详情：基础信息 + 面板属性（含分解）+ 已学技能 + 可学技能 + 经验池 + 剩余点数。
     */
    public PetDetail getPetDetail(Long petId) {
        PlayerPetEntity pet = requirePet(petId);
        PetSpeciesConfig species = requireSpecies(pet.getSpeciesId());
        PlayerEntity player = requirePlayer();

        PetDetail detail = new PetDetail();
        detail.setPet(pet);
        detail.setSpecies(toSpeciesView(species, pet));
        detail.setPanelStats(growthService.computePanelStats(pet, species));
        detail.setLearnedSkills(loadLearnedSkills(pet.getId(), species));
        detail.setAvailableSkills(loadAvailableSkills(species, pet.getLevel()));
        detail.setPassives(loadPassives(species, pet.getLevel()));
        detail.setTotalInnateActiveSkills(species.getSkills() != null ? species.getSkills().size() : 0);
        detail.setExpPool(player.getExpPool());
        detail.setFreePointsAvailable(growthService.freePointsAvailable(pet, species));
        detail.setAllocatedFreePoints(growthService.consumedFreePoints(pet));
        detail.setExpToNextLevel(growthService.expToNextLevel(pet.getLevel()));
        return detail;
    }

    // ==================== 升级 ====================

    /**
     * 升级预览（需求 §17：所需经验、升级后等级、属性变化、获得点数、即将解锁技能）。
     *
     * @param petId       宠物 ID
     * @param targetLevel 目标等级（须 > 当前等级且 <= levelCap）
     */
    public PetGrowthService.LevelUpPreview previewLevelUp(Long petId, int targetLevel) {
        PlayerPetEntity pet = requirePet(petId);
        PetSpeciesConfig species = requireSpecies(pet.getSpeciesId());
        PlayerEntity player = requirePlayer();

        PetGrowthService.LevelUpPreview preview;
        try {
            preview = growthService.previewLevelUp(pet, species, targetLevel);
        } catch (IllegalArgumentException e) {
            // 领域校验失败统一转为业务错误（INVALID_LEVEL_UP），避免 500
            throw new BusinessException("INVALID_LEVEL_UP", e.getMessage());
        }
        preview.setExpPoolAvailable(player.getExpPool());
        preview.setExpPoolSufficient(player.getExpPool() >= preview.getExpRequired());
        return preview;
    }

    /**
     * 执行升级（需求 §17：五种投入方式）。
     * <p>
     * 升级消耗玩家公共经验池；经验不足时按 EXP_NOT_ENOUGH 拒绝。
     * 升级只增加最大 HP，不恢复当前 HP；新解锁技能自动学习但默认不装备。
     * 新等级若达稀有度每 10 级门槛，自动获得额外自由点数。
     *
     * @param petId           宠物 ID
     * @param mode            升级模式（ONE/FIVE/TO_LEVEL/TO_CAP/CUSTOM_EXP）
     * @param targetLevelParam TO_LEVEL 模式目标等级
     * @param exp             CUSTOM_EXP 模式投入经验量
     */
    @Transactional
    public PetDetail levelUp(Long petId, String mode, Integer targetLevelParam, Integer exp) {
        if (mode == null) {
            throw new BusinessException("INVALID_LEVEL_UP", "升级请求缺少 mode");
        }
        PlayerPetEntity pet = requirePet(petId);
        PetSpeciesConfig species = requireSpecies(pet.getSpeciesId());
        PlayerEntity player = requirePlayer();
        SystemRuleConfig rules = registry.getSystemRules();

        int currentLevel = pet.getLevel();
        int cap = rules.getLevelCap();
        if (currentLevel >= cap) {
            throw new BusinessException("LEVEL_CAP_REACHED",
                    "已达等级上限 " + cap + "，无法继续升级");
        }

        int targetLevel = resolveTargetLevel(currentLevel, cap, mode, targetLevelParam, exp);
        if (targetLevel <= currentLevel) {
            throw new BusinessException("INVALID_LEVEL_UP",
                    "目标等级必须大于当前等级 " + currentLevel);
        }

        int expRequired = growthService.totalExpToReach(currentLevel, targetLevel);
        if (expRequired <= 0) {
            throw new BusinessException("INVALID_LEVEL_UP", "升级所需经验为 0");
        }
        if (player.getExpPool() < expRequired) {
            throw new BusinessException("EXP_NOT_ENOUGH",
                    "经验池不足：需要 " + expRequired + "，当前 " + player.getExpPool());
        }

        // 1. 扣减经验池
        player.setExpPool(player.getExpPool() - expRequired);
        playerMapper.updateById(player);

        // 2. 升级宠物
        pet.setLevel(targetLevel);
        // HP 跨战斗保留：升级只增加最大 HP，不恢复当前 HP（需求 §17 核心规则）
        // currentHp 保持不变（若已倒下 currentHp=0，升级后仍为 0，需要恢复道具）
        playerPetMapper.updateById(pet);

        // 3. 自动学习新解锁技能（REV-013：主动写表、被动不写表随等级自动生效；
        //    REV-011：槽位未满 4 个自动装备，已满 4/4 仅入库不覆盖并提示）
        List<PetGrowthService.UnlockedSkill> unlocked =
                growthService.skillsUnlockedBetween(species, currentLevel, targetLevel);
        int equippedCount = Math.toIntExact(playerPetSkillMapper.selectCount(
                new LambdaQueryWrapper<PlayerPetSkillEntity>()
                        .eq(PlayerPetSkillEntity::getPetId, pet.getId())
                        .isNotNull(PlayerPetSkillEntity::getSlot)));
        List<String> newActiveNames = new ArrayList<>();
        boolean overflow = false;
        for (PetGrowthService.UnlockedSkill skill : unlocked) {
            if (!"ACTIVE".equals(skill.getSkillType())) {
                continue; // 被动解锁后自动生效，无需玩家额外配置
            }
            // 避免重复插入
            Long exists = playerPetSkillMapper.selectCount(
                    new LambdaQueryWrapper<PlayerPetSkillEntity>()
                            .eq(PlayerPetSkillEntity::getPetId, pet.getId())
                            .eq(PlayerPetSkillEntity::getSkillId, skill.getSkillId()));
            if (exists != null && exists > 0) {
                continue;
            }
            PlayerPetSkillEntity petSkill = new PlayerPetSkillEntity();
            petSkill.setPetId(pet.getId());
            petSkill.setSkillId(skill.getSkillId());
            petSkill.setSourceType("LEVEL_UP");
            if (equippedCount < 4) {
                petSkill.setSlot(equippedCount + 1);
                equippedCount++;
            } else {
                petSkill.setSlot(null);
                overflow = true;
            }
            playerPetSkillMapper.insert(petSkill);
            newActiveNames.add(skill.getName());
        }

        log.info("宠物升级：petId={} {}→{}，消耗经验 {}，新解锁技能 {} 个",
                petId, currentLevel, targetLevel, expRequired, unlocked.size());

        // 阶段 8：图鉴技能解锁研究值
        for (PetGrowthService.UnlockedSkill skill : unlocked) {
            try {
                pokedexService.recordSkillUnlock(player.getSaveId(), species.getId(), skill.getSkillId());
            } catch (Exception e) {
                log.warn("图鉴技能解锁记录失败（不阻断升级）：species={}, skill={}",
                        species.getId(), skill.getSkillId(), e);
            }
        }

        PetDetail detail = getPetDetail(petId);
        detail.setNewlyLearnedSkillNames(newActiveNames);
        detail.setSkillEquipOverflow(overflow);
        return detail;
    }

    /**
     * 根据升级模式解析目标等级。
     * <ul>
     *   <li>ONE：当前 + 1</li>
     *   <li>FIVE：当前 + 5（封顶 levelCap）</li>
     *   <li>TO_LEVEL：升到指定等级</li>
     *   <li>TO_CAP：升到等级上限</li>
     *   <li>CUSTOM_EXP：按投入经验尽可能升级</li>
     * </ul>
     */
    private int resolveTargetLevel(int currentLevel, int cap, String mode,
                                    Integer targetLevelParam, Integer exp) {
        switch (mode) {
            case "ONE":
                return Math.min(currentLevel + 1, cap);
            case "FIVE":
                return Math.min(currentLevel + 5, cap);
            case "TO_LEVEL":
                if (targetLevelParam == null) {
                    throw new BusinessException("INVALID_LEVEL_UP", "TO_LEVEL 模式必须提供 targetLevel");
                }
                if (targetLevelParam <= currentLevel || targetLevelParam > cap) {
                    throw new BusinessException("INVALID_LEVEL_UP",
                            "targetLevel 须在 (" + currentLevel + ", " + cap + "] 范围内");
                }
                return targetLevelParam;
            case "TO_CAP":
                return cap;
            case "CUSTOM_EXP":
                if (exp == null || exp <= 0) {
                    throw new BusinessException("INVALID_LEVEL_UP", "CUSTOM_EXP 模式必须提供正数 exp");
                }
                return resolveLevelByExp(currentLevel, cap, exp);
            default:
                throw new BusinessException("INVALID_LEVEL_UP", "未知升级模式: " + mode);
        }
    }

    /** 按 CUSTOM_EXP 投入经验尽可能升级：从 currentLevel 起逐级扣减，直到剩余经验不足或达上限。 */
    private int resolveLevelByExp(int currentLevel, int cap, int exp) {
        int level = currentLevel;
        int remaining = exp;
        while (level < cap) {
            int need = growthService.expToNextLevel(level);
            if (need <= 0 || remaining < need) {
                break;
            }
            remaining -= need;
            level++;
        }
        return level;
    }

    // ==================== 自由属性点 ====================

    /**
     * 分配自由属性点（需求 §20 转换表）。
     * <p>
     * 剩余可分配点数 = 已获得 - 已分配；超出时按 POINTS_NOT_ENOUGH 拒绝。
     * 速度维度消耗 speedPointCost（默认 2），其他维度消耗 statPointCost 或 hpPointCost。
     *
     * @param statKey 维度键（PetPanelStats.HP/STRENGTH/SPIRIT/DEFENSE/RESISTANCE/SPEED）
     * @param points  投入「次数」（按维度转换系数转换为最终属性）
     */
    @Transactional
    public PetDetail allocatePoints(Long petId, String statKey, int points) {
        if (statKey == null || statKey.isBlank()) {
            throw new BusinessException("INVALID_STAT", "属性维度不能为空");
        }
        if (points <= 0) {
            throw new BusinessException("INVALID_POINTS", "分配点数必须为正数");
        }
        PlayerPetEntity pet = requirePet(petId);
        PetSpeciesConfig species = requireSpecies(pet.getSpeciesId());

        int available = growthService.freePointsAvailable(pet, species);
        int cost = growthService.pointCostForStat(statKey, points);
        if (cost > available) {
            throw new BusinessException("POINTS_NOT_ENOUGH",
                    "自由点数不足：需要 " + cost + "，剩余 " + available);
        }

        switch (statKey) {
            case PetPanelStats.HP:
                pet.setFreePointHp(nz(pet.getFreePointHp()) + points);
                break;
            case PetPanelStats.STRENGTH:
                pet.setFreePointStrength(nz(pet.getFreePointStrength()) + points);
                break;
            case PetPanelStats.SPIRIT:
                pet.setFreePointSpirit(nz(pet.getFreePointSpirit()) + points);
                break;
            case PetPanelStats.DEFENSE:
                pet.setFreePointDefense(nz(pet.getFreePointDefense()) + points);
                break;
            case PetPanelStats.RESISTANCE:
                pet.setFreePointResistance(nz(pet.getFreePointResistance()) + points);
                break;
            case PetPanelStats.SPEED:
                pet.setFreePointSpeed(nz(pet.getFreePointSpeed()) + points);
                break;
            default:
                throw new BusinessException("INVALID_STAT", "未知属性维度: " + statKey);
        }
        playerPetMapper.updateById(pet);

        log.info("宠物加点：petId={}, stat={}, points={}, cost={}", petId, statKey, points, cost);
        return getPetDetail(petId);
    }

    /**
     * 洗点：第一阶段免费（需求 §21），全部已分配自由点数返还为可用。
     */
    @Transactional
    public PetDetail resetPoints(Long petId) {
        PlayerPetEntity pet = requirePet(petId);
        int refunded = growthService.consumedFreePoints(pet);

        pet.setFreePointHp(0);
        pet.setFreePointStrength(0);
        pet.setFreePointSpirit(0);
        pet.setFreePointDefense(0);
        pet.setFreePointResistance(0);
        pet.setFreePointSpeed(0);
        // currentHp 不变；最大 HP 通过面板公式实时计算
        playerPetMapper.updateById(pet);

        log.info("宠物洗点：petId={}, 返还自由点数 {}", petId, refunded);
        return getPetDetail(petId);
    }

    // ==================== 技能装配 ====================

    /**
     * 装备技能到指定槽位（需求 §24）。
     * <p>
     * 校验：技能已学习、槽位 1~4、槽位未占用（占用时先卸下原技能）。
     * 被动技能不进表，无需装配。
     */
    @Transactional
    public PetDetail equipSkill(Long petId, String skillId, int slot) {
        if (skillId == null || skillId.isBlank()) {
            throw new BusinessException("INVALID_SKILL", "技能 ID 不能为空");
        }
        if (slot < 1 || slot > 4) {
            throw new BusinessException("INVALID_SLOT", "槽位必须在 1~4 范围内");
        }
        PlayerPetEntity pet = requirePet(petId);

        PlayerPetSkillEntity learned = playerPetSkillMapper.selectOne(
                new LambdaQueryWrapper<PlayerPetSkillEntity>()
                        .eq(PlayerPetSkillEntity::getPetId, pet.getId())
                        .eq(PlayerPetSkillEntity::getSkillId, skillId));
        if (learned == null) {
            throw new BusinessException("SKILL_NOT_LEARNED", "宠物未学习该技能: " + skillId);
        }

        // 若目标槽位已有技能，先卸下
        PlayerPetSkillEntity occupant = playerPetSkillMapper.selectOne(
                new LambdaQueryWrapper<PlayerPetSkillEntity>()
                        .eq(PlayerPetSkillEntity::getPetId, pet.getId())
                        .eq(PlayerPetSkillEntity::getSlot, slot));
        if (occupant != null && !occupant.getId().equals(learned.getId())) {
            occupant.setSlot(null);
            playerPetSkillMapper.updateById(occupant);
        }

        learned.setSlot(slot);
        playerPetSkillMapper.updateById(learned);

        log.info("技能装配：petId={}, skillId={}, slot={}", petId, skillId, slot);
        return getPetDetail(petId);
    }

    /**
     * 卸下指定槽位的技能（slot 置空，技能仍保留为已学习状态）。
     */
    @Transactional
    public PetDetail unequipSkill(Long petId, int slot) {
        if (slot < 1 || slot > 4) {
            throw new BusinessException("INVALID_SLOT", "槽位必须在 1~4 范围内");
        }
        PlayerPetEntity pet = requirePet(petId);

        PlayerPetSkillEntity equipped = playerPetSkillMapper.selectOne(
                new LambdaQueryWrapper<PlayerPetSkillEntity>()
                        .eq(PlayerPetSkillEntity::getPetId, pet.getId())
                        .eq(PlayerPetSkillEntity::getSlot, slot));
        if (equipped == null) {
            throw new BusinessException("SLOT_EMPTY", "槽位 " + slot + " 未装备技能");
        }
        equipped.setSlot(null);
        playerPetSkillMapper.updateById(equipped);

        log.info("技能卸下：petId={}, slot={}", petId, slot);
        return getPetDetail(petId);
    }

    // ==================== 内部工具 ====================

    private PlayerPetEntity requirePet(Long petId) {
        if (petId == null) {
            throw new BusinessException("INVALID_PET", "宠物 ID 不能为空");
        }
        PlayerPetEntity pet = playerPetMapper.selectById(petId);
        if (pet == null) {
            throw new BusinessException("PET_NOT_FOUND", "宠物不存在: " + petId);
        }
        return pet;
    }

    private PetSpeciesConfig requireSpecies(String speciesId) {
        PetSpeciesConfig species = registry.getSpecies(speciesId);
        if (species == null) {
            throw new BusinessException("SPECIES_CONFIG_MISSING", "种族配置缺失: " + speciesId);
        }
        return species;
    }

    private PlayerEntity requirePlayer() {
        PlayerEntity player = playerMapper.selectOne(null);
        if (player == null) {
            throw new BusinessException("NO_SAVE", "不存在存档，请先创建新游戏");
        }
        return player;
    }

    /** 加载宠物已学技能视图（含槽位、技能类型与技能配置摘要）。 */
    private List<PetDetail.LearnedSkillView> loadLearnedSkills(Long petId,
                                                                  PetSpeciesConfig species) {
        List<PlayerPetSkillEntity> records = playerPetSkillMapper.selectList(
                new LambdaQueryWrapper<PlayerPetSkillEntity>()
                        .eq(PlayerPetSkillEntity::getPetId, petId));
        List<PetDetail.LearnedSkillView> views = new ArrayList<>();
        for (PlayerPetSkillEntity rec : records) {
            SkillConfig skill = registry.getSkill(rec.getSkillId());
            if (skill == null) {
                continue;
            }
            PetDetail.LearnedSkillView view = new PetDetail.LearnedSkillView();
            view.setSkillId(skill.getId());
            view.setName(skill.getName());
            view.setElement(skill.getElement());
            view.setDamageType(skill.getDamageType());
            view.setEffectType(skill.getEffectType());
            view.setCooldown(skill.getCooldown());
            view.setSlot(rec.getSlot());
            view.setSourceType(rec.getSourceType());
            view.setSkillType(skill.getSkillType());
            view.setSignature(isSignatureSkill(species, rec.getSkillId()));
            views.add(view);
        }
        views.sort(Comparator.nullsLast(
                Comparator.comparing(PetDetail.LearnedSkillView::getSlot)));
        return views;
    }

    /** 判定技能是否为种族配置中的特色/专属技能（REV-016）。 */
    private boolean isSignatureSkill(PetSpeciesConfig species, String skillId) {
        if (species.getSkills() == null) {
            return false;
        }
        return species.getSkills().stream()
                .anyMatch(s -> skillId.equals(s.getSkillId()) && s.isSignature());
    }

    /** 加载被动技能视图（REV-016：含解锁状态，全部自动生效）。 */
    private List<PetDetail.PassiveSkillView> loadPassives(PetSpeciesConfig species, int level) {
        List<PetDetail.PassiveSkillView> views = new ArrayList<>();
        for (PetSpeciesConfig.SpeciesPassiveSlot slot : species.getPassives()) {
            com.petgame.config.model.PassiveSkillConfig passive = registry.getPassive(slot.getPassiveId());
            if (passive == null) {
                continue;
            }
            PetDetail.PassiveSkillView view = new PetDetail.PassiveSkillView();
            view.setPassiveId(passive.getId());
            view.setName(passive.getName());
            view.setUnlockLevel(slot.getUnlockLevel());
            view.setUnlocked(slot.getUnlockLevel() <= level);
            view.setSource("INNATE");
            view.setSignature(slot.isSignature());
            views.add(view);
        }
        views.sort(Comparator.comparingInt(PetDetail.PassiveSkillView::getUnlockLevel));
        return views;
    }

    /** 加载尚未学习但未来可解锁的种族技能（unlockLevel > currentLevel），按解锁等级升序。 */
    private List<PetDetail.AvailableSkillView> loadAvailableSkills(
            PetSpeciesConfig species, int currentLevel) {
        List<PetDetail.AvailableSkillView> list = new ArrayList<>();
        if (species.getSkills() == null) {
            return list;
        }
        for (PetSpeciesConfig.SpeciesSkillSlot slot : species.getSkills()) {
            if (slot.getUnlockLevel() <= currentLevel) {
                continue;
            }
            SkillConfig skill = registry.getSkill(slot.getSkillId());
            if (skill == null) {
                continue;
            }
            PetDetail.AvailableSkillView view = new PetDetail.AvailableSkillView();
            view.setSkillId(skill.getId());
            view.setName(skill.getName());
            view.setElement(skill.getElement());
            view.setUnlockLevel(slot.getUnlockLevel());
            list.add(view);
        }
        list.sort(Comparator.comparingInt(PetDetail.AvailableSkillView::getUnlockLevel));
        return list;
    }

    private PetDetail.SpeciesView toSpeciesView(PetSpeciesConfig species, PlayerPetEntity pet) {
        PetDetail.SpeciesView view = new PetDetail.SpeciesView();
        view.setSpeciesId(species.getId());
        view.setName(species.getName());
        view.setElement(species.getElement());
        view.setRarity(species.getRarity());
        view.setDescription(species.getDescription());
        view.setBaseHp(species.getBaseHp());
        view.setBaseStrength(species.getBaseStrength());
        view.setBaseSpirit(species.getBaseSpirit());
        view.setBaseDefense(species.getBaseDefense());
        view.setBaseResistance(species.getBaseResistance());
        view.setBaseSpeed(species.getBaseSpeed());
        // 资质是个体属性（宠物存档字段），阶段 5 起从实体读取而非种族配置
        view.setAptitudeHp(nz(pet.getHpAptitude()));
        view.setAptitudeStrength(nz(pet.getStrengthAptitude()));
        view.setAptitudeSpirit(nz(pet.getSpiritAptitude()));
        view.setAptitudeDefense(nz(pet.getDefenseAptitude()));
        view.setAptitudeResistance(nz(pet.getResistanceAptitude()));
        view.setAptitudeSpeed(nz(pet.getSpeedAptitude()));
        return view;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
