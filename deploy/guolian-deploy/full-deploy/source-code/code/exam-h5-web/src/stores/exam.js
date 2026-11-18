// 考试会话：进入考试（TAKE-02）后的答卷上下文与交卷结果，本地持久化用于断线续考
import { defineStore } from 'pinia'

const key = (examId) => `h5_exam_session_${examId}`

export const useExamStore = defineStore('exam', {
  state: () => ({
    session: null // {examId, sheetId, examName, deadlineTime, remainingSec, examRules, resumeFrom}
  }),
  actions: {
    setSession(examId, data, examName) {
      this.session = {
        examId: Number(examId),
        sheetId: data.sheetId,
        examName: examName || '',
        deadlineTime: data.deadlineTime,
        remainingSec: data.remainingSec,
        examRules: data.examRules || {},
        resumeFrom: data.resumeFrom || 'NEW'
      }
      localStorage.setItem(key(examId), JSON.stringify(this.session))
    },
    loadSession(examId) {
      if (this.session && this.session.examId === Number(examId)) return this.session
      try {
        const s = JSON.parse(localStorage.getItem(key(examId)))
        if (s && s.sheetId) {
          this.session = s
          return s
        }
      } catch (e) {
        // ignore
      }
      return null
    },
    updateRemaining(remainingSec, deadlineTime) {
      if (!this.session) return
      this.session.remainingSec = remainingSec
      if (deadlineTime) this.session.deadlineTime = deadlineTime
      localStorage.setItem(key(this.session.examId), JSON.stringify(this.session))
    },
    clearSession(examId) {
      localStorage.removeItem(key(examId))
      if (this.session && this.session.examId === Number(examId)) this.session = null
    }
  }
})
