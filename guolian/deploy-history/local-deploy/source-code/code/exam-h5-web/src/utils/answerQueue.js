// 弱网/断网答案本地队列：保存失败批次入 localStorage，网络恢复后按序重放（clientBatchId 保证幂等）
const queueKey = (sheetId) => `h5_answer_queue_${sheetId}`
const snapshotKey = (sheetId) => `h5_answer_snapshot_${sheetId}`

export function loadQueue(sheetId) {
  try {
    return JSON.parse(localStorage.getItem(queueKey(sheetId))) || []
  } catch (e) {
    return []
  }
}

function saveQueue(sheetId, queue) {
  if (queue.length === 0) {
    localStorage.removeItem(queueKey(sheetId))
  } else {
    localStorage.setItem(queueKey(sheetId), JSON.stringify(queue))
  }
}

export function enqueueBatch(sheetId, batch) {
  const q = loadQueue(sheetId)
  // 同 clientBatchId 不重复入队
  if (!q.some((b) => b.clientBatchId === batch.clientBatchId)) {
    q.push(batch)
    saveQueue(sheetId, q)
  }
}

export function dequeueBatch(sheetId, clientBatchId) {
  const q = loadQueue(sheetId).filter((b) => b.clientBatchId !== clientBatchId)
  saveQueue(sheetId, q)
}

export function clearQueue(sheetId) {
  localStorage.removeItem(queueKey(sheetId))
}

// 答案快照（用于本地兜底与恢复提示；以服务端 TAKE-08 数据为最终权威）
export function saveSnapshot(sheetId, snapshot) {
  try {
    localStorage.setItem(snapshotKey(sheetId), JSON.stringify(snapshot))
  } catch (e) {
    // 存储满等异常忽略
  }
}

export function loadSnapshot(sheetId) {
  try {
    return JSON.parse(localStorage.getItem(snapshotKey(sheetId))) || null
  } catch (e) {
    return null
  }
}

export function clearSnapshot(sheetId) {
  localStorage.removeItem(snapshotKey(sheetId))
}

export function clearAll(sheetId) {
  clearQueue(sheetId)
  clearSnapshot(sheetId)
}
