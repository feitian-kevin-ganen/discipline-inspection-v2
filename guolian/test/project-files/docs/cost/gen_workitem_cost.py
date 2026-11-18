# -*- coding: utf-8 -*-
"""生成 cost/workitem_cost.xlsx：纪检人员考试培训系统 工作项与成本估算"""
import os
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter

OUT = os.path.join(os.path.dirname(__file__), "workitem_cost.xlsx")

TITLE_FONT = Font(name="微软雅黑", size=14, bold=True, color="FFFFFF")
HEAD_FONT = Font(name="微软雅黑", size=10, bold=True)
HEAD_FILL = PatternFill("solid", fgColor="1B5BD7")
SUB_FILL = PatternFill("solid", fgColor="D9E2F7")
TITLE_FILL = PatternFill("solid", fgColor="1B5BD7")
TOTAL_FONT = Font(name="微软雅黑", size=10, bold=True, color="C00000")
BODY_FONT = Font(name="微软雅黑", size=10)
THIN = Side(style="thin", color="999999")
BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)
CENTER = Alignment(horizontal="center", vertical="center", wrap_text=True)
LEFT = Alignment(horizontal="left", vertical="center", wrap_text=True)
RIGHT = Alignment(horizontal="right", vertical="center")


def sheet(wb, name, title, headers, rows, widths, note=None, first=False):
    ws = wb.active if first else wb.create_sheet()
    ws.title = name
    ncol = len(headers)
    ws.merge_cells(start_row=1, start_column=1, end_row=1, end_column=ncol)
    c = ws.cell(1, 1, title)
    c.font = TITLE_FONT
    c.fill = TITLE_FILL
    c.alignment = CENTER
    ws.row_dimensions[1].height = 28
    for j, h in enumerate(headers, 1):
        c = ws.cell(2, j, h)
        c.font = Font(name="微软雅黑", size=10, bold=True, color="FFFFFF")
        c.fill = HEAD_FILL
        c.alignment = CENTER
        c.border = BORDER
    r = 3
    total_idx = None
    for i, row in enumerate(rows):
        is_sub = isinstance(row[0], str) and row[0].startswith("【小计】")
        is_sec = isinstance(row[0], str) and row[0].startswith("■")
        for j, v in enumerate(row, 1):
            c = ws.cell(r, j, v)
            c.font = TOTAL_FONT if is_sub else (HEAD_FONT if is_sec else BODY_FONT)
            c.alignment = LEFT if j == 1 else (RIGHT if isinstance(v, (int, float)) else CENTER)
            c.border = BORDER
            if is_sub:
                c.fill = SUB_FILL
            if isinstance(v, float):
                c.number_format = "#,##0.00"
            elif isinstance(v, int) and j > 1:
                c.number_format = "#,##0"
        if is_sub:
            total_idx = r
        r += 1
    if note:
        ws.merge_cells(start_row=r + 1, start_column=1, end_row=r + 1, end_column=ncol)
        c = ws.cell(r + 1, 1, note)
        c.font = Font(name="微软雅黑", size=9, color="808080")
        c.alignment = LEFT
    for j, w in enumerate(widths, 1):
        ws.column_dimensions[get_column_letter(j)].width = w
    ws.freeze_panes = "A3"
    return ws


wb = Workbook()

# ---------------- 1. 汇总 ----------------
SUMMARY = [
    ["一、系统开发", "软件开发工作项（需求/设计/后端/四端前端/小程序/测试/部署）", 148, 258200.00, "一次性"],
    ["二、服务器资源", "云主机/数据库/Redis/对象存储/带宽/证书/备份", None, 13600.00, "按年"],
    ["三、安全服务", "渗透测试、漏洞扫描、安全加固", 12, 26500.00, "渗透/加固一次性，漏扫按年"],
    ["四、微信端工作项", "小程序/服务号注册认证、订阅消息、登录绑定、上架", 13, 23400.00, "一次性"],
    ["五、微信平台成本", "小程序认证费、服务号认证费（腾讯官方收取）", None, 600.00, "按年"],
]
ws = wb.active
ws.title = "汇总"
headers = ["费用类别", "内容说明", "工作量(人天)", "金额(元)", "计费方式"]
sheet(wb, "汇总", "纪检人员考试培训微信小程序 — 工作项与成本估算汇总", headers, [], [22, 52, 14, 16, 26], first=True)
r = 3
for row in SUMMARY:
    for j, v in enumerate(row, 1):
        c = ws.cell(r, j, v)
        c.font = BODY_FONT
        c.border = BORDER
        c.alignment = LEFT if j == 2 else (RIGHT if j == 4 else CENTER)
        if isinstance(v, float):
            c.number_format = "#,##0.00"
    r += 1
