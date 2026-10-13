/**
 * AI 聊天悬浮组件（静态版）
 * --------------------------------------------------
 * 纯原生 JS，无任何框架依赖，可单独部署/缓存。
 * 宿主统一入口 utils/ai-assistant.ts 登录后调用 BootSchoolChat.login()，
 * 退出登录时调用 logout() 清理。
 *
 * 行为：右下角悬浮一个 AI 图标，点击展开气泡面板，
 * 面板内嵌 <iframe src="/chat/"> 作为聊天界面。
 * 按钮可拖动改位置（阈值 5px 内视为点击），拖后位置写入 localStorage
 * (key = ai-chat-launcher-position)，下次挂载自动复位；视口变化时自动夹回可视区。
 *
 * 暴露的 API：
 *   window.BootSchoolChat.mount()              // 显示悬浮按钮
 *   window.BootSchoolChat.unmount()            // 移除整个组件
 *   window.BootSchoolChat.open()               // 打开面板
 *   window.BootSchoolChat.close()              // 关闭面板
 *   window.BootSchoolChat.setContext(ctx)      // 设置/更新下发给 iframe 的上下文
 *   window.BootSchoolChat.configure(config)    // 动态更新助手标题和首页 JSON 配置
 *   window.BootSchoolChat.postToChat(type,pl)  // 主动向 iframe 发消息
 *   window.BootSchoolChat.on(type, handler)    // 监听来自 iframe 的消息，返回取消函数
 *   window.BootSchoolChat.tip(text, opts?)     // 在悬浮按钮旁弹出小气泡；opts: {duration?:ms, silent?:bool}
 *   window.BootSchoolChat.hideTip()            // 立即收起气泡
 *
 * 首次挂载会自动弹一条欢迎气泡（内容可用 window.__AI_CHAT_WELCOME__ 覆盖）；
 * 用户关掉后写入 localStorage(ai-chat-welcome-seen)，之后 mount 不再自动弹。
 * 主动调 tip() 不受此限制。
 *
 * 与 iframe(聊天页)的 postMessage 协议：
 *   信封：{ channel: 'boot-school-chat', type, payload }
 *   子→父：chat:ready / chat:close / chat:event
 *   父→子：host:context / host:open / host:close / host:command
 */
