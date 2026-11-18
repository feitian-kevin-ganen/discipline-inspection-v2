<template>
  <div class="page-container">
    <div class="page-card">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="在线设计器基于 bpmn-js：可直接拖拽建模，也可导入本地 BPMN 文件；部署后生成新的流程定义版本，业务侧按流程 key 的最新版本发起。"
        style="margin-bottom: 12px"
      />

      <div class="toolbar">
        <el-button type="primary" plain @click="newDiagram">新建</el-button>
        <el-button plain @click="pickFile">打开本地 XML</el-button>
        <el-button plain :disabled="!xmlReady" @click="exportXml">导出 XML</el-button>
        <el-button type="primary" :disabled="!xmlReady" @click="openDeploy">部署</el-button>
        <span class="hint" v-if="currentKey">当前编辑：{{ currentKey }}</span>
        <input ref="fileRef" type="file" accept=".bpmn,.bpmn20.xml,.xml" style="display: none" @change="onFile" />
      </div>

      <div class="designer-wrap">
        <div class="def-list">
          <div class="def-head">
            <span>流程定义</span>
            <el-button link type="primary" @click="loadDefinitions">刷新</el-button>
          </div>
          <el-scrollbar height="100%">
            <div
              v-for="d in definitions"
              :key="d.id"
              class="def-item"
              :class="{ active: d.key === currentKey }"
            >
              <div class="def-name" :title="d.name">{{ d.name }}</div>
              <div class="def-meta">
                <span class="key">{{ d.key }}</span>
                <el-tag size="small" :type="Number(d.version) > 1 ? 'info' : 'success'">v{{ d.version }}</el-tag>
                <el-tag v-if="d.suspended" size="small" type="danger">挂起</el-tag>
              </div>
              <div class="def-ops">
                <el-button link type="primary" @click="editDefinition(d)">编辑</el-button>
              </div>
            </div>
            <el-empty v-if="!definitions.length" description="暂无已部署流程" :image-size="60" />
          </el-scrollbar>
        </div>

        <div class="canvas-col">
          <div ref="canvasRef" class="canvas"></div>
        </div>
      </div>
    </div>

    <el-dialog v-model="deployVisible" title="部署流程" width="520px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="流程名称" required>
          <el-input v-model="deployName" placeholder="如 题目审核流程" />
        </el-form-item>
      </el-form>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="流程 key 取自 BPMN 中 process 的 id；同一 key 再次部署会生成新版本，已运行实例仍走旧版本。"
      />
      <template #footer>
        <el-button @click="deployVisible = false">取消</el-button>
        <el-button type="primary" :loading="deploying" @click="doDeploy">部署</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="xmlDrawer" title="XML 文本编辑" width="720px" destroy-on-close append-to-body>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="该流程定义没有图形坐标信息（BPMN DI），无法用图形方式打开。可直接修改下方 XML 并部署，部署后生成新版本。"
        style="margin-bottom: 12px"
      />
      <el-input v-model="xmlName" placeholder="流程名称（部署时使用）" style="margin-bottom: 12px" />
      <el-input v-model="xmlText" type="textarea" :rows="26" spellcheck="false" class="xml-area" />
      <template #footer>
        <el-button @click="xmlDrawer = false">取消</el-button>
        <el-button type="primary" :loading="xmlDeploying" @click="deployRawXml">保存并部署</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import Modeler from 'bpmn-js/lib/Modeler'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import { workflowApi } from '../../api'

