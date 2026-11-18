package com.group.exam.workflow.spi;

import java.util.Collection;
import java.util.Map;

/**
 * 发起人姓名解析（可选实现，由装配层提供，如 exam-server 通过人员表解析）。
 * 未提供实现时列表只展示发起人 ID。
 */
public interface FlowUserNameResolver {

    Map<Long, String> names(Collection<Long> userIds);
}
