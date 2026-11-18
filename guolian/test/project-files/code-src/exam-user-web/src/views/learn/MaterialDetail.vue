<template>
  <div class="page" v-loading="loading">
    <el-card shadow="never" v-if="detail">
      <template #header>
        <div class="head">
          <div>
            <el-button size="small" class="back-btn" @click="$router.back()">返回</el-button>
            <span class="title">{{ detail.title }}</span>
            <el-tag size="small" class="ml8" :type="typeTag">{{ MATERIAL_TYPE_NAME[detail.materialType] || detail.materialType }}</el-tag>
            <el-tag v-for="t in detail.tags || []" :key="t" size="small" effect="plain" class="ml8">{{ t }}</el-tag>
          </div>
          <div class="progress-box">
            <el-progress
              type="circle"
              :percentage="Math.round(progressPct)"
              :status="status === 'COMPLETED' ? 'success' : undefined"
              :width="64"
            />
            <div class="progress-text">
              <div>{{ statusText }}</div>
              <div class="text-secondary">有效时长 {{ fmtDuration(effectiveSec) }}</div>
            </div>
          </div>
        </div>
      </template>

      <el-alert
        v-if="detail.summary"
        :title="detail.summary"
        type="info"
        :closable="false"
        class="mb16"
        show-icon
      />

      <!-- 图文 -->
      <div v-if="detail.materialType === 'RICHTEXT'" class="rich-content richtext-body" v-html="detail.contentHtml || ''"></div>

      <!-- 视频 -->
      <div v-else-if="detail.materialType === 'VIDEO'" class="video-body">
        <video
          ref="videoRef"
          :key="videoSrcMp4"
          controls
          class="player"
          @loadedmetadata="onVideoLoaded"
          @timeupdate="onTimeUpdate"
          @ended="onVideoEnded"
        >
          <!-- 双格式源走同源代理（Range 流式）：H.264/MP4 优先，浏览器缺 H.264 解码时自动降级 WebM/VP9 -->
          <source :src="videoSrcMp4" type="video/mp4" />
          <source v-if="hasWebm" :src="videoSrcWebm" type="video/webm" />
        </video>
        <div class="text-secondary">断点续学：将从上次播放位置 {{ fmtDuration(resumePosition) }} 继续；页面失焦或暂停时不计入有效时长。</div>
      </div>

      <!-- 文档：DOCX 走后端 HTML 解析滚动阅读；PDF 走页图翻页；旧版 doc/ppt 提示下载 -->
      <div v-else-if="detail.materialType === 'DOC'" class="doc-body">
        <!-- DOCX：后端解析 word 结构输出 HTML，滚动阅读、进度按滚动位置自动记录 -->
        <template v-if="docHtml">
          <div class="doc-toolbar">
            <span class="text-secondary">共 {{ pageCount }} 页 · 上下滚动阅读，进度自动记录 · 已读至第 {{ docPageNo }} 页</span>
            <el-link v-if="fileUrl" :href="fileUrl" target="_blank" type="primary">新窗口打开</el-link>
          </div>
          <div ref="docScrollRef" class="docx-scroll" @scroll.passive="onDocScroll">
            <div class="docx-content" v-html="docHtml"></div>
          </div>
        </template>

        <!-- 旧版二进制格式（doc/ppt 等）：服务端不支持在线解析，提示下载查看 -->
        <el-alert
          v-else-if="docUnsupported"
          type="warning"
          :closable="false"
          :title="`该格式（${docUnsupported}）暂不支持在线预览`"
          description="请点击“新窗口打开”下载后查看"
          class="mb16"
        >
          <template v-if="fileUrl">
            <el-link :href="fileUrl" target="_blank" type="primary">新窗口打开</el-link>
          </template>
        </el-alert>

        <!-- PDF 页图预览：后端逐页转 JPEG，前端只加载图片，避免下载工具拦截 PDF 流 -->
        <template v-else>
          <div class="doc-toolbar">
            <el-button :disabled="pageNo <= 1" @click="goPage(pageNo - 1)">上一页</el-button>
            <span>
              第
              <el-input-number v-model="pageInput" :min="1" :max="pageCount || 1" size="small" style="width: 90px" @change="onPageInput" />
              / {{ pageCount }} 页
            </span>
            <el-button :disabled="pageNo >= pageCount" @click="goPage(pageNo + 1)">下一页</el-button>
            <el-button-group>
              <el-button size="small" @click="zoom(-10)">缩小</el-button>
              <el-button size="small" @click="zoom(10)">放大</el-button>
            </el-button-group>
            <span class="text-secondary">已读 {{ readCount }}/{{ pageCount }} 页</span>
            <el-link v-if="fileUrl" :href="fileUrl" target="_blank" type="primary">新窗口打开</el-link>
          </div>
          <div class="canvas-wrap" v-loading="pageRendering">
            <img v-if="pageImageUrl" :src="pageImageUrl" class="doc-page-img" :style="{ width: zoomPct + '%' }" alt="资料页图" />
          </div>
        </template>
        <el-alert v-if="pdfError" type="error" :closable="false" title="文档加载失败，可点击右上角“新窗口打开”查看" class="mb16" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getMaterialDetail, getMaterialFile } from '@/api/learn'
