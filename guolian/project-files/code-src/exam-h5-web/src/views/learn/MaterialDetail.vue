<template>
  <div class="page page-has-navbar">
    <van-nav-bar :title="detail.title || '资料详情'" left-arrow @click-left="onBack" fixed placeholder />
    <van-loading v-if="loading" class="page-loading" size="24px">加载中...</van-loading>

    <template v-else-if="detail.materialId">
      <!-- 头部信息 -->
      <div class="meta-bar">
        <van-tag plain type="primary">{{ materialTypeName[detail.materialType] || detail.materialType }}</van-tag>
        <span class="text-muted">{{ detail.catName }}</span>
        <span class="text-muted">标称时长 {{ formatDuration(detail.durationSec) }}</span>
        <van-tag v-if="hb.status.value === 'COMPLETED' || myStatus === 'COMPLETED'" type="success" plain>已完成</van-tag>
      </div>
      <van-progress
        :percentage="displayProgress"
        stroke-width="6"
        :color="displayProgress >= 100 ? '#07c160' : '#1989fa'"
      />

      <!-- 图文类：富文本渲染（后端类型为 RICHTEXT，兼容 ARTICLE） -->
      <div
        v-if="detail.materialType === 'RICHTEXT' || detail.materialType === 'ARTICLE'"
        class="rich-content"
        v-html="detail.contentHtml || '<p>暂无内容</p>'"
      ></div>

      <!-- 文档类 DOCX：后端解析 word 结构输出 HTML，滚动阅读、进度按滚动位置自动记录 -->
      <div v-else-if="detail.materialType === 'DOC' && docHtml" class="docx-wrap">
        <div class="doc-toolbar">
          <span class="doc-page-text">共 {{ totalPages }} 页 · 上下滚动阅读，进度自动记录</span>
        </div>
        <div class="docx-html" v-html="docHtml"></div>
      </div>

      <!-- 文档类：PDF 页图预览（后端逐页转 JPEG，前端只加载图片，避免下载工具拦截 PDF 流） -->
      <div v-else-if="detail.materialType === 'DOC'" class="doc-wrap">
        <div class="doc-toolbar">
          <van-button size="small" :disabled="currentPage <= 1" @click="gotoPage(currentPage - 1)">上一页</van-button>
          <span class="doc-page-text">第 {{ currentPage }} / {{ totalPages }} 页</span>
          <van-button size="small" :disabled="currentPage >= totalPages" @click="gotoPage(currentPage + 1)">下一页</van-button>
        </div>
        <div class="doc-toolbar doc-toolbar-zoom">
          <van-button size="mini" :disabled="widthPct <= 100" @click="zoomWidth(-15)">缩小</van-button>
          <span class="doc-page-text">{{ widthPct }}%</span>
          <van-button size="mini" :disabled="widthPct >= 300" @click="zoomWidth(15)">放大</van-button>
        </div>
        <van-progress
          :percentage="totalPages ? Math.round((readPageSet.size / totalPages) * 100) : 0"
          stroke-width="6"
          class="doc-progress"
        />
        <div class="text-muted doc-progress-text">已读 {{ readPageSet.size }} / {{ totalPages }} 页 · 左右滑动查看整行，双指捏合可缩放，点击图片全屏阅读</div>
        <div
          v-if="pageImageUrl"
          v-show="!pdfError"
          class="doc-canvas-wrap"
          @touchstart="onPinchStart"
          @touchmove="onPinchMove"
          @touchend="onPinchEnd"
          @touchcancel="onPinchEnd"
        >
          <van-loading v-if="pageRendering" class="doc-loading" />
          <img :src="pageImageUrl" class="doc-page-img" :style="{ width: widthPct + '%' }" alt="资料页图" @click="previewPage" />
        </div>
        <van-loading v-else-if="!pdfError" class="doc-loading-inline" />
        <van-empty v-if="pdfError" description="文档预览加载失败" />

        <!-- 全屏预览：左右滑动翻页 + 双指捏合缩放（vant 原生支持），底部固定翻页按钮 -->
        <van-image-preview
          v-model:show="showPreview"
          ref="previewRef"
          :images="previewImages"
          :start-position="previewStart"
          closeable
          @change="onPreviewChange"
        >
          <template #cover>
            <div class="preview-nav" @click.stop>
              <van-button size="small" round icon="arrow-left" :disabled="currentPage <= 1" @click.stop="previewPrev">上一页</van-button>
              <span class="preview-nav-text">{{ currentPage }} / {{ totalPages }}</span>
              <van-button size="small" round icon-position="right" :disabled="currentPage >= totalPages" @click.stop="previewNext">下一页<i class="van-icon van-icon-arrow" style="margin-left:2px" /></van-button>
            </div>
          </template>
        </van-image-preview>
      </div>

      <!-- 视频类：播放器 -->
      <div v-else-if="detail.materialType === 'VIDEO'" class="video-wrap">
        <video
          ref="videoRef"
          :key="videoSrcMp4"
          controls
          playsinline
          webkit-playsinline
          class="video-player"
          @loadedmetadata="onVideoReady"
          @play="videoPlaying = true"
          @pause="videoPlaying = false"
          @ended="onVideoEnded"
        >
          <!-- 双格式源走同源代理（Range 流式）：H.264/MP4 优先，浏览器缺 H.264 解码时自动降级 WebM/VP9 -->
          <source :src="videoSrcMp4" type="video/mp4" />
          <source v-if="hasWebm" :src="videoSrcWebm" type="video/webm" />
        </video>
        <div class="text-muted video-tip">拖动进度条不影响有效时长统计，请完整观看。</div>
      </div>

      <!-- 简介 -->
      <van-cell-group v-if="detail.summary" inset title="资料简介" class="summary-group">
        <div class="summary-text">{{ detail.summary }}</div>
      </van-cell-group>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, reactive, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showLoadingToast, closeToast } from 'vant'
