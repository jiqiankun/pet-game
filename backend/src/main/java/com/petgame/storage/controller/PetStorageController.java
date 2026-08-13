package com.petgame.storage.controller;

import com.petgame.common.ApiResponse;
import com.petgame.storage.PetStorageService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 宠物仓库接口（阶段 5）。
 * <p>
 * 仓库不限容量：筛选排序浏览、昵称、锁定、收藏、放生预览与放生（临别礼物）。
 */
@RestController
@RequestMapping("/api/storage")
public class PetStorageController {

    private final PetStorageService storageService;

    public PetStorageController(PetStorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * 查询仓库：按名称/属性/稀有度/等级/综合资质/稀有技能/特殊外观/收藏/锁定/是否在队伍筛选，
     * 按等级/稀有度/综合资质/捕获时间排序。
     */
    @GetMapping("/pets")
    public ApiResponse<List<PetStorageService.StoragePetView>> listStorage(
            PetStorageService.StorageQueryRequest request) {
        return ApiResponse.success(storageService.listStorage(request));
    }

    /** 设置昵称（空字符串或 null = 清除昵称，恢复显示种族名称）。 */
    @PutMapping("/pets/{petId}/nickname")
    public ApiResponse<PetStorageService.StoragePetView> setNickname(
            @PathVariable Long petId, @RequestBody NicknameRequest request) {
        return ApiResponse.success(storageService.setNickname(petId, request.getNickname()));
    }

    /** 设置锁定状态（锁定后禁止放生）。 */
    @PutMapping("/pets/{petId}/locked")
    public ApiResponse<PetStorageService.StoragePetView> setLocked(
            @PathVariable Long petId, @RequestBody FlagRequest request) {
        return ApiResponse.success(storageService.setLocked(petId, request.isValue()));
    }

    /** 设置收藏状态（收藏宠物批量放生时自动排除）。 */
    @PutMapping("/pets/{petId}/favorite")
    public ApiResponse<PetStorageService.StoragePetView> setFavorite(
            @PathVariable Long petId, @RequestBody FlagRequest request) {
        return ApiResponse.success(storageService.setFavorite(petId, request.isValue()));
    }

    /** 放生预览：逐只返回可否放生、保护原因、礼物点数与额外警告原因。 */
    @PostMapping("/release-preview")
    public ApiResponse<PetStorageService.ReleasePreview> previewRelease(
            @RequestBody ReleaseRequest request) {
        return ApiResponse.success(storageService.previewRelease(request.getPetIds()));
    }

    /**
     * 执行放生（单只与批量）：自动排除锁定、收藏、在队宠物，礼物汇总发放。
     * 单只放生受保护时明确报错；批量放生静默排除并在结果中列出跳过原因。
     */
    @PostMapping("/release")
    public ApiResponse<PetStorageService.ReleaseResult> release(
            @RequestBody ReleaseRequest request) {
        return ApiResponse.success(storageService.releasePets(request.getPetIds()));
    }

    @Data
    public static class NicknameRequest {
        private String nickname;
    }

    @Data
    public static class FlagRequest {
        private boolean value;
    }

    @Data
    public static class ReleaseRequest {
        private List<Long> petIds;
    }
}
