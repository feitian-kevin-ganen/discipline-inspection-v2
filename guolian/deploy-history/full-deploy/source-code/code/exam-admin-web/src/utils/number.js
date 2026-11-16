// 数值输入校正：将 v 取整到最近的 step 倍数，并夹在 [min, max] 内
// 用于 el-input-number 直接键盘输入绕过步进时的失焦/提交纠正
export function snapToStep(v, step, min = 0, max = Infinity) {
  const n = Number(v)
  if (!Number.isFinite(n)) return min
  // 先按 step 四舍五入，再消除浮点误差（如 0.5 步进）
  let snapped = Math.round(n / step) * step
  snapped = Number(snapped.toFixed(6))
  if (snapped < min) snapped = min
  if (snapped > max) snapped = max
  return snapped
}