import { getMaterialDetail, getMaterialFile, getLearnRecord } from '../../api/learn'
import { useAuthStore } from '../../stores/auth'
import { useLearnHeartbeat } from '../../composables/learnHeartbeat'
import { materialTypeName, formatDuration } from '../../utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const materialId = route.params.id

const loading = ref(true)
const detail = ref({})
const fileUrl = ref('')
const altFileUrl = ref('')
// 视频同源代理地址（媒体标签无法带 Authorization 头，用 access_token 查询参数）
const hasWebm = ref(false)
const videoSrcMp4 = computed(() =>
  `/api/v1/app/learn/materials/${materialId}/video?access_token=${auth.accessToken}`)
const videoSrcWebm = computed(() =>
  `/api/v1/app/learn/materials/${materialId}/video?fmt=webm&access_token=${auth.accessToken}`)
const myStatus = ref('')

// 文档翻页状态
const currentPage = ref(1)
const totalPages = ref(1)
const readPageSet = reactive(new Set())
const pageRendering = ref(false)
const pdfError = ref(false)
const pageImageUrl = ref('')
// DOCX 滚动阅读：后端解析 word 结构输出 HTML，按滚动虚拟页码记录进度
const docHtml = ref('')
const docPageNo = ref(1)
// 页图默认 100%（整页适配屏宽）；用户可通过缩放按钮/双指捏合放大，最大 300%
const widthPct = ref(100)

function zoomWidth(delta) {
  widthPct.value = Math.min(300, Math.max(100, widthPct.value + delta))
}

// ---------- 双指捏合缩放（往外扩放大、往里缩缩小） ----------
let lastPinchDist = 0
function pinchDist(touches) {
  const [a, b] = [touches[0], touches[1]]
  return Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY)
}
function onPinchStart(e) {
  if (e.touches.length === 2) {
    lastPinchDist = pinchDist(e.touches)
  }
}
function onPinchMove(e) {
  if (e.touches.length === 2 && lastPinchDist > 0) {
    const d = pinchDist(e.touches)
    if (d > 0) {
      widthPct.value = Math.min(300, Math.max(100, Math.round((widthPct.value * d) / lastPinchDist)))
    }
    lastPinchDist = d
    e.preventDefault()
  }
}
function onPinchEnd(e) {
  if (e.touches.length < 2) {
    lastPinchDist = 0
  }
}

