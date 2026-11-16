// 日常练习会话：发起（PRAC-01）后的题目上下文，本地持久化用于刷新续答
import { defineStore } from 'pinia'

const KEY = 'h5_practice_session'

export const usePracticeStore = defineStore('practice', {
  state: () => ({
    session: null // {mode, bankId, bankName, count, questions, index, rightCount, wrongCount, finished}
  }),
  actions: {
    setSession(data, bankId) {
      this.session = {
        mode: data.mode,
        bankId: bankId || null,
        bankName: data.bankName || '',
        count: data.count,
        questions: data.questions || [],
        index: 0,
        rightCount: 0,
        wrongCount: 0,
        finished: false
      }
      sessionStorage.setItem(KEY, JSON.stringify(this.session))
    },
    loadSession() {
      if (this.session) return this.session
      try {
        const s = JSON.parse(sessionStorage.getItem(KEY))
        if (s && s.questions && s.questions.length) {
          this.session = s
          return s
        }
      } catch (e) {
        // ignore
      }
      return null
    },
    // 推进一题：记录正误并移动游标，答完标记 finished
    advance(isRight) {
      if (!this.session) return
      if (isRight === true) this.session.rightCount += 1
      if (isRight === false) this.session.wrongCount += 1
      if (this.session.index >= this.session.questions.length - 1) {
        this.session.finished = true
      } else {
        this.session.index += 1
      }
      sessionStorage.setItem(KEY, JSON.stringify(this.session))
    },
    clearSession() {
      sessionStorage.removeItem(KEY)
      this.session = null
    }
  }
})
