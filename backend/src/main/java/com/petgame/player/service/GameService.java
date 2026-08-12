package com.petgame.player.service;

import com.petgame.config.GameConfigRegistry;
import com.petgame.config.GameProperties;
import com.petgame.config.model.InitialPetsConfig;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
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

import java.util.UUID;

/**
 * 游戏服务。
 * <p>
 * 负责新游戏创建、Bootstrap 数据聚合、手动存档等核心流程。
 */
@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private final PlayerMapper playerMapper;
    private final PlayerPetMapper playerPetMapper;
    private final PlayerTeamMapper playerTeamMapper;
    private final PlayerTeamMemberMapper playerTeamMemberMapper;
    private final GameConfigRegistry configRegistry;
    private final GameProperties gameProperties;

    public GameService(PlayerMapper playerMapper,
                       PlayerPetMapper playerPetMapper,
                       PlayerTeamMapper playerTeamMapper,
                       PlayerTeamMemberMapper playerTeamMemberMapper,
                       GameConfigRegistry configRegistry,
                       GameProperties gameProperties) {
        this.playerMapper = playerMapper;
        this.playerPetMapper = playerPetMapper;
        this.playerTeamMapper = playerTeamMapper;
        this.playerTeamMemberMapper = playerTeamMemberMapper;
        this.configRegistry = configRegistry;
        this.gameProperties = gameProperties;
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

        // 2. 创建初始宠物
        PlayerPetEntity pet = new PlayerPetEntity();
        pet.setSaveId(saveId);
        pet.setSpeciesId(chosenPet.getSpeciesId());
        pet.setLevel(1);
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
        pet.setCurrentHp(chosenPet.getBaseHp());
        pet.setIsStarter(true);
        pet.setLocked(true);
        pet.setFavorite(true);
        pet.setBattleCount(0);
        pet.setWinCount(0);
        playerPetMapper.insert(pet);

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
     * Bootstrap 聚合接口：一次返回首页所需核心状态。
     */
    public BootstrapData getBootstrapData() {
        PlayerEntity player = getCurrentPlayer();
        if (player == null) {
            return null;
        }

        // 查询宠物列表
        var petQuery = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PlayerPetEntity>()
                .eq(PlayerPetEntity::getSaveId, player.getSaveId());
        var pets = playerPetMapper.selectList(petQuery);

        // 查询当前激活队伍
        var teamQuery = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PlayerTeamEntity>()
                .eq(PlayerTeamEntity::getSaveId, player.getSaveId())
                .eq(PlayerTeamEntity::getIsActive, true);
        var teams = playerTeamMapper.selectList(teamQuery);
        PlayerTeamEntity activeTeam = teams.isEmpty() ? null : teams.get(0);

        // 查询队伍成员
        java.util.List<PlayerTeamMemberEntity> teamMembers = java.util.List.of();
        if (activeTeam != null) {
            var memberQuery = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PlayerTeamMemberEntity>()
                    .eq(PlayerTeamMemberEntity::getTeamId, activeTeam.getId())
                    .orderByAsc(PlayerTeamMemberEntity::getPosition);
            teamMembers = playerTeamMemberMapper.selectList(memberQuery);
        }

        BootstrapData data = new BootstrapData();
        data.setPlayer(player);
        data.setPets(pets);
        data.setActiveTeam(activeTeam);
        data.setTeamMembers(teamMembers);
        data.setGameVersion(gameProperties.getVersion());
        data.setSaveVersion(gameProperties.getSaveVersion());
        data.setDeveloperMode(gameProperties.isDeveloperMode());
        return data;
    }

    /**
     * Bootstrap 聚合数据 DTO。
     */
    @lombok.Data
    public static class BootstrapData {
        private PlayerEntity player;
        private java.util.List<PlayerPetEntity> pets;
        private PlayerTeamEntity activeTeam;
        private java.util.List<PlayerTeamMemberEntity> teamMembers;
        private String gameVersion;
        private int saveVersion;
        private boolean developerMode;
    }
}
