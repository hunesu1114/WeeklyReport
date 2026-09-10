<script setup>
/**
 * 앱 아이콘. 파비콘(public/favicon.svg)과 같은 그림을 쓴다.
 * 탭에 보이는 것과 화면 좌상단이 다르면 같은 앱으로 안 보인다.
 */
defineProps({
  size: { type: Number, default: 30 },
})
</script>

<template>
  <svg
    class="brandmark"
    :width="size"
    :height="size"
    viewBox="0 0 32 32"
    aria-hidden="true"
    focusable="false"
  >
    <!--
      테마 토큰을 쓰지 않고 색을 고정한다. 다크에서 토큰을 따라가면
      초록 바탕에 어두운 막대가 되어 탭 아이콘과 다른 로고처럼 보인다.
      초록 바탕은 라이트/다크 어느 쪽에서도 잘 보인다.
    -->
    <rect width="32" height="32" rx="7.5" fill="#16a34a" />
    <g fill="#ffffff">
      <rect x="6.5" y="7.5" width="5" height="17" rx="1.6" />
      <rect x="13.5" y="7.5" width="5" height="12" rx="1.6" opacity="0.82" />
      <rect x="20.5" y="7.5" width="5" height="7" rx="1.6" opacity="0.62" />
    </g>
  </svg>
</template>

<style scoped>
.brandmark {
  display: block;
  flex: none;
}
</style>