one_time = 258200 + 12500 + 6000 + 23400  # 开发+渗透+加固+微信工作项
yearly = 13600 + 8000 + 600              # 服务器+漏扫+平台
for label, val in [("一次性投入合计（开发+渗透测试+安全加固+微信端工作项）", one_time),
                   ("年度持续投入合计（服务器+漏洞扫描+微信平台费）", yearly)]:
    ws.merge_cells(start_row=r, start_column=1, end_row=r, end_column=3)
    c = ws.cell(r, 1, label)
    c.font = TOTAL_FONT
    c.fill = SUB_FILL
    c.border = BORDER
    c.alignment = LEFT
    v = ws.cell(r, 4, float(val))
    v.font = TOTAL_FONT
    v.fill = SUB_FILL
    v.border = BORDER
    v.number_format = "#,##0.00"
    v.alignment = RIGHT
    ws.cell(r, 5, "").border = BORDER
    r += 1
ws.merge_cells(start_row=r + 1, start_column=1, end_row=r + 1, end_column=5)
ws.cell(r + 1, 1, "说明：本估算为市场参考价（人天单价：高级2000/中级1500/测试1200/设计1800元），实际以商务报价为准；明细见后续各 Sheet。").font = Font(name="微软雅黑", size=9, color="808080")

# ---------------- 2. 开发工作项 ----------------
DEV = [
    ["■ 需求与设计", None, None, None, None, None],
    ["需求调研与分析（含客户任务书对齐、原型）", "需求工程", 8, "高级/需求", 1800, 14400],
    ["概要/详细/数据库/接口设计（v1.0+v1.1）", "设计", 10, "高级", 2000, 20000],
    ["■ 后端开发（Spring Boot 多模块，117+ 接口）", None, None, None, None, None],
    ["系统管理（组织/人员/角色权限/参数/日志/通知）", "后端", 10, "高级", 2000, 20000],
    ["题库与试卷（题库/题目/组卷策略/等级规则/Excel导入）", "后端", 8, "高级", 2000, 16000],
    ["考试引擎（场次/下发/答卷/判分/防作弊/幂等交卷）", "后端", 12, "高级", 2000, 24000],
    ["阅卷与成绩发布", "后端", 5, "高级", 2000, 10000],
    ["学习培训（资料/心跳/断点/学习任务/学习统计）", "后端", 6, "高级", 2000, 12000],
    ["统计分析（总览/分层级/分数段/等级/导出）", "后端", 4, "高级", 2000, 8000],
    ["■ 前端开发", None, None, None, None, None],
    ["PC 管理端（Vue3 + Element Plus，25+ 页面）", "前端", 15, "中级", 1500, 22500],
    ["PC 用户端", "前端", 8, "中级", 1500, 12000],
    ["H5 移动端（Vue3 + Vant，12 页面）", "前端", 10, "中级", 1500, 15000],
    ["原生微信小程序（13 页面，功能对齐 H5）", "前端", 12, "高级", 1800, 21600],
    ["H5 套壳小程序（web-view 壳 + 通讯）", "前端", 2, "中级", 1500, 3000],
    ["■ v1.1 新增（客户任务书）", None, None, None, None, None],
    ["日常练习模块（分类/随机练习、即时解析、练习记录）", "全栈", 8, "高级", 1800, 14400],
    ["微信订阅通知接入（订阅消息/服务通知、提醒任务）", "后端", 5, "高级", 1800, 9000],
    ["■ 测试与交付", None, None, None, None, None],
    ["测试（功能/接口自动化/兼容/回归）", "测试", 12, "测试", 1200, 14400],
    ["部署联调与初始数据导入", "实施", 5, "中级", 1500, 7500],
    ["项目管理与文档交付", "管理", 8, "高级", 1800, 14400],
    ["【小计】系统开发", "", 148, "", "", 258200],
]
sheet(wb, "开发工作项及成本", "系统开发工作项、人天及成本估算", ["工作项", "类别", "人天", "人员级别", "单价(元/人天)", "金额(元)"],
      DEV, [52, 10, 8, 12, 14, 14],
      note="注：人天单价为市场参考价（高级2000/中级1500/测试1200元/人天）；后端已完成 117 个接口、五端前端已交付，工作量按实际交付物估算。")