import { useUserStore } from '@/stores/user'
import { useLearnHeartbeat } from '@/composables/useLearnHeartbeat'
import { fmtDuration, MATERIAL_TYPE_NAME } from '@/utils'

const route = useRoute()
const userStore = useUserStore()
const materialId = route.params.materialId

const loading = ref(true)
const detail = ref(null)
const fileUrl = ref('')
const altFileUrl = ref('')
// 视频同源代理地址（媒体标签无法带 Authorization 头，用 access_token 查询参数）
const hasWebm = ref(false)
const videoSrcMp4 = computed(() =>
  `/api/v1/app/learn/materials/${materialId}/video?access_token=${userStore.accessToken}`)
const videoSrcWebm = computed(() =>
  `/api/v1/app/learn/materials/${materialId}/video?fmt=webm&access_token=${userStore.accessToken}`)
const progressPct = ref(0)
const effectiveSec = ref(0)
const status = ref('NOT_STARTED')

// 视频
const videoRef = ref()
const resumePosition = ref(0)
const currentPosition = ref(0)
const videoEnded = ref(false)

// 文档
const pageNo = ref(1)
const pageInput = ref(1)
const pageCount = ref(0)
const zoomPct = ref(100)
const pageRendering = ref(false)
const pdfError = ref(false)
const pageImageUrl = ref('')
// DOCX：后端 HTML 解析滚动阅读
const docHtml = ref('')
const docPageNo = ref(1)
const docUnsupported = ref('')
const docScrollRef = ref()
const readPages = new Set()
const newReadPages = new Set()

const typeTag = computed(() => ({ DOC: 'primary', VIDEO: 'danger', RICHTEXT: 'success' }[detail.value?.materialType] || 'info'))
const statusText = computed(() => ({ NOT_STARTED: '未开始', IN_PROGRESS: '学习中', COMPLETED: '已完成' }[status.value] || ''))
const readCount = computed(() => readPages.size)

// ---- 学习心跳：15s 间隔、失焦停发、断点续学 ----
const { flush, pendingSec } = useLearnHeartbeat(materialId, {
  isActive: () => {
    if (detail.value?.materialType === 'VIDEO') {
      const v = videoRef.value
      return !!v && !v.paused && !v.ended
    }
    return true
  },
  getPayload: () => {
    if (detail.value?.materialType === 'VIDEO') {
      const v = videoRef.value
      return {
        positionSec: Math.floor(currentPosition.value),
        // 视频实际时长作为进度分母上报，服务端优先于资料标称时长
        durationSec: v && v.duration ? Math.floor(v.duration) : undefined
      }
    }
    if (detail.value?.materialType === 'DOC') {
      const pages = [...newReadPages]
      newReadPages.clear()
      // DOCX 滚动阅读按滚动虚拟页码上报；PDF 页图按翻页页码上报（服务端取单调最大值）
      return { pageNo: docHtml.value ? docPageNo.value : pageNo.value, readPages: pages.length ? pages : undefined }
    }
    return {}
  },
  onUpdate: (data) => {
    if (!data) return
    progressPct.value = Math.max(progressPct.value, data.progressPct ?? 0)
    effectiveSec.value = data.effectiveSec ?? effectiveSec.value
    if (data.status) status.value = data.status
  }
})

