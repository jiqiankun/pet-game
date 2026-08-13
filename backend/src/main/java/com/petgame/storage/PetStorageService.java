package com.petgame.storage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.common.BusinessException;
import com.petgame.common.GameRandom;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.ItemConfig;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.config.model.ReleaseGiftsConfig;
import com.petgame.config.model.SystemRuleConfig;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.entity.PlayerPetSkillEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.pet.mapper.PlayerPetSkillMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.team.service.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 宠物仓库服务（阶段 5）。
 * <p>
 * 不限容量的宠物仓库管理：筛选排序浏览、昵称、锁定、收藏、放生与临别礼物。
 * <ul>
 *   <li>筛选：名称/属性/稀有度/等级/综合资质/稀有技能/特殊外观/收藏/锁定/是否在队伍。</li>
 *   <li>排序：等级/稀有度/综合资质/捕获时间（升序或降序）。</li>
 *   <li>锁定后禁止放生；批量放生自动排除锁定、收藏、在队宠物。</li>
 *   <li>放生表现为临别礼物（决策七），单事务内删除宠物并发放礼物。</li>
 * </ul>
 */
@Service
public class PetStorageService {

    private static final Logger log = LoggerFactory.getLogger(PetStorageService.class);

    /** 昵称最大长度。 */
    public static final int MAX_NICKNAME_LENGTH = 12;

    /** 稀有度排序权重。 */
    private static final List<String> RARITY_ORDER =
            List.of("COMMON", "RARE", "EPIC", "LEGENDARY");

    private final PlayerMapper playerMapper;
    private final PlayerPetMapper playerPetMapper;
    private final PlayerPetSkillMapper playerPetSkillMapper;
    private final PlayerInventoryMapper playerInventoryMapper;
    private final TeamService teamService;
    private final PetGrowthService growthService;
    private final GameConfigRegistry registry;

    public PetStorageService(PlayerMapper playerMapper,
                             PlayerPetMapper playerPetMapper,
                             PlayerPetSkillMapper playerPetSkillMapper,
                             PlayerInventoryMapper playerInventoryMapper,
                             TeamService teamService,
                             PetGrowthService growthService,
                             GameConfigRegistry registry) {
        this.playerMapper = playerMapper;
        this.playerPetMapper = playerPetMapper;
        this.playerPetSkillMapper = playerPetSkillMapper;
        this.playerInventoryMapper = playerInventoryMapper;
        this.teamService = teamService;
        this.growthService = growthService;
        this.registry = registry;
    }

    // ==================== 仓库浏览 ====================

    /**
     * 查询仓库（不限容量）：全部宠物按筛选条件过滤、按排序规则排序。
     */
    public List<StoragePetView> listStorage(StorageQueryRequest request) {
        PlayerEntity player = requirePlayer();
        StorageQueryRequest q = request != null ? request : new StorageQueryRequest();
        Set<Long> teamPetIds = teamService.getActiveTeamPetIds();

        List<PlayerPetEntity> pets = playerPetMapper.selectList(
                new LambdaQueryWrapper<PlayerPetEntity>()
                        .eq(PlayerPetEntity::getSaveId, player.getSaveId()));

        List<StoragePetView> views = new ArrayList<>();
        for (PlayerPetEntity pet : pets) {
            PetSpeciesConfig species = registry.getSpecies(pet.getSpeciesId());
            if (species == null) {
                continue;
            }
            StoragePetView view = toStorageView(pet, species, teamPetIds.contains(pet.getId()));
            if (!matchesFilter(view, q)) {
                continue;
            }
            views.add(view);
        }

        sortViews(views, q.getSortBy(), q.getSortDirection());
        return views;
    }