const EMPTY_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:flowable="http://flowable.org/bpmn" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_1" name="新建流程" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="开始">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="EndEvent_1" />
    <bpmn:endEvent id="EndEvent_1" name="结束">
      <bpmn:incoming>Flow_1</bpmn:incoming>
    </bpmn:endEvent>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="172" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <dc:Bounds x="322" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
        <di:waypoint x="208" y="120" />
        <di:waypoint x="322" y="120" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`

const canvasRef = ref(null)
const fileRef = ref(null)
const modeler = shallowRef(null)
const definitions = ref([])
const currentKey = ref('')
const xmlReady = ref(false)
const deployVisible = ref(false)
const deploying = ref(false)
const deployName = ref('')
const xmlDrawer = ref(false)
const xmlText = ref('')
const xmlName = ref('')
const xmlDeploying = ref(false)

async function initModeler() {
  await nextTick()
  modeler.value = new Modeler({ container: canvasRef.value })
  try {
    await modeler.value.importXML(EMPTY_XML)
    xmlReady.value = true
    fitViewport()
  } catch (e) {
    ElMessage.error('设计器初始化失败：' + (e.message || e))
  }
}

function fitViewport() {
  if (!modeler.value) return
  const canvas = modeler.value.get('canvas')
  canvas.zoom('fit-viewport', 'auto')
}

function parseProcessMeta(xml) {
  const text = String(xml || '')
  const m = /<(?:\w+:)?process\b[^>]*>/i.exec(text)
  const tag = m ? m[0] : ''
  return {
    name: (/name="([^"]*)"/i.exec(tag) || [])[1] || '',
    key: (/id="([^"]*)"/i.exec(tag) || [])[1] || ''
  }
}

function openXmlFallback(xml, key) {
  const meta = parseProcessMeta(xml)
  xmlText.value = typeof xml === 'string' ? xml : (xml && xml.xml) || ''
  xmlName.value = meta.name || ''
  currentKey.value = key || meta.key || ''
  xmlReady.value = false
  xmlDrawer.value = true
  ElMessage.warning('该流程定义无图形信息，请用 XML 文本方式编辑')
}

async function deployRawXml() {
  const meta = parseProcessMeta(xmlText.value)
  const name = xmlName.value.trim() || meta.name
  if (!name) {
    ElMessage.warning('流程名称必填')
    return
  }
  if (!xmlText.value.trim()) {
    ElMessage.warning('XML 内容为空')
    return
  }
  xmlDeploying.value = true
  try {
    await workflowApi.deployDesigner({ name, xml: xmlText.value })
    ElMessage.success('部署成功')
    xmlDrawer.value = false
    await loadDefinitions()
  } finally {
    xmlDeploying.value = false
  }
}

async function loadDefinitions() {
  try {
    const data = await workflowApi.designerList()
    definitions.value = Array.isArray(data) ? data : data.records || data.list || []
  } catch (e) {
    // 拦截器已提示
  }
}

async function newDiagram() {
  currentKey.value = ''
  await modeler.value.importXML(EMPTY_XML)
  xmlReady.value = true
  fitViewport()
}

async function editDefinition(d) {
  let xml
  try {
    xml = await workflowApi.designerXml(d.key)
  } catch (e) {
    return
  }
  if (!xml) {
    ElMessage.warning('该流程定义未取到 XML')
    return
  }
  const text = typeof xml === 'string' ? xml : xml.xml
  currentKey.value = d.key
  try {
    await modeler.value.importXML(text)
    xmlReady.value = true
    fitViewport()
    ElMessage.success(`已载入 ${d.key}（v${d.version}）`)
  } catch (e) {
    openXmlFallback(text, d.key)
  }
}

function pickFile() {
  fileRef.value && fileRef.value.click()
}

async function onFile(ev) {
  const file = ev.target.files && ev.target.files[0]
  ev.target.value = ''
  if (!file) return
  try {
    const text = await file.text()
    currentKey.value = ''
    try {
      await modeler.value.importXML(text)
      xmlReady.value = true
      fitViewport()
      ElMessage.success('已载入本地文件')
    } catch (e) {
      openXmlFallback(text, '')
    }
  } catch (e) {
    ElMessage.error('文件读取失败：' + (e.message || e))
  }
}

async function exportXml() {
  try {
    const { xml } = await modeler.value.saveXML({ format: true })
    const blob = new Blob([xml], { type: 'application/xml;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = (currentKey.value || 'process') + '.bpmn20.xml'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('导出失败：' + (e.message || e))
  }
}

function openDeploy() {
  const root = modeler.value && modeler.value.getDefinitions && modeler.value.getDefinitions().rootElements
  const proc = root && root.find((el) => el.$type === 'bpmn:Process')
  deployName.value = (proc && proc.name) || '新建流程'
  deployVisible.value = true
}

async function doDeploy() {
  if (!deployName.value.trim()) {
    ElMessage.warning('流程名称必填')
    return
  }
  deploying.value = true
  try {
    const { xml } = await modeler.value.saveXML({ format: true })
    await workflowApi.deployDesigner({ name: deployName.value.trim(), xml })
    ElMessage.success('部署成功')
    deployVisible.value = false
    await loadDefinitions()
  } finally {
    deploying.value = false
  }
}

onMounted(async () => {
  await initModeler()
  loadDefinitions()
})

onBeforeUnmount(() => {
  if (modeler.value) {
    modeler.value.destroy()
    modeler.value = null
  }
})
</script>

<style scoped>
.hint {
  margin-left: 12px;
  color: #909399;
  font-size: 13px;
}
.designer-wrap {
  display: flex;
  gap: 12px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  height: calc(100vh - 250px);
  min-height: 460px;
  overflow: hidden;
}
.def-list {
  width: 280px;
  flex: none;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
}
.def-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
  font-weight: 600;
  font-size: 14px;
}
.def-item {
  padding: 10px 12px;
  border-bottom: 1px solid #f5f7fa;
  cursor: pointer;
}
.def-item:hover {
  background: #f5f7fa;
}
.def-item.active {
  background: #ecf5ff;
}
.def-name {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.def-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
.def-meta .key {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.canvas-col {
  flex: 1;
  min-width: 0;
}
.canvas {
  width: 100%;
  height: 100%;
  background: #fff;
}
.xml-area :deep(textarea) {
  font-family: Consolas, Monaco, "Courier New", monospace;
  font-size: 12px;
  line-height: 1.5;
}
</style>
