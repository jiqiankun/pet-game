package com.petgame.pet.controller;

import com.petgame.common.ApiResponse;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.BuildRecommendationConfig;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.service.PetDetail;
import com.petgame.pet.service.PetService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 宠物接口（阶段 4：养成与队伍闭环）。
 * <p>
 * 提供宠物详情查询、升级、自由加点、洗点、技能装配等能力。
 * 所有计算在后端完成，前端只提交意图（升级模式、加点维度与次数、装备槽位）。
 * 升级、加点、洗点不允许在战斗过程中操作（由前端进入战斗时禁用入口）。
 */
@RestController
@RequestMapping("/api/pets")
public class PetController {

    private final PetService petService;
    private final GameConfigRegistry registry;

    public PetController(PetService petService, GameConfigRegistry registry) {
        this.petService = petService;
        this.registry = registry;
    }

    /**
     * 查询宠物详情（基础 / 属性 / 技能三标签一次返回）。
     */
    @GetMapping("/{petId}")
    public ApiResponse<PetDetail> getPetDetail(@PathVariable Long petId) {
        return ApiResponse.success(petService.getPetDetail(petId));
    }

    /**
     * 升级预览：所需经验、升级后等级、属性变化、获得点数、即将解锁技能。
     *
     * @param to 目标等级（须 > 当前等级且 <= levelCap）
     */
    @GetMapping("/{petId}/level-up/preview")
    public ApiResponse<PetGrowthService.LevelUpPreview> previewLevelUp(
            @PathVariable Long petId, @RequestParam("to") int targetLevel) {
        return ApiResponse.success(petService.previewLevelUp(petId, targetLevel));
    }

    /**
     * 执行升级。支持五种模式（需求 §17）：
     * <ul>
     *   <li>ONE：升 1 级</li>
     *   <li>FIVE：升 5 级（封顶 levelCap）</li>
     *   <li>TO_LEVEL：升到指定 targetLevel</li>
     *   <li>TO_CAP：升到等级上限</li>
     *   <li>CUSTOM_EXP：按投入 exp 尽可能升级</li>
     * </ul>
     */
    @PostMapping("/{petId}/level-up")
    public ApiResponse<PetDetail> levelUp(@PathVariable Long petId,
                                            @RequestBody LevelUpRequest request) {
        return ApiResponse.success(petService.levelUp(petId,
                request.getMode(), request.getTargetLevel(), request.getExp()));
    }

    /**
     * 分配自由属性点（需求 §20 转换表）。
     * <p>
     * points 为投入「次数」，按维度转换为最终属性（HP +5/次、力量等 +1/次、速度 +1/次但消耗 2 点）。
     */
    @PostMapping("/{petId}/allocate-points")
    public ApiResponse<PetDetail> allocatePoints(@PathVariable Long petId,
                                                   @RequestBody AllocatePointsRequest request) {
        return ApiResponse.success(petService.allocatePoints(petId, request.getStat(), request.getPoints()));
    }

    /**
     * 洗点：第一阶段免费（需求 §21），全部已分配自由点数返还为可用。
     */
    @PostMapping("/{petId}/reset-points")
    public ApiResponse<PetDetail> resetPoints(@PathVariable Long petId) {
        return ApiResponse.success(petService.resetPoints(petId));
    }

    /**
     * 装备技能到指定槽位（需求 §24，槽位 1~4）。
     */
    @PostMapping("/{petId}/skills/equip")
    public ApiResponse<PetDetail> equipSkill(@PathVariable Long petId,
                                                @RequestBody EquipSkillRequest request) {
        return ApiResponse.success(petService.equipSkill(petId, request.getSkillId(), request.getSlot()));
    }

    /**
     * 卸下指定槽位的技能（仍保留为已学习状态）。
     */
    @PostMapping("/{petId}/skills/unequip")
    public ApiResponse<PetDetail> unequipSkill(@PathVariable Long petId,
                                                 @RequestBody UnequipSkillRequest request) {
        return ApiResponse.success(petService.unequipSkill(petId, request.getSlot()));
    }

