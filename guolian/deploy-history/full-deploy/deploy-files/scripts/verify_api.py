#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""在线考试系统端到端 API 自动校验脚本。
按《04-API接口设计说明书》契约，走通：登录→组织/人员→题库→组卷→场次→下发→
考生答卷→交卷→阅卷→成绩发布→统计→学习心跳 全流程，并冒烟其余查询接口。
用法: python3 verify_api.py [--base http://127.0.0.1:8090/api/v1]
"""
import sys, json, time, uuid, urllib.request, urllib.parse, urllib.error
from datetime import datetime, timedelta

BASE = "http://127.0.0.1:8088/api/v1"
if "--base" in sys.argv:
    BASE = sys.argv[sys.argv.index("--base") + 1]

PASS, FAIL = [], []

def req(method, path, token=None, body=None, params=None, raw=False):
    url = BASE + path
    if params:
        url += "?" + urllib.parse.urlencode({k: v for k, v in params.items() if v is not None})
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            payload = resp.read().decode()
    except urllib.error.HTTPError as e:
        payload = e.read().decode()
        return e.code, _safe_json(payload)
    except Exception as e:
        return -1, {"code": -1, "message": str(e)}
    return 200, _safe_json(payload)

def _safe_json(s):
    try:
        return json.loads(s)
    except Exception:
        return {"code": -2, "message": "non-json: " + s[:200]}

def check(name, method, path, token=None, body=None, params=None, expect=0, save=None):
    http, res = req(method, path, token, body, params)
    expects = expect if isinstance(expect, (tuple, list)) else (expect,)
    ok = http == 200 and res.get("code") in expects
    (PASS if ok else FAIL).append((name, method, path, res.get("code"), res.get("message", "")[:120]))
    print(("PASS " if ok else "FAIL ") + f"{name} {method} {path} -> code={res.get('code')} {res.get('message','')[:80]}")
    if save is not None and res.get("data") is not None:
        save.update(res["data"] if isinstance(res["data"], dict) else {"_list": res["data"]})
    return res.get("data")

def login(username, password, terminal="PC_ADMIN"):
    http, res = req("POST", "/auth/login", body={"username": username, "password": password, "terminal": terminal})
    assert http == 200 and res.get("code") == 0, f"login {username} failed: {res}"
    return res["data"]

def main():
    t0 = time.time()
    # ---------- 0. 健康检查 ----------
    check("health", "GET", "/health")

    # ---------- 1. 管理员登录（处理强制改密；兼容已改过密码的重复运行） ----------
    try:
        adm = login("admin", "Admin@123")
    except AssertionError:
        adm = login("admin", "Admin@12345")
    if adm.get("mustChangePassword"):
        tok = adm["accessToken"]
        r = check("auth.changePassword", "PUT", "/auth/password", tok,
                  {"oldPassword": "Admin@123", "newPassword": "Admin@12345"})
        adm = login("admin", "Admin@12345")
        print("NOTE: admin 密码已强制修改为 Admin@12345")
    admin = adm["accessToken"]
    refresh = adm.get("refreshToken")
    check("auth.me", "GET", "/auth/me", admin)
    if refresh:
        check("auth.refresh", "POST", "/auth/refresh", body={"refreshToken": refresh})

    # ---------- 2. 组织/人员 ----------
    tree_holder = {}
    check("org.tree", "GET", "/admin/org/tree", admin, save=tree_holder)
    users = check("user.list", "GET", "/admin/users", admin, params={"pageNum": 1, "pageSize": 200})
    ulist = (users or {}).get("list", [])

    def pick(role, prefix):
        us = [u for u in ulist if role in (u.get("roles") or [])]
        return next((u for u in us if str(u.get("jobNo", "")).startswith(prefix)), us[0] if us else None)

    def login_known(user, terminal):
        """优先用种子密码登录；失败则走管理员重置密码→强制改密为 Exam@123。"""
        try:
            return login(user["jobNo"], "Exam@123", terminal=terminal)
        except AssertionError:
            data = check("user.resetPassword." + user["jobNo"], "PUT",
                         f"/admin/users/{user['userId']}/password/reset", admin)
            pwd = (data or {}).get("initialPassword")
            assert pwd, "重置密码未返回初始密码"
            s = login(user["jobNo"], pwd, terminal=terminal)
            if s.get("mustChangePassword"):
                check("user.initChangePassword." + user["jobNo"], "PUT", "/auth/password",
                      s["accessToken"], {"oldPassword": pwd, "newPassword": "Exam@123"})
                s = login(user["jobNo"], "Exam@123", terminal=terminal)
            return s

    examinee = pick("EXAMINEE", "ks")
    marker = pick("MARKER", "marker")
    assert examinee, "种子数据中未找到 EXAMINEE 用户"
    root_org_id = 1
    check("org.create", "POST", "/admin/org", admin,
          {"parentId": root_org_id, "orgName": "校验测试部门", "orgCode": "VERIFY_DEPT", "orgType": "DEPT"},
          expect=(0, 20002))
    check("user.create", "POST", "/admin/users", admin,
          {"name": "校验考生", "jobNo": "VERIFY001", "phone": "13800000001", "gender": "M",
           "orgId": root_org_id, "roleCodes": ["EXAMINEE"]}, expect=(0, 20001))

    # ---------- 3. 学习资料/系统查询冒烟 ----------
    check("lib.catTree", "GET", "/admin/lib/categories/tree", admin)
    check("lib.materials", "GET", "/admin/lib/materials", admin, params={"pageNum": 1, "pageSize": 10})
    check("sys.configs", "GET", "/admin/sys/configs", admin)
    check("sys.loginLogs", "GET", "/admin/sys/logs/login", admin, params={"pageNum": 1, "pageSize": 5})
    check("sys.opLogs", "GET", "/admin/sys/logs/operation", admin, params={"pageNum": 1, "pageSize": 5})
    check("sys.abnormalLogs", "GET", "/admin/sys/logs/exam-abnormal", admin, params={"pageNum": 1, "pageSize": 5})
    check("sys.notices", "GET", "/admin/sys/notices", admin, params={"pageNum": 1, "pageSize": 5})
    check("lstat.orgSummary", "GET", "/admin/lstat/org-summary", admin)
    check("lstat.ranking", "GET", "/admin/lstat/ranking", admin, params={"rankType": "USER"})

    # ---------- 4. 题库 ----------
    banks = check("ques.bankTree", "GET", "/admin/ques/banks/tree", admin)
    items_s = check("ques.items.SINGLE", "GET", "/admin/ques/items", admin,
                    params={"quesType": "SINGLE", "status": "ENABLED", "pageNum": 1, "pageSize": 200})
    items_j = check("ques.items.JUDGE", "GET", "/admin/ques/items", admin,
                    params={"quesType": "JUDGE", "status": "ENABLED", "pageNum": 1, "pageSize": 200})
    items_m = check("ques.items.MULTI", "GET", "/admin/ques/items", admin,
                    params={"quesType": "MULTI", "status": "ENABLED", "pageNum": 1, "pageSize": 200})
    items_e = check("ques.items.ESSAY", "GET", "/admin/ques/items", admin,
                    params={"quesType": "ESSAY", "status": "ENABLED", "pageNum": 1, "pageSize": 200})
    singles = (items_s or {}).get("list", [])
    judges = (items_j or {}).get("list", [])
    multis = (items_m or {}).get("list", [])
    essays = (items_e or {}).get("list", [])
    assert len(singles) >= 8 and len(judges) >= 4 and len(multis) >= 2 and len(essays) >= 1, \
        f"启用题目不足: single={len(singles)} judge={len(judges)} multi={len(multis)} essay={len(essays)}"
    first_bank_id = singles[0].get("bankId")
    check("ques.create", "POST", "/admin/ques/items", admin,
          {"bankId": first_bank_id, "quesType": "SINGLE",
           "stem": "校验用题：中国共产党纪律处分条例规定的处分种类不包括以下哪项？",
           "options": [{"optKey": "A", "content": "警告", "isAnswer": False},
                       {"optKey": "B", "content": "严重警告", "isAnswer": False},
                       {"optKey": "C", "content": "行政拘留", "isAnswer": True},
                       {"optKey": "D", "content": "开除党籍", "isAnswer": False}],
           "difficulty": "EASY", "analysis": "行政拘留属行政处罚，非党纪处分。"})
    check("ques.dupCheck", "POST", "/admin/ques/items/dup-check", admin,
          {"stem": "中国共产党纪律处分条例规定的处分种类", "quesType": "SINGLE"})

    # ---------- 5. 组卷（固定卷，含1道简答） ----------
    qs, seq = [], 1
    for it in singles[:8]:
        qs.append({"itemId": it["itemId"], "score": 5, "seq": seq}); seq += 1
    for it in judges[:4]:
        qs.append({"itemId": it["itemId"], "score": 5, "seq": seq}); seq += 1
    for it in multis[:2]:
        qs.append({"itemId": it["itemId"], "score": 10, "seq": seq}); seq += 1
    qs.append({"itemId": essays[0]["itemId"], "score": 20, "seq": seq})
    paper = {}
    check("paper.create", "POST", "/admin/papers", admin,
          {"paperName": "端到端校验统考卷", "paperType": "FIXED", "totalScore": 100, "passScore": 60,
           "multiScoreRule": "HALF_ON_MISS", "questions": qs,
           "gradeRules": [{"gradeName": "优", "minScore": 90, "maxScore": 100},
                          {"gradeName": "良", "minScore": 80, "maxScore": 90},
                          {"gradeName": "中", "minScore": 60, "maxScore": 80},
                          {"gradeName": "差", "minScore": 0, "maxScore": 60}]}, save=paper)
    paper_id = paper.get("paperId")
    assert paper_id, "创建试卷失败"
    check("paper.detail", "GET", f"/admin/papers/{paper_id}", admin)
    check("paper.preview", "GET", f"/admin/papers/{paper_id}/preview", admin)
    check("paper.list", "GET", "/admin/papers", admin, params={"pageNum": 1, "pageSize": 10})

    # ---------- 6. 考试场次 ----------
    now = datetime.now()
    exam = {}
    check("exam.create", "POST", "/admin/exams", admin,
          {"examName": "端到端校验考试场次", "paperId": paper_id,
           "startTime": (now - timedelta(minutes=30)).strftime("%Y-%m-%d %H:%M:%S"),
           "endTime": (now + timedelta(hours=8)).strftime("%Y-%m-%d %H:%M:%S"),
           "durationMin": 120, "lateEnterMin": 60, "switchScreenLimit": 3,
           "allowEarlySubmit": True, "notice": "请独立作答，切屏超过3次将自动交卷。"}, save=exam)
    exam_id = exam.get("examId")
    assert exam_id, "创建场次失败"
    check("exam.detail", "GET", f"/admin/exams/{exam_id}", admin)
    check("exam.status.START", "PUT", f"/admin/exams/{exam_id}/status", admin, {"action": "START"})
    check("exam.scope", "POST", f"/admin/exams/{exam_id}/candidates/scope", admin,
          {"scopes": [{"orgId": root_org_id, "includeSub": True}]})
    cands = check("exam.candidates", "GET", f"/admin/exams/{exam_id}/candidates", admin,
                  params={"pageNum": 1, "pageSize": 200})
    assert (cands or {}).get("list"), "考生名单为空"
    check("exam.notify", "POST", f"/admin/exams/{exam_id}/notify", admin,
          {"message": "校验考试已开始，请及时参加。", "channels": ["INBOX"]})
    check("exam.list", "GET", "/admin/exams", admin, params={"pageNum": 1, "pageSize": 10})

    # ---------- 7. 考生答卷全流程 ----------
    stu = login_known(examinee, "PC_APP")
    stu_tok = stu["accessToken"]
    check("app.exams", "GET", "/app/exams", stu_tok, params={"type": "TODO"})
    enter = {}
    check("app.enter", "POST", f"/app/exams/{exam_id}/enter", stu_tok,
          {"noticeConfirmed": True, "deviceId": "verify-pc-1", "terminal": "PC_APP"}, save=enter)
    sheet_id = enter.get("sheetId")
    assert sheet_id, "进入考试失败"
    check("app.time", "GET", "/app/time", stu_tok)
    paper_view = check("app.paper", "GET", f"/app/exams/{exam_id}/paper", stu_tok, params={"sheetId": sheet_id})
    sections = (paper_view or {}).get("sections", [])
    assert sections, "拉取试卷失败"
    answers = []
    for sec in sections:
        for q in sec.get("questions", []):
            qt = sec.get("quesType")
            if qt in ("SINGLE",):
                answers.append({"sheetQuesId": q["sheetQuesId"], "answer": {"selected": [q["options"][0]["optKey"]]}})
            elif qt == "MULTI":
                keys = [o["optKey"] for o in q["options"][:1]]
                answers.append({"sheetQuesId": q["sheetQuesId"], "answer": {"selected": keys}})
            elif qt == "JUDGE":
                answers.append({"sheetQuesId": q["sheetQuesId"], "answer": {"selected": ["T"]}})
            elif qt == "FILL":
                answers.append({"sheetQuesId": q["sheetQuesId"], "answer": {"blanks": ["校验答案"]}})
            else:
                answers.append({"sheetQuesId": q["sheetQuesId"], "answer": {"text": "校验作答：坚决维护党的纪律，严格执行监督执纪规则。"}})
    check("app.saveAnswers", "PUT", f"/app/sheets/{sheet_id}/answers", stu_tok,
          {"clientBatchId": str(uuid.uuid4()), "answers": answers})
    check("app.screenEvent", "POST", f"/app/sheets/{sheet_id}/screen-events", stu_tok,
          {"eventType": "LEAVE", "occurredAt": now.strftime("%Y-%m-%d %H:%M:%S")})
    check("app.sheetStatus", "GET", f"/app/sheets/{sheet_id}", stu_tok)
    check("app.submit", "POST", f"/app/sheets/{sheet_id}/submit", stu_tok,
          {"confirmUnanswered": True, "clientSubmitId": str(uuid.uuid4())})
    check("app.submit.idempotent", "POST", f"/app/sheets/{sheet_id}/submit", stu_tok,
          {"confirmUnanswered": True, "clientSubmitId": str(uuid.uuid4())})

    # ---------- 8. 阅卷 ----------
    if marker:
        mk = login_known(marker, "PC_ADMIN")
        mk_tok = mk["accessToken"]
    else:
        print("WARN: 未找到 MARKER 用户，用 admin 阅卷")
        mk_tok = admin
    tasks = check("mark.tasks", "GET", "/admin/mark/tasks", mk_tok, params={"examId": exam_id, "pageNum": 1, "pageSize": 50})
    tlist = (tasks or {}).get("list", [])
    for t in tlist:
        tid = t.get("taskId")
        check("mark.claim", "POST", f"/admin/mark/tasks/{tid}/claim", mk_tok)
        items = check("mark.items", "GET", f"/admin/mark/tasks/{tid}/items", mk_tok,
                      params={"pageNum": 1, "pageSize": 200})
        scores = []
        for it in (items or {}).get("list", []):
            if not it.get("marked"):
                full = float(it.get("fullScore") or 0)
                scores.append({"detailId": it["detailId"], "score": round(full * 0.8, 1), "comment": "基本覆盖要点"})
        if scores:
            check("mark.scores", "POST", "/admin/mark/scores", mk_tok, {"taskId": tid, "scores": scores})
    check("mark.reviewQueue", "GET", "/admin/mark/review-queue", mk_tok, params={"examId": exam_id})

    # ---------- 9. 成绩发布与统计 ----------
    check("mark.publish", "POST", f"/admin/mark/exams/{exam_id}/publish", admin, {"confirm": True})
    check("stat.overview", "GET", f"/admin/stat/exams/{exam_id}/overview", admin)
    check("stat.orgSummary", "GET", f"/admin/stat/exams/{exam_id}/org-summary", admin)
    check("stat.scoreSegments", "GET", f"/admin/stat/exams/{exam_id}/score-segments", admin)
    check("stat.gradeDist", "GET", f"/admin/stat/exams/{exam_id}/grade-distribution", admin)
    check("stat.quesAnalysis", "GET", f"/admin/stat/exams/{exam_id}/question-analysis", admin)
    check("app.result", "GET", f"/app/exams/{exam_id}/result", stu_tok)
    check("app.transcripts", "GET", "/app/stat/my-transcripts", stu_tok)

    # ---------- 10. 学习流程 ----------
    mats = check("app.learn.materials", "GET", "/app/learn/materials", stu_tok, params={"pageNum": 1, "pageSize": 10})
    mlist = (mats or {}).get("list", [])
    if mlist:
        mid = mlist[0]["materialId"]
        check("app.learn.detail", "GET", f"/app/learn/materials/{mid}", stu_tok)
        check("app.learn.file", "GET", f"/app/learn/materials/{mid}/file", stu_tok)
        check("app.learn.heartbeat", "POST", "/app/learn/heartbeat", stu_tok,
              {"materialId": mid, "eventId": str(uuid.uuid4()), "pageNo": 1, "deltaSec": 15})
        check("app.learn.record", "GET", f"/app/learn/records/{mid}", stu_tok)
    check("app.learn.tasks", "GET", "/app/learn/tasks", stu_tok)
    check("app.lstat.personal", "GET", "/app/lstat/personal", stu_tok)
    check("app.notices", "GET", "/app/notices", stu_tok)

    # ---------- 汇总 ----------
    print("\n===== 校验汇总 =====")
    print(f"通过 {len(PASS)} 项, 失败 {len(FAIL)} 项, 耗时 {time.time()-t0:.1f}s")
    if FAIL:
        print("失败明细:")
        for f in FAIL:
            print("  ", f)
        sys.exit(1)
    print("全部 API 校验通过。")

if __name__ == "__main__":
    main()
