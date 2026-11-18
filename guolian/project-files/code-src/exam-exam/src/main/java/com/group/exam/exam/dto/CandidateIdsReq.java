package com.group.exam.exam.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 追加/剔除考生（EXAM-09/10）
 */
@Data
public class CandidateIdsReq implements Serializable {

    private List<Long> userIds;
}
