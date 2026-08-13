package com.petgame.pet;

import com.petgame.common.BusinessException;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.InitialPetsConfig;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.entity.PlayerPetSkillEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.pet.mapper.PlayerPetSkillMapper;
import com.petgame.pet.service.PetDetail;
import com.petgame.pet.service.PetService;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.petgame.pet.PetGrowthTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * PetService 单元测试（阶段 4 验收标准）。
 * <p>
 * 覆盖：升级（五种模式 + 经验池扣减 + 不恢复 HP + 自动学习技能不重复插入）、
 * 加点（速度消耗 2 + 点数不足拒绝）、洗点（免费 + 全量返还）、
 * 技能装配（槽位范围 + 占用先卸下 + 未学习拒绝）。
 * <p>
 * 不验证数据库实际写入（由集成测试覆盖），重点验证业务规则与异常分支。
 */
@ExtendWith(MockitoExtension.class)
class PetServiceTest {

    @Mock
    private PlayerMapper playerMapper;
    @Mock
    private PlayerPetMapper playerPetMapper;
    @Mock
    private PlayerPetSkillMapper playerPetSkillMapper;

    private PetGrowthService growthService;
    private GameConfigRegistry registry;

    @InjectMocks
    private PetService petService;

    private InitialPetsConfig.InitialPetOption species;

    @BeforeEach
    void setUp() {
        // 构建真实 PetGrowthService（无状态，直接依赖配置）
        species = species("SPEC_TEST", "RARE", 50,
                List.of(skillSlot("SKILL_A", 1), skillSlot("SKILL_B", 10)));
        registry = buildRegistry(List.of(species));
        growthService = new PetGrowthService(registry);

        // 重新构建 PetService（@InjectMocks 无法注入构造参数中非 Mock 的 growthService/registry）
        petService = new PetService(playerMapper, playerPetMapper, playerPetSkillMapper,
                growthService, registry);
    }

    // ==================== 升级 ====================

    @Test
    void levelUp_oneMode_consumesExpPoolAndDoesNotHealHp() {
        // Lv.5 RARE → 6，所需经验 = expToNextLevel(5) = 100 * 1.15^4 = 175
        PlayerPetEntity pet = pet(5, 50, 30);  // currentHp=30 < maxHp
        PlayerEntity player = playerWithExp(1000);

        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        // 5→6 不在 (5,6] 解锁区间，selectCount 不会被调用
        when(playerPetSkillMapper.selectList(any())).thenReturn(List.of());  // 无已学技能
        when(playerPetMapper.updateById(any(PlayerPetEntity.class))).thenReturn(1);
        when(playerMapper.updateById(any(PlayerEntity.class))).thenReturn(1);

        PetDetail result = petService.levelUp(1L, "ONE", null, null);

        // 升到 6 级
        assertEquals(6, pet.getLevel());
        // 经验池扣减 175
        assertEquals(1000 - 175, player.getExpPool());
        // currentHp 保持不变（升级不恢复 HP）
        assertEquals(30, pet.getCurrentHp());
        // 自动学习 SKILL_B（10 级技能，5→6 不在 (5,6] 区间，不会学习）
        verify(playerPetSkillMapper, never()).insert(any(PlayerPetSkillEntity.class));

        assertNotNull(result);
    }

    @Test
    void levelUp_unlocksNewSkill_autoLearnsButDoesNotEquip() {
        // Lv.9 → 10，解锁 SKILL_B
        PlayerPetEntity pet = pet(9, 50, 100);
        PlayerEntity player = playerWithExp(10000);

        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetSkillMapper.selectCount(any())).thenReturn(0L);  // SKILL_B 未学习
        when(playerPetSkillMapper.selectList(any())).thenReturn(List.of());
        when(playerPetMapper.updateById(any(PlayerPetEntity.class))).thenReturn(1);
        when(playerMapper.updateById(any(PlayerEntity.class))).thenReturn(1);

        petService.levelUp(1L, "ONE", null, null);

