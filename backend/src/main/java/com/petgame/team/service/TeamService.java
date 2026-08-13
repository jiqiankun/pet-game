package com.petgame.team.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.common.BusinessException;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 队伍服务（阶段 4）。
 * <p>
 * 管理玩家队伍编辑：6 宠携带（3 首发 + 3 候补）。
 * 阶段 4 仅支持 1 套队伍（当前激活队伍）；5 套预设切换属阶段 6。
 * <p>
 * 关键约束：
 * <ul>
 *   <li>队伍位置 1~6：1-3 首发、4-6 候补（需求 §6 携带与上场规则）。</li>
 *   <li>同一宠物不能在同一队伍重复占位。</li>
 *   <li>宠物必须属于当前存档。</li>
 *   <li>整体替换在单事务内完成，避免中间状态。</li>
 *   <li>战斗阵容（BattleService）实时从激活队伍读取，编辑后立即生效。</li>
 * </ul>
 */
@Service
public class TeamService {

    private static final Logger log = LoggerFactory.getLogger(TeamService.class);

    private final PlayerMapper playerMapper;
    private final PlayerTeamMapper playerTeamMapper;
    private final PlayerTeamMemberMapper playerTeamMemberMapper;
    private final PlayerPetMapper playerPetMapper;

    public TeamService(PlayerMapper playerMapper,
                       PlayerTeamMapper playerTeamMapper,
                       PlayerTeamMemberMapper playerTeamMemberMapper,
                       PlayerPetMapper playerPetMapper) {
        this.playerMapper = playerMapper;
        this.playerTeamMapper = playerTeamMapper;
        this.playerTeamMemberMapper = playerTeamMemberMapper;
        this.playerPetMapper = playerPetMapper;
    }

    /**
     * 查询当前激活队伍（含成员与宠物摘要）。
     */
    public TeamView getActiveTeam() {
        PlayerEntity player = requirePlayer();
        PlayerTeamEntity team = requireActiveTeam(player.getSaveId());

        List<PlayerTeamMemberEntity> members = playerTeamMemberMapper.selectList(
                new LambdaQueryWrapper<PlayerTeamMemberEntity>()
                        .eq(PlayerTeamMemberEntity::getTeamId, team.getId())
                        .orderByAsc(PlayerTeamMemberEntity::getPosition));

        List<TeamView.MemberView> memberViews = new ArrayList<>();
        for (PlayerTeamMemberEntity m : members) {
            PlayerPetEntity pet = playerPetMapper.selectById(m.getPetId());
            if (pet == null) {
                continue;
            }
            TeamView.MemberView view = new TeamView.MemberView();
            view.setMemberId(m.getId());
            view.setPetId(pet.getId());
            view.setPosition(m.getPosition());
            view.setSpeciesId(pet.getSpeciesId());
            view.setNickname(pet.getNickname());
            view.setLevel(pet.getLevel());
            view.setCurrentHp(pet.getCurrentHp());
            // 首发 = 位置 1~3（需求 §6 上场规则），与宠物「初始伙伴」纪念标记无关
            view.setIsStarter(m.getPosition() <= 3);
            memberViews.add(view);
        }

        TeamView view = new TeamView();
        view.setTeamId(team.getId());
        view.setName(team.getName());
        view.setSlot(team.getSlot());
        view.setIsActive(team.getIsActive());
        view.setMembers(memberViews);
        return view;
    }

    /**
     * 整体替换当前激活队伍的成员布局（需求 §6：6 宠携带，3 首发 + 3 候补）。
     * <p>
     * 前端提交完整的成员列表（最多 6 条，position 1~6 唯一），后端校验后全量替换。
     * 同一事务内删除旧成员、插入新成员，避免中间状态。
     *
     * @param request 新成员布局
     */
    @Transactional
    public TeamView updateTeamMembers(UpdateMembersRequest request) {
        if (request == null || request.getMembers() == null) {
            throw new BusinessException("INVALID_TEAM", "成员列表不能为空");
        }
        PlayerEntity player = requirePlayer();
        PlayerTeamEntity team = requireActiveTeam(player.getSaveId());

        List<MemberEntry> entries = request.getMembers();
        if (entries.size() > 6) {
            throw new BusinessException("TEAM_FULL", "队伍最多 6 名成员（3 首发 + 3 候补）");
        }

        // 校验：position 唯一且在 1~6；petId 唯一且属于当前存档
        Set<Integer> positions = new HashSet<>();
        Set<Long> petIds = new HashSet<>();
        for (MemberEntry e : entries) {
            if (e.getPosition() == null || e.getPosition() < 1 || e.getPosition() > 6) {
                throw new BusinessException("INVALID_POSITION", "位置必须在 1~6 范围内");
            }
            if (!positions.add(e.getPosition())) {
                throw new BusinessException("DUPLICATE_POSITION", "位置重复: " + e.getPosition());
            }
            if (e.getPetId() == null) {
                throw new BusinessException("INVALID_PET", "宠物 ID 不能为空");
            }
            if (!petIds.add(e.getPetId())) {
                throw new BusinessException("DUPLICATE_PET", "同一宠物不能在同一队伍重复占位: " + e.getPetId());
            }
            PlayerPetEntity pet = playerPetMapper.selectById(e.getPetId());
            if (pet == null || !player.getSaveId().equals(pet.getSaveId())) {
                throw new BusinessException("PET_NOT_OWNED", "宠物不存在或不属于当前存档: " + e.getPetId());
            }
        }

        // 全量替换：先删除旧成员，再插入新成员
        playerTeamMemberMapper.delete(new LambdaQueryWrapper<PlayerTeamMemberEntity>()
                .eq(PlayerTeamMemberEntity::getTeamId, team.getId()));

        for (MemberEntry e : entries) {
            PlayerTeamMemberEntity member = new PlayerTeamMemberEntity();
            member.setTeamId(team.getId());
            member.setPetId(e.getPetId());
            member.setPosition(e.getPosition());
            playerTeamMemberMapper.insert(member);
        }

        log.info("队伍成员更新：teamId={}, 成员 {} 名", team.getId(), entries.size());
        return getActiveTeam();
    }

