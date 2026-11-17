package com.group.exam.exam.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 按组织范围生成考生名单（EXAM-07），重复调用为全量重建
 */
@Data
public class CandidateScopeReq implements Serializable {

    private List<ScopeItem> scopes;

    @Data
    public static class ScopeItem implements Serializable {
        private Long orgId;
        private Boolean includeSub;
    }
}
