/**
 * `<input type="date">` 는 브라우저 기본 동작상 오른쪽 달력 아이콘을 정확히 눌러야만
 * 달력이 열린다. 아이콘이 작아서 잘 안 눌리므로, 입력칸 아무 데나 눌러도 열리게 한다.
 *
 * showPicker() 는 사용자 제스처 안에서만 부를 수 있어 click 핸들러에 붙인다.
 * 지원하지 않는 브라우저에서는 아무 일도 하지 않고 기존 동작(아이콘 클릭)이 그대로 남는다.
 */
export const datePicker = {
  mounted(el) {
    el._openPicker = () => {
      if (el.disabled || el.readOnly || typeof el.showPicker !== 'function') return
      try {
        el.showPicker()
      } catch {
        // 이미 열려 있거나(아이콘을 직접 누른 경우) 브라우저가 막은 경우.
        // 기본 동작이 있으므로 조용히 넘긴다.
      }
    }
    el.addEventListener('click', el._openPicker)
  },

  unmounted(el) {
    el.removeEventListener('click', el._openPicker)
    delete el._openPicker
  },
}