    // ==================== 内部工具 ====================

    /**
     * 查询当前激活队伍全部成员宠物 ID（阶段 5：仓库「是否在队伍」筛选与放生排除用）。
     * 无激活队伍时返回空集合。
     */
    public Set<Long> getActiveTeamPetIds() {
        PlayerEntity player = requirePlayer();
        PlayerTeamEntity team = playerTeamMapper.selectOne(
                new LambdaQueryWrapper<PlayerTeamEntity>()
                        .eq(PlayerTeamEntity::getSaveId, player.getSaveId())
                        .eq(PlayerTeamEntity::getIsActive, true)
                        .last("LIMIT 1"));
        if (team == null) {
            return Set.of();
        }
        List<PlayerTeamMemberEntity> members = playerTeamMemberMapper.selectList(
                new LambdaQueryWrapper<PlayerTeamMemberEntity>()
                        .eq(PlayerTeamMemberEntity::getTeamId, team.getId()));
        Set<Long> petIds = new HashSet<>();
        for (PlayerTeamMemberEntity m : members) {
            petIds.add(m.getPetId());
        }
        return petIds;
    }

    /**
     * 将宠物加入当前激活队伍首个空位（阶段 5：捕捉成功后直接入队）。
     * <p>
     * 校验：宠物属于当前存档、未在队伍中、队伍未满 6 只。
     *
     * @return 加入后的队伍位置（1~6）
     */
    @Transactional
    public int addPetToActiveTeam(Long petId) {
        PlayerEntity player = requirePlayer();
        PlayerTeamEntity team = requireActiveTeam(player.getSaveId());

        PlayerPetEntity pet = playerPetMapper.selectById(petId);
        if (pet == null || !player.getSaveId().equals(pet.getSaveId())) {
            throw new BusinessException("PET_NOT_OWNED", "宠物不存在或不属于当前存档: " + petId);
        }

        List<PlayerTeamMemberEntity> members = playerTeamMemberMapper.selectList(
                new LambdaQueryWrapper<PlayerTeamMemberEntity>()
                        .eq(PlayerTeamMemberEntity::getTeamId, team.getId())
                        .orderByAsc(PlayerTeamMemberEntity::getPosition));
        if (members.size() >= 6) {
            throw new BusinessException("TEAM_FULL", "队伍已满 6 只，无法加入");
        }
        for (PlayerTeamMemberEntity m : members) {
            if (m.getPetId().equals(petId)) {
                throw new BusinessException("PET_ALREADY_IN_TEAM", "宠物已在队伍中: " + petId);
            }
        }

        // 首个空位（位置 1~6）
        Set<Integer> occupied = new HashSet<>();
        for (PlayerTeamMemberEntity m : members) {
            occupied.add(m.getPosition());
        }
        int position = 1;
        while (occupied.contains(position)) {
            position++;
        }

        PlayerTeamMemberEntity member = new PlayerTeamMemberEntity();
        member.setTeamId(team.getId());
        member.setPetId(petId);
        member.setPosition(position);
        playerTeamMemberMapper.insert(member);

        log.info("捕捉宠物入队：petId={}, teamId={}, position={}", petId, team.getId(), position);
        return position;
    }

    private PlayerEntity requirePlayer() {
        PlayerEntity player = playerMapper.selectOne(null);
        if (player == null) {
            throw new BusinessException("NO_SAVE", "不存在存档，请先创建新游戏");
        }
        return player;
    }

    private PlayerTeamEntity requireActiveTeam(String saveId) {
        PlayerTeamEntity team = playerTeamMapper.selectOne(
                new LambdaQueryWrapper<PlayerTeamEntity>()
                        .eq(PlayerTeamEntity::getSaveId, saveId)
                        .eq(PlayerTeamEntity::getIsActive, true)
                        .last("LIMIT 1"));
        if (team == null) {
            throw new BusinessException("NO_ACTIVE_TEAM", "当前没有激活队伍");
        }
        return team;
    }

    // ==================== DTO ====================

    /** 队伍视图。 */
    @lombok.Data
    public static class TeamView {
        private Long teamId;
        private String name;
        private Integer slot;
        private Boolean isActive;
        private List<MemberView> members = new ArrayList<>();

        @lombok.Data
        public static class MemberView {
            private Long memberId;
            private Long petId;
            private Integer position;
            private String speciesId;
            private String nickname;
            private Integer level;
            private Integer currentHp;
            /** 是否首发（队伍位置 1~3，需求 §6）。 */
            private Boolean isStarter;
        }
    }

    /** 更新成员请求。 */
    @lombok.Data
    public static class UpdateMembersRequest {
        private List<MemberEntry> members;
    }

    /** 单个成员条目（前端提交）。 */
    @lombok.Data
    public static class MemberEntry {
        private Long petId;
        private Integer position;
    }
}