    private StoragePetView toStorageView(PlayerPetEntity pet, PetSpeciesConfig species, boolean inTeam) {
        StoragePetView view = new StoragePetView();
        view.setPetId(pet.getId());
        view.setSpeciesId(species.getId());
        view.setSpeciesName(species.getName());
        view.setNickname(pet.getNickname());
        view.setElement(species.getElement());
        view.setRarity(species.getRarity());
        view.setLevel(nz(pet.getLevel()));
        view.setCapturedLevel(nz(pet.getCapturedLevel()));
        view.setAptitudeTotal(aptitudeTotal(pet));
        view.setAptitudeAverage(Math.round(view.getAptitudeTotal() / 6.0 * 10.0) / 10.0);
        view.setLocked(Boolean.TRUE.equals(pet.getLocked()));
        view.setFavorite(Boolean.TRUE.equals(pet.getFavorite()));
        view.setInTeam(inTeam);
        view.setStarter(Boolean.TRUE.equals(pet.getIsStarter()));
        view.setSpecialAppearance(pet.getSpecialAppearance());
        view.setCapturedMapId(pet.getCapturedMapId());
        view.setCapturedAt(pet.getCapturedAt());
        view.setCurrentHp(nz(pet.getCurrentHp()));
        view.setRareSkillIds(loadRareSkillIds(pet.getId()));
        return view;
    }

    /** 稀有技能 = 捕捉时携带的额外技能（sourceType=CAPTURE）。 */
    private List<String> loadRareSkillIds(Long petId) {
        List<PlayerPetSkillEntity> records = playerPetSkillMapper.selectList(
                new LambdaQueryWrapper<PlayerPetSkillEntity>()
                        .eq(PlayerPetSkillEntity::getPetId, petId)
                        .eq(PlayerPetSkillEntity::getSourceType, "CAPTURE"));
        List<String> ids = new ArrayList<>();
        for (PlayerPetSkillEntity rec : records) {
            ids.add(rec.getSkillId());
        }
        return ids;
    }

    /** 综合资质 = 六维资质总和。 */
    private int aptitudeTotal(PlayerPetEntity pet) {
        return nz(pet.getHpAptitude()) + nz(pet.getStrengthAptitude()) + nz(pet.getSpiritAptitude())
                + nz(pet.getDefenseAptitude()) + nz(pet.getResistanceAptitude()) + nz(pet.getSpeedAptitude());
    }

    private boolean matchesFilter(StoragePetView view, StorageQueryRequest q) {
        if (q.getName() != null && !q.getName().isBlank()) {
            String keyword = q.getName().trim();
            boolean nameMatch = view.getSpeciesName().contains(keyword)
                    || (view.getNickname() != null && view.getNickname().contains(keyword));
            if (!nameMatch) {
                return false;
            }
        }
        if (q.getElement() != null && !q.getElement().isBlank()
                && !q.getElement().equalsIgnoreCase(view.getElement())) {
            return false;
        }
        if (q.getRarity() != null && !q.getRarity().isBlank()
                && !q.getRarity().equalsIgnoreCase(view.getRarity())) {
            return false;
        }
        if (q.getLevelMin() != null && view.getLevel() < q.getLevelMin()) {
            return false;
        }
        if (q.getLevelMax() != null && view.getLevel() > q.getLevelMax()) {
            return false;
        }
        if (q.getAptitudeMin() != null && view.getAptitudeTotal() < q.getAptitudeMin()) {
            return false;
        }
        if (Boolean.TRUE.equals(q.getHasRareSkill()) && view.getRareSkillIds().isEmpty()) {
            return false;
        }
        if (Boolean.TRUE.equals(q.getHasSpecialAppearance()) && view.getSpecialAppearance() == null) {
            return false;
        }
        if (Boolean.TRUE.equals(q.getFavoriteOnly()) && !view.isFavorite()) {
            return false;
        }
        if (Boolean.TRUE.equals(q.getLockedOnly()) && !view.isLocked()) {
            return false;
        }
        if (q.getInTeam() != null && q.getInTeam() != view.isInTeam()) {
            return false;
        }
        return true;
    }

