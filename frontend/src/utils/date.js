// 统一时间/日期格式化
// 日期时间：YYYY-MM-DD HH:mm:ss
export function formatDateTime(input) {
  if (!input) return ''
  // 已经是“第X周…”等业务文案，直接返回
  if (typeof input === 'string' && (input.includes('第') || /周[一二三四五六日][上下]午/.test(input))) return input
  try {
    const d = new Date(input)
    if (!isNaN(d.getTime())) {
      const y = d.getFullYear()
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      const hh = String(d.getHours()).padStart(2, '0')
      const mm = String(d.getMinutes()).padStart(2, '0')
      const ss = String(d.getSeconds()).padStart(2, '0')
      return `${y}-${m}-${day} ${hh}:${mm}:${ss}`
    }
  } catch (e) {}
  return String(input)
}

// 日期：YYYY-MM-DD
export function formatDate(input) {
  if (!input) return ''
  try {
    const d = new Date(input)
    if (!isNaN(d.getTime())) {
      const y = d.getFullYear()
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      return `${y}-${m}-${day}`
    }
  } catch (e) {}
  return String(input)
}


