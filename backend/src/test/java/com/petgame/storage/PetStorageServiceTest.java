package com.petgame.storage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.common.BusinessException;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.config.model.ReleaseGiftsConfig;
import com.petgame.config.model.SystemRuleConfig;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.entity.PlayerPetSkillEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.pet.mapper.PlayerPetSkillMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.team.service.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 宠物仓库服务测试（阶段 5，测试规约 §2.5）。
 * <p>
 * 验证：放生保护规则（锁定/收藏/在队自动排除、单只受保护报错）、
 * 放生预览警告原因、仓库筛选排序。
 */
class PetStorageServiceTest {

    private static final String SAVE_ID = "SAVE_TEST";

    private PlayerMapper playerMapper;
    private PlayerPetMapper playerPetMapper;
    private PlayerPetSkillMapper playerPetSkillMapper;
    private PlayerInventoryMapper playerInventoryMapper;
    private TeamService teamService;
    private PetGrowthService growthService;
    private GameConfigRegistry registry;
    private PetStorageService service;
    private PlayerEntity player;

    @BeforeEach
    void setUp() {
        playerMapper = mock(PlayerMapper.class);
        playerPetMapper = mock(PlayerPetMapper.class);
        playerPetSkillMapper = mock(PlayerPetSkillMapper.class);
        playerInventoryMapper = mock(PlayerInventoryMapper.class);
        teamService = mock(TeamService.class);
        growthService = mock(PetGrowthService.class);
        registry = mock(GameConfigRegistry.class);

        player = new PlayerEntity();
        player.setSaveId(SAVE_ID);
        player.setGold(100);
        player.setExpPool(0);
        when(playerMapper.selectOne(any())).thenReturn(player);

        SystemRuleConfig rules = new SystemRuleConfig();
        rules.getReleaseGiftBaseValue().put("COMMON", 20);
        rules.getReleaseGiftBaseValue().put("EPIC", 150);
        rules.getReleaseGiftBaseValue().put("LEGENDARY", 400);
        when(registry.getSystemRules()).thenReturn(rules);

        when(registry.getSpecies("PET_COMMON")).thenReturn(species("PET_COMMON", "普通兽", "COMMON"));
        when(registry.getSpecies("PET_EPIC")).thenReturn(species("PET_EPIC", "珍稀兽", "EPIC"));

        ReleaseGiftsConfig gifts = new ReleaseGiftsConfig();
        ReleaseGiftsConfig.GiftEntry goldGift = new ReleaseGiftsConfig.GiftEntry();
        goldGift.setType("GOLD");
        goldGift.setQuantity(5);
        goldGift.setUnitValue(1);
        goldGift.setWeight(100);
        gifts.setGifts(List.of(goldGift));
        when(registry.getReleaseGiftsConfig()).thenReturn(gifts);

        when(teamService.getActiveTeamPetIds()).thenReturn(Set.of());
        when(growthService.allocatedFreePoints(any())).thenReturn(0);

        service = new PetStorageService(playerMapper, playerPetMapper, playerPetSkillMapper,
                playerInventoryMapper, teamService, growthService, registry);
    }

    private PetSpeciesConfig species(String id, String name, String rarity) {
        PetSpeciesConfig species = new PetSpeciesConfig();
        species.setId(id);
        species.setName(name);
        species.setRarity(rarity);
        species.setElement("FIRE");
        return species;
    }

    private PlayerPetEntity pet(long id, String speciesId, boolean locked, boolean favorite) {
        PlayerPetEntity pet = new PlayerPetEntity();
        pet.setId(id);
        pet.setSaveId(SAVE_ID);
        pet.setSpeciesId(speciesId);
        pet.setLevel(5);
        pet.setCapturedLevel(5);
        pet.setLocked(locked);
        pet.setFavorite(favorite);
        pet.setHpAptitude(50);
        pet.setStrengthAptitude(50);
        pet.setSpiritAptitude(50);
        pet.setDefenseAptitude(50);
        pet.setResistanceAptitude(50);
        pet.setSpeedAptitude(50);
        return pet;
    }

