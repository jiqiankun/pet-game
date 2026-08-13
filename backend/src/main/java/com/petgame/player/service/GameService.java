package com.petgame.player.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.GameProperties;
import com.petgame.config.model.InitialPetsConfig;
import com.petgame.config.model.ItemConfig;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.config.model.SkillConfig;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.entity.PlayerPetSkillEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.pet.mapper.PlayerPetSkillMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.team.entity.PlayerTeamEntity;
import com.petgame.team.entity.PlayerTeamMemberEntity;
import com.petgame.team.mapper.PlayerTeamMapper;
import com.petgame.team.mapper.PlayerTeamMemberMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 游戏服务。
 * <p>
 * 负责新游戏创建、Bootstrap 数据聚合、手动存档等核心流程。
 * 阶段 4 Bootstrap 扩展：注入宠物面板属性、装备技能、背包数据。
 */
@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    /** 初始宠物等级（用户裁决：5 级，避免初始状态打不过野生宠物）。 */
    public static final int STARTER_LEVEL = 5;

    private final PlayerMapper playerMapper;
    private final PlayerPetMapper playerPetMapper;
    private final PlayerPetSkillMapper playerPetSkillMapper;
    private final PlayerTeamMapper playerTeamMapper;
    private final PlayerTeamMemberMapper playerTeamMemberMapper;
    private final PlayerInventoryMapper playerInventoryMapper;
    private final GameConfigRegistry configRegistry;
    private final GameProperties gameProperties;
    private final PetGrowthService growthService;

    public GameService(PlayerMapper playerMapper,
                       PlayerPetMapper playerPetMapper,
                       PlayerPetSkillMapper playerPetSkillMapper,
                       PlayerTeamMapper playerTeamMapper,
                       PlayerTeamMemberMapper playerTeamMemberMapper,
                       PlayerInventoryMapper playerInventoryMapper,
                       GameConfigRegistry configRegistry,
                       GameProperties gameProperties,
                       PetGrowthService growthService) {
        this.playerMapper = playerMapper;
        this.playerPetMapper = playerPetMapper;
        this.playerPetSkillMapper = playerPetSkillMapper;
        this.playerTeamMapper = playerTeamMapper;
        this.playerTeamMemberMapper = playerTeamMemberMapper;
        this.playerInventoryMapper = playerInventoryMapper;
        this.configRegistry = configRegistry;
        this.gameProperties = gameProperties;
        this.growthService = growthService;
    }

    /**
     * 检查是否存在存档。
     */
    public boolean hasSave() {
        return playerMapper.selectCount(null) > 0;
    }

    /**
     * 获取当前存档的玩家。
     */
    public PlayerEntity getCurrentPlayer() {
        return playerMapper.selectOne(null);
    }

    /**
     * 创建新游戏。
     *
     * @param playerName  玩家名称
     * @param avatarId    预设头像 ID
     * @param petChoiceId 选择的初始宠物 speciesId
     */
    @Transactional
    public PlayerEntity createNewGame(String playerName, String avatarId, String petChoiceId) {
        // 校验：不能重复创建
        if (hasSave()) {
            throw new IllegalStateException("存档已存在，不能重复创建新游戏");
        }

        // 校验：选择的宠物在初始宠物配置中
        InitialPetsConfig initialPets = configRegistry.getInitialPetsConfig();
        InitialPetsConfig.InitialPetOption chosenPet = initialPets.getInitialPets().stream()
                .filter(p -> p.getSpeciesId().equals(petChoiceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("无效的初始宠物选择: " + petChoiceId));
        // 种族数据从 pets 配置读取（阶段 5 起唯一来源）
        PetSpeciesConfig species = configRegistry.getSpecies(chosenPet.getSpeciesId());
        if (species == null) {
            throw new IllegalStateException("初始宠物种族配置缺失: " + chosenPet.getSpeciesId());
        }

        String saveId = UUID.randomUUID().toString();

        // 1. 创建玩家
        PlayerEntity player = new PlayerEntity();
        player.setSaveId(saveId);
        player.setSaveVersion(gameProperties.getSaveVersion());
        player.setGameVersion(gameProperties.getVersion());
        player.setPlayerName(playerName);
        player.setAvatarId(avatarId != null ? avatarId : "AVATAR_DEFAULT");
        player.setGold(initialPets.getInitialGold());
        player.setExpPool(initialPets.getInitialExpPool());
        player.setCurrentMapId(initialPets.getInitialMapId());
        player.setPlayTimeSeconds(0L);
        playerMapper.insert(player);

        // 2. 创建初始宠物（用户裁决：初始等级 5 级，避免初始状态打不过野生宠物）
        PlayerPetEntity pet = new PlayerPetEntity();
        pet.setSaveId(saveId);
        pet.setSpeciesId(chosenPet.getSpeciesId());
        pet.setLevel(STARTER_LEVEL);
        pet.setCapturedLevel(STARTER_LEVEL);
        // 初始宠物不生成个体浮动，base_offset 全部使用默认 0
        pet.setBaseHpOffset(0);
        pet.setBaseStrengthOffset(0);
        pet.setBaseSpiritOffset(0);
        pet.setBaseDefenseOffset(0);
        pet.setBaseResistanceOffset(0);
        pet.setBaseSpeedOffset(0);
        pet.setHpAptitude(chosenPet.getAptitudeHp());
        pet.setStrengthAptitude(chosenPet.getAptitudeStrength());
        pet.setSpiritAptitude(chosenPet.getAptitudeSpirit());
        pet.setDefenseAptitude(chosenPet.getAptitudeDefense());
        pet.setResistanceAptitude(chosenPet.getAptitudeResistance());
        pet.setSpeedAptitude(chosenPet.getAptitudeSpeed());
        pet.setFreePointHp(0);
        pet.setFreePointStrength(0);
        pet.setFreePointSpirit(0);
        pet.setFreePointDefense(0);
        pet.setFreePointResistance(0);
        pet.setFreePointSpeed(0);
        pet.setCurrentHp(growthService.computePanelStats(pet, species).getMaxHp());
        pet.setIsStarter(true);
        pet.setLocked(true);
        pet.setFavorite(true);
        pet.setBattleCount(0);
        pet.setWinCount(0);
        playerPetMapper.insert(pet);

        // 2.1 学习初始技能（unlockLevel <= 初始等级的种族技能），按配置槽位装备
        int equippedSlots = 0;
        for (PetSpeciesConfig.SpeciesSkillSlot skillSlot : species.getSkills()) {
            if (skillSlot.getUnlockLevel() > STARTER_LEVEL) {
                continue;
            }
            PlayerPetSkillEntity petSkill = new PlayerPetSkillEntity();
            petSkill.setPetId(pet.getId());
            petSkill.setSkillId(skillSlot.getSkillId());
            petSkill.setSourceType("LEVEL_UP");
            petSkill.setSlot(skillSlot.getSlot());
            playerPetSkillMapper.insert(petSkill);
            if (skillSlot.getSlot() != null) {
                equippedSlots = Math.max(equippedSlots, skillSlot.getSlot());
            }
        }

        // 2.1.1 新游戏赠送技能（REV-014：留生一击等，来源记 SKILL_BOOK；
        //        阶段 5 临时获取途径，商店/教学赠书属阶段 9/10；槽位未满时自动装备）
        if (initialPets.getGrantSkills() != null) {
            for (String grantSkillId : initialPets.getGrantSkills()) {
                if (configRegistry.getSkill(grantSkillId) == null) {
                    continue;
                }
                PlayerPetSkillEntity petSkill = new PlayerPetSkillEntity();
                petSkill.setPetId(pet.getId());
                petSkill.setSkillId(grantSkillId);
                petSkill.setSourceType("SKILL_BOOK");
                petSkill.setSlot(equippedSlots < 4 ? equippedSlots + 1 : null);
                if (equippedSlots < 4) {
                    equippedSlots++;
                }
                playerPetSkillMapper.insert(petSkill);
            }
        }

        // 2.2 发放初始道具（阶段 5：新游戏赠送三档捕捉球）
        if (initialPets.getInitialItems() != null) {
            for (InitialPetsConfig.InitialItemEntry entry : initialPets.getInitialItems()) {
                PlayerInventoryEntity inv = new PlayerInventoryEntity();
                inv.setSaveId(saveId);
                inv.setItemId(entry.getItemId());
                inv.setQuantity(entry.getQuantity());
                playerInventoryMapper.insert(inv);
            }
        }

        // 3. 创建默认队伍（预设 1，激活）
        PlayerTeamEntity team = new PlayerTeamEntity();
        team.setSaveId(saveId);
        team.setName("队伍 1");
        team.setSlot(1);
        team.setIsActive(true);
        playerTeamMapper.insert(team);

        // 4. 将初始宠物放入队伍首发位置 1
        PlayerTeamMemberEntity member = new PlayerTeamMemberEntity();
        member.setTeamId(team.getId());
        member.setPetId(pet.getId());
        member.setPosition(1);
        playerTeamMemberMapper.insert(member);

        log.info("新游戏创建完成：玩家={}, saveId={}, 初始宠物={}", playerName, saveId, petChoiceId);
        return player;
    }

    /**
     * Bootstrap 聚合接口：一次返回首页所需核心状态（阶段 4 扩展）。
     * <p>
     * 聚合：玩家、宠物列表（含面板属性与装备技能摘要）、激活队伍、队伍成员、背包。
     */
    public BootstrapData getBootstrapData() {
        PlayerEntity player = getCurrentPlayer();
        if (player == null) {
            return null;
        }

        // 查询宠物列表
        var petQuery = new LambdaQueryWrapper<PlayerPetEntity>()
                .eq(PlayerPetEntity::getSaveId, player.getSaveId());
        var pets = playerPetMapper.selectList(petQuery);

        // 计算每只宠物的面板属性与装备技能摘要
        List<PetSummary> petSummaries = new ArrayList<>();
        for (PlayerPetEntity pet : pets) {
            petSummaries.add(buildPetSummary(pet));
        }

        // 查询当前激活队伍
        var teamQuery = new LambdaQueryWrapper<PlayerTeamEntity>()
                .eq(PlayerTeamEntity::getSaveId, player.getSaveId())
                .eq(PlayerTeamEntity::getIsActive, true);
        var teams = playerTeamMapper.selectList(teamQuery);
        PlayerTeamEntity activeTeam = teams.isEmpty() ? null : teams.get(0);

        // 查询队伍成员
        java.util.List<PlayerTeamMemberEntity> teamMembers = java.util.List.of();
        if (activeTeam != null) {
            var memberQuery = new LambdaQueryWrapper<PlayerTeamMemberEntity>()
                    .eq(PlayerTeamMemberEntity::getTeamId, activeTeam.getId())
                    .orderByAsc(PlayerTeamMemberEntity::getPosition);
            teamMembers = playerTeamMemberMapper.selectList(memberQuery);
        }

        // 查询背包
        List<PlayerInventoryEntity> invRecords = playerInventoryMapper.selectList(
                new LambdaQueryWrapper<PlayerInventoryEntity>()
                        .eq(PlayerInventoryEntity::getSaveId, player.getSaveId()));
        List<BootstrapData.InventoryItemView> inventory = new ArrayList<>();
        for (PlayerInventoryEntity rec : invRecords) {
            ItemConfig item = configRegistry.getItem(rec.getItemId());
            if (item == null) {
                continue;
            }
            BootstrapData.InventoryItemView iv = new BootstrapData.InventoryItemView();
            iv.setItemId(item.getId());
            iv.setName(item.getName());
            iv.setDescription(item.getDescription());
            iv.setCategory(item.getCategory());
            iv.setItemType(item.getItemType());
            iv.setValue(item.getValue());
            iv.setUsableOutsideBattle(item.isUsableOutsideBattle());
            iv.setUsableInBattle(item.isUsableInBattle());
            iv.setDiscardable(item.isDiscardable());
            iv.setQuantity(rec.getQuantity());
            inventory.add(iv);
        }
        inventory.sort(Comparator.comparing(BootstrapData.InventoryItemView::getCategory)
                .thenComparing(BootstrapData.InventoryItemView::getName));

        BootstrapData data = new BootstrapData();
        data.setPlayer(player);
        data.setPets(pets);
        data.setPetSummaries(petSummaries);
        data.setActiveTeam(activeTeam);
        data.setTeamMembers(teamMembers);
        data.setInventory(inventory);
        data.setGameVersion(gameProperties.getVersion());
        data.setSaveVersion(gameProperties.getSaveVersion());
        data.setDeveloperMode(gameProperties.isDeveloperMode());
        return data;
    }

    /** 构建宠物摘要：种族信息 + 面板属性 + 装备技能。 */
    private PetSummary buildPetSummary(PlayerPetEntity pet) {
        PetSpeciesConfig species = configRegistry.getSpecies(pet.getSpeciesId());
        PetSummary summary = new PetSummary();
        summary.setPet(pet);
        if (species != null) {
            summary.setSpeciesName(species.getName());
            summary.setElement(species.getElement());
            summary.setRarity(species.getRarity());
            summary.setPanelStats(growthService.computePanelStats(pet, species));
        }

        // 查询已装备技能（slot 不为 null）
        List<PlayerPetSkillEntity> skills = playerPetSkillMapper.selectList(
                new LambdaQueryWrapper<PlayerPetSkillEntity>()
                        .eq(PlayerPetSkillEntity::getPetId, pet.getId())
                        .isNotNull(PlayerPetSkillEntity::getSlot)
                        .orderByAsc(PlayerPetSkillEntity::getSlot));
        for (PlayerPetSkillEntity s : skills) {
            SkillConfig skill = configRegistry.getSkill(s.getSkillId());
            if (skill == null) {
                continue;
            }
            PetSummary.EquippedSkillView view = new PetSummary.EquippedSkillView();
            view.setSkillId(skill.getId());
            view.setName(skill.getName());
            view.setSlot(s.getSlot());
            summary.getEquippedSkills().add(view);
        }
        return summary;
    }

    /**
     * Bootstrap 聚合数据 DTO。
     */
    @lombok.Data
    public static class BootstrapData {
        private PlayerEntity player;
        private java.util.List<PlayerPetEntity> pets;
        /** 宠物摘要列表（含面板属性与装备技能），阶段 4 新增。 */
        private java.util.List<PetSummary> petSummaries;
        private PlayerTeamEntity activeTeam;
        private java.util.List<PlayerTeamMemberEntity> teamMembers;
        /** 背包道具列表（含配置摘要与数量），阶段 4 新增。 */
        private java.util.List<InventoryItemView> inventory;
        private String gameVersion;
        private int saveVersion;
        private boolean developerMode;

        /** 背包道具视图。 */
        @lombok.Data
        public static class InventoryItemView {
            private String itemId;
            private String name;
            private String description;
            private String category;
            private String itemType;
            private double value;
            private boolean usableOutsideBattle;
            private boolean usableInBattle;
            private boolean discardable;
            private int quantity;
        }
    }
}