# ---------------- 3. 服务器 ----------------
SRV = [
    ["云服务器（应用+后端服务）", "4C8G / 100G SSD / 华东", "1 台", 4000, 4000, "按年"],
    ["云数据库 PostgreSQL", "2C4G / 100G 高可用版", "1 套", 3600, 3600, "按年"],
    ["云 Redis", "1G 主从", "1 套", 800, 800, "按年"],
    ["对象存储（资料/文件）", "100G 标准存储 + 流量包", "1 项", 600, 600, "按年"],
    ["公网带宽", "5M BGP（小程序/H5 访问）", "1 项", 3000, 3000, "按年"],
    ["SSL 证书（HTTPS，微信强制）", "OV 单域名（可用免费 DV 替代降为0元）", "1 张", 1200, 1200, "按年"],
    ["备份存储", "200G 快照/备份空间", "1 项", 400, 400, "按年"],
    ["【小计】服务器资源", "", "", "", 13600, "按年"],
]
sheet(wb, "服务器配置及成本", "服务器与云资源配置及年度成本", ["资源项", "规格", "数量", "单价(元/年)", "金额(元/年)", "计费方式"],
      SRV, [34, 34, 10, 14, 14, 12],
      note="注：按主流云厂商（阿里云/腾讯云）华东区公开价估算；若部署在集团已有私有云/政务云，可按内部结算价下调。")

# ---------------- 4. 安全服务 ----------------
SEC = [
    ["渗透测试（小程序/管理端/API 全覆盖）", "人工渗透：认证绕过/越权/注入/XSS/文件上传/逻辑漏洞，出报告+复测", 5, 2500, 12500, "一次性"],
    ["漏洞扫描（年度服务）", "季度全量漏扫（主机/Web/弱口令）+ 扫描报告", 4, 2000, 8000, "按年（4次）"],
    ["安全加固整改", "依据渗透/漏扫报告修复与基线加固", 3, 2000, 6000, "一次性"],
    ["等保二级测评（可选）", "如涉及定级备案要求，第三方测评机构收取", None, None, 0, "可选，约3-5万另计"],
    ["【小计】安全服务", "", 12, "", 26500, ""],
]
sheet(wb, "安全服务", "安全服务（渗透测试/漏洞扫描）工作量及成本", ["服务项", "服务内容", "人天/次数", "单价(元)", "金额(元)", "计费方式"],
      SEC, [36, 46, 12, 12, 14, 18],
      note="注：渗透测试含一次复测；等保测评为可选项，价格以测评机构报价为准。")

# ---------------- 5. 微信端工作项 ----------------
WXWORK = [
    ["小程序注册与认证", "主体资料准备、注册、微信认证、年审协助", 1, 1800, 1800],
    ["小程序订阅消息开发", "模板申请（考试发布/考前提醒/截止提醒/成绩公布）+ 授权引导 + 服务端 subscribeMessage.send 推送", 3, 1800, 5400],
    ["微信服务号接入", "服务号注册认证、模板消息开发（服务通知通道）", 3, 1800, 5400],
    ["微信登录与用户绑定", "wx.login/code2session、openid/UnionID 与内部账号绑定", 3, 1800, 5400],
    ["web-view 业务域名配置", "业务域名申请、校验文件部署、H5 端 HTTPS 适配", 1, 1800, 1800],
    ["小程序审核上架与版本管理", "提审材料、类目说明、灰度与发布", 2, 1800, 3600],
    ["【小计】微信端工作项", "", 13, "", 23400],
]
sheet(wb, "微信端工作项及成本", "微信公众号及小程序相关工作项、人天及成本", ["工作项", "内容说明", "人天", "单价(元/人天)", "金额(元)"],
      WXWORK, [30, 62, 8, 14, 14])

# ---------------- 6. 微信平台成本 ----------------
WXFEE = [
    ["小程序认证费", "微信公众平台收取，企业主体", "300 元/年", 300, "按年（年审）"],
    ["微信服务号认证费", "微信公众平台收取，企业主体", "300 元/年", 300, "按年（年审）"],
    ["订阅消息/模板消息", "推送条数", "免费", 0, "微信官方免费"],
    ["微信支付商户号", "本项目无收费场景", "不涉及", 0, "—"],
    ["【小计】微信平台成本", "", "", 600, "按年"],
]
sheet(wb, "微信平台成本", "微信端平台成本（腾讯官方费用）", ["费用项", "说明", "官方价格", "金额(元/年)", "计费方式"],
      WXFEE, [26, 40, 16, 14, 16],
      note="注：腾讯官方价格可能调整，以微信公众平台最新公告为准；小程序/服务号需企业主体认证。")

wb.save(OUT)
print("saved:", OUT, os.path.getsize(OUT), "bytes")
