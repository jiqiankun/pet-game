package com.petgame.team;

import com.petgame.common.BusinessException;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.team.entity.PlayerTeamEntity;
import com.petgame.team.entity.PlayerTeamMemberEntity;
import com.petgame.team.mapper.PlayerTeamMapper;
import com.petgame.team.mapper.PlayerTeamMemberMapper;
import com.petgame.team.service.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * TeamService 单元测试（阶段 4 验收标准 + 阶段 6 预设扩展）。
 * <p>
 * 覆盖：成员数量上限（6 宠携带）、位置范围与唯一性、同一宠物不可重复占位、
 * 宠物归属校验、整体替换在单事务内完成（先删除后插入）、查询激活队伍、
 * 5 套预设懒创建与切换、战斗中禁止编辑/切换（阶段 6）。
 */
@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private PlayerMapper playerMapper;
    @Mock
    private PlayerTeamMapper playerTeamMapper;
    @Mock
    private PlayerTeamMemberMapper playerTeamMemberMapper;
    @Mock
    private PlayerPetMapper playerPetMapper;
    @Mock
    private com.petgame.battle.service.BattleService battleService;

    @InjectMocks
    private TeamService teamService;

    private PlayerEntity player;
    private PlayerTeamEntity activeTeam;

    @BeforeEach
    void setUp() {
        player = new PlayerEntity();
        player.setId(1L);
        player.setSaveId("SAVE_1");
        player.setPlayerName("TEST");

        activeTeam = new PlayerTeamEntity();
        activeTeam.setId(100L);
        activeTeam.setSaveId("SAVE_1");
        activeTeam.setSlot(1);
        activeTeam.setIsActive(true);
        activeTeam.setName("主队伍");
    }

    // ==================== 查询激活队伍 ====================

    @Test
    void getActiveTeam_returnsMembersOrderedByPosition() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerTeamMapper.selectOne(any())).thenReturn(activeTeam);

        PlayerTeamMemberEntity m1 = member(11L, 100L, 1L, 1);
        PlayerTeamMemberEntity m2 = member(12L, 100L, 2L, 2);
        when(playerTeamMemberMapper.selectList(any())).thenReturn(List.of(m1, m2));

        PlayerPetEntity pet1 = pet(1L, "SPEC_A", "小白");
        PlayerPetEntity pet2 = pet(2L, "SPEC_B", "小黑");
        when(playerPetMapper.selectById(1L)).thenReturn(pet1);
        when(playerPetMapper.selectById(2L)).thenReturn(pet2);

        TeamService.TeamView view = teamService.getActiveTeam();

        assertEquals(100L, view.getTeamId());
        assertTrue(view.getIsActive());
        assertEquals(2, view.getMembers().size());
        assertEquals(1, view.getMembers().get(0).getPosition());
        assertEquals("小白", view.getMembers().get(0).getNickname());
        assertEquals("SPEC_B", view.getMembers().get(1).getSpeciesId());
    }

    @Test
    void getActiveTeam_isStarter_marksOnlyPositions1To3() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerTeamMapper.selectOne(any())).thenReturn(activeTeam);

        PlayerTeamMemberEntity m1 = member(11L, 100L, 1L, 1);
        PlayerTeamMemberEntity m2 = member(12L, 100L, 2L, 2);
        PlayerTeamMemberEntity m3 = member(13L, 100L, 3L, 3);
        PlayerTeamMemberEntity m4 = member(14L, 100L, 4L, 4);
        when(playerTeamMemberMapper.selectList(any())).thenReturn(List.of(m1, m2, m3, m4));

        // 宠物均非「初始伙伴」（isStarter=false），首发标记必须来自位置而非宠物标记
        when(playerPetMapper.selectById(1L)).thenReturn(pet(1L, "SPEC_A", "小白"));
        when(playerPetMapper.selectById(2L)).thenReturn(pet(2L, "SPEC_B", "小黑"));
        when(playerPetMapper.selectById(3L)).thenReturn(pet(3L, "SPEC_C", "小灰"));
        when(playerPetMapper.selectById(4L)).thenReturn(pet(4L, "SPEC_D", "小蓝"));

        TeamService.TeamView view = teamService.getActiveTeam();

        assertEquals(4, view.getMembers().size());
        // 位置 1~3 为首发，位置 4 为候补（需求 §6）
        assertTrue(view.getMembers().get(0).getIsStarter());
        assertTrue(view.getMembers().get(1).getIsStarter());
        assertTrue(view.getMembers().get(2).getIsStarter());
        assertFalse(view.getMembers().get(3).getIsStarter());
    }

    @Test
    void getActiveTeam_skipsMembersWithMissingPet() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerTeamMapper.selectOne(any())).thenReturn(activeTeam);

        PlayerTeamMemberEntity m1 = member(11L, 100L, 1L, 1);
        PlayerTeamMemberEntity m2 = member(12L, 100L, 2L, 2);
        when(playerTeamMemberMapper.selectList(any())).thenReturn(List.of(m1, m2));

        when(playerPetMapper.selectById(1L)).thenReturn(pet(1L, "SPEC_A", "小白"));
        when(playerPetMapper.selectById(2L)).thenReturn(null);  // 宠物不存在

        TeamService.TeamView view = teamService.getActiveTeam();
        // 只返回有效成员
        assertEquals(1, view.getMembers().size());
        assertEquals(1L, view.getMembers().get(0).getPetId());
    }

    @Test
    void getActiveTeam_noSave_rejected() {
        when(playerMapper.selectOne(isNull())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.getActiveTeam());
        assertEquals("NO_SAVE", ex.getErrorCode());
    }

    @Test
    void getActiveTeam_noActiveTeam_rejected() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerTeamMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.getActiveTeam());
        assertEquals("NO_ACTIVE_TEAM", ex.getErrorCode());
    }

    // ==================== 整体替换成员 ====================

    @Test
    void updateTeamMembers_replacesAllInOneTransaction() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerTeamMapper.selectOne(any())).thenReturn(activeTeam);

        // 校验阶段：所有宠物都属于当前存档
        when(playerPetMapper.selectById(1L)).thenReturn(pet(1L, "SPEC_A", "小白"));
        when(playerPetMapper.selectById(2L)).thenReturn(pet(2L, "SPEC_B", "小黑"));
        when(playerPetMapper.selectById(3L)).thenReturn(pet(3L, "SPEC_C", "小灰"));

        // getActiveTeam 阶段：返回替换后的 3 名成员
        when(playerTeamMemberMapper.selectList(any())).thenReturn(List.of(
                member(11L, 100L, 1L, 1),
                member(12L, 100L, 2L, 2),
                member(13L, 100L, 3L, 3)
        ));

        TeamService.UpdateMembersRequest request = new TeamService.UpdateMembersRequest();
        request.setMembers(List.of(
                entry(1L, 1), entry(2L, 2), entry(3L, 3)
        ));

        TeamService.TeamView result = teamService.updateTeamMembers(request);

        // 先删除旧成员，再插入新成员
        verify(playerTeamMemberMapper).delete(any());
        verify(playerTeamMemberMapper, times(3)).insert(any(PlayerTeamMemberEntity.class));
        assertEquals(3, result.getMembers().size());
    }

    @Test
    void updateTeamMembers_overSixMembers_rejected() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerTeamMapper.selectOne(any())).thenReturn(activeTeam);

        TeamService.UpdateMembersRequest request = new TeamService.UpdateMembersRequest();
        request.setMembers(List.of(
                entry(1L, 1), entry(2L, 2), entry(3L, 3),
                entry(4L, 4), entry(5L, 5), entry(6L, 6), entry(7L, 7)  // 7 个
        ));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.updateTeamMembers(request));
        assertEquals("TEAM_FULL", ex.getErrorCode());
        // 不删除/不插入
        verify(playerTeamMemberMapper, never()).delete(any());
        verify(playerTeamMemberMapper, never()).insert(any(PlayerTeamMemberEntity.class));
    }

    @Test
    void updateTeamMembers_duplicatePosition_rejected() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerTeamMapper.selectOne(any())).thenReturn(activeTeam);
        // 第一项校验通过需要 pet 存在
        when(playerPetMapper.selectById(1L)).thenReturn(pet(1L, "SPEC_A", "小白"));

        TeamService.UpdateMembersRequest request = new TeamService.UpdateMembersRequest();
        request.setMembers(List.of(
                entry(1L, 1), entry(2L, 1)  // 位置重复
        ));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.updateTeamMembers(request));
        assertEquals("DUPLICATE_POSITION", ex.getErrorCode());
        verify(playerTeamMemberMapper, never()).delete(any());
    }

    @Test
    void updateTeamMembers_duplicatePet_rejected() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerTeamMapper.selectOne(any())).thenReturn(activeTeam);
        // 第一项校验通过需要 pet 存在
        when(playerPetMapper.selectById(1L)).thenReturn(pet(1L, "SPEC_A", "小白"));

        TeamService.UpdateMembersRequest request = new TeamService.UpdateMembersRequest();
        request.setMembers(List.of(
                entry(1L, 1), entry(1L, 2)  // 同一宠物
        ));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.updateTeamMembers(request));
        assertEquals("DUPLICATE_PET", ex.getErrorCode());
    }

    @Test
    void updateTeamMembers_invalidPosition_rejected() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerTeamMapper.selectOne(any())).thenReturn(activeTeam);

        TeamService.UpdateMembersRequest request = new TeamService.UpdateMembersRequest();
        request.setMembers(List.of(entry(1L, 0)));  // 位置 0 非法

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.updateTeamMembers(request));
        assertEquals("INVALID_POSITION", ex.getErrorCode());

        // 位置 7 非法
        TeamService.UpdateMembersRequest r2 = new TeamService.UpdateMembersRequest();
        r2.setMembers(List.of(entry(1L, 7)));
        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> teamService.updateTeamMembers(r2));
        assertEquals("INVALID_POSITION", ex2.getErrorCode());
    }

    @Test
    void updateTeamMembers_nullPetId_rejected() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerTeamMapper.selectOne(any())).thenReturn(activeTeam);

        TeamService.UpdateMembersRequest request = new TeamService.UpdateMembersRequest();
        TeamService.MemberEntry e = new TeamService.MemberEntry();
        e.setPetId(null);
        e.setPosition(1);
        request.setMembers(List.of(e));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.updateTeamMembers(request));
        assertEquals("INVALID_PET", ex.getErrorCode());
    }

    @Test
    void updateTeamMembers_petNotOwned_rejected() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerTeamMapper.selectOne(any())).thenReturn(activeTeam);

        // 宠物存在但不属于当前存档
        PlayerPetEntity otherSavePet = pet(1L, "SPEC_A", "小白");
        otherSavePet.setSaveId("SAVE_OTHER");
        when(playerPetMapper.selectById(1L)).thenReturn(otherSavePet);

        TeamService.UpdateMembersRequest request = new TeamService.UpdateMembersRequest();
        request.setMembers(List.of(entry(1L, 1)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.updateTeamMembers(request));
        assertEquals("PET_NOT_OWNED", ex.getErrorCode());
        verify(playerTeamMemberMapper, never()).delete(any());
    }

    @Test
    void updateTeamMembers_petNotFound_rejected() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerTeamMapper.selectOne(any())).thenReturn(activeTeam);
        when(playerPetMapper.selectById(999L)).thenReturn(null);

        TeamService.UpdateMembersRequest request = new TeamService.UpdateMembersRequest();
        request.setMembers(List.of(entry(999L, 1)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.updateTeamMembers(request));
        assertEquals("PET_NOT_OWNED", ex.getErrorCode());
    }

    @Test
    void updateTeamMembers_emptyMembers_createsEmptyTeam() {
        // 允许清空队伍（0 成员）
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerTeamMapper.selectOne(any())).thenReturn(activeTeam);
        when(playerTeamMemberMapper.selectList(any())).thenReturn(List.of());

        TeamService.UpdateMembersRequest request = new TeamService.UpdateMembersRequest();
        request.setMembers(List.of());

        TeamService.TeamView result = teamService.updateTeamMembers(request);

        verify(playerTeamMemberMapper).delete(any());
        verify(playerTeamMemberMapper, never()).insert(any(PlayerTeamMemberEntity.class));
        assertTrue(result.getMembers().isEmpty());
    }

    @Test
    void updateTeamMembers_nullRequest_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.updateTeamMembers(null));
        assertEquals("INVALID_TEAM", ex.getErrorCode());
    }

    @Test
    void updateTeamMembers_sixMembersWithValidPositions_succeeds() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerTeamMapper.selectOne(any())).thenReturn(activeTeam);
        when(playerPetMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return pet(id, "SPEC_" + id, "宠物" + id);
        });
        // getActiveTeam 阶段：返回替换后的 6 名成员
        when(playerTeamMemberMapper.selectList(any())).thenReturn(List.of(
                member(21L, 100L, 1L, 1),
                member(22L, 100L, 2L, 2),
                member(23L, 100L, 3L, 3),
                member(24L, 100L, 4L, 4),
                member(25L, 100L, 5L, 5),
                member(26L, 100L, 6L, 6)
        ));

        TeamService.UpdateMembersRequest request = new TeamService.UpdateMembersRequest();
        request.setMembers(List.of(
                entry(1L, 1), entry(2L, 2), entry(3L, 3),
                entry(4L, 4), entry(5L, 5), entry(6L, 6)
        ));

        TeamService.TeamView result = teamService.updateTeamMembers(request);

        // 6 个成员全部插入
        verify(playerTeamMemberMapper, times(6)).insert(any(PlayerTeamMemberEntity.class));
        assertEquals(6, result.getMembers().size());
    }

    // ==================== 5 套预设（阶段 6） ====================

    @Test
    void getTeamPresets_lazilyCreatesMissingPresets() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        // 存量存档只有 1 套队伍（阶段 4 行为）：首次查询返回 1 套，懒创建后返回 5 套
        when(playerTeamMapper.selectList(any()))
                .thenReturn(List.of(activeTeam))
                .thenReturn(List.of(activeTeam, preset(201L, 2), preset(202L, 3),
                        preset(203L, 4), preset(204L, 5)));
        when(playerTeamMemberMapper.selectList(any())).thenReturn(List.of());

        List<TeamService.TeamPresetView> presets = teamService.getTeamPresets();

        assertEquals(5, presets.size());
        // 缺失的 4 套预设懒创建
        verify(playerTeamMapper, times(4)).insert(any(PlayerTeamEntity.class));
    }

    @Test
    void activatePreset_switchesActiveTeam() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        PlayerTeamEntity target = preset(200L, 2);
        when(playerTeamMapper.selectById(200L)).thenReturn(target);
        List<PlayerTeamEntity> fiveTeams = List.of(activeTeam, target,
                preset(201L, 3), preset(202L, 4), preset(203L, 5));
        // 第 1 次：切换阶段（2 套）；后续预设列表查询（5 套已齐全，不再懒创建）
        when(playerTeamMapper.selectList(any()))
                .thenReturn(List.of(activeTeam, target))
                .thenReturn(fiveTeams);
        when(playerTeamMemberMapper.selectList(any())).thenReturn(List.of());

        List<TeamService.TeamPresetView> presets = teamService.activatePreset(200L);

        // 旧预设取消激活、新预设激活
        ArgumentCaptor<PlayerTeamEntity> captor = ArgumentCaptor.forClass(PlayerTeamEntity.class);
        verify(playerTeamMapper, times(2)).updateById(captor.capture());
        List<PlayerTeamEntity> updated = captor.getAllValues();
        assertFalse(updated.stream().filter(t -> t.getId() == 100L).findFirst().orElseThrow().getIsActive());
        assertTrue(updated.stream().filter(t -> t.getId() == 200L).findFirst().orElseThrow().getIsActive());
        assertEquals(5, presets.size());
    }

    @Test
    void activatePreset_otherSaveTeam_rejected() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        PlayerTeamEntity foreign = new PlayerTeamEntity();
        foreign.setId(300L);
        foreign.setSaveId("SAVE_OTHER");
        when(playerTeamMapper.selectById(300L)).thenReturn(foreign);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.activatePreset(300L));
        assertEquals("TEAM_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void activatePreset_inBattle_rejected() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(battleService.hasActiveBattle()).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.activatePreset(200L));
        assertEquals("BATTLE_IN_PROGRESS", ex.getErrorCode());
    }

    @Test
    void updateTeamMembers_inBattle_rejected() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(battleService.hasActiveBattle()).thenReturn(true);

        TeamService.UpdateMembersRequest request = new TeamService.UpdateMembersRequest();
        request.setMembers(List.of(entry(1L, 1)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.updateTeamMembers(request));
        assertEquals("BATTLE_IN_PROGRESS", ex.getErrorCode());
    }

    @Test
    void updateTeamMembers_targetPreset_editsSpecifiedTeam() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        PlayerTeamEntity preset2 = new PlayerTeamEntity();
        preset2.setId(200L);
        preset2.setSaveId("SAVE_1");
        preset2.setSlot(2);
        preset2.setName("队伍 2");
        preset2.setIsActive(false);
        when(playerTeamMapper.selectById(200L)).thenReturn(preset2);
        when(playerPetMapper.selectById(1L)).thenReturn(pet(1L, "SPEC_A", "小白"));
        when(playerTeamMemberMapper.selectList(any())).thenReturn(
                List.of(member(31L, 200L, 1L, 1)));

        TeamService.UpdateMembersRequest request = new TeamService.UpdateMembersRequest();
        request.setTeamId(200L);
        request.setMembers(List.of(entry(1L, 1)));

        TeamService.TeamView result = teamService.updateTeamMembers(request);

        assertEquals(200L, result.getTeamId());
        verify(playerTeamMemberMapper).delete(any());
        verify(playerTeamMemberMapper, times(1)).insert(any(PlayerTeamMemberEntity.class));
    }

    // ==================== 工具方法 ====================

    private PlayerTeamEntity preset(Long id, int slot) {
        PlayerTeamEntity team = new PlayerTeamEntity();
        team.setId(id);
        team.setSaveId("SAVE_1");
        team.setSlot(slot);
        team.setName("队伍 " + slot);
        team.setIsActive(false);
        return team;
    }

    private PlayerPetEntity pet(Long id, String speciesId, String nickname) {
        PlayerPetEntity pet = new PlayerPetEntity();
        pet.setId(id);
        pet.setSaveId("SAVE_1");
        pet.setSpeciesId(speciesId);
        pet.setNickname(nickname);
        pet.setLevel(5);
        pet.setCurrentHp(100);
        pet.setIsStarter(false);
        return pet;
    }

    private PlayerTeamMemberEntity member(Long id, Long teamId, Long petId, int position) {
        PlayerTeamMemberEntity m = new PlayerTeamMemberEntity();
        m.setId(id);
        m.setTeamId(teamId);
        m.setPetId(petId);
        m.setPosition(position);
        return m;
    }

    private TeamService.MemberEntry entry(Long petId, int position) {
        TeamService.MemberEntry e = new TeamService.MemberEntry();
        e.setPetId(petId);
        e.setPosition(position);
        return e;
    }
}
