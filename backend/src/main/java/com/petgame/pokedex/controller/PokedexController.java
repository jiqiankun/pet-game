package com.petgame.pokedex.controller;

import com.petgame.common.ApiResponse;
import com.petgame.pokedex.service.PokedexService;
import com.petgame.pokedex.vo.PokedexDetailVo;
import com.petgame.pokedex.vo.PokedexEntryVo;
import com.petgame.pokedex.vo.WildIdentificationVo;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.service.GameService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 图鉴 REST 控制器（阶段 8）。
 */
@RestController
@RequestMapping("/api/pokedex")
public class PokedexController {

    private final PokedexService pokedexService;
    private final GameService gameService;

    public PokedexController(PokedexService pokedexService, GameService gameService) {
        this.pokedexService = pokedexService;
        this.gameService = gameService;
    }

    /** 返回全量图鉴列表（含研究等级 + 已解锁信息摘要）。 */
    @GetMapping
    public ApiResponse<List<PokedexEntryVo>> listPokedex() {
        PlayerEntity player = gameService.getCurrentPlayer();
        return ApiResponse.success(pokedexService.getFullPokedex(player.getSaveId()));
    }

    /** 返回单个种族详情（完整已解锁信息 + 历史记录）。 */
    @GetMapping("/{speciesId}")
    public ApiResponse<PokedexDetailVo> getSpeciesEntry(@PathVariable String speciesId) {
        PlayerEntity player = gameService.getCurrentPlayer();
        return ApiResponse.success(pokedexService.getSpeciesEntry(player.getSaveId(), speciesId));
    }

    /**
     * Lv.5 野外识别（遭遇时调用，返回资质预估等级）。
     * <p>
     * 请求体须包含六维资质数组。
     */
    @PostMapping("/{speciesId}/identify")
    public ApiResponse<WildIdentificationVo> identifyWild(@PathVariable String speciesId,
                                                           @RequestBody IdentifyRequest request) {
        PlayerEntity player = gameService.getCurrentPlayer();
        WildIdentificationVo result = pokedexService.getWildIdentification(
                player.getSaveId(), speciesId, request.getAptitudes());
        return ApiResponse.success(result);
    }

    @Data
    public static class IdentifyRequest {
        /** 六维资质 [hp, str, spr, def, res, spd]。 */
        private int[] aptitudes;
    }
}