        assertEquals(10, pet.getLevel());
        // SKILL_B 自动学习（slot=null，默认不装备）
        verify(playerPetSkillMapper, times(1)).insert(argThat((PlayerPetSkillEntity s) ->
                "SKILL_B".equals(s.getSkillId())
                        && "LEVEL_UP".equals(s.getSourceType())
                        && s.getSlot() == null));
    }

    @Test
    void levelUp_existingSkillNotReinserted() {
        // Lv.9 → 10，SKILL_B 已存在（不重复插入）
        PlayerPetEntity pet = pet(9, 50, 100);
        PlayerEntity player = playerWithExp(10000);

        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetSkillMapper.selectCount(any())).thenReturn(1L);  // SKILL_B 已学习
        when(playerPetSkillMapper.selectList(any())).thenReturn(List.of());
        when(playerPetMapper.updateById(any(PlayerPetEntity.class))).thenReturn(1);
        when(playerMapper.updateById(any(PlayerEntity.class))).thenReturn(1);

        petService.levelUp(1L, "ONE", null, null);

        verify(playerPetSkillMapper, never()).insert(any(PlayerPetSkillEntity.class));
    }

    @Test
    void levelUp_levelCapReached_rejected() {
        PlayerPetEntity pet = pet(50, 50, 100);  // 已达上限
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerMapper.selectOne(isNull())).thenReturn(playerWithExp(1000));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> petService.levelUp(1L, "ONE", null, null));
        assertEquals("LEVEL_CAP_REACHED", ex.getErrorCode());
        // 经验池不被扣减
        verify(playerMapper, never()).updateById(any(PlayerEntity.class));
        verify(playerPetMapper, never()).updateById(any(PlayerPetEntity.class));
    }

    @Test
    void levelUp_expNotEnough_rejected() {
        PlayerPetEntity pet = pet(5, 50, 100);  // 需 175
        PlayerEntity player = playerWithExp(100);  // 不足
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerMapper.selectOne(isNull())).thenReturn(player);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> petService.levelUp(1L, "ONE", null, null));
        assertEquals("EXP_NOT_ENOUGH", ex.getErrorCode());
        // 经验池未被扣减
        assertEquals(100, player.getExpPool());
        verify(playerPetMapper, never()).updateById(any(PlayerPetEntity.class));
    }

    @Test
    void levelUp_fiveMode_cappedAtLevelCap() {
        // Lv.48 + 5 = 53，封顶 50
        PlayerPetEntity pet = pet(48, 50, 100);
        PlayerEntity player = playerWithExp(1000000);

        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetSkillMapper.selectList(any())).thenReturn(List.of());
        when(playerPetMapper.updateById(any(PlayerPetEntity.class))).thenReturn(1);
        when(playerMapper.updateById(any(PlayerEntity.class))).thenReturn(1);

        petService.levelUp(1L, "FIVE", null, null);

        assertEquals(50, pet.getLevel());
    }

    @Test
    void levelUp_toLevelMode_validatesRange() {
        PlayerPetEntity pet = pet(5, 50, 100);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerMapper.selectOne(isNull())).thenReturn(playerWithExp(10000));

        // 未提供 targetLevel
        BusinessException ex1 = assertThrows(BusinessException.class,
                () -> petService.levelUp(1L, "TO_LEVEL", null, null));
        assertEquals("INVALID_LEVEL_UP", ex1.getErrorCode());

        // targetLevel <= 当前等级
        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> petService.levelUp(1L, "TO_LEVEL", 5, null));
        assertEquals("INVALID_LEVEL_UP", ex2.getErrorCode());

        // targetLevel > 上限
        BusinessException ex3 = assertThrows(BusinessException.class,
                () -> petService.levelUp(1L, "TO_LEVEL", 51, null));
        assertEquals("INVALID_LEVEL_UP", ex3.getErrorCode());
    }

    @Test
    void levelUp_customExpMode_upgradesAsFarAsPossible() {
        // Lv.1，投入 400 经验：1→2(100) → 3(115) → 4(132) → 剩余 53 不足以升 5(152)
        PlayerPetEntity pet = pet(1, 50, 100);
        PlayerEntity player = playerWithExp(10000);

        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetSkillMapper.selectList(any())).thenReturn(List.of());
        when(playerPetMapper.updateById(any(PlayerPetEntity.class))).thenReturn(1);
        when(playerMapper.updateById(any(PlayerEntity.class))).thenReturn(1);

        petService.levelUp(1L, "CUSTOM_EXP", null, 400);

        assertEquals(4, pet.getLevel());
        // 扣减累计经验 100+115+132 = 347
        assertEquals(10000 - 347, player.getExpPool());
    }

    @Test
    void levelUp_customExpMode_invalidExpRejected() {
        PlayerPetEntity pet = pet(1, 50, 100);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerMapper.selectOne(isNull())).thenReturn(playerWithExp(10000));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> petService.levelUp(1L, "CUSTOM_EXP", null, 0));
        assertEquals("INVALID_LEVEL_UP", ex.getErrorCode());
    }

    @Test
    void levelUp_nullMode_rejected() {
        // mode == null 时直接抛异常，不调用 mapper
        BusinessException ex = assertThrows(BusinessException.class,
                () -> petService.levelUp(1L, null, null, null));
        assertEquals("INVALID_LEVEL_UP", ex.getErrorCode());
    }

    @Test
    void levelUp_petNotFound_rejected() {
        when(playerPetMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> petService.levelUp(999L, "ONE", null, null));
        assertEquals("PET_NOT_FOUND", ex.getErrorCode());
    }

    // ==================== 升级预览 ====================

    @Test
    void previewLevelUp_success_returnsPreviewWithExpPool() {
        PlayerPetEntity pet = pet(5, 50, 100);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerMapper.selectOne(isNull())).thenReturn(playerWithExp(1000));

        PetGrowthService.LevelUpPreview preview = petService.previewLevelUp(1L, 6);

        assertEquals(5, preview.getFromLevel());
        assertEquals(6, preview.getToLevel());
        assertTrue(preview.getExpRequired() > 0);
        assertEquals(1000, preview.getExpPoolAvailable());
        assertTrue(preview.getExpPoolSufficient());
    }

    @Test
    void previewLevelUp_invalidTarget_returnsBusinessError() {
        PlayerPetEntity pet = pet(5, 50, 100);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerMapper.selectOne(isNull())).thenReturn(playerWithExp(1000));

        // 目标等级 <= 当前等级 → 业务错误而非 500
        BusinessException ex1 = assertThrows(BusinessException.class,
                () -> petService.previewLevelUp(1L, 5));
        assertEquals("INVALID_LEVEL_UP", ex1.getErrorCode());
        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> petService.previewLevelUp(1L, 3));
        assertEquals("INVALID_LEVEL_UP", ex2.getErrorCode());
        // 超过等级上限同样转为业务错误
        BusinessException ex3 = assertThrows(BusinessException.class,
                () -> petService.previewLevelUp(1L, 51));
        assertEquals("INVALID_LEVEL_UP", ex3.getErrorCode());
    }

    // ==================== 加点 ====================

    @Test
    void allocatePoints_speedCostsDouble() {
        // Lv.10 RARE 已获得 29；速度每点消耗 2
        PlayerPetEntity pet = pet(10, 50, 100);
        pet.setFreePointSpeed(0);

        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerMapper.selectOne(isNull())).thenReturn(playerWithExp(1000));
        when(playerPetSkillMapper.selectList(any())).thenReturn(List.of());

        petService.allocatePoints(1L, "SPEED", 1);

        // 速度 +1 点次（消耗 2 自由点数）
        assertEquals(1, pet.getFreePointSpeed());
        verify(playerPetMapper).updateById(pet);
    }

    @Test
    void allocatePoints_strengthCostsOne() {
        PlayerPetEntity pet = pet(10, 50, 100);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerMapper.selectOne(isNull())).thenReturn(playerWithExp(1000));
        when(playerPetSkillMapper.selectList(any())).thenReturn(List.of());

        petService.allocatePoints(1L, "STRENGTH", 3);

        assertEquals(3, pet.getFreePointStrength());
    }

    @Test
    void allocatePoints_pointsNotEnough_rejected() {
        // Lv.2 RARE 已获得 3；速度消耗 2*2=4 > 3
        PlayerPetEntity pet = pet(2, 50, 100);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> petService.allocatePoints(1L, "SPEED", 2));
        assertEquals("POINTS_NOT_ENOUGH", ex.getErrorCode());
        // 不写入
        verify(playerPetMapper, never()).updateById(any(PlayerPetEntity.class));
    }

    @Test
    void allocatePoints_invalidArguments_rejected() {
        // 空 statKey - 在 requirePet 之前抛出
        BusinessException ex1 = assertThrows(BusinessException.class,
                () -> petService.allocatePoints(1L, "", 1));
        assertEquals("INVALID_STAT", ex1.getErrorCode());

        // 非正 points - 在 requirePet 之前抛出
        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> petService.allocatePoints(1L, "STRENGTH", 0));
        assertEquals("INVALID_POINTS", ex2.getErrorCode());

        // 未知 statKey - 在 requirePet 之后、switch default 抛出（不调用 requirePlayer）
        PlayerPetEntity pet = pet(10, 50, 100);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        BusinessException ex3 = assertThrows(BusinessException.class,
                () -> petService.allocatePoints(1L, "UNKNOWN", 1));
        assertEquals("INVALID_STAT", ex3.getErrorCode());
    }

    // ==================== 洗点 ====================

    @Test
    void resetPoints_refundsAllAllocatedFree() {
        PlayerPetEntity pet = pet(10, 50, 100);
        pet.setFreePointHp(2);
        pet.setFreePointStrength(1);
        pet.setFreePointSpeed(3);

        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerMapper.selectOne(isNull())).thenReturn(playerWithExp(1000));
        when(playerPetSkillMapper.selectList(any())).thenReturn(List.of());

        PetDetail result = petService.resetPoints(1L);

        // 全部清零
        assertEquals(0, pet.getFreePointHp());
        assertEquals(0, pet.getFreePointStrength());
        assertEquals(0, pet.getFreePointSpirit());
        assertEquals(0, pet.getFreePointDefense());
        assertEquals(0, pet.getFreePointResistance());
        assertEquals(0, pet.getFreePointSpeed());
        // currentHp 不变
        assertEquals(100, pet.getCurrentHp());
        verify(playerPetMapper).updateById(pet);
        assertNotNull(result);
    }

    // ==================== 技能装配 ====================

    @Test
    void equipSkill_toEmptySlot_succeeds() {
        PlayerPetEntity pet = pet(5, 50, 100);
        PlayerPetSkillEntity learned = new PlayerPetSkillEntity();
        learned.setId(10L);
        learned.setPetId(1L);
        learned.setSkillId("SKILL_A");
        learned.setSourceType("LEVEL_UP");
        learned.setSlot(null);

        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerPetSkillMapper.selectOne(any())).thenReturn(learned);
        when(playerMapper.selectOne(isNull())).thenReturn(playerWithExp(1000));
        when(playerPetSkillMapper.selectList(any())).thenReturn(List.of(learned));

        petService.equipSkill(1L, "SKILL_A", 2);

        assertEquals(2, learned.getSlot());
        verify(playerPetSkillMapper).updateById(learned);
    }

    @Test
    void equipSkill_occupiedSlot_unequipPreviousFirst() {
        PlayerPetEntity pet = pet(5, 50, 100);
        PlayerPetSkillEntity learned = new PlayerPetSkillEntity();
        learned.setId(10L);
        learned.setPetId(1L);
        learned.setSkillId("SKILL_A");
        learned.setSlot(null);

        PlayerPetSkillEntity occupant = new PlayerPetSkillEntity();
        occupant.setId(20L);
        occupant.setSkillId("SKILL_OLD");
        occupant.setSlot(1);

        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        // 第一次 selectOne 返回 learned，第二次返回 occupant
        when(playerPetSkillMapper.selectOne(any())).thenReturn(learned, occupant);
        when(playerMapper.selectOne(isNull())).thenReturn(playerWithExp(1000));
        when(playerPetSkillMapper.selectList(any())).thenReturn(List.of());

        petService.equipSkill(1L, "SKILL_A", 1);

        // 原占用槽位被清空
        assertNull(occupant.getSlot());
        verify(playerPetSkillMapper).updateById(occupant);
        // 新技能装入槽位 1
        assertEquals(1, learned.getSlot());
        verify(playerPetSkillMapper).updateById(learned);
    }

    @Test
    void equipSkill_sameSlotSameSkill_noExtraUpdate() {
        PlayerPetEntity pet = pet(5, 50, 100);
        PlayerPetSkillEntity learned = new PlayerPetSkillEntity();
        learned.setId(10L);
        learned.setSkillId("SKILL_A");
        learned.setSlot(2);  // 已在槽位 2

        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerPetSkillMapper.selectOne(any())).thenReturn(learned, learned);
        when(playerMapper.selectOne(isNull())).thenReturn(playerWithExp(1000));
        when(playerPetSkillMapper.selectList(any())).thenReturn(List.of());

        petService.equipSkill(1L, "SKILL_A", 2);

        // 同技能同槽位：不卸下原占用（id 相同），仅更新槽位
        verify(playerPetSkillMapper, times(1)).updateById(learned);
    }

    @Test
    void equipSkill_skillNotLearned_rejected() {
        PlayerPetEntity pet = pet(5, 50, 100);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerPetSkillMapper.selectOne(any())).thenReturn(null);  // 未学习

        BusinessException ex = assertThrows(BusinessException.class,
                () -> petService.equipSkill(1L, "SKILL_UNKNOWN", 1));
        assertEquals("SKILL_NOT_LEARNED", ex.getErrorCode());
    }

    @Test
    void equipSkill_invalidSlot_rejected() {
        // slot 校验在 requirePet 之前，不调用 mapper
        BusinessException ex1 = assertThrows(BusinessException.class,
                () -> petService.equipSkill(1L, "SKILL_A", 0));
        assertEquals("INVALID_SLOT", ex1.getErrorCode());

        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> petService.equipSkill(1L, "SKILL_A", 5));
        assertEquals("INVALID_SLOT", ex2.getErrorCode());
    }

    @Test
    void equipSkill_emptySkillId_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> petService.equipSkill(1L, "", 1));
        assertEquals("INVALID_SKILL", ex.getErrorCode());
    }

    @Test
    void unequipSkill_clearsSlot() {
        PlayerPetEntity pet = pet(5, 50, 100);
        PlayerPetSkillEntity equipped = new PlayerPetSkillEntity();
        equipped.setId(10L);
        equipped.setSkillId("SKILL_A");
        equipped.setSlot(2);

        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerPetSkillMapper.selectOne(any())).thenReturn(equipped);
        when(playerMapper.selectOne(isNull())).thenReturn(playerWithExp(1000));
        when(playerPetSkillMapper.selectList(any())).thenReturn(List.of());

        petService.unequipSkill(1L, 2);

        assertNull(equipped.getSlot());
        verify(playerPetSkillMapper).updateById(equipped);
    }

    @Test
    void unequipSkill_emptySlot_rejected() {
        PlayerPetEntity pet = pet(5, 50, 100);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerPetSkillMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> petService.unequipSkill(1L, 2));
        assertEquals("SLOT_EMPTY", ex.getErrorCode());
    }

    @Test
    void unequipSkill_invalidSlot_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> petService.unequipSkill(1L, 0));
        assertEquals("INVALID_SLOT", ex.getErrorCode());
    }

    // ==================== 详情查询 ====================

    @Test
    void getPetDetail_aggregatesAllFields() {
        PlayerPetEntity pet = pet(10, 50, 80);
        PlayerEntity player = playerWithExp(500);

        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetSkillMapper.selectList(any())).thenReturn(List.of());

        PetDetail detail = petService.getPetDetail(1L);

        assertNotNull(detail);
        assertSame(pet, detail.getPet());
        assertEquals("RARE", detail.getSpecies().getRarity());
        assertEquals(500, detail.getExpPool());
        // Lv.10 RARE 已获得 29
        assertEquals(29, detail.getFreePointsAvailable());
        // 升下一级经验 = expToNextLevel(10)
        assertTrue(detail.getExpToNextLevel() > 0);
        // 面板属性已计算
        assertTrue(detail.getPanelStats().getMaxHp() > 0);
        // 可学技能：未来可解锁的（无，因为只配置 1/10 级两个技能，Lv.10 已全解锁）
        assertTrue(detail.getAvailableSkills().isEmpty());
    }

    // ==================== 工具方法 ====================

    private PlayerPetEntity pet(int level, int aptitude, int currentHp) {
        PlayerPetEntity pet = new PlayerPetEntity();
        pet.setId(1L);
        pet.setSaveId("SAVE_1");
        pet.setSpeciesId("SPEC_TEST");
        pet.setLevel(level);
        pet.setCapturedLevel(1);
        pet.setHpAptitude(aptitude);
        pet.setStrengthAptitude(aptitude);
        pet.setSpiritAptitude(aptitude);
        pet.setDefenseAptitude(aptitude);
        pet.setResistanceAptitude(aptitude);
        pet.setSpeedAptitude(aptitude);
        pet.setFreePointHp(0);
        pet.setFreePointStrength(0);
        pet.setFreePointSpirit(0);
        pet.setFreePointDefense(0);
        pet.setFreePointResistance(0);
        pet.setFreePointSpeed(0);
        pet.setCurrentHp(currentHp);
        pet.setBaseHpOffset(0);
        pet.setBaseStrengthOffset(0);
        pet.setBaseSpiritOffset(0);
        pet.setBaseDefenseOffset(0);
        pet.setBaseResistanceOffset(0);
        pet.setBaseSpeedOffset(0);
        return pet;
    }

    private PlayerEntity playerWithExp(int expPool) {
        PlayerEntity player = new PlayerEntity();
        player.setId(1L);
        player.setSaveId("SAVE_1");
        player.setExpPool(expPool);
        player.setGold(1000);
        return player;
    }
}
