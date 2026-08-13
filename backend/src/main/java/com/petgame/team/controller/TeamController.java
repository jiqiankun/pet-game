package com.petgame.team.controller;

import com.petgame.common.ApiResponse;
import com.petgame.team.service.TeamService;
import org.springframework.web.bind.annotation.*;

/**
 * 队伍接口（阶段 4）。
 * <p>
 * 提供当前激活队伍查询与成员编辑能力。
 * 阶段 4 仅支持 1 套队伍；5 套预设切换与拖拽调整属阶段 6。
 * 战斗阵容实时从激活队伍读取，编辑后立即生效。
 */
@RestController
@RequestMapping("/api/team")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    /**
     * 查询当前激活队伍（含成员与宠物摘要）。
     */
    @GetMapping
    public ApiResponse<TeamService.TeamView> getActiveTeam() {
        return ApiResponse.success(teamService.getActiveTeam());
    }

    /**
     * 整体替换当前激活队伍的成员布局。
     * <p>
     * 前端提交完整的成员列表（最多 6 条，position 1~6 唯一）。
     * 1-3 为首发位置，4-6 为候补位置。
     */
    @PutMapping("/members")
    public ApiResponse<TeamService.TeamView> updateMembers(
            @RequestBody TeamService.UpdateMembersRequest request) {
        return ApiResponse.success(teamService.updateTeamMembers(request));
    }
}