    @Test
    void releasePets_batch_shouldExcludeProtectedPets() {
        PlayerPetEntity normal = pet(1L, "PET_COMMON", false, false);
        PlayerPetEntity locked = pet(2L, "PET_COMMON", true, false);
        PlayerPetEntity favorite = pet(3L, "PET_COMMON", false, true);
        PlayerPetEntity inTeam = pet(4L, "PET_COMMON", false, false);
        when(playerPetMapper.selectById(1L)).thenReturn(normal);
        when(playerPetMapper.selectById(2L)).thenReturn(locked);
        when(playerPetMapper.selectById(3L)).thenReturn(favorite);
        when(playerPetMapper.selectById(4L)).thenReturn(inTeam);
        when(teamService.getActiveTeamPetIds()).thenReturn(Set.of(4L));

        PetStorageService.ReleaseResult result = service.releasePets(List.of(1L, 2L, 3L, 4L));

        assertEquals(1, result.getReleased().size(), "仅未受保护宠物被放生");
        assertEquals(1L, result.getReleased().get(0).getPetId());
        assertEquals(3, result.getSkipped().size(), "锁定/收藏/在队自动排除");
        assertTrue(result.getSkipped().stream().anyMatch(s -> "LOCKED".equals(s.getReason())));
        assertTrue(result.getSkipped().stream().anyMatch(s -> "FAVORITE".equals(s.getReason())));
        assertTrue(result.getSkipped().stream().anyMatch(s -> "IN_TEAM".equals(s.getReason())));

        verify(playerPetMapper, times(1)).deleteById(1L);
        verify(playerPetMapper, never()).deleteById(2L);
        verify(playerPetMapper, never()).deleteById(3L);
        verify(playerPetMapper, never()).deleteById(4L);

        // 礼物发放：全 GOLD（unitValue=1），COMMON Lv.5 → 点数 20×1.05=21 → 金币增加 ≥21
        assertFalse(result.getGifts().isEmpty());
        int goldGained = result.getGifts().stream()
                .filter(g -> "GOLD".equals(g.getType())).mapToInt(g -> g.getQuantity()).sum();
        assertTrue(goldGained >= result.getTotalGiftPoints());
        assertEquals(100 + goldGained, player.getGold());
    }

