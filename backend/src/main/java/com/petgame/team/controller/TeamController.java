package com.petgame.team.controller;

import com.petgame.common.ApiResponse;
import com.petgame.team.service.TeamService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 队伍接口（阶段 4 基础，阶段 6 扩展 5 套预设）。
 * <p>
 * 提供当前激活队伍查询、成员编辑、预设列表与预设切换。
 * 战斗阵容实时从激活队伍读取，编辑/切换后立即生效；战斗中禁止编辑与切换。
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
     * 查询全部 5 套预设（阶段 6，缺失预设懒创建）。
     */
    @GetMapping("/presets")
    public ApiResponse<List<TeamService.TeamPresetView>> getPresets() {
        return ApiResponse.success(teamService.getTeamPresets());
    }

    /**
     * 切换激活预设（阶段 6）；战斗中禁止切换。
     */
    @PostMapping("/presets/{teamId}/activate")
    public ApiResponse<List<TeamService.TeamPresetView>> activatePreset(@PathVariable Long teamId) {
        return ApiResponse.success(teamService.activatePreset(teamId));
    }

    /**
     * 整体替换队伍成员布局。
     * <p>
     * 前端提交完整的成员列表（最多 6 条，position 1~6 唯一）。
     * 1-3 为首发位置，4-6 为候补位置。
     * request.teamId 为空时编辑当前激活队伍；阶段 6 起可指定任意预设。
     */
    @PutMapping("/members")
    public ApiResponse<TeamService.TeamView> updateMembers(
            @RequestBody TeamService.UpdateMembersRequest request) {
        return ApiResponse.success(teamService.updateTeamMembers(request));
    }
}