// ---- 视频 ----
// 进度口径：实际观看时长(服务端有效时长 + 本地未上报秒数) / 视频实际时长，每 5s 本地刷新一次
function refreshVideoProgress() {
  if (detail.value?.materialType !== 'VIDEO') return
  if (videoEnded.value) {
    progressPct.value = 100
    return
  }
  const v = videoRef.value
  if (!v || !v.duration) return
  const watched = effectiveSec.value + pendingSec()
  progressPct.value = Math.max(progressPct.value, Math.min(100, (watched / v.duration) * 100))
}

function onVideoLoaded() {
  const v = videoRef.value
  if (v && resumePosition.value > 0 && resumePosition.value < v.duration) {
    v.currentTime = resumePosition.value
  }
}
function onTimeUpdate() {
  const v = videoRef.value
  if (v) {
    currentPosition.value = v.currentTime
  }
}
// 视频播放结束：进度直接显示 100%，并补发一次心跳把剩余有效时长落库
function onVideoEnded() {
  videoEnded.value = true
  progressPct.value = 100
  flush()
}

// ---- 文档（PDF 页图翻页 / DOCX 滚动阅读） ----
// DOCX：后端解析 word 结构输出 HTML（图片内嵌 base64），滚动阅读、进度按滚动虚拟页码记录
async function loadDocx() {
  try {
    const resp = await fetch(`/api/v1/app/learn/materials/${materialId}/doc-html`, {
      headers: { Authorization: `Bearer ${userStore.accessToken}` }
    })
    if (!resp.ok) throw new Error(`文档解析失败(${resp.status})`)
    const tp = parseInt(resp.headers.get('X-Total-Pages') || '0', 10)
    if (tp > 0) pageCount.value = tp
    docHtml.value = await resp.text()
    // 断点续学：滚动到上次阅读位置（一屏记一页）
    await nextTick()
    const sc = docScrollRef.value
    if (sc && resumePosition.value > 1 && pageCount.value > 1) {
      sc.scrollTop = (resumePosition.value - 1) * sc.clientHeight
    }
    onDocScroll()
  } catch (e) {
    pdfError.value = true
  }
}

function onDocScroll() {
  const sc = docScrollRef.value
  if (!sc || !pageCount.value) return
  const p = Math.min(pageCount.value, Math.floor(sc.scrollTop / Math.max(1, sc.clientHeight)) + 1)
  if (p > docPageNo.value) {
    docPageNo.value = p
    newReadPages.add(p)
    readPages.add(p)
    progressPct.value = Math.max(progressPct.value, (p / pageCount.value) * 100)
  }
}

// PDF 页图翻页：预签名地址仅用于"新窗口打开"兜底；页内预览全部走页图接口
async function renderPage() {
  pageRendering.value = true
  try {
    const resp = await fetch(`/api/v1/app/learn/materials/${materialId}/pages/${pageNo.value}`, {
      headers: { Authorization: `Bearer ${userStore.accessToken}` }
    })
    if (!resp.ok) throw new Error(`页图加载失败(${resp.status})`)
    const tp = parseInt(resp.headers.get('X-Total-Pages') || '0', 10)
    if (tp > 0) pageCount.value = tp
    const blob = await resp.blob()
    if (pageImageUrl.value) URL.revokeObjectURL(pageImageUrl.value)
    pageImageUrl.value = URL.createObjectURL(blob)
    if (!readPages.has(pageNo.value)) {
      readPages.add(pageNo.value)
      newReadPages.add(pageNo.value)
    }
    if (pageCount.value) {
      progressPct.value = Math.max(progressPct.value, (readPages.size / pageCount.value) * 100)
    }
    pageInput.value = pageNo.value
  } catch (e) {
    pdfError.value = true
  } finally {
    pageRendering.value = false
  }
}