    @Test
    void releasePets_singleProtected_shouldThrow() {
        PlayerPetEntity locked = pet(2L, "PET_COMMON", true, false);
        when(playerPetMapper.selectById(2L)).thenReturn(locked);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.releasePets(List.of(2L)));
        assertEquals("PET_PROTECTED", ex.getErrorCode());
        verify(playerPetMapper, never()).deleteById(anyLong());
    }

    @Test
    void releasePets_allProtected_shouldThrow() {
        PlayerPetEntity locked = pet(2L, "PET_COMMON", true, false);
        PlayerPetEntity favorite = pet(3L, "PET_COMMON", false, true);
        when(playerPetMapper.selectById(2L)).thenReturn(locked);
        when(playerPetMapper.selectById(3L)).thenReturn(favorite);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.releasePets(List.of(2L, 3L)));
        assertEquals("NO_RELEASABLE_PETS", ex.getErrorCode());
    }

    @Test
    void previewRelease_shouldReturnBlockAndWarningReasons() {
        // EPIC + 高资质 + 稀有技能 → 多重警告
        PlayerPetEntity epic = pet(10L, "PET_EPIC", false, false);
        epic.setHpAptitude(95);
        epic.setStrengthAptitude(95);
        epic.setSpiritAptitude(95);
        epic.setDefenseAptitude(95);
        epic.setResistanceAptitude(95);
        epic.setSpeedAptitude(95);
        when(playerPetMapper.selectById(10L)).thenReturn(epic);

        PlayerPetSkillEntity rareSkill = new PlayerPetSkillEntity();
        rareSkill.setSkillId("SKILL_RARE");
        rareSkill.setSourceType("CAPTURE");
        when(playerPetSkillMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(rareSkill));

        PlayerPetEntity locked = pet(11L, "PET_COMMON", true, false);
        when(playerPetMapper.selectById(11L)).thenReturn(locked);

        PetStorageService.ReleasePreview preview = service.previewRelease(List.of(10L, 11L));
        assertEquals(2, preview.getPets().size());

        PetStorageService.ReleasePreview.PetReleaseInfo epicInfo = preview.getPets().stream()
                .filter(p -> p.getPetId() == 10L).findFirst().orElseThrow();
        assertTrue(epicInfo.isReleasable());
        assertTrue(epicInfo.getWarningReasons().contains("HIGH_RARITY"), "珍稀/传说需警告");
        assertTrue(epicInfo.getWarningReasons().contains("HIGH_APTITUDE"), "高资质需警告");
        assertTrue(epicInfo.getWarningReasons().contains("RARE_SKILL"), "稀有技能需警告");
        assertTrue(epicInfo.getGiftPoints() >= 150, "EPIC 基础点数 150");

        PetStorageService.ReleasePreview.PetReleaseInfo lockedInfo = preview.getPets().stream()
                .filter(p -> p.getPetId() == 11L).findFirst().orElseThrow();
        assertFalse(lockedInfo.isReleasable());
        assertTrue(lockedInfo.getBlockReasons().contains("LOCKED"));
    }

    @Test
    void listStorage_shouldApplyFilterAndSort() {
        PlayerPetEntity low = pet(1L, "PET_COMMON", false, false);
        low.setLevel(2);
        PlayerPetEntity mid = pet(2L, "PET_EPIC", false, false);
        mid.setLevel(10);
        PlayerPetEntity high = pet(3L, "PET_COMMON", false, false);
        high.setLevel(20);
        when(playerPetMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(low, mid, high));

        // 按等级升序
        PetStorageService.StorageQueryRequest query = new PetStorageService.StorageQueryRequest();
        query.setSortBy("LEVEL");
        query.setSortDirection("ASC");
        List<PetStorageService.StoragePetView> all = service.listStorage(query);
        assertEquals(List.of(2, 10, 20), all.stream().map(PetStorageService.StoragePetView::getLevel).toList());

        // 稀有度筛选 EPIC
        query.setRarity("EPIC");
        List<PetStorageService.StoragePetView> epics = service.listStorage(query);
        assertEquals(1, epics.size());
        assertEquals("PET_EPIC", epics.get(0).getSpeciesId());

        // 等级范围筛选
        query.setRarity(null);
        query.setLevelMin(5);
        query.setLevelMax(15);
        List<PetStorageService.StoragePetView> ranged = service.listStorage(query);
        assertEquals(1, ranged.size());
        assertEquals(10, ranged.get(0).getLevel());
    }

    @Test
    void listStorage_inTeamFilter_shouldUseTeamService() {
        PlayerPetEntity inTeam = pet(1L, "PET_COMMON", false, false);
        PlayerPetEntity notInTeam = pet(2L, "PET_COMMON", false, false);
        when(playerPetMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(inTeam, notInTeam));
        when(teamService.getActiveTeamPetIds()).thenReturn(Set.of(1L));

        PetStorageService.StorageQueryRequest query = new PetStorageService.StorageQueryRequest();
        query.setInTeam(true);
        List<PetStorageService.StoragePetView> result = service.listStorage(query);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getPetId());
        assertTrue(result.get(0).isInTeam());

        query.setInTeam(false);
        result = service.listStorage(query);
        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getPetId());
    }

    @Test
    void setNickname_shouldEnforceLengthLimit() {
        PlayerPetEntity petEntity = pet(1L, "PET_COMMON", false, false);
        when(playerPetMapper.selectById(1L)).thenReturn(petEntity);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.setNickname(1L, "这个名字超过十二个字符的限制啦"));
        assertEquals("INVALID_NICKNAME", ex.getErrorCode());

        // 合法昵称与清除
        service.setNickname(1L, "小炎");
        assertEquals("小炎", petEntity.getNickname());
        service.setNickname(1L, "");
        assertNull(petEntity.getNickname(), "空昵称应清除，恢复显示种族名称");
    }
}
