package com.group.exam.learn.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * LEARN-08 下达学习任务
 */
@Data
public class LearnTaskCreateRequest {

    @NotBlank(message = "任务名称不能为空")
    private String title;

    /** 指定资料（须已发布） */
    @NotEmpty(message = "资料不能为空")
    private List<Long> materialIds;

    /** 要求有效学习时长（秒） */
    @NotNull(message = "requiredSec 不能为空")
    private Integer requiredSec;

    /** 完成期限 */
    @NotNull(message = "deadline 不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadline;

    /** 目标单位（含下级，服务端展开为人员快照） */
    private List<Long> targetOrgIds;

    /** 额外指定人员 */
    private List<Long> userIds;

    private String remark;
}
