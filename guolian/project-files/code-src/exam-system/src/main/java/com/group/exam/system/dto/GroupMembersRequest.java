package com.group.exam.system.dto;

import java.io.Serializable;
import java.util.List;

/**
 * GROUP-03/04 批量设置/移出分组请求
 */
public class GroupMembersRequest implements Serializable {

    /** 目标用户 id 数组 */
    private List<Long> userIds;

    public List<Long> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<Long> userIds) {
        this.userIds = userIds;
    }
}
