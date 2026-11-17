// 可见范围选择口径：scopeType=L2_SUB（指定二级集团及下属）时排除集团本部根节点；
// SPECIFIC（指定单位）允许勾选任意单位（含本部），不做处理。

// 判断节点是否为集团本部（orgType=HQ）
function isHqOrg(node) {
  return node && node.orgType === 'HQ'
}

// L2_SUB 模式下使用的组织树：剔除集团本部节点并将其下级提升一层展示；
// 兼容多根数据（存在多个根节点时同样剔除其中的本部节点）
export function excludeRootOrg(tree) {
  const list = Array.isArray(tree) ? tree : []
  const out = []
  for (const n of list) {
    if (isHqOrg(n)) {
      if (Array.isArray(n.children)) out.push(...n.children)
    } else {
      out.push(n)
    }
  }
  return out
}

// 组织树中集团本部节点的 id 集合（任意层级，通常即根节点）
export function rootOrgIds(tree) {
  const ids = []
  const walk = (list) => {
    for (const n of Array.isArray(list) ? list : []) {
      if (isHqOrg(n)) ids.push(n.orgId)
      walk(n.children)
    }
  }
  walk(tree)
  return ids
}

// 从已选单位 id 列表中剔除集团本部节点（切换到 L2_SUB 时调用）
export function stripRootOrgIds(tree, ids) {
  const roots = rootOrgIds(tree)
  if (!roots.length || !Array.isArray(ids)) return ids
  return ids.filter((id) => !roots.includes(id))
}