// ---------- 文档翻页（PDF 页图：后端转 JPEG，fetch 带鉴权头拉图片字节转 blob 展示） ----------
// 页图 blob 缓存：翻页与全屏预览共用，避免重复请求
const pageBlobs = {}

async function fetchPageBlob(p) {
  const resp = await fetch(`/api/v1/app/learn/materials/${materialId}/pages/${p}`, {
    headers: { Authorization: `Bearer ${auth.accessToken}` }
  })
  if (!resp.ok) throw new Error(`页图加载失败(${resp.status})`)
  const tp = parseInt(resp.headers.get('X-Total-Pages') || '0', 10)
  if (tp > 0) totalPages.value = tp
  const blob = await resp.blob()
  return URL.createObjectURL(blob)
}

async function loadPageImage(p) {
  pageRendering.value = true
  try {
    if (!pageBlobs[p]) pageBlobs[p] = await fetchPageBlob(p)
    pageImageUrl.value = pageBlobs[p]
  } catch (e) {
    pdfError.value = true
  } finally {
    pageRendering.value = false
  }
}

function markRead(p) {
  if (!readPageSet.has(p)) {
    readPageSet.add(p)
    hb.markPageRead(p)
  }
}

function gotoPage(p) {
  if (p < 1 || p > totalPages.value) return
  currentPage.value = p
  markRead(p)
  loadPageImage(p)
}

// ---------- DOCX 滚动阅读：后端解析 word 结构输出 HTML，进度按滚动虚拟页码记录 ----------
async function loadDocxHtml() {
  try {
    const resp = await fetch(`/api/v1/app/learn/materials/${materialId}/doc-html`, {
      headers: { Authorization: `Bearer ${auth.accessToken}` }
    })
    if (!resp.ok) throw new Error(`文档解析失败(${resp.status})`)
    const tp = parseInt(resp.headers.get('X-Total-Pages') || '0', 10)
    if (tp > 0) totalPages.value = tp
    docHtml.value = await resp.text()
    // 断点续学：滚动到上次阅读位置（一屏记一页）
    await nextTick()
    const last = (detail.value.myRecord && detail.value.myRecord.lastPosition) || 0
    if (last > 1 && totalPages.value > 1) {
      window.scrollTo(0, (last - 1) * window.innerHeight)
    }
    onDocScroll()
  } catch (e) {
    pdfError.value = true
  }
}

function onDocScroll() {
  if (!docHtml.value || !totalPages.value) return
  const vh = Math.max(1, window.innerHeight)
  const p = Math.min(totalPages.value, Math.floor((window.scrollY || window.pageYOffset || 0) / vh) + 1)
  if (p > docPageNo.value) {
    docPageNo.value = p
    hb.markPageRead(p)
    localDocPct.value = Math.max(localDocPct.value, (p / totalPages.value) * 100)
  }
}

// ---------- 全屏预览：多页左右滑动翻页 + 双指缩放 + 底部翻页按钮 ----------
const showPreview = ref(false)
const previewImages = ref([])
const previewStart = ref(0)
const previewRef = ref(null)

async function previewPage() {
  if (!pageImageUrl.value) return
  showLoadingToast({ message: '加载预览...', forbidClick: true, duration: 0 })
  try {
    // 预取全部页图，保证预览内可连续滑动翻页
    for (let p = 1; p <= totalPages.value; p++) {
      if (!pageBlobs[p]) pageBlobs[p] = await fetchPageBlob(p)
    }
    previewImages.value = Array.from({ length: totalPages.value }, (_, i) => pageBlobs[i + 1])
    previewStart.value = currentPage.value - 1
    showPreview.value = true
  } catch (e) {
    showToast('预览加载失败，请重试')
  } finally {
    closeToast()
  }
}

// 预览内滑动/按钮翻页：同步页码与已读进度，关闭后页内视图跟随
function onPreviewChange(idx) {
  const p = idx + 1
  currentPage.value = p
  markRead(p)
  if (pageBlobs[p]) pageImageUrl.value = pageBlobs[p]
}

