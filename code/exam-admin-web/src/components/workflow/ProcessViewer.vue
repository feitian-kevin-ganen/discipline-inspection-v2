<template>
  <div ref="containerRef" class="process-viewer" :style="{ height }">
    <div v-if="loadError" class="viewer-error">流程图渲染失败：{{ loadError }}</div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer'
import flowableDescriptor from './flowable-ext.json'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'

const props = defineProps({
  /** 流程定义 BPMN XML */
  xml: { type: String, default: '' },
  /** 高亮：{ finished: [], active: [], rejected: [] } */
  highlight: { type: Object, default: () => ({}) },
  height: { type: String, default: '360px' }
})

const containerRef = ref(null)
const loadError = ref('')
let viewer = null

function clearMarkers() {
  if (!viewer) return
  const canvas = viewer.get('canvas')
  viewer.get('elementRegistry').forEach((el) => {
    canvas.removeMarker(el, 'highlight-finished')
    canvas.removeMarker(el, 'highlight-active')
    canvas.removeMarker(el, 'highlight-rejected')
  })
}

function applyHighlight() {
  if (!viewer) return
  const canvas = viewer.get('canvas')
  const data = props.highlight || {}
  ;(data.finished || []).forEach((id) => canvas.addMarker(id, 'highlight-finished'))
  ;(data.active || []).forEach((id) => canvas.addMarker(id, 'highlight-active'))
  ;(data.rejected || []).forEach((id) => canvas.addMarker(id, 'highlight-rejected'))
}

async function render() {
  if (!viewer || !props.xml) return
  try {
    await viewer.importXML(props.xml)
    loadError.value = ''
    viewer.get('canvas').zoom('fit-viewport', 'auto')
    clearMarkers()
    applyHighlight()
  } catch (err) {
    loadError.value = (err && err.message) || 'XML 解析异常'
  }
}

watch(() => [props.xml, props.highlight], render, { deep: true })

onMounted(() => {
  viewer = new NavigatedViewer({
    container: containerRef.value,
    moddleExtensions: { flowable: flowableDescriptor }
  })
  render()
})

onBeforeUnmount(() => {
  if (viewer) {
    viewer.destroy()
    viewer = null
  }
})
</script>

<style scoped>
.process-viewer {
  width: 100%;
  border: 1px solid var(--el-border-color-lighter);
  background: #fff;
  position: relative;
  overflow: hidden;
}
.viewer-error {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-color-danger);
  font-size: 14px;
  background: #fff;
  z-index: 10;
}
</style>

<style>
/* 流程图节点高亮：已完成绿 / 进行中橙 / 驳回红 */
.highlight-finished:not(.djs-connection) .djs-visual > :nth-child(1) {
  stroke: #67c23a !important;
  stroke-width: 2px !important;
  fill: rgba(103, 194, 58, 0.15) !important;
}
.highlight-active:not(.djs-connection) .djs-visual > :nth-child(1) {
  stroke: #e6a23c !important;
  stroke-width: 2px !important;
  fill: rgba(230, 162, 60, 0.18) !important;
}
.highlight-rejected:not(.djs-connection) .djs-visual > :nth-child(1) {
  stroke: #f56c6c !important;
  stroke-width: 2px !important;
  fill: rgba(245, 108, 108, 0.18) !important;
}
</style>
