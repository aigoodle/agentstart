<script setup>
defineProps(['name'])
</script>

<template>
  <!-- 工作流图标 -->
  <svg v-if="name === 'workflow'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M6 2v6h.01L6 8.01L10 12l-4 4l.01.01H6V22h12v-5.99h-.01L18 16l-4-4l4-4l-.01-.01H18V2H6zm2 2h8v2.99h-.01L16 7l-3 3l3 3l-.01.01H16V20H8v-2.99h.01L8 17l3-3l-3-3l.01-.01H8V4z"/>
  </svg>

  <!-- 操作按钮图标 -->
  <svg v-else-if="name === 'play'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M8 5v14l11-7z"/>
  </svg>

  <svg v-else-if="name === 'debug'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M14 12l-4 4V8l4 4zm6-1c0-5.54-4.46-10-10-10S0 5.46 0 11s4.46 10 10 10s10-4.46 10-10zm-1 0c0 4.96-4.04 9-9 9s-9-4.04-9-9s4.04-9 9-9s9 4.04 9 9z"/>
  </svg>

  <svg v-else-if="name === 'publish'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/>
  </svg>

  <svg v-else-if="name === 'save'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3s3 1.34 3 3s-1.34 3-3 3zm3-10H5V5h10v4z"/>
  </svg>

  <svg v-else-if="name === 'share'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81c1.66 0 3-1.34 3-3s-1.34-3-3-3s-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65c0 1.61 1.31 2.92 2.92 2.92s2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z"/>
  </svg>

  <svg v-else-if="name === 'close'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M19 6.41L17.59 5L12 10.59L6.41 5L5 6.41L10.59 12L5 17.59L6.41 19L12 13.41L17.59 19L19 17.59L13.41 12z"/>
  </svg>

  <!-- 节点分类图标 -->
  <svg v-else-if="name === 'input'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10s10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5l1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
  </svg>

  <svg v-else-if="name === 'ai'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10s10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.94-.49-7-3.85-7-7.93c0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41c0 2.08-.8 3.97-2.1 5.39z"/>
  </svg>

  <svg v-else-if="name === 'logic'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M12 2l3.09 6.26L22 9.27l-5 4.87L18.18 22L12 18.77L5.82 22L7 14.14L2 9.27l6.91-1.01L12 2z"/>
  </svg>

  <svg v-else-if="name === 'tools'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M22.7 19l-9.1-9.1c.9-2.3.4-5-1.5-6.9c-2-2-5-2.4-7.4-1.3L9 6L6 9L1.6 4.7C.4 7.1.9 10.1 2.9 12.1c1.9 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0l2.3-2.3c.5-.4.5-1.1.1-1.4z"/>
  </svg>

  <svg v-else-if="name === 'output'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M9 16.2L4.8 12l-1.4 1.4L9 19L21 7l-1.4-1.4L9 16.2z"/>
  </svg>

  <!-- 具体节点图标 -->
  <svg v-else-if="name === 'start'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M8 5v14l11-7z"/>
  </svg>

  <svg v-else-if="name === 'user'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4s-4 1.79-4 4s1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
  </svg>

  <svg v-else-if="name === 'file'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z"/>
  </svg>

  <svg v-else-if="name === 'llm'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10s10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.94-.49-7-3.85-7-7.93c0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41c0 2.08-.8 3.97-2.1 5.39z"/>
  </svg>

  <svg v-else-if="name === 'agent'"  viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" class="w-3.5 h-3.5" data-icon="Agent" aria-hidden="true"><g id="agent"><g id="Vector"><path d="M14.7401 5.80454C14.5765 4.77996 14.1638 3.79808 13.5306 2.97273C12.8973 2.14738 12.0648 1.48568 11.1185 1.06589C10.1722 0.646098 9.12632 0.461106 8.08751 0.546487C7.05582 0.624753 6.04548 0.966277 5.17744 1.53548C4.3094 2.09758 3.58366 2.88024 3.09272 3.79808C2.59466 4.70881 2.33852 5.7405 2.33852 6.7793V7.22756L1.25703 9.3692C1.04357 9.80322 1.22145 10.3368 1.65547 10.5574L2.3314 10.8989V12.3006C2.3314 12.82 2.53063 13.3038 2.90061 13.6738C3.2706 14.0367 3.75442 14.243 4.27382 14.243H6.01702V14.7624C6.01702 15.1538 6.3372 15.4739 6.72853 15.4739C7.11986 15.4739 7.44004 15.1538 7.44004 14.7624V13.7094C7.44004 13.2185 7.04159 12.82 6.55065 12.82H4.27382C4.13864 12.82 4.00345 12.7631 3.91095 12.6706C3.81846 12.5781 3.76154 12.4429 3.76154 12.3077V10.5716C3.76154 10.2301 3.56943 9.92417 3.2706 9.77476L2.77254 9.52573L3.66904 7.73984C3.72596 7.61889 3.76154 7.4837 3.76154 7.34851V6.77219C3.76154 5.96818 3.96076 5.17129 4.34498 4.4669C4.72919 3.76251 5.28417 3.15772 5.9601 2.7237C6.63603 2.28968 7.41158 2.02643 8.20847 1.96239C9.00536 1.89835 9.81648 2.04066 10.5493 2.36795C11.2822 2.69524 11.9225 3.20042 12.4135 3.84077C12.8973 4.47402 13.2246 5.23533 13.3456 6.02511C13.4665 6.81488 13.3954 7.63312 13.125 8.38731C12.8617 9.12017 12.4206 9.78187 11.8585 10.3084C11.6735 10.4792 11.5668 10.7139 11.5668 10.9701V14.7624C11.5668 15.1538 11.887 15.4739 12.2783 15.4739C12.6696 15.4739 12.9898 15.1538 12.9898 14.7624V11.1978C13.6515 10.5432 14.1567 9.73918 14.4697 8.87114C14.8184 7.89637 14.918 6.83623 14.7615 5.81165L14.7401 5.80454Z" fill="currentColor"></path><path d="M10.8055 7.99599C10.8909 7.83234 10.962 7.66158 11.0189 7.4837H11.6522C12.0435 7.4837 12.3637 7.16352 12.3637 6.77219C12.3637 6.38086 12.0435 6.06068 11.6522 6.06068H11.0189C10.9691 5.8828 10.898 5.71204 10.8055 5.54839L11.2537 5.10014C11.5312 4.82266 11.5312 4.3744 11.2537 4.09692C10.9762 3.81943 10.528 3.81943 10.2505 4.09692L9.80225 4.54517C9.6386 4.45267 9.46784 4.38863 9.28996 4.33171V3.69847C9.28996 3.30714 8.96978 2.98696 8.57845 2.98696C8.18712 2.98696 7.86694 3.30714 7.86694 3.69847V4.33171C7.68907 4.38152 7.5183 4.45267 7.35466 4.54517L6.90641 4.09692C6.62892 3.81943 6.18067 3.81943 5.90318 4.09692C5.62569 4.3744 5.62569 4.82266 5.90318 5.10014L6.35143 5.54839C6.26605 5.71204 6.1949 5.8828 6.13798 6.06068H5.50473C5.1134 6.06068 4.79323 6.38086 4.79323 6.77219C4.79323 7.16352 5.1134 7.4837 5.50473 7.4837H6.13798C6.18778 7.66158 6.25893 7.83234 6.35143 7.99599L5.90318 8.44424C5.62569 8.72172 5.62569 9.16997 5.90318 9.44746C6.04548 9.58976 6.22336 9.6538 6.40835 9.6538C6.59334 9.6538 6.77122 9.58265 6.91352 9.44746L7.36177 8.99921C7.52542 9.08459 7.69618 9.15574 7.87406 9.21267V9.84591C7.87406 10.2372 8.19424 10.5574 8.58557 10.5574C8.9769 10.5574 9.29708 10.2372 9.29708 9.84591V9.21267C9.47496 9.16286 9.64572 9.09171 9.80936 8.99921L10.2576 9.44746C10.3999 9.58976 10.5778 9.6538 10.7628 9.6538C10.9478 9.6538 11.1257 9.58265 11.268 9.44746C11.5454 9.16997 11.5454 8.72172 11.268 8.44424L10.8197 7.99599H10.8055ZM7.44004 6.77219C7.44004 6.14606 7.94521 5.64089 8.57134 5.64089C9.19747 5.64089 9.70264 6.14606 9.70264 6.77219C9.70264 7.39832 9.19747 7.90349 8.57134 7.90349C7.94521 7.90349 7.44004 7.39832 7.44004 6.77219Z" fill="currentColor"></path></g></g></svg>

  <svg v-else-if="name === 'knowledge'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M18 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM6 4h5v8l-2.5-1.5L6 12V4z"/>
  </svg>

  <svg v-else-if="name === 'classifier'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M12 2l3.09 6.26L22 9.27l-5 4.87L18.18 22L12 18.77L5.82 22L7 14.14L2 9.27l6.91-1.01L12 2z"/>
  </svg>

  <svg v-else-if="name === 'condition'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M12 2l9 10l-9 10l-9-10l9-10z"/>
  </svg>

  <svg v-else-if="name === 'loop'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6c0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 15.03 20 13.57 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6c0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 4 12c0 4.42 3.58 8 8 8v3l4-4l-4-4v3z"/>
  </svg>

  <svg v-else-if="name === 'variable'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm-5 14H4v-4h11v4zm0-5H4V9h11v4zm5 5h-4V9h4v9z"/>
  </svg>

  <svg v-else-if="name === 'http'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z"/>
  </svg>

  <svg v-else-if="name === 'service'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M4 5h16v3H4V5zm0 5.5h16v3H4v-3zM4 16h16v3H4v-3zM7 6.5a.5.5 0 1 0 0-1a.5.5 0 0 0 0 1zm2.5 0a.5.5 0 1 0 0-1a.5.5 0 0 0 0 1zM7 12a.5.5 0 1 0 0-1a.5.5 0 0 0 0 1zm2.5 0a.5.5 0 1 0 0-1a.5.5 0 0 0 0 1zM7 17.5a.5.5 0 1 0 0-1a.5.5 0 0 0 0 1zm2.5 0a.5.5 0 1 0 0-1a.5.5 0 0 0 0 1z"/>
  </svg>
  <svg v-else-if="name === 'connector'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon"><path fill="currentColor" d="M7 2h2v5h6V2h2v5h1a2 2 0 0 1 2 2v2a7 7 0 0 1-6 6.93V22h-4v-4.07A7 7 0 0 1 4 11V9a2 2 0 0 1 2-2h1V2Zm-1 7v2a5 5 0 0 0 10 0V9H6Z"/></svg>

  <svg v-else-if="name === 'schedule'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M12 2a10 10 0 1 0 10 10A10.01 10.01 0 0 0 12 2zm0 18a8 8 0 1 1 8-8a8 8 0 0 1-8 8zm1-13h-2v6l5.25 3.15l1-1.64L13 12z"/>
  </svg>

  <svg v-else-if="name === 'code'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6l6 6l1.4-1.4zm5.2 0L19.2 12l-4.6-4.6L16 6l6 6l-6 6l-1.4-1.4z"/>
  </svg>

  <svg v-else-if="name === 'template'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z"/>
  </svg>

  <svg v-else-if="name === 'answer'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M20 2H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h4l4 4l4-4h4c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z"/>
  </svg>

  <svg v-else-if="name === 'end'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M6 6h12v12H6z"/>
  </svg>

  <svg v-else-if="name === 'plus'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>
  </svg>

  <svg v-else-if="name === 'copy'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/>
  </svg>

  <!-- 数学运算图标 -->
  <svg v-else-if="name === '+'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M11 13H5v-2h6V5h2v6h6v2h-6v6h-2z" />
  </svg>

  <svg v-else-if="name === '-'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M19 13H5v-2h14v2z" />
  </svg>

  <svg v-else-if="name === '*'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path
        fill="none"
        stroke="currentColor"
        stroke-linecap="round"
        stroke-linejoin="round"
        stroke-width="2"
        d="M12 12L6 6m6 6l6 6m-6-6l6-6m-6 6l-6 6"
    />
  </svg>

  <svg v-else-if="name === '/'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path
        fill="currentColor"
        d="M19 13H5v-2h14zm-7-8a2 2 0 0 1 2 2a2 2 0 0 1-2 2a2 2 0 0 1-2-2a2 2 0 0 1 2-2m0 10a2 2 0 0 1 2 2a2 2 0 0 1-2 2a2 2 0 0 1-2-2a2 2 0 0 1 2-2"
    />
  </svg>

  <!-- 工具栏图标 -->
  <svg v-else-if="name === 'fit'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M9 3V1h6v2h4a2 2 0 0 1 2 2v4h-2V5h-4V3H9zm6 18v-2h4v-4h2v4a2 2 0 0 1-2 2h-4zM3 15v4a2 2 0 0 0 2 2h4v2H5a2 2 0 0 1-2-2v-4h2zM1 9V5a2 2 0 0 1 2-2h4v2H3v4H1z"/>
  </svg>

  <svg v-else-if="name === 'undo'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88c3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z"/>
  </svg>

  <svg v-else-if="name === 'redo'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M18.4 10.6C16.55 8.99 14.15 8 11.5 8c-4.65 0-8.58 3.03-9.96 7.22L3.9 16c1.05-3.19 4.05-5.5 7.6-5.5c1.95 0 3.73.72 5.12 1.88L13 16h9V7l-3.6 3.6z"/>
  </svg>

  <svg v-else-if="name === 'arrange'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="icon">
    <path fill="currentColor" d="M3 3h8v8H3V3zm10 0h8v8h-8V3zM3 13h8v8H3v-8zm15 0h-5v8h8v-5l-3-3z"/>
  </svg>
</template>

<style scoped>
.icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}
</style>