    private void sortViews(List<StoragePetView> views, String sortBy, String sortDirection) {
        Comparator<StoragePetView> comparator;
        String key = sortBy != null ? sortBy.toUpperCase() : "CAPTURED_AT";
        switch (key) {
            case "LEVEL" -> comparator = Comparator.comparingInt(StoragePetView::getLevel);
            case "RARITY" -> comparator = Comparator.comparingInt(
                    v -> RARITY_ORDER.indexOf(v.getRarity()));
            case "APTITUDE" -> comparator = Comparator.comparingInt(StoragePetView::getAptitudeTotal);
            default -> comparator = Comparator.comparing(
                    StoragePetView::getCapturedAt, Comparator.nullsFirst(Comparator.naturalOrder()));
        }
        if ("ASC".equalsIgnoreCase(sortDirection)) {
            views.sort(comparator.thenComparing(StoragePetView::getPetId));
        } else {
            views.sort(comparator.reversed().thenComparing(
                    StoragePetView::getPetId, Comparator.reverseOrder()));
        }
    }

    // ==================== 昵称 / 锁定 / 收藏 ====================

    /**
     * 设置昵称（空或 null = 清除昵称，恢复显示种族名称；种族名称始终保留由前端叠加展示）。
     */
    @Transactional
    public StoragePetView setNickname(Long petId, String nickname) {
        PlayerPetEntity pet = requireOwnedPet(petId);
        String trimmed = nickname == null ? null : nickname.trim();
        if (trimmed != null && !trimmed.isEmpty() && trimmed.length() > MAX_NICKNAME_LENGTH) {
            throw new BusinessException("INVALID_NICKNAME",
                    "昵称长度不能超过 " + MAX_NICKNAME_LENGTH + " 个字符");
        }
        pet.setNickname(trimmed == null || trimmed.isEmpty() ? null : trimmed);
        playerPetMapper.updateById(pet);
        log.info("宠物昵称更新：petId={}, nickname={}", petId, pet.getNickname());
        return viewOf(pet);
    }

    /** 设置锁定状态（锁定后禁止放生）。 */
    @Transactional
    public StoragePetView setLocked(Long petId, boolean locked) {
        PlayerPetEntity pet = requireOwnedPet(petId);
        pet.setLocked(locked);
        playerPetMapper.updateById(pet);
        log.info("宠物锁定更新：petId={}, locked={}", petId, locked);
        return viewOf(pet);
    }

    /** 设置收藏状态（收藏宠物批量放生时自动排除）。 */
    @Transactional
    public StoragePetView setFavorite(Long petId, boolean favorite) {
        PlayerPetEntity pet = requireOwnedPet(petId);
        pet.setFavorite(favorite);
        playerPetMapper.updateById(pet);
        log.info("宠物收藏更新：petId={}, favorite={}", petId, favorite);
        return viewOf(pet);
    }

    // ==================== 放生 ====================

    /**
     * 放生预览：逐只返回可否放生、保护原因、应得礼物点数与额外警告原因。
     */
    public ReleasePreview previewRelease(List<Long> petIds) {
        requirePlayer();
        Set<Long> teamPetIds = teamService.getActiveTeamPetIds();
        SystemRuleConfig rules = registry.getSystemRules();

        ReleasePreview preview = new ReleasePreview();
        if (petIds == null) {
            return preview;
        }
        for (Long petId : petIds) {
            PlayerPetEntity pet = playerPetMapper.selectById(petId);
            if (pet == null) {
                continue;
            }
            PetSpeciesConfig species = registry.getSpecies(pet.getSpeciesId());
            ReleasePreview.PetReleaseInfo info = new ReleasePreview.PetReleaseInfo();
            info.setPetId(petId);
            info.setName(pet.getNickname() != null && !pet.getNickname().isBlank()
                    ? pet.getNickname() : (species != null ? species.getName() : pet.getSpeciesId()));
            info.setRarity(species != null ? species.getRarity() : "COMMON");

            if (Boolean.TRUE.equals(pet.getLocked())) {
                info.getBlockReasons().add("LOCKED");
            }
            if (Boolean.TRUE.equals(pet.getFavorite())) {
                info.getBlockReasons().add("FAVORITE");
            }
            if (teamPetIds.contains(petId)) {
                info.getBlockReasons().add("IN_TEAM");
            }
            info.setReleasable(info.getBlockReasons().isEmpty());

            if (species != null) {
                info.setGiftPoints(ReleaseGiftCalculator.computeGiftPoints(species.getRarity(),
                        nz(pet.getCapturedLevel()), growthService.allocatedFreePoints(pet), rules));
                buildWarnings(info, pet, species, rules);
            }
            preview.getPets().add(info);
            if (info.isReleasable()) {
                preview.setTotalGiftPoints(preview.getTotalGiftPoints() + info.getGiftPoints());
            }
        }
        return preview;
    }

