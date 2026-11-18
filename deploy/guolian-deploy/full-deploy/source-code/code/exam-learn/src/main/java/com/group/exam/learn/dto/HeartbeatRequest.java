package com.group.exam.learn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * LEARN-04 学习进度心跳上报
 */
@Data
public class HeartbeatRequest {

    @NotNull(message = "materialId 不能为空")
    private Long materialId;

    /** 客户端生成 UUID，幂等去重键 */
    @NotBlank(message = "eventId 不能为空")
    private String eventId;

    /** 视频当前播放位置（秒） */
    private Integer positionSec;

    /** 文档当前页码 */
    private Integer pageNo;

    /** 本次新增已读页码（当前仅做契约兼容，不逐页落库） */
    private List<Integer> readPages;

    /** 本次心跳新增有效时长（秒） */
    @NotNull(message = "deltaSec 不能为空")
    private Integer deltaSec;
}