function previewPrev() {
  if (currentPage.value > 1) previewRef.value?.swipeTo(currentPage.value - 2)
}

function previewNext() {
  if (currentPage.value < totalPages.value) previewRef.value?.swipeTo(currentPage.value)
}

// 视频状态
const videoRef = ref(null)
const videoPlaying = ref(false)
const videoEnded = ref(false)
let resumePosition = 0

const hb = useLearnHeartbeat(materialId)

// 视频本地进度：实际观看时长(服务端有效时长 + 未上报秒数) / 视频实际时长，每 5s 刷新
const localPct = ref(0)
// DOCX 滚动本地进度：已滚动虚拟页码 / 总页数
const localDocPct = ref(0)
function refreshLocalProgress() {
  if (detail.value.materialType !== 'VIDEO') return
  const v = videoRef.value
  if (!v || !v.duration) return
  const watched = (hb.effectiveSec.value || 0) + hb.pendingDeltaSec()
  localPct.value = Math.min(100, (watched / v.duration) * 100)
}
const pctTimer = setInterval(refreshLocalProgress, 5000)

const displayProgress = computed(() => {
  // 视频播放结束直接显示 100%
  if (videoEnded.value) return 100
  const pct = Math.max(
    Number(hb.progressPct.value) || 0,
    localPct.value,
    localDocPct.value,
    (detail.value.myRecord && detail.value.myRecord.progressPct) || 0
  )
  // 后端 progressPct 口径为 0~100，直接取整，不得再乘 100
  return Math.min(100, Math.round(pct))
})

function onBack() {
  router.back()
}

// ---------- 视频 ----------
function onVideoReady() {
  // 断点续学：恢复上次播放位置
  if (videoRef.value && resumePosition > 0 && resumePosition < videoRef.value.duration - 5) {
    videoRef.value.currentTime = resumePosition
    showToast(`已从 ${Math.floor(resumePosition / 60)}分${Math.floor(resumePosition % 60)}秒 处继续学习`)
  }
}

function onVideoEnded() {
  videoPlaying.value = false
  videoEnded.value = true
  // 补发一次心跳，把最后一段有效时长和断点落库（不停止周期心跳，重播仍可继续计时）
  hb.reportNow()
}

async function init() {
  try {
    const d = await getMaterialDetail(materialId)
    const rec = d.myRecord || {}
    // 断点续看：必须先拿到续看位置再渲染 <video>，否则 loadedmetadata 可能先于赋值触发，导致无法恢复
    if (d.materialType === 'VIDEO') {
      resumePosition = rec.lastPosition || 0
    }
    detail.value = d
    myStatus.value = rec.status || ''
    hb.progressPct.value = rec.progressPct || 0
    hb.effectiveSec.value = rec.effectiveSec || 0

    if (d.materialType === 'VIDEO') {
      try {
        const f = await getMaterialFile(materialId)
        fileUrl.value = f.fileUrl
        altFileUrl.value = f.altFileUrl || ''
        hasWebm.value = !!f.altFileUrl
      } catch (e) {
        // 文件地址获取失败，页面仍可展示元信息
      }
    }

    if (d.materialType === 'DOC') {
      // 按文件实际扩展名选择预览方式：docx 走 HTML 滚动阅读，pdf 走页图翻页
      let ft = ''
      try {
        const f = await getMaterialFile(materialId)
        fileUrl.value = f.fileUrl
        ft = (f.fileType || '').toLowerCase()
      } catch (e) {
        // 文件地址获取失败时按 PDF 页图流程兜底（会走错误提示）
      }
      if (ft === 'docx') {
        await loadDocxHtml()
        hb.setPositionGetter(() => ({ pageNo: docPageNo.value }))
        window.addEventListener('scroll', onDocScroll, { passive: true })
      } else {
        ;(rec.readPages || []).forEach((p) => readPageSet.add(p))
        // PDF 页图预览：总页数取资料元数据，实际以页图接口返回为准
        totalPages.value = Math.max(1, d.pageCount || 1)
        // 断点续学：恢复最后阅读页码
        const last = rec.lastPosition || 0
        gotoPage(last >= 1 && last <= totalPages.value ? last : 1)
        hb.setPositionGetter(() => ({ pageNo: currentPage.value }))
      }
    } else if (d.materialType === 'VIDEO') {
      hb.setPositionGetter(() => ({
        positionSec: videoRef.value ? Math.floor(videoRef.value.currentTime) : undefined,
        // 视频实际时长作为进度分母上报，服务端优先于资料标称时长
        durationSec: videoRef.value && videoRef.value.duration ? Math.floor(videoRef.value.duration) : undefined
      }))
      // 视频仅播放中计有效时长
      hb.setActiveChecker(() => videoPlaying.value)
    }

    // 切换终端场景下再对账一次断点（LEARN-05）
    try {
      const r = await getLearnRecord(materialId)
      if (d.materialType === 'VIDEO' && (r.lastPosition || 0) > resumePosition) {
        resumePosition = r.lastPosition
        if (videoRef.value && videoRef.value.duration) {
          videoRef.value.currentTime = resumePosition
        }
      }
    } catch (e) {
      // 忽略
    }

    hb.start()
  } catch (e) {
    // 错误提示由拦截器处理
  } finally {
    loading.value = false
  }
}