    // ==================== 技能书接口（阶段 10） ====================

    /**
     * 使用技能书学习技能。
     */
    @PostMapping("/{petId}/learn-skill-book")
    public ApiResponse<PetDetail> learnSkillBook(@PathVariable Long petId,
                                                   @RequestBody LearnSkillBookRequest request) {
        return ApiResponse.success(petService.learnSkillBook(petId, request.getItemId(), request.getForgetSkillId()));
    }

    /**
     * 遗忘技能书主动技能。
     */
    @PostMapping("/{petId}/forget-book-skill")
    public ApiResponse<PetDetail> forgetBookSkill(@PathVariable Long petId,
                                                    @RequestBody ForgetBookSkillRequest request) {
        return ApiResponse.success(petService.forgetBookSkill(petId, request.getSkillId()));
    }

    /**
     * 装备技能书主动技能（槽位 5~6）。
     */
    @PostMapping("/{petId}/equip-book-skill")
    public ApiResponse<PetDetail> equipBookSkill(@PathVariable Long petId,
                                                   @RequestBody EquipBookSkillRequest request) {
        return ApiResponse.success(petService.equipBookSkill(petId, request.getSkillId(), request.getBookSlot()));
    }

    /**
     * 卸下技能书主动技能。
     */
    @PostMapping("/{petId}/unequip-book-skill")
    public ApiResponse<PetDetail> unequipBookSkill(@PathVariable Long petId,
                                                     @RequestBody UnequipBookSkillRequest request) {
        return ApiResponse.success(petService.unequipBookSkill(petId, request.getBookSlot()));
    }

    // ==================== 推荐 Build 接口（阶段 10） ====================

    /**
     * 查询宠物种族推荐 Build 方案（纯展示，不修改数据）。
     */
    @GetMapping("/{petId}/build-recommendations")
    public ApiResponse<List<BuildRecommendationConfig.BuildConfig>> getBuildRecommendations(
            @PathVariable Long petId) {
        PetDetail detail = petService.getPetDetail(petId);
        String speciesId = detail.getPet().getSpeciesId();
        BuildRecommendationConfig config = registry.getBuildRecommendationsConfig();
        if (config == null || config.getRecommendations() == null) {
            return ApiResponse.success(List.of());
        }
        for (BuildRecommendationConfig.SpeciesBuildConfig rec : config.getRecommendations()) {
            if (speciesId.equals(rec.getSpeciesId())) {
                return ApiResponse.success(rec.getBuilds());
            }
        }
        return ApiResponse.success(List.of());
    }

    // ---- 请求 DTO ----

    @Data
    public static class LevelUpRequest {
        /** 升级模式：ONE / FIVE / TO_LEVEL / TO_CAP / CUSTOM_EXP。 */
        private String mode;
        /** TO_LEVEL 模式目标等级。 */
        private Integer targetLevel;
        /** CUSTOM_EXP 模式投入经验量。 */
        private Integer exp;
    }

    @Data
    public static class AllocatePointsRequest {
        /** 维度键：HP / STRENGTH / SPIRIT / DEFENSE / RESISTANCE / SPEED。 */
        private String stat;
        /** 投入次数（>0）。 */
        private Integer points;
    }

    @Data
    public static class EquipSkillRequest {
        private String skillId;
        /** 槽位 1~4。 */
        private Integer slot;
    }

    @Data
    public static class UnequipSkillRequest {
        /** 槽位 1~4。 */
        private Integer slot;
    }

    @Data
    public static class LearnSkillBookRequest {
        private String itemId;
        private String forgetSkillId;
    }

    @Data
    public static class ForgetBookSkillRequest {
        private String skillId;
    }

    @Data
    public static class EquipBookSkillRequest {
        private String skillId;
        /** 技能书槽位 5~6。 */
        private Integer bookSlot;
    }

    @Data
    public static class UnequipBookSkillRequest {
        /** 技能书槽位 5~6。 */
        private Integer bookSlot;
    }
}
