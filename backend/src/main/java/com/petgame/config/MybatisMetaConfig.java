package com.petgame.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器。
 * <p>
 * 负责 {@code @TableField(fill = FieldFill.INSERT)} / {@code FieldFill.INSERT_UPDATE}
 * 标注的 createdAt / updatedAt / learnedAt 字段自动填充，保证 NOT NULL 约束不被破坏
 * （strictInsertFill 仅对实体中存在且值为 null 的字段生效）。
 */
@Component
public class MybatisMetaConfig implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "learnedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
