package com.group.exam.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.system.entity.SysNotice;
import com.group.exam.system.mapper.SysNoticeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 站内消息/通知服务（供 EXAM/MARK/LEARN 等模块触发消息）
 */
@Service
public class SysNoticeService extends ServiceImpl<SysNoticeMapper, SysNotice> {

    /**
     * 发送单人站内消息，返回消息 id。
     *
     * @param noticeType 1考试通知 2成绩发布 3学习任务提醒 9系统公告
     * @param bizType    关联业务：session/task 等，可空
     * @param bizId      业务主键，可空
     */
    public Long send(Integer noticeType, String title, String content, String bizType, Long bizId, Long receiverId) {
        SysNotice notice = build(noticeType, title, content, bizType, bizId, receiverId);
        save(notice);
        return notice.getId();
    }

    /**
     * 批量发送站内消息（一接收人一行），返回插入的消息 id 列表。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> sendBatch(Integer noticeType, String title, String content, String bizType, Long bizId,
                                Collection<Long> receiverIds) {
        List<Long> ids = new ArrayList<>();
        if (receiverIds == null || receiverIds.isEmpty()) {
            return ids;
        }
        Set<Long> distinct = new LinkedHashSet<>(receiverIds);
        List<SysNotice> notices = new ArrayList<>(distinct.size());
        for (Long receiverId : distinct) {
            if (receiverId != null) {
                notices.add(build(noticeType, title, content, bizType, bizId, receiverId));
            }
        }
        saveBatch(notices);
        notices.forEach(n -> ids.add(n.getId()));
        return ids;
    }

    private SysNotice build(Integer noticeType, String title, String content, String bizType, Long bizId, Long receiverId) {
        SysNotice notice = new SysNotice();
        notice.setNoticeType(noticeType);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setBizType(bizType);
        notice.setBizId(bizId);
        notice.setReceiverId(receiverId);
        notice.setReadStatus(0);
        notice.setChannel(1);
        notice.setSendStatus(1);
        return notice;
    }

    /** 通知类型编码 → 数值（EXAM/SCORE/LEARN_TASK/GENERAL → 1/2/3/9），非法返回 null */
    public static Integer toNoticeType(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "EXAM" -> 1;
            case "SCORE" -> 2;
            case "LEARN_TASK" -> 3;
            case "GENERAL" -> 9;
            default -> null;
        };
    }

    /** 通知类型数值 → 编码 */
    public static String toNoticeTypeCode(Integer type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case 1 -> "EXAM";
            case 2 -> "SCORE";
            case 3 -> "LEARN_TASK";
            case 9 -> "GENERAL";
            default -> String.valueOf(type);
        };
    }
}
