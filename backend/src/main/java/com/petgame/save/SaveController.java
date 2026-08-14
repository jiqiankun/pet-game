package com.petgame.save;

import com.petgame.common.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 存档备份接口（阶段 14）。
 * <p>
 * 提供存档导出（下载 .pet-save.zip）、导入、手动备份、重置游戏与备份列表查询。
 */
@RestController
@RequestMapping("/api/save")
public class SaveController {

    private final SaveBackupService saveBackupService;

    public SaveController(SaveBackupService saveBackupService) {
        this.saveBackupService = saveBackupService;
    }

    /**
     * 导出当前存档（下载 .pet-save.zip）。
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportSave() {
        byte[] bytes = saveBackupService.exportSaveBytes();
        String filename = URLEncoder.encode(saveBackupService.exportFileName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(bytes);
    }

    /**
     * 导入存档（上传 .pet-save.zip）。导入前自动备份当前存档，失败回滚。
     */
    @PostMapping("/import")
    public ApiResponse<Map<String, String>> importSave(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error("INVALID_SAVE_FILE", "请选择存档文件");
        }
        try {
            saveBackupService.importSave(file.getBytes());
            return ApiResponse.success(Map.of("status", "imported"));
        } catch (IOException e) {
            return ApiResponse.error("INVALID_SAVE_FILE", "存档文件读取失败：" + e.getMessage());
        }
    }

    /**
     * 手动备份当前存档。
     */
    @PostMapping("/backup")
    public ApiResponse<Map<String, String>> manualBackup() {
        String filename = saveBackupService.createBackup("manual");
        return ApiResponse.success(Map.of("status", "backed-up", "file", filename));
    }

    /**
     * 查询备份列表。
     */
    @GetMapping("/backups")
    public ApiResponse<List<String>> listBackups() {
        return ApiResponse.success(saveBackupService.listBackups());
    }

    /**
     * 重置游戏（先自动备份，再清空当前存档）。
     */
    @PostMapping("/reset")
    public ApiResponse<Map<String, String>> resetGame() {
        saveBackupService.resetGame();
        return ApiResponse.success(Map.of("status", "reset"));
    }
}