onMounted(init)

onBeforeUnmount(async () => {
  clearInterval(pctTimer)
  window.removeEventListener('scroll', onDocScroll)
  // 离开页面前补发一次心跳，尽量保留进度
  await hb.flush()
  hb.stop()
  Object.values(pageBlobs).forEach((u) => URL.revokeObjectURL(u))
})
</script>

<style scoped>
.page-loading {
  display: block;
  text-align: center;
  margin-top: 80px;
}

.meta-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fff;
}

.doc-wrap {
  background: #fff;
  margin-top: 8px;
  padding-bottom: 12px;
}

/* DOCX 滚动阅读：正文白底限宽，模拟纸张排版 */
.docx-wrap {
  background: #fff;
  margin-top: 8px;
  padding-bottom: 12px;
}

.docx-html {
  padding: 4px 16px 16px;
  font-size: 16px;
  line-height: 1.9;
  color: #333;
  word-break: break-word;
  background: #fff;
}

.docx-html :deep(img) {
  max-width: 100%;
}

.docx-html :deep(.docx-page-break) {
  border-top: 2px dashed #c0c4cc;
  margin: 16px 0;
}

.doc-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
}

.doc-toolbar-zoom {
  padding-top: 0;
  justify-content: center;
  gap: 12px;
}

.doc-page-text {
  font-size: 14px;
  color: #323233;
}

.doc-progress {
  margin: 0 16px;
  width: auto;
}

.doc-progress-text {
  text-align: center;
  margin: 6px 0;
}

.doc-canvas-wrap {
  position: relative;
  width: 100%;
  min-height: 40vh;
  background: #fff;
  padding: 4px 16px;
  box-sizing: border-box;
  overflow-x: auto;
}

.doc-page-img {
  display: block;
  width: 100%;
}

.doc-loading {
  position: absolute;
  top: 40px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2;
}

.doc-loading-inline {
  display: block;
  text-align: center;
  padding: 40px 0;
}

.video-wrap {
  background: #000;
  margin-top: 8px;
}

.video-player {
  width: 100%;
  max-height: 60vh;
  display: block;
}

.video-tip {
  background: #fff;
  padding: 8px 16px;
}

.summary-group {
  margin-top: 12px;
}

.summary-text {
  padding: 12px 16px;
  font-size: 14px;
  color: #646566;
  line-height: 1.6;
}

/* 全屏预览底部翻页栏（cover 插槽在每页内绝对定位，固定到视口底部） */
.preview-nav {
  position: fixed;
  bottom: 48px;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  z-index: 10;
}

.preview-nav-text {
  color: #fff;
  font-size: 14px;
  text-shadow: 0 1px 1px #323233;
}
</style>