    /** 珍稀/传说/高资质/稀有技能/特殊外观放生额外警告（前端二次确认依据）。 */
    private void buildWarnings(ReleasePreview.PetReleaseInfo info, PlayerPetEntity pet,
                               PetSpeciesConfig species, SystemRuleConfig rules) {
        if ("EPIC".equals(species.getRarity()) || "LEGENDARY".equals(species.getRarity())) {
            info.getWarningReasons().add("HIGH_RARITY");
        }
        double averageAptitude = aptitudeTotal(pet) / 6.0;
        if (averageAptitude >= rules.getReleaseWarningAptitudeThreshold()) {
            info.getWarningReasons().add("HIGH_APTITUDE");
        }
        if (!loadRareSkillIds(pet.getId()).isEmpty()) {
            info.getWarningReasons().add("RARE_SKILL");
        }
        if (pet.getSpecialAppearance() != null) {
            info.getWarningReasons().add("SPECIAL_APPEARANCE");
        }
    }

    /**
     * 执行放生（单只与批量）：自动排除锁定、收藏、在队宠物，同事务删除宠物并发放汇总礼物。
     * <p>
     * 单只放生（列表仅 1 只）若受保护则明确报错；批量放生静默排除受保护宠物。
     *
     * @return 放生结果（实际放生明细 + 汇总礼物）
     */
    @Transactional
    public ReleaseResult releasePets(List<Long> petIds) {
        if (petIds == null || petIds.isEmpty()) {
            throw new BusinessException("INVALID_REQUEST", "放生宠物列表不能为空");
        }
        PlayerEntity player = requirePlayer();
        Set<Long> teamPetIds = teamService.getActiveTeamPetIds();
        SystemRuleConfig rules = registry.getSystemRules();
        List<ReleaseGiftsConfig.GiftEntry> giftPool = registry.getReleaseGiftsConfig().getGifts();

        boolean single = petIds.size() == 1;
        ReleaseResult result = new ReleaseResult();
        GameRandom random = new GameRandom();
        int totalPoints = 0;

        for (Long petId : petIds) {
            PlayerPetEntity pet = playerPetMapper.selectById(petId);
            if (pet == null || !player.getSaveId().equals(pet.getSaveId())) {
                if (single) {
                    throw new BusinessException("PET_NOT_OWNED", "宠物不存在或不属于当前存档: " + petId);
                }
                continue;
            }
            PetSpeciesConfig species = registry.getSpecies(pet.getSpeciesId());
            String blockReason = null;
            if (Boolean.TRUE.equals(pet.getLocked())) {
                blockReason = "LOCKED";
            } else if (Boolean.TRUE.equals(pet.getFavorite())) {
                blockReason = "FAVORITE";
            } else if (teamPetIds.contains(petId)) {
                blockReason = "IN_TEAM";
            }
            if (blockReason != null) {
                if (single) {
                    throw new BusinessException("PET_PROTECTED", "该宠物受保护无法放生（" + blockReason + "）");
                }
                result.getSkipped().add(new ReleaseResult.SkippedPet(petId, blockReason));
                continue;
            }

            int points = species == null ? 0 : ReleaseGiftCalculator.computeGiftPoints(
                    species.getRarity(), nz(pet.getCapturedLevel()),
                    growthService.allocatedFreePoints(pet), rules);
            totalPoints += points;

            // 删除宠物与技能记录
            playerPetSkillMapper.delete(new LambdaQueryWrapper<PlayerPetSkillEntity>()
                    .eq(PlayerPetSkillEntity::getPetId, petId));
            playerPetMapper.deleteById(petId);

            ReleaseResult.ReleasedPet released = new ReleaseResult.ReleasedPet();
            released.setPetId(petId);
            released.setSpeciesId(pet.getSpeciesId());
            released.setName(species != null ? species.getName() : pet.getSpeciesId());
            released.setLevel(nz(pet.getLevel()));
            released.setGiftPoints(points);
            result.getReleased().add(released);
        }

        if (result.getReleased().isEmpty()) {
            throw new BusinessException("NO_RELEASABLE_PETS", "没有可放生的宠物（均受保护或不存在）");
        }

        // 汇总礼物：按总点数一次性抽取（批量放生汇总发放）
        List<ReleaseGiftCalculator.GiftResult> gifts =
                ReleaseGiftCalculator.rollGifts(totalPoints, giftPool, random);
        applyGifts(player, gifts);
        playerMapper.updateById(player);

        result.setTotalGiftPoints(totalPoints);
        result.setGifts(gifts);
        log.info("放生完成：{} 只，礼物总点数 {}，礼物 {} 项",
                result.getReleased().size(), totalPoints, gifts.size());
        return result;
    }

