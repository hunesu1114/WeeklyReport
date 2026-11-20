<script setup>
/**
 * 앱 아이콘. 파비콘(public/favicon.svg)과 같은 그림을 쓴다.
 * 탭에 보이는 것과 화면 좌상단이 다르면 같은 앱으로 안 보인다.
 *
 * 월~금 다섯 칸 가운데 보고하는 금요일만 칠했다. 한 주를 쓰고 금요일에 묶는 앱이다.
 */
defineProps({
  size: { type: Number, default: 22 },
})
</script>

<template>
  <svg
    class="brandmark"
    :width="size"
    :height="size"
    viewBox="0 0 20 20"
    aria-hidden="true"
    focusable="false"
  >
    <!--
      바탕 타일을 두지 않는다. 평일 네 칸은 글자색을 따라가 라이트/다크 어디서든
      옅은 회색으로 깔리고, 금요일 한 칸만 키컬러로 남는다.
    -->
    <g class="brandmark__days">
      <rect x="0" y="3" width="2.6" height="14" rx="1.3" />
      <rect x="4.35" y="3" width="2.6" height="14" rx="1.3" />
      <rect x="8.7" y="3" width="2.6" height="14" rx="1.3" />
      <rect x="13.05" y="3" width="2.6" height="14" rx="1.3" />
    </g>
    <rect class="brandmark__friday" x="17.4" y="3" width="2.6" height="14" rx="1.3" />
  </svg>
</template>

<style scoped>
.brandmark {
  display: block;
  flex: none;
}

.brandmark__days {
  fill: currentColor;
  opacity: 0.28;
}

.brandmark__friday {
  fill: var(--brand);
}
</style>