async function goPage(n) {
  if (!pageCount.value) return
  pageNo.value = Math.min(Math.max(1, n), pageCount.value)
  await renderPage()
}

function onPageInput(n) {
  goPage(n || 1)
}

function zoom(delta) {
  zoomPct.value = Math.min(200, Math.max(50, zoomPct.value + delta))
}

async function load() {
  loading.value = true
  try {
    const d = await getMaterialDetail(materialId)
    detail.value = d
    const rec = d.myRecord || {}
    progressPct.value = rec.progressPct ?? 0
    effectiveSec.value = rec.effectiveSec ?? 0
    status.value = rec.status || 'NOT_STARTED'
    resumePosition.value = rec.lastPosition ?? 0
    ;(rec.readPages || []).forEach((p) => readPages.add(p))

    if (d.materialType === 'VIDEO') {
      const res = await getMaterialFile(materialId)
      fileUrl.value = res.fileUrl
      altFileUrl.value = res.altFileUrl || ''
      hasWebm.value = !!res.altFileUrl
    } else if (d.materialType === 'DOC') {
      await nextTick()
      try {
        const res = await getMaterialFile(materialId)
        fileUrl.value = res.fileUrl
        const ft = (res.fileType || '').toLowerCase()
        if (ft === 'pdf') {
          // PDF 页图预览：总页数取资料元数据，实际以页图接口返回为准
          pageCount.value = Math.max(1, d.pageCount || 1)
          const last = Math.min(Math.max(1, resumePosition.value || 1), pageCount.value)
          await goPage(last)
        } else if (ft === 'docx') {
          await loadDocx()
        } else {
          // 旧版 doc/ppt 等二进制格式：服务端不支持在线解析，提示下载查看
          docUnsupported.value = ft || '未知'
        }
      } catch (e) {
        pdfError.value = true
      }
    }
  } finally {
    loading.value = false
  }
}

// 视频进度百分比每 5s 本地刷新一次（心跳本身 15s 才上报一次）
const pctTimer = setInterval(refreshVideoProgress, 5000)

onMounted(load)
onBeforeUnmount(() => {
  clearInterval(pctTimer)
  if (pageImageUrl.value) URL.revokeObjectURL(pageImageUrl.value)
})
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.title {
  font-size: 17px;
  font-weight: 600;
}
.back-btn {
  margin-right: 10px;
}
.ml8 {
  margin-left: 8px;
}
.progress-box {
  display: flex;
  align-items: center;
  gap: 12px;
}
.progress-text {
  font-size: 13px;
}
.richtext-body {
  padding: 8px 16px;
}
.video-body .player {
  width: 100%;
  max-height: 70vh;
  background: #000;
  margin-bottom: 8px;
}
.doc-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.canvas-wrap {
  text-align: center;
  background: #525659;
  padding: 16px;
  border-radius: 4px;
  overflow: auto;
}
.canvas-wrap .doc-page-img {
  background: #fff;
  max-width: none;
  display: inline-block;
}
/* DOCX 滚动阅读容器：正文限宽居中，模拟纸张排版 */
.docx-scroll {
  max-height: 70vh;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #f5f7fa;
  padding: 16px;
}
.docx-content {
  max-width: 820px;
  margin: 0 auto;
  background: #fff;
  padding: 32px 40px;
  border-radius: 2px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  word-break: break-word;
}
.docx-content :deep(img) {
  max-width: 100%;
}
.docx-content :deep(.docx-page-break) {
  border-top: 2px dashed #c0c4cc;
  margin: 16px 0;
}
</style>