    /** 发放礼物：GOLD → 玩家金币、EXP → 公共经验池、ITEM → 背包。 */
    private void applyGifts(PlayerEntity player, List<ReleaseGiftCalculator.GiftResult> gifts) {
        for (ReleaseGiftCalculator.GiftResult gift : gifts) {
            switch (gift.getType()) {
                case "GOLD" -> player.setGold(nz(player.getGold()) + gift.getQuantity());
                case "EXP" -> player.setExpPool(nz(player.getExpPool()) + gift.getQuantity());
                case "ITEM" -> {
                    ItemConfig item = registry.getItem(gift.getItemId());
                    if (item == null) {
                        log.warn("放生礼物道具配置缺失，跳过: {}", gift.getItemId());
                        continue;
                    }
                    addInventoryItem(player.getSaveId(), gift.getItemId(), gift.getQuantity());
                }
                default -> log.warn("未知放生礼物类型: {}", gift.getType());
            }
        }
    }

    /** 增加玩家背包道具数量（已存在则累加，不存在则新增）。 */
    private void addInventoryItem(String saveId, String itemId, int quantity) {
        PlayerInventoryEntity existing = playerInventoryMapper.selectOne(
                new LambdaQueryWrapper<PlayerInventoryEntity>()
                        .eq(PlayerInventoryEntity::getSaveId, saveId)
                        .eq(PlayerInventoryEntity::getItemId, itemId));
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
            playerInventoryMapper.updateById(existing);
        } else {
            PlayerInventoryEntity inv = new PlayerInventoryEntity();
            inv.setSaveId(saveId);
            inv.setItemId(itemId);
            inv.setQuantity(quantity);
            playerInventoryMapper.insert(inv);
        }
    }

    // ==================== 内部工具 ====================

    private PlayerEntity requirePlayer() {
        PlayerEntity player = playerMapper.selectOne(null);
        if (player == null) {
            throw new BusinessException("NO_SAVE", "不存在存档，请先创建新游戏");
        }
        return player;
    }

    private PlayerPetEntity requireOwnedPet(Long petId) {
        PlayerEntity player = requirePlayer();
        if (petId == null) {
            throw new BusinessException("INVALID_PET", "宠物 ID 不能为空");
        }
        PlayerPetEntity pet = playerPetMapper.selectById(petId);
        if (pet == null || !player.getSaveId().equals(pet.getSaveId())) {
            throw new BusinessException("PET_NOT_OWNED", "宠物不存在或不属于当前存档: " + petId);
        }
        return pet;
    }

    private StoragePetView viewOf(PlayerPetEntity pet) {
        PetSpeciesConfig species = registry.getSpecies(pet.getSpeciesId());
        boolean inTeam = teamService.getActiveTeamPetIds().contains(pet.getId());
        return toStorageView(pet, species, inTeam);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    // ==================== DTO ====================

    /** 仓库查询请求（全部条件可选）。 */
    @lombok.Data
    public static class StorageQueryRequest {
        /** 名称模糊筛选（种族名称或昵称）。 */
        private String name;
        /** 属性筛选。 */
        private String element;
        /** 稀有度筛选。 */
        private String rarity;
        private Integer levelMin;
        private Integer levelMax;
        /** 综合资质（六维总和）下限。 */
        private Integer aptitudeMin;
        /** 仅含稀有技能。 */
        private Boolean hasRareSkill;
        /** 仅特殊外观。 */
        private Boolean hasSpecialAppearance;
        /** 仅收藏。 */
        private Boolean favoriteOnly;
        /** 仅锁定。 */
        private Boolean lockedOnly;
        /** 是否在队伍（true/false 筛选，null 不过滤）。 */
        private Boolean inTeam;
        /** 排序键：LEVEL / RARITY / APTITUDE / CAPTURED_AT（默认）。 */
        private String sortBy;
        /** 排序方向：ASC / DESC（默认）。 */
        private String sortDirection;
    }

    /** 仓库宠物视图。 */
    @lombok.Data
    public static class StoragePetView {
        private Long petId;
        private String speciesId;
        /** 种族名称（始终保留，昵称叠加展示）。 */
        private String speciesName;
        private String nickname;
        private String element;
        private String rarity;
        private int level;
        private int capturedLevel;
        /** 综合资质（六维总和）。 */
        private int aptitudeTotal;
        /** 平均资质（保留 1 位小数）。 */
        private double aptitudeAverage;
        private boolean locked;
        private boolean favorite;
        private boolean inTeam;
        private boolean starter;
        private String specialAppearance;
        private String capturedMapId;
        private LocalDateTime capturedAt;
        private int currentHp;
        private List<String> rareSkillIds = new ArrayList<>();
    }

    /** 放生预览。 */
    @lombok.Data
    public static class ReleasePreview {
        private List<PetReleaseInfo> pets = new ArrayList<>();
        /** 可放生宠物的礼物点数合计。 */
        private int totalGiftPoints;

        @lombok.Data
        public static class PetReleaseInfo {
            private Long petId;
            private String name;
            private String rarity;
            private boolean releasable;
            /** 保护原因：LOCKED / FAVORITE / IN_TEAM。 */
            private List<String> blockReasons = new ArrayList<>();
            /** 额外警告原因：HIGH_RARITY / HIGH_APTITUDE / RARE_SKILL / SPECIAL_APPEARANCE。 */
            private List<String> warningReasons = new ArrayList<>();
            private int giftPoints;
        }
    }

    /** 放生结果。 */
    @lombok.Data
    public static class ReleaseResult {
        private List<ReleasedPet> released = new ArrayList<>();
        private List<SkippedPet> skipped = new ArrayList<>();
        /** 礼物总价值点数。 */
        private int totalGiftPoints;
        /** 汇总礼物明细。 */
        private List<ReleaseGiftCalculator.GiftResult> gifts = new ArrayList<>();

        @lombok.Data
        public static class ReleasedPet {
            private Long petId;
            private String speciesId;
            private String name;
            private int level;
            private int giftPoints;
        }

        @lombok.Data
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        public static class SkippedPet {
            private Long petId;
            /** 排除原因：LOCKED / FAVORITE / IN_TEAM。 */
            private String reason;
        }
    }
}
