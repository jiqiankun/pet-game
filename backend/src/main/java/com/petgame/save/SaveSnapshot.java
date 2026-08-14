package com.petgame.save;

import com.petgame.achievement.entity.PlayerAchievementEntity;
import com.petgame.boss.entity.BossDefeatCountEntity;
import com.petgame.boss.entity.BossDifficultyUnlockEntity;
import com.petgame.boss.entity.BossDropUnlockEntity;
import com.petgame.boss.entity.BossEncounterSnapshotEntity;
import com.petgame.boss.entity.BossLuckEntity;
import com.petgame.boss.entity.BossManualClearEntity;
import com.petgame.boss.entity.PlayerBossChallengeEntity;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.map.entity.PlayerAmbushTriggeredEntity;
import com.petgame.map.entity.PlayerCampActivationEntity;
import com.petgame.map.entity.PlayerChestLootEntity;
import com.petgame.map.entity.PlayerGatherUsedEntity;
import com.petgame.map.entity.PlayerMapSessionEntity;
import com.petgame.map.entity.PlayerRandomEventUsedEntity;
import com.petgame.map.entity.PlayerRegionUnlockEntity;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.entity.PlayerPetSkillEntity;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.pokedex.entity.PokedexEntity;
import com.petgame.pokedex.entity.PokedexHistoryEntity;
import com.petgame.quest.entity.PlayerDialogueEntity;
import com.petgame.quest.entity.PlayerHiddenTriggerEntity;
import com.petgame.quest.entity.PlayerMapChangeEntity;
import com.petgame.quest.entity.PlayerQuestEntity;
import com.petgame.quest.entity.PlayerQuestObjectiveEntity;
import com.petgame.quest.entity.PlayerTutorialEntity;
import com.petgame.save.mapper.GameSettingEntity;
import com.petgame.statistics.entity.PlayerStatisticEntity;
import com.petgame.team.entity.PlayerTeamEntity;
import com.petgame.team.entity.PlayerTeamMemberEntity;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 存档全量快照（阶段 14）。
 * <p>
 * 保存所有玩家逻辑存档数据，用于导出 / 导入。玩家数据只保存引用（ID），
 * 配置内容一律不落快照，未来可平滑做版本迁移。
 * <p>
 * 除宠物（pet）、队伍（team）两张存在自增主键被他人引用的表需要重映射外，
 * 其余表均以 save_id 为键，导入时无需重映射。
 */
@Data
public class SaveSnapshot {

    /** 玩家主档（单条）。 */
    private PlayerEntity player;
    /** 玩家宠物。 */
    private List<PlayerPetEntity> pets = new ArrayList<>();
    /** 宠物技能（petId 需重映射）。 */
    private List<PlayerPetSkillEntity> petSkills = new ArrayList<>();
    /** 玩家队伍（id 需重映射）。 */
    private List<PlayerTeamEntity> teams = new ArrayList<>();
    /** 队伍成员（teamId/petId 需重映射）。 */
    private List<PlayerTeamMemberEntity> teamMembers = new ArrayList<>();
    /** 背包。 */
    private List<PlayerInventoryEntity> inventory = new ArrayList<>();
    /** 游戏设置。 */
    private List<GameSettingEntity> settings = new ArrayList<>();

    // ---- 地图探索 ----
    private List<PlayerRegionUnlockEntity> regionUnlocks = new ArrayList<>();
    private List<PlayerCampActivationEntity> campActivations = new ArrayList<>();
    private List<PlayerChestLootEntity> chestLoots = new ArrayList<>();
    private List<PlayerMapSessionEntity> mapSessions = new ArrayList<>();
    private List<PlayerGatherUsedEntity> gatherUsed = new ArrayList<>();
    private List<PlayerAmbushTriggeredEntity> ambushTriggered = new ArrayList<>();
    private List<PlayerRandomEventUsedEntity> randomEventUsed = new ArrayList<>();

    // ---- Boss ----
    private List<BossDefeatCountEntity> bossDefeatCounts = new ArrayList<>();
    private List<BossDifficultyUnlockEntity> bossDifficultyUnlocks = new ArrayList<>();
    private List<BossLuckEntity> bossLucks = new ArrayList<>();
    private List<BossDropUnlockEntity> bossDropUnlocks = new ArrayList<>();
    private List<BossManualClearEntity> bossManualClears = new ArrayList<>();
    private List<PlayerBossChallengeEntity> bossChallenges = new ArrayList<>();
    private List<BossEncounterSnapshotEntity> bossEncounterSnapshots = new ArrayList<>();

    // ---- 图鉴 ----
    private List<PokedexEntity> pokedex = new ArrayList<>();
    private List<PokedexHistoryEntity> pokedexHistory = new ArrayList<>();

    // ---- 任务 / 教学 / 对话 ----
    private List<PlayerQuestEntity> quests = new ArrayList<>();
    private List<PlayerQuestObjectiveEntity> questObjectives = new ArrayList<>();
    private List<PlayerDialogueEntity> dialogues = new ArrayList<>();
    private List<PlayerTutorialEntity> tutorials = new ArrayList<>();
    private List<PlayerMapChangeEntity> mapChanges = new ArrayList<>();
    private List<PlayerHiddenTriggerEntity> hiddenTriggers = new ArrayList<>();

    // ---- 成就 / 统计 ----
    private List<PlayerAchievementEntity> achievements = new ArrayList<>();
    private List<PlayerStatisticEntity> statistics = new ArrayList<>();
}