(function () {
  if (window.BootSchoolChat) {
    return;
  }

  // 聊天页地址，如需修改可在引入前设置 window.__AI_CHAT_SRC__
  var IFRAME_SRC = window.__AI_CHAT_SRC__ || new URL('./?mode=Copilot', document.currentScript ? document.currentScript.src : new URL('/chat/chat-frame.js', window.location.href).href).href;
  var PANEL_TITLE = window.__AI_CHAT_TITLE__ || 'AI 助手';
  var STYLE_ID = 'ai-chat-widget-style';
  var ROOT_ID = 'ai-chat-widget-root';
  var CHANNEL = 'boot-school-chat';
  // 停靠模式：面板固定右侧满高，把宿主主应用压窄到左侧
  var DOCK_WIDTH = window.__AI_CHAT_DOCK_WIDTH__ || 400; // 面板/停靠默认宽度
  var DOCK_MIN_WIDTH = 320; // 拖拽可缩到的最小宽度
  var HOST_SELECTOR = window.__AI_CHAT_HOST_SELECTOR__ || '#app'; // 被压缩的主应用容器
  var DOCK_MIN_VIEWPORT = 1024; // 视口窄于此值时只浮层覆盖、不压缩主应用
  var DOCK_WIDTH_KEY = 'ai-chat-dock-width'; // 拖拽后的宽度持久化 key
  var DOCK_MODE_KEY = 'ai-chat-docked';
  function loadDockMode() {
    try { return window.localStorage.getItem(DOCK_MODE_KEY) === 'true'; }
    catch (e) { return false; }
  }
  var POSITION_KEY = 'ai-chat-launcher-position'; // 悬浮按钮位置持久化 key
  var LAUNCHER_SIZE = 56; // 悬浮按钮直径，需与 CSS 保持一致
  var LAUNCHER_MARGIN = 8; // 拖拽时距视口边缘的最小留白
  var DRAG_THRESHOLD = 5; // 位移超过此值才判定为拖动，否则算点击
  var WELCOME_SEEN_KEY = 'ai-chat-welcome-seen'; // 欢迎气泡"已看过"持久化 key
  var DEFAULT_WELCOME = window.__AI_CHAT_WELCOME__ || 'hi，欢迎你，我是你的智能小助手～';
  var ASSISTANT_CONFIG = window.__AI_CHAT_CONFIG__ || null;
  var TIP_DEFAULT_DURATION = 8000; // 气泡默认自动关闭时长(ms)，传 0 表示不自动关

  // 拖拽宽度的上限：不超过视口 60%，且给主内容留够空间
  function maxDockWidth() {
    return Math.max(DOCK_MIN_WIDTH, Math.min(900, Math.round(window.innerWidth * 0.6)));
  }
  function clampDockWidth(w) {
    return Math.max(DOCK_MIN_WIDTH, Math.min(w, maxDockWidth()));
  }
  function loadDockWidth() {
    try {
      var v = parseInt(window.localStorage.getItem(DOCK_WIDTH_KEY), 10);
      return v > 0 ? clampDockWidth(v) : DOCK_WIDTH;
    } catch (e) {
      return DOCK_WIDTH;
    }
  }

  // 悬浮按钮位置：夹到当前视口范围内，防止改窗口后按钮跑到屏幕外
  function clampPosition(pos) {
    var maxLeft = Math.max(LAUNCHER_MARGIN, window.innerWidth - LAUNCHER_SIZE - LAUNCHER_MARGIN);
    var maxTop = Math.max(LAUNCHER_MARGIN, window.innerHeight - LAUNCHER_SIZE - LAUNCHER_MARGIN);
    return {
      left: Math.min(Math.max(LAUNCHER_MARGIN, pos.left), maxLeft),
      top: Math.min(Math.max(LAUNCHER_MARGIN, pos.top), maxTop),
    };
  }
  function loadPosition() {
    try {
      var raw = window.localStorage.getItem(POSITION_KEY);
      if (!raw) {
        return null;
      }
      var pos = JSON.parse(raw);
      if (!pos || typeof pos.left !== 'number' || typeof pos.top !== 'number') {
        return null;
      }
      return clampPosition(pos);
    } catch (e) {
      return null;
    }
  }
  function savePosition(pos) {
    try {
      window.localStorage.setItem(POSITION_KEY, JSON.stringify(pos));
    } catch (e) {
      /* 忽略持久化失败 */
    }
  }
  // iframe 目标 origin（相对地址 /chat/ 解析后即本站 origin；跨域时取其 origin）
  var TARGET_ORIGIN = (function () {
    try {
      return new URL(IFRAME_SRC, window.location.href).origin;
    } catch (e) {
      return window.location.origin;
    }
  })();

  var state = {
    root: null, // 容器
    panel: null, // 气泡面板
    launcher: null, // 悬浮按钮
    iframe: null,
    loading: null, // 加载动画占位
    open: false,
    dockOnOpen: loadDockMode(),
    iframeLoaded: false,
    chatReady: false, // iframe 内聊天页是否已发来 chat:ready
    context: null, // 待下发/已下发给 iframe 的上下文
    assistantConfig: ASSISTANT_CONFIG,
    docked: false, // 是否处于侧边停靠模式
    appCode: null, // 内部 SaaS 助手稳定编码（来自 web_config.AGENT_APP_CODE）
    dockWidth: loadDockWidth(), // 当前停靠宽度（可拖拽，持久化）
    hostCompressed: false, // 主应用当前是否被压缩
    resizer: null, // 拖拽分隔条
    overlay: null, // 拖拽时覆盖 iframe 的透明层（防止鼠标事件被 iframe 吞掉）
    resizeTimer: null, // 停靠切换后触发 resize 的定时器
    position: null, // 悬浮按钮当前位置 {left, top}；null 表示走默认右下角
    dragStart: null, // 拖动过程中的起点快照
    panelDragStart: null, // iframe 标题栏拖动窗口时的起点快照
    suppressClick: false, // 刚拖完时抑制随后的 click，避免误触发开合
    tip: null, // 欢迎/提示气泡 DOM
    tipBody: null, // 气泡文本容器
    tipClose: null, // 气泡关闭按钮
    tipTimer: null, // 气泡自动关闭定时器
  };

  // 来自 iframe 的消息监听表：type -> [handler]
  var listeners = {};

  function injectStyle() {
    if (document.getElementById(STYLE_ID)) {
      return;
    }
    var css = [
      // root 始终保持高 z-index, 让悬浮按钮 / 气泡 / 停靠面板全程压在 antd modal/drawer 之上。
      // 注意本项目在 use-design-tokens.ts 把 antd zIndexPopupBase 改到了 2000,
      // 即 modal/drawer 起步 2000+, 多层叠加可到 4000~5000, 这里用 9000 安全压制;
      // 留 9999/10000 给少数 !important 提层的极端弹窗(如 grade-subject-modal-wrap)。
      '#' + ROOT_ID + '{position:fixed;right:5px;bottom:5px;z-index:9000;',
      'font-family:inherit;}',
      // 悬浮按钮
      '#' + ROOT_ID + ' .aicw-launcher{width:56px;height:56px;border:none;',
      'border-radius:50%;cursor:grab;display:flex;align-items:center;',
      'justify-content:center;color:#fff;touch-action:none;user-select:none;',
      'position:relative;isolation:isolate;overflow:visible;',
      'background:linear-gradient(160deg,#ffffff 0%,#b7cef0 100%);',
      'box-shadow:0 6px 20px rgba(91,124,250,.45);transition:transform .2s ease,',
      'box-shadow .2s ease;outline:none;}',
      '#' + ROOT_ID + ' .aicw-launcher:hover{transform:translateY(-2px) scale(1.05);',
      'box-shadow:0 10px 26px rgba(91,124,250,.55);}',
      '#' + ROOT_ID + ' .aicw-launcher svg{width:46px;height:46px;pointer-events:none;',
      'overflow:visible;}',
      '#' + ROOT_ID + ' .aicw-launcher.aicw-active{transform:scale(.92);}',
      // 常驻萤火呼吸光晕：双层柔光以不同节奏明暗扩散，不改变按钮本体 transform。
      '#' + ROOT_ID + ' .aicw-launcher::before,#' + ROOT_ID + ' .aicw-launcher::after{',
      'content:"";position:absolute;inset:-9px;border-radius:50%;pointer-events:none;z-index:-1;}',
      '#' + ROOT_ID + ' .aicw-launcher::before{background:radial-gradient(circle,',
      'rgba(125,211,252,.48) 0%,rgba(96,165,250,.2) 48%,transparent 72%);',
      'filter:blur(5px);animation:aicw-firefly-breathe 3.2s ease-in-out infinite;}',
      '#' + ROOT_ID + ' .aicw-launcher::after{inset:-5px;border:1px solid rgba(167,139,250,.42);',
      'box-shadow:0 0 10px rgba(125,211,252,.7),0 0 20px rgba(99,102,241,.28);',
      'animation:aicw-firefly-ring 3.2s ease-in-out .8s infinite;}',
      '@keyframes aicw-firefly-breathe{0%,100%{opacity:.32;transform:scale(.88);}',
      '50%{opacity:1;transform:scale(1.18);}}',
      '@keyframes aicw-firefly-ring{0%,100%{opacity:.2;transform:scale(.92);}',
      '50%{opacity:.78;transform:scale(1.12);}}',
      '@media (prefers-reduced-motion:reduce){#' + ROOT_ID + ' .aicw-launcher::before,',
      '#' + ROOT_ID + ' .aicw-launcher::after{animation:none;opacity:.45;}}',
      // 拖拽中：抹掉 hover/active 的 transform，避免抖动
      '#' + ROOT_ID + '.aicw-is-dragging .aicw-launcher{cursor:grabbing;',
      'transform:none !important;transition:none;box-shadow:0 12px 30px rgba(91,124,250,.6);}',
      // 气泡面板
      '#' + ROOT_ID + ' .aicw-panel{position:absolute;right:0;bottom:72px;',
      'width:400px;height:600px;max-width:calc(100vw - 32px);',
      'max-height:calc(100vh - 120px);background:#fff;border-radius:14px;',
      'overflow:hidden;display:flex;flex-direction:column;',
      'box-shadow:0 12px 40px rgba(0,0,0,.18);',
      'transform-origin:bottom right;opacity:0;transform:translateY(12px) scale(.96);',
      'pointer-events:none;transition:opacity .22s ease,transform .22s ease;}',
      '#' + ROOT_ID + ' .aicw-panel.aicw-open{opacity:1;transform:translateY(0) scale(1);',
      'pointer-events:auto;}',
      // 停靠模式：贴右，从宿主顶部栏下方延伸至视口底部（放在 open 规则之后以胜出）。
      '#' + ROOT_ID + ' .aicw-panel.aicw-dock{position:fixed;left:auto;right:0;top:var(--vben-header-height, 50px);bottom:0;',
      'width:' + DOCK_WIDTH + 'px;max-width:100vw;height:auto;max-height:none;',
      'border-radius:0;box-shadow:-6px 0 24px rgba(0,0,0,.10);}',
      // 停靠时隐藏悬浮按钮（面板常驻）
      '#' + ROOT_ID + '.aicw-is-docked .aicw-launcher{display:none;}',
      // 拖拽分隔条（贴面板左缘，仅停靠时显示）
      '#' + ROOT_ID + ' .aicw-resizer{display:none;position:absolute;left:0;top:0;',
      'bottom:0;width:6px;cursor:ew-resize;z-index:4;}',
      '#' + ROOT_ID + ' .aicw-resizer::before{content:"";position:absolute;left:2px;',
      'top:0;bottom:0;width:2px;background:transparent;transition:background .15s;}',
      '#' + ROOT_ID + ' .aicw-resizer:hover::before,',
      '#' + ROOT_ID + ' .aicw-resizer.aicw-resizing::before{background:#5b7cfa;}',
      '#' + ROOT_ID + '.aicw-is-docked .aicw-resizer{display:block;}',
      // 拖拽期间覆盖全屏的透明层（避免鼠标移到 iframe 上时事件丢失）
      '.aicw-resize-overlay{position:fixed;inset:0;z-index:2147483000;',
      'cursor:ew-resize;}',
      // iframe
      '#' + ROOT_ID + ' .aicw-body{flex:1;min-height:0;min-width:0;position:relative;background:#f5f6fa;}',
      '#' + ROOT_ID + ' .aicw-iframe{position:absolute;inset:0;width:100%;height:100%;border:none;display:block;}',
      // 加载动画（iframe 就绪前的占位）
      '#' + ROOT_ID + ' .aicw-loading{position:absolute;inset:0;z-index:1;',
      'display:flex;flex-direction:column;align-items:center;justify-content:center;',
      'gap:14px;background:#f5f6fa;transition:opacity .35s ease;}',
      '#' + ROOT_ID + ' .aicw-loading.aicw-hide{opacity:0;pointer-events:none;}',
      '#' + ROOT_ID + ' .aicw-spinner{position:relative;width:44px;height:44px;}',
      '#' + ROOT_ID + ' .aicw-spinner span{position:absolute;inset:0;border-radius:50%;',
      'border:3px solid transparent;border-top-color:#5b7cfa;',
      'animation:aicw-spin 1s linear infinite;}',
      '#' + ROOT_ID + ' .aicw-spinner span:nth-child(2){inset:7px;border-top-color:#7c4dff;',
      'animation-duration:1.4s;animation-direction:reverse;}',
      '#' + ROOT_ID + ' .aicw-loading-text{font-size:13px;color:#8a8f99;',
      'letter-spacing:.5px;}',
      '#' + ROOT_ID + ' .aicw-loading-text::after{content:"";animation:aicw-dots 1.4s steps(4) infinite;}',
      '@keyframes aicw-spin{to{transform:rotate(360deg);}}',
      '@keyframes aicw-dots{0%{content:"";}25%{content:".";}50%{content:"..";}',
      '75%,100%{content:"...";}}',
      '.dark #' + ROOT_ID + ' .aicw-loading{background:#141414;}',
      // 移动端
      '@media (max-width:520px){#' + ROOT_ID + ' .aicw-panel{',
      'width:calc(100vw - 32px);height:70vh;}}',
      // 深色模式跟随系统
      '.dark #' + ROOT_ID + ' .aicw-panel{background:#1f1f1f;}',
      '.dark #' + ROOT_ID + ' .aicw-body{background:#141414;}',
      // 参考"3D 白色助手机器人"造型:白圆头 + 深蓝屏幕脸 + 头两侧蓝色听筒 + 头顶两根小天线。
      // 屏幕上两只青色像素眼周期性眨眼(整块矩形塌成一条水平线);整脸微幅上下浮动像待机呼吸。
      '#' + ROOT_ID + ' .aicw-face .aicw-eye{transform-box:fill-box;',
      'transform-origin:center;animation:aicw-blink 4.8s ease-in-out infinite;}',
      '#' + ROOT_ID + ' .aicw-face-inner{transform-box:fill-box;',
      'transform-origin:center;animation:aicw-face-float 3.4s ease-in-out infinite;}',
      '@keyframes aicw-blink{0%,88%,100%{transform:scaleY(1);}',
      '92%,95%{transform:scaleY(.08);}}',
      '@keyframes aicw-face-float{0%,100%{transform:translateY(0);}',
      '50%{transform:translateY(-1.2px);}}',
      // 拖动时暂停装饰动画，避免和拖动手势叠加抖动
      '#' + ROOT_ID + '.aicw-is-dragging .aicw-face-inner,',
      '#' + ROOT_ID + '.aicw-is-dragging .aicw-face .aicw-eye{',
      'animation-play-state:paused;}',
      // 欢迎/提示气泡：白底圆角小卡片，尾巴指向悬浮按钮
      '#' + ROOT_ID + ' .aicw-tip{position:absolute;bottom:12px;right:72px;',
      'max-width:240px;min-width:120px;padding:11px 30px 11px 14px;',
      'background:#fff;color:#1e293b;border-radius:14px;font-size:13px;',
      'line-height:1.55;letter-spacing:.2px;cursor:pointer;',
      'box-shadow:0 10px 32px rgba(0,0,0,.14),0 2px 6px rgba(0,0,0,.06);',
      'opacity:0;transform:translateX(8px) scale(.96);',
      'transform-origin:100% 60%;pointer-events:none;',
      'transition:opacity .28s ease,transform .28s ease;}',
      '#' + ROOT_ID + ' .aicw-tip.aicw-tip-open{opacity:1;',
      'transform:translateX(0) scale(1);pointer-events:auto;}',
      '#' + ROOT_ID + ' .aicw-tip.aicw-tip-right::after{content:"";position:absolute;',
      'right:-5px;bottom:18px;width:12px;height:12px;background:#fff;',
      'transform:rotate(45deg);border-bottom-right-radius:2px;',
      'box-shadow:2px -2px 4px rgba(0,0,0,.04);}',
      // 按钮拖到左半区时气泡在按钮右侧，尾巴翻到左边
      '#' + ROOT_ID + ' .aicw-tip.aicw-tip-left{right:auto;left:72px;',
      'transform-origin:0 60%;transform:translateX(-8px) scale(.96);}',
      '#' + ROOT_ID + ' .aicw-tip.aicw-tip-left.aicw-tip-open{transform:translateX(0) scale(1);}',
      '#' + ROOT_ID + ' .aicw-tip.aicw-tip-left::after{content:"";position:absolute;',
      'left:-5px;bottom:18px;width:12px;height:12px;background:#fff;',
      'transform:rotate(45deg);border-top-left-radius:2px;',
      'box-shadow:-2px 2px 4px rgba(0,0,0,.04);}',
      '#' + ROOT_ID + ' .aicw-tip-close{position:absolute;right:4px;top:2px;',
      'width:22px;height:22px;background:transparent;border:none;color:#94a3b8;',
      'cursor:pointer;font-size:18px;line-height:20px;padding:0;',
      'border-radius:50%;transition:color .15s,background .15s;}',
      '#' + ROOT_ID + ' .aicw-tip-close:hover{color:#334155;background:rgba(0,0,0,.05);}',
      // 停靠 / 面板打开时不显示气泡
      '#' + ROOT_ID + '.aicw-is-open .aicw-tip,',
      '#' + ROOT_ID + '.aicw-is-docked .aicw-tip{opacity:0;pointer-events:none;}',
      // 深色模式
      '.dark #' + ROOT_ID + ' .aicw-tip{background:#2a2a2a;color:#e5e7eb;',
      'box-shadow:0 10px 32px rgba(0,0,0,.5),0 2px 6px rgba(0,0,0,.3);}',
      '.dark #' + ROOT_ID + ' .aicw-tip.aicw-tip-right::after,',
      '.dark #' + ROOT_ID + ' .aicw-tip.aicw-tip-left::after{background:#2a2a2a;}',
      '.dark #' + ROOT_ID + ' .aicw-tip-close:hover{background:rgba(255,255,255,.08);color:#e5e7eb;}',
    ].join('');

    var style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = css;
    document.head.appendChild(style);
  }

  function robotIcon() {
    // 参考"3D 白色助手机器人"造型:白圆头(按钮背景) + 深蓝屏幕脸 + 侧头听筒 + 头顶两根小天线。
    // 屏幕上两只青色像素方眼(用 <g> 分组以便同步眨眼 = scaleY 塌成横线) + 一条青色浅笑弧。
    // 天线和听筒都在 SVG 内绘制,利用 overflow:visible 突破按钮圆边向外延伸。
    return (
      '<svg class="aicw-face" viewBox="0 0 44 44" xmlns="http://www.w3.org/2000/svg">' +
      '<g class="aicw-face-inner">' +
      // 头顶两根天线(细杆 + 青色球头)
      '<line x1="17.5" y1="5" x2="16" y2="-3" stroke="#5b7cfa" ' +
      'stroke-width="1.5" stroke-linecap="round"/>' +
      '<line x1="26.5" y1="5" x2="28" y2="-3" stroke="#5b7cfa" ' +
      'stroke-width="1.5" stroke-linecap="round"/>' +
      '<circle cx="16" cy="-4" r="1.8" fill="#7dd3fc"/>' +
      '<circle cx="28" cy="-4" r="1.8" fill="#7dd3fc"/>' +
      // 头两侧听筒(蓝色圆角矩形 + 深色扬声器孔)
      '<rect x="-8" y="17" width="8" height="12" rx="3" fill="#5b7cfa"/>' +
      '<rect x="44" y="17" width="8" height="12" rx="3" fill="#5b7cfa"/>' +
      '<circle cx="-4" cy="23" r="1.7" fill="#0f1a3d"/>' +
      '<circle cx="48" cy="23" r="1.7" fill="#0f1a3d"/>' +
      // 面部屏幕(深藏青圆角矩形 + 纤细白 stroke 高光边)
      '<rect x="6" y="13" width="32" height="20" rx="5" fill="#0f1a3d"/>' +
      '<rect x="6" y="13" width="32" height="20" rx="5" fill="none" ' +
      'stroke="#ffffff" stroke-width=".6" opacity=".18"/>' +
      // 左眼(青色像素方眼)
      '<g class="aicw-eye aicw-eye-l">' +
      '<rect x="13.5" y="17.5" width="3" height="6" rx="1" fill="#7dd3fc"/>' +
      '</g>' +
      // 右眼
      '<g class="aicw-eye aicw-eye-r">' +
      '<rect x="27.5" y="17.5" width="3" height="6" rx="1" fill="#7dd3fc"/>' +
      '</g>' +
      // 微笑弧
      '<path class="aicw-mouth" d="M17.5 27 Q22 29.5 26.5 27" ' +
      'stroke="#7dd3fc" stroke-width="1.7" fill="none" stroke-linecap="round"/>' +
      '</g></svg>'
    );
  }

  // ==================== postMessage 通讯桥 ====================

  // 向 iframe 发送一条消息（iframe 未就绪时静默忽略）
  function postToChat(type, payload) {
    if (!state.iframe || !state.iframe.contentWindow) {
      return;
    }
    state.iframe.contentWindow.postMessage(
      { channel: CHANNEL, type: type, payload: payload },
      TARGET_ORIGIN,
    );
  }

  // 设置/更新上下文（token、用户、主题、语言等）。若聊天页已就绪则立即下发。
  function setContext(ctx) {
    state.context = ctx ? Object.assign({}, ctx, {
      // 优先保留宿主明确传入的主题；Vue 更新 DOM 前不能用旧 class 覆盖新主题。
      theme: ctx.theme === 'dark' || ctx.theme === 'light'
        ? ctx.theme
        : document.documentElement.classList.contains('dark') ? 'dark' : 'light',
      assistant: state.assistantConfig,
    }) : null;
    if (state.chatReady) {
      postToChat('host:context', state.context);
    }
  }

  function configure(config) {
    state.assistantConfig = config && typeof config === 'object' ? config : null;
    if (state.context) {
      setContext(Object.assign({}, state.context, { assistant: state.assistantConfig }));
    }
  }

  var hostThemeObserver = null;

  function syncHostTheme() {
    if (!state.context) return;
    var theme = document.documentElement.classList.contains('dark') ? 'dark' : 'light';
    if (state.context.theme !== theme) {
      setContext(Object.assign({}, state.context, { theme: theme }));
    }
  }

  function observeHostTheme() {
    if (hostThemeObserver) return;
    hostThemeObserver = new MutationObserver(syncHostTheme);
    hostThemeObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class'],
    });
    syncHostTheme();
  }

  // 注册来自 iframe 的消息监听，返回取消函数
  function on(type, handler) {
    if (!listeners[type]) {
      listeners[type] = [];
    }
    listeners[type].push(handler);
    return function off() {
      var arr = listeners[type] || [];
      var i = arr.indexOf(handler);
      if (i !== -1) {
        arr.splice(i, 1);
      }
    };
  }

  function emit(type, payload) {
    var arr = listeners[type];
    if (!arr) {
      return;
    }
    for (var i = 0; i < arr.length; i++) {
      try {
        arr[i](payload);
      } catch (e) {
        // 单个监听器异常不影响其它监听器
        console.error('[ai-chat-widget] listener error:', e);
      }
    }
  }

  // 接收 iframe 消息：用 event.source 身份校验（同源/跨域均可靠）
  function onMessage(event) {
    if (!state.iframe || event.source !== state.iframe.contentWindow) {
      return;
    }
    if (event.origin !== TARGET_ORIGIN) return;
    var data = event.data;
    if (!data || data.channel !== CHANNEL || !data.type) {
      return;
    }
    switch (data.type) {
      case 'chat:ready': {
        // 聊天页就绪 → 回握并下发当前上下文 + 当前面板开合 / 停靠状态
        state.chatReady = true;
        postToChat('host:context', state.context);
        postToChat(state.open ? 'host:open' : 'host:close', null);
        postToChat('host:dock', { docked: state.docked });
        break;
      }
      case 'chat:close': {
        // 聊天页请求关闭气泡
        close();
        break;
      }
      case 'chat:dock': {
        // 聊天页请求进入 / 退出侧边停靠
        setDocked(data.payload && data.payload.docked);
        break;
      }
      case 'chat:window-drag': {
        handlePanelDrag(data.payload);
        break;
      }
      default: {
        break;
      }
    }
    // 同时派发给外部监听器（如 chat:event 等业务消息）
    emit(data.type, data.payload);
  }

  // ==================== 侧边停靠 ====================

  // 压缩 / 还原宿主主应用。
  // 关键：只给容器设 width（压缩普通流的内容区），不用 transform/overflow——
  // 否则会把 vben 的 fixed 头部/侧栏变成相对 #app 定位，路由切换重排时上下抖动。
  // 停靠面板保留顶部栏空间，主应用按面板宽度收缩。
  function setHostCompressed(on) {
    state.hostCompressed = !!on;
    var host = document.querySelector(HOST_SELECTOR);
    if (host) {
      if (on) {
        host.style.transition = 'width .28s ease';
        host.style.width = 'calc(100% - ' + state.dockWidth + 'px)';
      } else {
        host.style.width = '';
      }
    }
    // 通知 ECharts / 表格等按新宽度重新布局
    window.clearTimeout(state.resizeTimer);
    state.resizeTimer = window.setTimeout(function () {
      window.dispatchEvent(new Event('resize'));
    }, 320);
  }

  // 应用当前停靠宽度到面板 + 主应用（拖拽时实时调用；live=true 时关闭过渡跟手）
  function applyDockWidth(live) {
    if (!state.panel) {
      return;
    }
    state.panel.style.width = state.dockWidth + 'px';
    var host = document.querySelector(HOST_SELECTOR);
    if (host && state.hostCompressed) {
      host.style.transition = live ? 'none' : 'width .28s ease';
      host.style.width = 'calc(100% - ' + state.dockWidth + 'px)';
    }
  }

  // 进入 / 退出停靠模式
  function setDocked(docked, remember) {
    if (!state.root) {
      return;
    }
    state.docked = !!docked;
    // 关闭仅还原布局，不改变用户选择的模式。
    if (remember !== false) {
      state.dockOnOpen = state.docked;
      try { window.localStorage.setItem(DOCK_MODE_KEY, String(state.docked)); }
      catch (e) { /* 存储不可用时保留当前页面内的选择。 */ }
    }
    if (state.docked) {
      open(); // 确保面板打开并加载 iframe
      state.panel.classList.add('aicw-dock');
      state.root.classList.add('aicw-is-docked');
      state.panel.style.width = state.dockWidth + 'px';
      // 拖动浮动按钮会留下内联定位，必须覆盖四个方向，避免覆盖停靠 CSS。
      state.panel.style.left = 'auto';
      state.panel.style.right = '0px';
      state.panel.style.top = 'var(--vben-header-height, 50px)';
      state.panel.style.bottom = '0px';
      // 视口够宽才压缩主应用，否则只做右侧浮层覆盖（避免窄屏挤垮表格）
      setHostCompressed(window.innerWidth >= DOCK_MIN_VIEWPORT);
    } else {
      state.panel.classList.remove('aicw-dock');
      state.root.classList.remove('aicw-is-docked');
      state.panel.style.top = '';
      state.panel.style.width = '';
      updatePanelPlacement();
      setHostCompressed(false);
    }
    // 回执，让聊天页的停靠按钮状态与宿主保持一致
    postToChat('host:dock', { docked: state.docked });
  }

  // ---------- 分隔条拖拽改宽 ----------
  function onResizeMove(e) {
    // 面板在右侧，向左拖（clientX 变小）→ 变宽
    var w = clampDockWidth(state.resizeStartW + (state.resizeStartX - e.clientX));
    state.dockWidth = w;
    applyDockWidth(true);
  }

  function onResizeUp() {
    document.removeEventListener('mousemove', onResizeMove);
    document.removeEventListener('mouseup', onResizeUp);
    if (state.overlay && state.overlay.parentNode) {
      state.overlay.parentNode.removeChild(state.overlay);
    }
    state.overlay = null;
    if (state.resizer) {
      state.resizer.classList.remove('aicw-resizing');
    }
    try {
      window.localStorage.setItem(DOCK_WIDTH_KEY, String(state.dockWidth));
    } catch (e) {
      /* 忽略持久化失败 */
    }
    // 拖完恢复过渡并通知图表/表格重排
    var host = document.querySelector(HOST_SELECTOR);
    if (host && state.hostCompressed) {
      host.style.transition = 'width .28s ease';
    }
    window.clearTimeout(state.resizeTimer);
    state.resizeTimer = window.setTimeout(function () {
      window.dispatchEvent(new Event('resize'));
    }, 60);
  }

  function startResize(e) {
    if (!state.docked) {
      return;
    }
    e.preventDefault();
    state.resizeStartX = e.clientX;
    state.resizeStartW = state.dockWidth;
    // 透明全屏层兜住鼠标事件（否则移到 iframe 上会丢失 mousemove）
    var overlay = document.createElement('div');
    overlay.className = 'aicw-resize-overlay';
    document.body.appendChild(overlay);
    state.overlay = overlay;
    if (state.resizer) {
      state.resizer.classList.add('aicw-resizing');
    }
    document.addEventListener('mousemove', onResizeMove);
    document.addEventListener('mouseup', onResizeUp);
  }

  // ==================== 悬浮按钮拖拽 ====================

  // iframe 内标题栏无法直接操作宿主 DOM，通过 postMessage 传屏幕坐标后在这里移动。
  function handlePanelDrag(payload) {
    if (!payload || state.docked || !state.root) {
      return;
    }
    var x = Number(payload.screenX);
    var y = Number(payload.screenY);
    if (!Number.isFinite(x) || !Number.isFinite(y)) {
      return;
    }
    if (payload.phase === 'start') {
      var rect = state.root.getBoundingClientRect();
      state.panelDragStart = {
        pointerX: x,
        pointerY: y,
        left: rect.left,
        top: rect.top,
        moved: false,
      };
      return;
    }
    if (!state.panelDragStart) {
      return;
    }
    if (payload.phase === 'move') {
      var dx = x - state.panelDragStart.pointerX;
      var dy = y - state.panelDragStart.pointerY;
      if (!state.panelDragStart.moved) {
        if (Math.abs(dx) < DRAG_THRESHOLD && Math.abs(dy) < DRAG_THRESHOLD) {
          return;
        }
        state.panelDragStart.moved = true;
        state.root.classList.add('aicw-is-dragging');
      }
      var pos = clampPosition({
        left: state.panelDragStart.left + dx,
        top: state.panelDragStart.top + dy,
      });
      state.position = pos;
      applyLauncherPosition(pos);
      return;
    }
    if (payload.phase === 'end') {
      var moved = state.panelDragStart.moved;
      state.panelDragStart = null;
      state.root.classList.remove('aicw-is-dragging');
      if (moved && state.position) {
        savePosition(state.position);
      }
    }
  }

  // 把根容器切到 left/top 定位（默认是 right/bottom），拖动才成立
  function applyLauncherPosition(pos) {
    if (!state.root) {
      return;
    }
    if (pos) {
      state.root.style.right = 'auto';
      state.root.style.bottom = 'auto';
      state.root.style.left = pos.left + 'px';
      state.root.style.top = pos.top + 'px';
    } else {
      state.root.style.left = '';
      state.root.style.top = '';
      state.root.style.right = '5px';
      state.root.style.bottom = '5px';
    }
    updatePanelPlacement();
    updateTipPlacement();
  }

  // 面板默认在按钮"左上方"展开（right:0; bottom:72px）。当按钮被拖到视口另一
  // 半区时，翻转方向让面板始终朝屏幕中心展开，避免超出可视区域。停靠模式下
  // 面板已固定右侧，不需要调整。
  function updatePanelPlacement() {
    if (!state.panel || state.docked) {
      return;
    }
    var s = state.panel.style;
    if (!state.position) {
      // 恢复 CSS 默认：右上方展开
      s.left = '';
      s.top = '';
      s.right = '';
      s.bottom = '';
      return;
    }
    var vw = window.innerWidth;
    var vh = window.innerHeight;
    var launcherCenterX = state.position.left + LAUNCHER_SIZE / 2;
    var launcherCenterY = state.position.top + LAUNCHER_SIZE / 2;
    var gap = LAUNCHER_SIZE + 16;
    // 水平：按钮居左半区 → 面板向右延伸（left:0）；否则贴右
    if (launcherCenterX < vw / 2) {
      s.left = '0';
      s.right = 'auto';
    } else {
      s.left = 'auto';
      s.right = '0';
    }
    // 垂直：按钮居上半区 → 面板向下展开（top: gap）；否则贴上（bottom: gap）
    if (launcherCenterY < vh / 2) {
      s.top = gap + 'px';
      s.bottom = 'auto';
    } else {
      s.top = 'auto';
      s.bottom = gap + 'px';
    }
  }

  // ---------- 拖拽事件 ----------
  function getPointer(e) {
    if (e.touches && e.touches.length) {
      return { x: e.touches[0].clientX, y: e.touches[0].clientY };
    }
    if (e.changedTouches && e.changedTouches.length) {
      return { x: e.changedTouches[0].clientX, y: e.changedTouches[0].clientY };
    }
    return { x: e.clientX, y: e.clientY };
  }

  function onLauncherDragStart(e) {
    // 只响应鼠标左键，触摸事件没有 button 字段
    if (e.type === 'mousedown' && e.button !== 0) {
      return;
    }
    if (!state.root) {
      return;
    }
    var p = getPointer(e);
    var rect = state.root.getBoundingClientRect();
    state.dragStart = {
      pointerX: p.x,
      pointerY: p.y,
      offsetX: p.x - rect.left,
      offsetY: p.y - rect.top,
      moved: false,
    };
    if (e.type === 'touchstart') {
      document.addEventListener('touchmove', onLauncherDragMove, { passive: false });
      document.addEventListener('touchend', onLauncherDragEnd);
      document.addEventListener('touchcancel', onLauncherDragEnd);
    } else {
      document.addEventListener('mousemove', onLauncherDragMove);
      document.addEventListener('mouseup', onLauncherDragEnd);
    }
  }

  function onLauncherDragMove(e) {
    if (!state.dragStart) {
      return;
    }
    var p = getPointer(e);
    var dx = p.x - state.dragStart.pointerX;
    var dy = p.y - state.dragStart.pointerY;
    if (!state.dragStart.moved) {
      if (Math.abs(dx) < DRAG_THRESHOLD && Math.abs(dy) < DRAG_THRESHOLD) {
        return;
      }
      state.dragStart.moved = true;
      if (state.root) {
        state.root.classList.add('aicw-is-dragging');
      }
    }
    // touchmove 默认会滚动页面，拖动期间阻止
    if (e.cancelable) {
      e.preventDefault();
    }
    var pos = clampPosition({
      left: p.x - state.dragStart.offsetX,
      top: p.y - state.dragStart.offsetY,
    });
    state.position = pos;
    applyLauncherPosition(pos);
  }

  function onLauncherDragEnd() {
    document.removeEventListener('mousemove', onLauncherDragMove);
    document.removeEventListener('mouseup', onLauncherDragEnd);
    document.removeEventListener('touchmove', onLauncherDragMove);
    document.removeEventListener('touchend', onLauncherDragEnd);
    document.removeEventListener('touchcancel', onLauncherDragEnd);
    var moved = state.dragStart && state.dragStart.moved;
    state.dragStart = null;
    if (state.root) {
      state.root.classList.remove('aicw-is-dragging');
    }
    if (moved && state.position) {
      savePosition(state.position);
      // 拖动结束会紧接一个 click 事件，压住它避免误触发开合面板
      state.suppressClick = true;
      window.setTimeout(function () {
        state.suppressClick = false;
      }, 0);
    }
  }

  function onLauncherClick(e) {
    if (state.suppressClick) {
      e.preventDefault();
      e.stopPropagation();
      return;
    }
    toggle();
  }

  // 视口尺寸变化时把按钮夹回可视区，同时刷新面板展开方向
  function onWindowResizeForPosition() {
    if (!state.position) {
      return;
    }
    var clamped = clampPosition(state.position);
    if (clamped.left !== state.position.left || clamped.top !== state.position.top) {
      state.position = clamped;
      savePosition(clamped);
    }
    applyLauncherPosition(state.position);
  }

  // 内部助手按稳定 appCode 路由，真实 appId 仅由后端解析。
  function buildIframeSrc() {
    try {
      var url = new URL(IFRAME_SRC, window.location.href);
      url.searchParams.set('hostBridge', '1');
      if (state.appCode) {
        url.searchParams.set('appCode', state.appCode);
        url.searchParams.set('accessMode', 'internal');
      }
      return url.href;
    } catch (e) {
      var sep = IFRAME_SRC.indexOf('?') === -1 ? '?' : '&';
      return IFRAME_SRC + sep + 'hostBridge=1' + (state.appCode ? '&appCode=' + encodeURIComponent(state.appCode) + '&accessMode=internal' : '');
    }
  }

  function setAppCode(code) {
    var next = code || null;
    if (next === state.appCode) {
      return;
    }
    state.appCode = next;
    if (state.iframeLoaded && state.iframe) {
      state.chatReady = false;
      if (state.loading) {
        state.loading.classList.remove('aicw-hide');
      }
      state.iframe.src = buildIframeSrc();
    }
  }

  function ensureIframe() {
    if (state.iframeLoaded) {
      return;
    }
    state.iframeLoaded = true;
    if (state.loading) {
      state.loading.classList.remove('aicw-hide');
    }
    state.iframe.addEventListener(
      'load',
      function () {
        if (state.loading) {
          state.loading.classList.add('aicw-hide');
        }
      },
      { once: true },
    );
    state.iframe.src = buildIframeSrc();
  }

  function open() {
    if (!state.root || state.open) {
      return;
    }
    hideTip();
    ensureIframe();
    // 依据当前按钮位置刷新面板展开方向，避免按钮在拖到边角时面板超出视口
    updatePanelPlacement();
    state.open = true;
    state.panel.classList.add('aicw-open');
    state.root.classList.add('aicw-is-open');
    state.launcher.classList.add('aicw-active');
    state.launcher.setAttribute('aria-expanded', 'true');
    postToChat('host:open', null);
    if (state.dockOnOpen && !state.docked) setDocked(true, false);
  }

  function close() {
    if (!state.root || !state.open) {
      return;
    }
    // 关闭时先退出停靠，恢复主应用宽度
    if (state.docked) {
      setDocked(false, false);
    }
    state.open = false;
    state.panel.classList.remove('aicw-open');
    state.root.classList.remove('aicw-is-open');
    state.launcher.classList.remove('aicw-active');
    state.launcher.setAttribute('aria-expanded', 'false');
    postToChat('host:close', null);
  }

  function toggle() {
    if (state.open) {
      close();
    } else {
      open();
    }
  }

  function onKeydown(e) {
    if (e.key === 'Escape') {
      close();
    }
  }

  // ==================== 欢迎气泡 / 提示气泡 ====================

  // 依据按钮当前位置，把气泡挂到左侧或右侧（按钮居右→气泡在按钮左边；反之翻转）
  function updateTipPlacement() {
    if (!state.tip) {
      return;
    }
    var placement = 'right'; // 默认按钮在右下角，气泡贴按钮左边（tail 在气泡右缘）
    if (state.position) {
      var cx = state.position.left + LAUNCHER_SIZE / 2;
      if (cx < window.innerWidth / 2) {
        placement = 'left';
      }
    }
    state.tip.classList.remove('aicw-tip-right', 'aicw-tip-left');
    state.tip.classList.add('aicw-tip-' + placement);
  }

  // 弹出提示气泡；面板已打开或已停靠时静默不弹
  function showTip(content, opts) {
    if (!state.tip || !state.tipBody) {
      return;
    }
    if (state.open || state.docked) {
      return;
    }
    opts = opts || {};
    state.tipBody.textContent = content == null ? '' : String(content);
    updateTipPlacement();
    state.tip.classList.add('aicw-tip-open');
    window.clearTimeout(state.tipTimer);
    var duration = opts.duration == null ? TIP_DEFAULT_DURATION : opts.duration;
    if (duration > 0) {
      state.tipTimer = window.setTimeout(hideTip, duration);
    }
  }

  function hideTip() {
    if (!state.tip) {
      return;
    }
    state.tip.classList.remove('aicw-tip-open');
    window.clearTimeout(state.tipTimer);
    state.tipTimer = null;
  }

  // 首次挂载弹一次欢迎气泡：读取 localStorage 判"看过没"，写入延时到组件就绪之后
  function maybeShowWelcome() {
    var seen = false;
    try {
      seen = !!window.localStorage.getItem(WELCOME_SEEN_KEY);
    } catch (e) {
      /* localStorage 不可用则始终弹一次 */
    }
    if (seen) {
      return;
    }
    // 延时一点让页面加载动画先安顿；期间用户如果自己开了面板就不弹
    window.setTimeout(function () {
      if (!state.tip || state.open || state.docked) {
        return;
      }
      showTip(DEFAULT_WELCOME, { duration: TIP_DEFAULT_DURATION });
      try {
        window.localStorage.setItem(WELCOME_SEEN_KEY, '1');
      } catch (e) {
        /* 忽略持久化失败 */
      }
    }, 800);
  }

  function build() {
    injectStyle();

    var root = document.createElement('div');
    root.id = ROOT_ID;

    // 气泡面板（无标题栏，iframe 占满，标题由 iframe 内部承担）
    var panel = document.createElement('div');
    panel.className = 'aicw-panel';

    var body = document.createElement('div');
    body.className = 'aicw-body';

    var iframe = document.createElement('iframe');
    iframe.className = 'aicw-iframe';
    iframe.setAttribute('title', PANEL_TITLE);
    iframe.setAttribute('allow', 'microphone; clipboard-write');

    var loading = document.createElement('div');
    loading.className = 'aicw-loading';
    loading.innerHTML =
      '<div class="aicw-spinner"><span></span><span></span></div>' +
      '<div class="aicw-loading-text">' +
      PANEL_TITLE +
      ' 加载中</div>';

    // 关闭按钮由 iframe 内聊天页自带（点击后发 chat:close 通知宿主关闭面板），
    // 这里不再叠加外层关闭按钮。
    body.appendChild(iframe);
    body.appendChild(loading);
    panel.appendChild(body);

    // 拖拽分隔条（贴面板左缘，仅停靠模式下显示，可拖动改面板宽度）
    var resizer = document.createElement('div');
    resizer.className = 'aicw-resizer';
    resizer.setAttribute('title', '拖动调整宽度');
    resizer.addEventListener('mousedown', startResize);
    panel.appendChild(resizer);

    // 悬浮按钮（可拖动 + 点击展开）
    var launcher = document.createElement('button');
    launcher.className = 'aicw-launcher';
    launcher.type = 'button';
    launcher.setAttribute('aria-label', PANEL_TITLE);
    launcher.setAttribute('aria-expanded', 'false');
    launcher.innerHTML = robotIcon();
    launcher.addEventListener('mousedown', onLauncherDragStart);
    launcher.addEventListener('touchstart', onLauncherDragStart, { passive: true });
    launcher.addEventListener('click', onLauncherClick);

    // 欢迎/提示气泡（默认在按钮左边，尾巴指向按钮）
    var tip = document.createElement('div');
    tip.className = 'aicw-tip aicw-tip-right';
    tip.setAttribute('role', 'status');
    var tipBody = document.createElement('div');
    tipBody.className = 'aicw-tip-body';
    var tipClose = document.createElement('button');
    tipClose.className = 'aicw-tip-close';
    tipClose.type = 'button';
    tipClose.setAttribute('aria-label', '关闭提示');
    tipClose.innerHTML = '&times;';
    tipClose.addEventListener('click', function (e) {
      e.stopPropagation();
      hideTip();
    });
    // 点气泡本身 → 打开聊天面板
    tip.addEventListener('click', function () {
      hideTip();
      open();
    });
    tip.appendChild(tipClose);
    tip.appendChild(tipBody);

    root.appendChild(panel);
    root.appendChild(launcher);
    root.appendChild(tip);
    document.body.appendChild(root);
    document.addEventListener('keydown', onKeydown);
    window.addEventListener('message', onMessage);
    window.addEventListener('resize', onWindowResizeForPosition);

    state.root = root;
    state.panel = panel;
    state.launcher = launcher;
    state.iframe = iframe;
    state.loading = loading;
    state.resizer = resizer;
    state.tip = tip;
    state.tipBody = tipBody;
    state.tipClose = tipClose;

    // 恢复上次保存的按钮位置（若有），并根据位置刷新面板展开方向
    state.position = loadPosition();
    if (state.position) {
      applyLauncherPosition(state.position);
    } else {
      updateTipPlacement();
    }
  }

  function mount() {
    observeHostTheme();
    if (state.root) {
      state.root.style.display = '';
      return;
    }
    build();
    maybeShowWelcome();
  }

  function unmount() {
    if (hostThemeObserver) hostThemeObserver.disconnect();
    hostThemeObserver = null;
    close();
    hideTip();
    // 退出登录等场景：兜底还原被压缩的主应用
    if (state.docked) {
      setDocked(false, false);
    }
    // 兜底清理可能进行中的拖拽
    document.removeEventListener('mousemove', onResizeMove);
    document.removeEventListener('mouseup', onResizeUp);
    document.removeEventListener('mousemove', onLauncherDragMove);
    document.removeEventListener('mouseup', onLauncherDragEnd);
    document.removeEventListener('touchmove', onLauncherDragMove);
    document.removeEventListener('touchend', onLauncherDragEnd);
    document.removeEventListener('touchcancel', onLauncherDragEnd);
    if (state.overlay && state.overlay.parentNode) {
      state.overlay.parentNode.removeChild(state.overlay);
    }
    window.clearTimeout(state.resizeTimer);
    window.clearTimeout(state.tipTimer);
    document.removeEventListener('keydown', onKeydown);
    window.removeEventListener('message', onMessage);
    window.removeEventListener('resize', onWindowResizeForPosition);
    if (state.root && state.root.parentNode) {
      state.root.parentNode.removeChild(state.root);
    }
    state.root = null;
    state.panel = null;
    state.launcher = null;
    state.iframe = null;
    state.loading = null;
    state.resizer = null;
    state.overlay = null;
    state.tip = null;
    state.tipBody = null;
    state.tipClose = null;
    state.tipTimer = null;
    state.iframeLoaded = false;
    state.chatReady = false;
    state.docked = false;
    state.dragStart = null;
    state.suppressClick = false;
    // 保留 state.position 让下次 mount 复用最近拖到的位置
  }

  // 宿主共享数据统一入口。宿主无需重复实现 storage 序列化与异常降级；
  // 同源 iframe 可以直接读取，跨域部署时仍通过 postMessage 接收上下文。
  var storage = {
    get: function (key) {
      try {
        var raw = window.localStorage.getItem(key);
        if (raw === null) return null;
        try {
          return JSON.parse(raw);
        } catch (e) {
          return raw;
        }
      } catch (e) {
        return null;
      }
    },
    set: function (key, value) {
      try {
        var raw = typeof value === 'string' ? value : JSON.stringify(value);
        window.localStorage.setItem(key, raw);
        return true;
      } catch (e) {
        console.warn('[chat-frame] storage.set failed:', key, e);
        return false;
      }
    },
    remove: function (key) {
      try {
        window.localStorage.removeItem(key);
        return true;
      } catch (e) {
        console.warn('[chat-frame] storage.remove failed:', key, e);
        return false;
      }
    },
    merge: function (key, patch) {
      var current = storage.get(key);
      var base = current && typeof current === 'object' && !Array.isArray(current)
        ? current
        : {};
      var next = Object.assign({}, base, patch || {});
      return storage.set(key, next) ? next : null;
    },
  };

  var sdkEventOff = null;

  function persistHostSession(context) {
    var ctx = context || {};
    var user = ctx.user || {};
    storage.set('boot-school-ai-session', {
      app_name: ctx.app_name || null,
      nickName: user.realName || null,
      path: ctx.path || null,
      platform: ctx.platform || 'web',
      token: ctx.token || null,
      user_id: user.userId || null,
    });
  }

  /** 登录成功后调用：注入上下文并真正渲染悬浮助手。 */
  function login(options) {
    var opts = options || {};
    // 模式由用户选择决定，宿主重复登录初始化不覆盖；首次默认悬浮。
    if (Object.prototype.hasOwnProperty.call(opts, 'appCode')) {
      setAppCode(opts.appCode || null);
    }
    if (opts.context) {
      setContext(opts.context);
      persistHostSession(opts.context);
    }
    if (sdkEventOff) sdkEventOff();
    sdkEventOff = typeof opts.onEvent === 'function'
      ? on('chat:event', opts.onEvent)
      : null;
    mount();
  }

  /** 用户、主题、语言或路由改变时增量更新，不重复创建 iframe。 */
  function updateContext(context) {
    setContext(context || null);
    persistHostSession(context);
  }

  /** 退出登录后调用：清理会话数据并移除整套 UI。 */
  function logout() {
    if (sdkEventOff) sdkEventOff();
    sdkEventOff = null;
    storage.remove('boot-school-ai-session');
    storage.remove('bus_data');
    unmount();
  }

  var publicApi = {
    login: login,
    logout: logout,
    updateContext: updateContext,
    mount: mount,
    unmount: unmount,
    open: open,
    close: close,
    setDocked: setDocked,
    setAppCode: setAppCode,
    setContext: setContext,
    configure: configure,
    postToChat: postToChat,
    on: on,
    tip: showTip,
    hideTip: hideTip,
    storage: storage,
  };
  // 对外只保留一个 SDK 入口。
  window.BootSchoolChat = publicApi;
})();
