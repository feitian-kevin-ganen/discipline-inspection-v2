package com.group.exam.server.workflow;

import com.group.exam.system.entity.OrgUser;
import com.group.exam.system.service.OrgUserService;
import com.group.exam.workflow.spi.FlowUserNameResolver;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程列表展示发起人姓名（工作流模块不直连人员表，由装配层注入）
 */
@Component
public class FlowUserNameResolverImpl implements FlowUserNameResolver {

    private final OrgUserService orgUserService;

    public FlowUserNameResolverImpl(OrgUserService orgUserService) {
        this.orgUserService = orgUserService;
    }

    @Override
    public Map<Long, String> names(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return orgUserService.listByIds(userIds).stream()
                .filter(u -> u.getId() != null && u.getUserName() != null)
                .collect(Collectors.toMap(OrgUser::getId, OrgUser::getUserName, (a, b) -> a));
    }
}
