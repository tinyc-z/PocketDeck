package com.pocketdeck.launcher.lan

import org.json.JSONObject

/**
 * PocketDeck Web Console（Sunset Arcade 设计语言，100% 自包含零外网请求，实时同步掌机主题与亮/暗模式）。
 */
internal object WebManagerUi {

    private fun brandLogoSvg(sizePx: Int): String =
        """<svg width="$sizePx" height="$sizePx" viewBox="18 18 72 72" fill="none" xmlns="http://www.w3.org/2000/svg"><defs><linearGradient id="pdsg" x1="10" y1="4" x2="98" y2="104" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#FFB547"/><stop offset="0.48" stop-color="#FF5A5F"/><stop offset="1" stop-color="#E9327A"/></linearGradient><radialGradient id="pdhl" cx="30" cy="20" r="60" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#FFF" stop-opacity="0.3"/><stop offset="1" stop-color="#FFF" stop-opacity="0"/></radialGradient><linearGradient id="pdscr" x1="40" y1="33" x2="68" y2="54" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#5B2A86"/><stop offset="1" stop-color="#341A5C"/></linearGradient></defs><rect x="18" y="18" width="72" height="72" rx="18" fill="url(#pdsg)"/><rect x="18" y="18" width="72" height="72" rx="18" fill="url(#pdhl)"/><path fill="#9E1F4F" fill-opacity="0.22" d="M38,30 H70 A5,5 0 0 1 75,35 V73 A12,12 0 0 1 63,85 H38 A5,5 0 0 1 33,80 V35 A5,5 0 0 1 38,30 Z"/><path fill="#FFF" d="M38,27 H70 A5,5 0 0 1 75,32 V70 A12,12 0 0 1 63,82 H38 A5,5 0 0 1 33,77 V32 A5,5 0 0 1 38,27 Z"/><path fill="url(#pdscr)" d="M41,32 H67 A2.5,2.5 0 0 1 69.5,34.5 V52.5 A2.5,2.5 0 0 1 67,55 H41 A2.5,2.5 0 0 1 38.5,52.5 V34.5 A2.5,2.5 0 0 1 41,32 Z"/><path fill="#FFD84D" d="M48.4,37.1 h1.6 v1.6 h-1.6 Z M58,37.1 h1.6 v1.6 h-1.6 Z M50,38.7 h1.6 v1.6 h-1.6 Z M56.4,38.7 h1.6 v1.6 h-1.6 Z M48.4,40.3 h11.2 v1.6 h-11.2 Z M46.8,41.9 h3.2 v1.6 h-3.2 Z M51.6,41.9 h4.8 v1.6 h-4.8 Z M58,41.9 h3.2 v1.6 h-3.2 Z M45.2,43.5 h17.6 v1.6 h-17.6 Z M45.2,45.1 h1.6 v1.6 h-1.6 Z M48.4,45.1 h11.2 v1.6 h-11.2 Z M61.2,45.1 h1.6 v1.6 h-1.6 Z M45.2,46.7 h1.6 v1.6 h-1.6 Z M48.4,46.7 h1.6 v1.6 h-1.6 Z M58,46.7 h1.6 v1.6 h-1.6 Z M61.2,46.7 h1.6 v1.6 h-1.6 Z M50,48.3 h3.2 v1.6 h-3.2 Z M54.8,48.3 h3.2 v1.6 h-3.2 Z"/><path fill="#3B1D5E" d="M43,60 h3.6 v4.2 h4.2 v3.6 h-4.2 v4.2 h-3.6 v-4.2 h-4.2 v-3.6 h4.2 Z"/><circle cx="66" cy="62.6" r="3" fill="#FF4F7B"/><circle cx="60" cy="67.4" r="3" fill="#FFB547"/><rect x="46" y="75" width="5" height="2" rx="1" fill="#C9B8D8"/><rect x="53.5" y="75" width="5" height="2" rx="1" fill="#C9B8D8"/></svg>"""

    private const val FAVICON_DATA_URI =
        "data:image/svg+xml,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%20viewBox='18%2018%2072%2072'%3E%3Cdefs%3E%3ClinearGradient%20id='g'%20x1='0'%20y1='0'%20x2='1'%20y2='1'%3E%3Cstop%20offset='0'%20stop-color='%23FFB547'/%3E%3Cstop%20offset='0.5'%20stop-color='%23FF5A5F'/%3E%3Cstop%20offset='1'%20stop-color='%23E9327A'/%3E%3C/linearGradient%3E%3C/defs%3E%3Crect%20x='18'%20y='18'%20width='72'%20height='72'%20rx='18'%20fill='url(%23g)'/%3E%3Cpath%20fill='%23FFF'%20d='M38,27H70A5,5,0,0,1,75,32V70A12,12,0,0,1,63,82H38A5,5,0,0,1,33,77V32A5,5,0,0,1,38,27Z'/%3E%3Crect%20x='39'%20y='32'%20width='30'%20height='23'%20rx='2.5'%20fill='%233B1D5E'/%3E%3Cpath%20fill='%233B1D5E'%20d='M43,60h3.6v4.2h4.2v3.6h-4.2v4.2h-3.6v-4.2h-4.2v-3.6h4.2Z'/%3E%3Ccircle%20cx='66'%20cy='62.6'%20r='3'%20fill='%23FF4F7B'/%3E%3Ccircle%20cx='60'%20cy='67.4'%20r='3'%20fill='%23FFB547'/%3E%3C/svg%3E"

    private fun cssVars(theme: WebThemeConfig): String {
        val j = theme.toJson()
        val dark = j.getBoolean("isDark")
        val primary = j.getString("primary")
        val glow = j.getString("glow")
        val tertiary = j.getString("tertiary")
        val bg = j.getString("bg")
        val panel = j.getString("panel")
        val surf = j.getString("surf")
        val border = j.getString("border")
        val text = j.getString("text")
        val muted = j.getString("muted")
        val label = j.optString("label", muted)
        val bStart = j.optString("brandStart", glow)
        val bMid = j.optString("brandMid", primary)
        val bEnd = j.optString("brandEnd", tertiary)
        val surfAlt = j.optString("surfaceAlt", surf)
        val line = j.optString("line", border)
        val accSoft = j.optString("accentSoft", "rgba(232,62,108,0.12)")
        val flowA = j.optString("flowA", "rgba(255,196,138,0.55)")
        val flowB = j.optString("flowB", "rgba(255,157,181,0.50)")
        val flowC = j.optString("flowC", "rgba(201,178,255,0.42)")
        val sideBg = if (dark) "rgba(35,26,46,.68)" else "rgba(255,255,255,.62)"
        val shadow = if (dark) "0 8px 24px rgba(0,0,0,.45)" else "0 6px 18px rgba(120,40,70,.09),0 1px 3px rgba(120,40,70,.06)"
        val inputBg = if (dark) "#231A2E" else "#FFFFFF"
        return """
            --accent:$primary;
            --glow:$glow;
            --tertiary:$tertiary;
            --brand-start:$bStart;
            --brand-mid:$bMid;
            --brand-end:$bEnd;
            --brand:linear-gradient(120deg,$bStart 0%,$bMid 48%,$bEnd 100%);
            --bg:$bg;
            --panel:$panel;
            --surf:$surf;
            --surf-alt:$surfAlt;
            --border:$line;
            --line:$line;
            --text:$text;
            --muted:$muted;
            --label:$label;
            --accent-soft:$accSoft;
            --flow-a:$flowA;
            --flow-b:$flowB;
            --flow-c:$flowC;
            --side-bg:$sideBg;
            --shadow:$shadow;
            --input-bg:$inputBg;
            --danger:#e11d48;
            --ok:#1fa971;
            --warn:#f59e0b;
        """.trimIndent().replace("\n", "")
    }

    fun renderLoginHtml(theme: WebThemeConfig): String {
        val vars = cssVars(theme)
        val darkAttr = if (theme.isDark) "1" else "0"
        return """<!doctype html>
<html lang="en" data-dark="$darkAttr">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>PocketDeck · Login</title>
<link rel="icon" type="image/svg+xml" href="$FAVICON_DATA_URI"/>
<style>
  :root { $vars }
  * { box-sizing:border-box; margin:0; padding:0; }
  body {
    min-height:100vh; display:grid; place-items:center;
    background:
      radial-gradient(circle at 12% 10%, var(--flow-a) 0%, transparent 46%),
      radial-gradient(circle at 90% 35%, var(--flow-b) 0%, transparent 48%),
      radial-gradient(circle at 45% 105%, var(--flow-c) 0%, transparent 50%),
      var(--bg);
    color:var(--text);
    font-family:-apple-system,BlinkMacSystemFont,"Inter","Segoe UI",Roboto,sans-serif;
    padding:20px;
    transition:background .25s ease, color .25s ease;
  }
  .deck-card {
    width:100%; max-width:396px;
    background:var(--surf);
    border:1px solid var(--line);
    border-radius:24px;
    padding:28px 26px 26px;
    box-shadow:var(--shadow);
  }
  .logo-row { display:flex; align-items:center; gap:12px; margin-bottom:16px; }
  .logo-sq { width:44px; height:44px; border-radius:12px; overflow:hidden; flex-shrink:0; box-shadow:0 8px 20px color-mix(in srgb, var(--brand-end) 28%, transparent); }
  .brand-title { font-size:20px; font-weight:800; letter-spacing:-.3px; }
  .brand-sub { font-size:12px; color:var(--label); font-weight:600; margin-top:1px; }
  p { margin:0 0 18px; color:var(--muted); font-size:13.5px; line-height:1.5; }
  input {
    width:100%; padding:14px 16px; border-radius:14px;
    border:1.5px solid var(--line); background:var(--surf-alt); color:var(--text);
    font-size:24px; font-weight:800; letter-spacing:10px; text-align:center; outline:none;
    transition:border-color .15s, box-shadow .15s;
  }
  input:focus {
    border-color:var(--brand-mid);
    background:var(--input-bg);
    box-shadow:0 0 0 3px var(--accent-soft);
  }
  button {
    width:100%; margin-top:14px; padding:13px; border:0; border-radius:14px;
    background:var(--brand);
    color:#fff; font-weight:700; font-size:15px; cursor:pointer;
    box-shadow:0 8px 20px color-mix(in srgb, var(--brand-end) 32%, transparent);
    transition:transform .12s, opacity .15s;
  }
  button:hover { transform:translateY(-1px); }
  button:active { transform:translateY(0); }
  button:disabled { opacity:.6; cursor:default; transform:none; }
  .err { margin-top:12px; color:var(--danger); font-size:13px; text-align:center; min-height:18px; font-weight:600; }
  .lang-sel {
    margin-left:auto; appearance:none; -webkit-appearance:none;
    border:1px solid var(--line); background-color:var(--surf-alt); color:var(--text);
    border-radius:999px; padding:6px 26px 6px 12px; font-size:12px; font-weight:600; cursor:pointer; outline:none;
    background-image:linear-gradient(45deg,transparent 50%,var(--muted) 50%),linear-gradient(135deg,var(--muted) 50%,transparent 50%);
    background-position:calc(100% - 14px) 52%,calc(100% - 9px) 52%;
    background-size:5px 5px; background-repeat:no-repeat;
  }
</style>
</head>
<body>
<div class="deck-card">
  <div class="logo-row">
    <div class="logo-sq">${brandLogoSvg(44)}</div>
    <div>
      <div class="brand-title">PocketDeck</div>
      <div class="brand-sub" id="subTxt">Web Console</div>
    </div>
    <select id="langSel" class="lang-sel" onchange="setLoginLang(this.value)">
      <option value="en">English</option>
      <option value="zh-CN">简体中文</option>
      <option value="zh-TW">繁體中文</option>
    </select>
  </div>
  <p id="promptTxt"></p>
  <form id="f">
    <input id="pwd" type="password" inputmode="numeric" maxlength="4" placeholder="••••" autofocus autocomplete="off"/>
    <button id="btn" type="submit"></button>
    <div id="err" class="err"></div>
  </form>
</div>
<script>
const LOGIN_I18N = {
  'zh-CN': {
    title: 'PocketDeck · 登录',
    sub: '网页控制台',
    prompt: '输入掌机屏幕上显示的 4 位 PIN 码。',
    btn: '登录',
    tooMany: s => '尝试过多，请 ' + s + ' 秒后重试',
    remain: n => 'PIN 码错误，还可尝试 ' + n + ' 次',
    invalid: 'PIN 码错误',
    conn: '无法连接，请确认在同一局域网'
  },
  'zh-TW': {
    title: 'PocketDeck · 登入',
    sub: '網頁主控台',
    prompt: '輸入掌機螢幕上顯示的 4 位 PIN 碼。',
    btn: '登入',
    tooMany: s => '嘗試過多，請 ' + s + ' 秒後重試',
    remain: n => 'PIN 碼錯誤，還可嘗試 ' + n + ' 次',
    invalid: 'PIN 碼錯誤',
    conn: '無法連線，請確認在同一區域網路'
  },
  en: {
    title: 'PocketDeck · Login',
    sub: 'Web Console',
    prompt: 'Enter the PIN shown on your device.',
    btn: 'Log in',
    tooMany: s => 'Too many attempts. Retry in ' + s + 's',
    remain: n => 'Wrong PIN. ' + n + ' left',
    invalid: 'Wrong PIN.',
    conn: "Can't connect. Same network?"
  }
};
function detectWebLang() {
  const saved = localStorage.getItem('pd_web_lang');
  if (saved && LOGIN_I18N[saved]) return saved;
  const nav = (navigator.language || '').toLowerCase();
  if (nav.startsWith('zh')) {
    return (nav.includes('tw') || nav.includes('hk') || nav.includes('mo') || nav.includes('hant')) ? 'zh-TW' : 'zh-CN';
  }
  return 'en';
}
let curLang = detectWebLang();
function setLoginLang(lang) {
  if (!LOGIN_I18N[lang]) return;
  curLang = lang;
  localStorage.setItem('pd_web_lang', lang);
  const L = LOGIN_I18N[lang];
  document.documentElement.lang = lang;
  document.title = L.title;
  document.getElementById('langSel').value = lang;
  document.getElementById('subTxt').textContent = L.sub;
  document.getElementById('promptTxt').textContent = L.prompt;
  document.getElementById('btn').textContent = L.btn;
}
setLoginLang(curLang);

function applyThemeObj(t){
  if(!t) return;
  const r = document.documentElement;
  r.dataset.dark = t.isDark ? '1' : '0';
  const bs = t.brandStart || t.glow, bm = t.brandMid || t.primary, be = t.brandEnd || t.tertiary;
  r.style.setProperty('--accent', t.primary);
  r.style.setProperty('--brand-start', bs);
  r.style.setProperty('--brand-mid', bm);
  r.style.setProperty('--brand-end', be);
  r.style.setProperty('--brand', 'linear-gradient(120deg,' + bs + ' 0%,' + bm + ' 48%,' + be + ' 100%)');
  r.style.setProperty('--bg', t.bg);
  r.style.setProperty('--surf', t.surf);
  r.style.setProperty('--surf-alt', t.surfaceAlt || t.surf);
  r.style.setProperty('--line', t.line || t.border);
  r.style.setProperty('--text', t.text);
  r.style.setProperty('--muted', t.muted);
  r.style.setProperty('--label', t.label || t.muted);
  r.style.setProperty('--accent-soft', t.accentSoft || 'rgba(232,62,108,0.12)');
  if(t.flowA) r.style.setProperty('--flow-a', t.flowA);
  if(t.flowB) r.style.setProperty('--flow-b', t.flowB);
  if(t.flowC) r.style.setProperty('--flow-c', t.flowC);
  r.style.setProperty('--input-bg', t.isDark ? '#231A2E' : '#FFFFFF');
}
setInterval(async ()=>{
  try{
    const d = await (await fetch('/api/theme', { cache:'no-store' })).json();
    if(d.ok) applyThemeObj(d.theme);
  }catch{}
}, 4000);

document.getElementById('f').addEventListener('submit', async (e) => {
  e.preventDefault();
  const btn = document.getElementById('btn');
  const err = document.getElementById('err');
  const password = document.getElementById('pwd').value.trim();
  if (!password) return;
  btn.disabled = true; err.textContent = '';
  const L = LOGIN_I18N[curLang] || LOGIN_I18N['en'];
  try {
    const r = await fetch('/api/login', {
      method: 'POST',
      headers: {'Content-Type':'application/json'},
      body: JSON.stringify({password})
    });
    const d = await r.json();
    if (r.ok && d.ok) { location.reload(); return; }
    if (r.status === 429) err.textContent = L.tooMany(d.retryAfterSec || 30);
    else if (typeof d.remainingAttempts === 'number') err.textContent = L.remain(d.remainingAttempts);
    else err.textContent = L.invalid;
  } catch {
    err.textContent = L.conn;
  } finally {
    btn.disabled = false;
  }
});
</script>
</body>
</html>"""
    }

    fun renderAppHtml(theme: WebThemeConfig): String {
        val vars = cssVars(theme)
        val darkAttr = if (theme.isDark) "1" else "0"
        return """<!doctype html>
<html lang="en" data-dark="$darkAttr">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>PocketDeck</title>
<link rel="icon" type="image/svg+xml" href="$FAVICON_DATA_URI"/>
<style>
  :root { $vars }
  * { box-sizing:border-box; }
  body {
    margin:0; min-height:100vh;
    background:var(--bg);
    color:var(--text);
    font-family:-apple-system,BlinkMacSystemFont,"Inter","Segoe UI",Roboto,sans-serif;
    transition:background .25s ease, color .25s ease;
  }
  .bg-flow { position:fixed; inset:0; pointer-events:none; z-index:0; overflow:hidden; }
  .bg-flow i { position:absolute; border-radius:50%; filter:blur(90px); opacity:.85; }
  .bg-flow .b1 { width:62vw; height:55vh; left:-14vw; top:-16vh; background:var(--flow-a); }
  .bg-flow .b2 { width:56vw; height:52vh; right:-16vw; top:8vh; background:var(--flow-b); }
  .bg-flow .b3 { width:54vw; height:48vh; left:22vw; bottom:-24vh; background:var(--flow-c); }

  .shell { position:relative; z-index:1; display:flex; min-height:100vh; }
  .side {
    width:248px; flex-shrink:0;
    position:sticky; top:0; height:100vh;
    padding:22px 16px 18px;
    display:flex; flex-direction:column; gap:5px;
    background:var(--side-bg);
    backdrop-filter:blur(22px); -webkit-backdrop-filter:blur(22px);
    border-right:1px solid var(--line);
  }
  .brand-wrap { display:flex; align-items:center; gap:12px; padding:2px 6px 20px; }
  .logo-sq { width:40px; height:40px; border-radius:11px; overflow:hidden; flex-shrink:0; box-shadow:0 6px 16px color-mix(in srgb, var(--brand-end) 24%, transparent); }
  .brand { font-weight:800; font-size:18px; letter-spacing:-.3px; line-height:1.15; }
  .brand-sub { font-size:12px; color:var(--label); font-weight:600; margin-top:2px; }

  .nav-item {
    display:flex; align-items:center; gap:11px; height:42px; padding:0 13px;
    border-radius:12px; border:0; background:transparent; width:100%;
    color:var(--muted); font-size:14.5px; font-weight:600; cursor:pointer;
    text-align:left; transition:background .15s, color .15s, box-shadow .15s;
  }
  .nav-item:hover { background:color-mix(in srgb, var(--surf) 70%, transparent); color:var(--text); }
  .nav-item.active { background:var(--surf); color:var(--text); box-shadow:var(--shadow); }
  .nav-item.active svg { color:var(--accent); }
  .nav-cnt { margin-left:auto; font-size:12px; font-weight:700; color:var(--label); }

  .side-spacer { flex:1; }
  .more-wrap { position:relative; margin-bottom:10px; }
  .more-pop {
    display:none; position:absolute; left:0; right:0; bottom:46px; z-index:40;
    background:var(--surf); border:1px solid var(--line); border-radius:16px;
    box-shadow:0 16px 40px rgba(36,23,47,.18); padding:8px; flex-direction:column; gap:4px;
  }
  .more-pop.open { display:flex; }
  .more-row {
    display:flex; align-items:center; gap:9px; width:100%; padding:8px 10px;
    border:0; border-radius:10px; background:transparent; color:var(--text);
    font-size:13px; font-weight:600; cursor:pointer; text-align:left;
  }
  .more-row:hover { background:var(--surf-alt); }
  .more-row.danger { color:var(--danger); }

  .dev-card {
    background:var(--surf); border:1px solid var(--line); border-radius:16px;
    padding:13px 14px; box-shadow:var(--shadow); font-size:12.5px;
  }
  .dev-name { font-weight:800; font-size:14px; color:var(--text); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .dev-status { margin-top:4px; color:var(--muted); font-size:12px; line-height:1.4; }
  .conn-dot { display:inline-block; width:8px; height:8px; border-radius:50%; margin-right:5px; vertical-align:middle; }
  .conn-dot.ok { background:var(--ok); }
  .conn-dot.off { background:var(--danger); }
  .conn-off-txt { color:var(--danger); font-weight:700; }
  .dev-bar { height:6px; border-radius:999px; background:var(--surf-alt); margin:10px 0 5px; overflow:hidden; }
  .dev-bar i { display:block; height:100%; width:0%; background:var(--brand); border-radius:999px; transition:width .3s ease; }
  .dev-store { font-size:11.5px; color:var(--label); font-weight:600; }

  .main-wrap { flex:1; min-width:0; }
  .main { width:100%; max-width:1360px; margin:0 auto; padding:26px 32px 64px; }
  .page-top { display:flex; align-items:center; gap:14px; flex-wrap:wrap; margin-bottom:18px; }
  .page-top h1 { margin:0; font-size:28px; font-weight:800; letter-spacing:-.4px; }
  .page-sub { font-size:13.5px; color:var(--label); font-weight:600; margin-top:4px; }
  .sub { font-size:12.5px; color:var(--muted); }
  .ico { width:16px; height:16px; flex-shrink:0; display:inline-block; vertical-align:middle; }

  .search-box {
    margin-left:auto; display:flex; align-items:center; gap:9px;
    width:310px; max-width:100%; height:42px; padding:0 13px;
    border-radius:12px; background:var(--surf); border:1px solid var(--line);
    box-shadow:var(--shadow); color:var(--label);
    transition:border-color .15s, box-shadow .15s;
  }
  .search-box:focus-within { border-color:var(--brand-mid); box-shadow:0 0 0 3px var(--accent-soft); }
  .search-box input {
    border:0; background:transparent; color:var(--text); font-size:14px;
    outline:none; flex:1; min-width:0; padding:0;
  }
  .kbd { font-size:11px; font-weight:700; color:var(--label); border:1px solid var(--line); border-radius:6px; padding:1px 6px; }

  .btn {
    border:1px solid var(--line); background:var(--surf); color:var(--text);
    padding:8px 14px; border-radius:11px; cursor:pointer; font-size:13px; font-weight:600;
    text-decoration:none; display:inline-flex; align-items:center; gap:6px;
    transition:all .14s; white-space:nowrap;
  }
  .btn:hover:not(:disabled) { border-color:var(--brand-mid); color:var(--accent); }
  .btn.primary {
    background:var(--brand); background-origin:border-box; background-clip:border-box; background-repeat:no-repeat;
    color:#fff; border-color:transparent; font-weight:700;
    box-shadow:0 6px 16px color-mix(in srgb, var(--brand-end) 28%, transparent);
  }
  .btn.primary:hover:not(:disabled) { opacity:.94; color:#fff; transform:translateY(-1px); }
  .btn.danger { color:var(--danger); border-color:color-mix(in srgb, var(--danger) 30%, var(--line)); }
  .btn.danger:hover:not(:disabled) { background:color-mix(in srgb, var(--danger) 10%, transparent); border-color:var(--danger); color:var(--danger); }
  .btn.sm { padding:6px 11px; font-size:12.5px; border-radius:10px; }
  .btn:disabled { opacity:.42; cursor:default; transform:none; }
  .pbtn { height:42px; padding:0 18px; border-radius:12px; font-size:14px; }

  .filter-bar { display:flex; gap:8px; align-items:center; flex-wrap:wrap; margin-bottom:20px; }
  .fchip {
    height:34px; padding:0 14px; border-radius:999px;
    background:var(--surf); border:1px solid var(--line);
    color:var(--muted); font-size:13px; font-weight:600; cursor:pointer;
    display:inline-flex; align-items:center; gap:5px; transition:all .14s;
  }
  .fchip:hover { color:var(--text); border-color:var(--brand-mid); }
  .fchip.on { background:var(--brand); background-origin:border-box; background-clip:border-box; background-repeat:no-repeat; color:#fff; border-color:transparent; box-shadow:0 4px 12px color-mix(in srgb, var(--brand-end) 26%, transparent); }
  .seg { margin-left:auto; display:inline-flex; background:var(--surf-alt); border:1px solid var(--line); border-radius:11px; padding:3px; gap:2px; }
  .seg button {
    border:0; background:transparent; color:var(--muted); padding:6px 13px;
    border-radius:8px; font-size:13px; font-weight:600; cursor:pointer; transition:all .14s;
  }
  .seg button.active { background:var(--surf); color:var(--text); box-shadow:0 2px 6px rgba(36,23,47,.08); }

  .toolbar { display:flex; gap:8px; flex-wrap:wrap; align-items:center; margin-bottom:14px; }
  .search, select, input[type=text] {
    border:1px solid var(--line); background:var(--surf); color:var(--text);
    padding:8px 12px; border-radius:11px; font-size:13px; outline:none;
  }
  select {
    appearance:none; -webkit-appearance:none; padding-right:30px; cursor:pointer;
    background-image:linear-gradient(45deg,transparent 50%,var(--muted) 50%),linear-gradient(135deg,var(--muted) 50%,transparent 50%);
    background-position:calc(100% - 14px) 52%,calc(100% - 9px) 52%;
    background-size:5px 5px; background-repeat:no-repeat;
  }
  .search { flex:1; min-width:200px; }
  .search:focus, select:focus, input[type=text]:focus { border-color:var(--brand-mid); box-shadow:0 0 0 3px var(--accent-soft); }

  .game-grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(170px, 1fr)); gap:18px; }
  .gcard {
    background:var(--surf); border:1px solid var(--line); border-radius:16px;
    overflow:hidden; display:flex; flex-direction:column; cursor:pointer;
    box-shadow:var(--shadow);
    transition:transform .16s ease, box-shadow .16s ease, border-color .16s;
  }
  .gcard:hover { transform:translateY(-3px); border-color:var(--brand-mid); box-shadow:0 14px 28px color-mix(in srgb, var(--brand-end) 18%, rgba(36,23,47,.12)); }
  .gcard.is-hidden { opacity:.55; }
  .gcover-box { position:relative; aspect-ratio:1/1; background:var(--surf-alt); overflow:hidden; display:grid; place-items:center; }
  .gcover-box img { width:100%; height:100%; object-fit:cover; display:block; }
  .gcover-fallback {
    width:100%; height:100%; padding:14px; display:flex; flex-direction:column;
    justify-content:flex-end; align-items:flex-start; text-align:left;
    background:var(--brand); color:#fff;
  }
  .gcover-fallback .tt {
    font-weight:800; font-size:15px; line-height:1.25;
    display:-webkit-box; -webkit-line-clamp:3; -webkit-box-orient:vertical; overflow:hidden;
    text-shadow:0 1px 4px rgba(0,0,0,.18);
  }
  .badge-plat {
    position:absolute; top:9px; left:9px; padding:3px 8px; border-radius:7px;
    font-size:11px; font-weight:800; letter-spacing:.4px;
    background:rgba(36,23,47,.72); color:#fff; backdrop-filter:blur(6px);
  }
  .badge-fav {
    position:absolute; top:9px; right:9px; width:30px; height:30px; border-radius:50%;
    border:0; background:rgba(255,255,255,.92); color:#6E6176;
    display:grid; place-items:center; cursor:pointer;
    box-shadow:0 2px 8px rgba(36,23,47,.18); transition:transform .12s, background .15s;
  }
  .badge-fav:hover { transform:scale(1.08); }
  .badge-fav.on { background:var(--brand); color:#fff; }
  .gbody { padding:11px 13px 13px; }
  .gtitle { font-weight:700; font-size:14px; line-height:1.3; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .gmeta { font-size:12px; color:var(--label); margin-top:3px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; font-weight:500; }

  .chip {
    display:inline-flex; align-items:center; gap:4px;
    padding:4px 10px; border-radius:999px; font-size:12px; font-weight:700;
    background:var(--accent-soft); color:var(--accent);
  }
  .chip.warn { background:color-mix(in srgb, var(--warn) 16%, transparent); color:var(--warn); }
  .chip.muted { background:var(--surf-alt); color:var(--muted); }

  .list { display:flex; flex-direction:column; gap:8px; }
  .row {
    display:flex; align-items:center; gap:12px; padding:11px 14px;
    border-radius:14px; background:var(--surf); border:1px solid var(--line);
    box-shadow:0 2px 8px rgba(120,40,70,.05); transition:border-color .14s;
  }
  .row:hover { border-color:var(--brand-mid); }
  .cover-sm {
    width:46px; height:46px; border-radius:10px; object-fit:cover; background:var(--brand);
    flex-shrink:0; display:grid; place-items:center; font-size:11px; color:#fff; font-weight:800;
  }
  .meta { flex:1; min-width:0; }
  .title { font-weight:700; font-size:14px; display:flex; align-items:center; gap:7px; flex-wrap:wrap; }
  .desc { font-size:12px; color:var(--muted); margin-top:3px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
  .actions { display:flex; gap:6px; flex-wrap:wrap; }

  .cat-banner {
    display:flex; align-items:center; gap:10px; flex-wrap:wrap;
    padding:16px 18px; border-radius:16px; background:var(--surf);
    border:1px solid var(--line); box-shadow:var(--shadow); margin-bottom:14px;
  }
  .cat-row {
    display:flex; align-items:center; gap:12px; padding:12px 14px; border-radius:14px;
    background:var(--surf); border:1px solid var(--line); cursor:grab; user-select:none;
    box-shadow:0 2px 8px rgba(120,40,70,.05); transition:border-color .14s, opacity .14s;
  }
  .cat-row:hover { border-color:var(--brand-mid); }
  .cat-row.dragging { opacity:.45; border-style:dashed; border-color:var(--brand-mid); }
  .cat-row.drag-over { border-color:var(--brand-mid); box-shadow:0 0 0 2px var(--accent-soft); }
  .cat-row.is-hidden { opacity:.55; }
  .drag-handle { font-size:16px; color:var(--label); cursor:grab; padding:2px 4px; }

  .crumbs { display:flex; align-items:center; gap:6px; flex-wrap:wrap; margin-bottom:12px; }
  .crumb {
    border:1px solid var(--line); background:var(--surf); color:var(--muted);
    padding:5px 12px; border-radius:999px; cursor:pointer; font-size:12.5px; font-weight:600;
  }
  .crumb:hover { border-color:var(--brand-mid); color:var(--accent); }
  .crumb.cur { background:var(--brand); background-origin:border-box; background-clip:border-box; background-repeat:no-repeat; color:#fff; border-color:transparent; cursor:default; }
  .dropzone {
    border:1.5px dashed color-mix(in srgb, var(--brand-mid) 55%, var(--line));
    border-radius:16px; padding:20px; text-align:center; color:var(--muted);
    background:color-mix(in srgb, var(--surf) 75%, transparent); margin-bottom:14px; transition:all .15s;
  }
  .dropzone.over { border-color:var(--brand-mid); background:var(--accent-soft); color:var(--accent); }
  .prog { height:7px; border-radius:999px; background:var(--surf-alt); overflow:hidden; margin-top:10px; display:none; }
  .prog > div { height:100%; width:0%; background:var(--brand); transition:width .12s; border-radius:999px; }
  .batch-bar {
    position:sticky; bottom:16px; z-index:25; margin-top:14px;
    background:var(--surf); border:1.5px solid var(--brand-mid); border-radius:16px;
    padding:10px 16px; display:none; align-items:center; gap:8px; flex-wrap:wrap;
    box-shadow:0 16px 36px rgba(36,23,47,.2);
  }
  .batch-bar.show { display:flex; }

  .modal-bg {
    position:fixed; inset:0; background:rgba(23,17,31,.40); backdrop-filter:blur(4px);
    display:none; align-items:center; justify-content:center; z-index:50; padding:20px;
  }
  .modal-bg.open { display:flex; }
  .modal {
    width:100%; max-width:650px; max-height:88vh; background:var(--surf);
    border:1px solid var(--line); border-radius:22px; overflow:hidden;
    display:flex; flex-direction:column; box-shadow:0 30px 80px rgba(36,23,47,.28);
  }
  .modal-hd {
    padding:16px 20px; border-bottom:1px solid var(--line);
    display:flex; align-items:center; gap:10px; font-weight:800; font-size:17px;
  }
  .modal-bd { padding:20px; overflow:auto; flex:1; }
  .modal-ft {
    padding:13px 20px; border-top:1px solid var(--line);
    display:flex; gap:8px; justify-content:flex-end; flex-wrap:wrap; background:var(--surf);
  }

  .detail-hero { display:flex; gap:18px; align-items:flex-start; flex-wrap:wrap; margin-bottom:10px; }
  .drawer-cover {
    width:168px; height:168px; border-radius:16px; overflow:hidden; flex-shrink:0;
    background:var(--brand); box-shadow:var(--shadow);
    display:grid; place-items:center; color:#fff; font-weight:800; font-size:15px; text-align:center;
  }
  .drawer-cover span { padding:14px; }
  .drawer-cover img { width:100%; height:100%; object-fit:cover; display:block; }
  .detail-info { flex:1; min-width:260px; }
  .drawer-title { font-size:21px; font-weight:800; letter-spacing:-.3px; margin:0 0 10px; line-height:1.25; }
  .drawer-tags { display:flex; gap:6px; flex-wrap:wrap; margin-bottom:8px; }
  .meta-kv { display:grid; grid-template-columns:1fr 1fr; gap:6px; margin:8px 0 6px; }
  .kv-box { background:var(--surf-alt); border-radius:9px; padding:5px 9px; }
  .kv-k { font-size:10.5px; color:var(--label); font-weight:600; line-height:1.2; }
  .kv-v { font-size:12.5px; font-weight:700; margin-top:1px; word-break:break-word; color:var(--text); line-height:1.3; }
  .detail-path { font-size:11px; color:var(--label); opacity:.58; line-height:1.35; word-break:break-all; margin-top:4px; }
  .drawer-acts { display:flex; gap:7px; flex-wrap:wrap; margin-top:0; }
  .drawer-acts .btn { justify-content:center; }

  .plat-grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(120px, 1fr)); gap:7px; max-height:220px; overflow:auto; padding:2px; }
  .plat-chk {
    display:flex; align-items:center; gap:7px; padding:7px 10px; border-radius:10px;
    border:1px solid var(--line); background:var(--surf-alt); font-size:12.5px; cursor:pointer;
  }
  .plat-chk.checked { border-color:var(--brand-mid); background:var(--accent-soft); color:var(--accent); font-weight:700; }

  textarea.code {
    width:100%; min-height:360px; font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;
    font-size:13px; line-height:1.5; background:var(--surf-alt); color:var(--text);
    border:1px solid var(--line); border-radius:12px; padding:12px; outline:none;
  }
  .img-view { max-width:100%; max-height:66vh; display:block; margin:0 auto; border-radius:12px; }
  .cand-grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(108px, 1fr)); gap:10px; margin-top:12px; }
  .cand-item { border:1px solid var(--line); border-radius:12px; overflow:hidden; cursor:pointer; background:var(--surf-alt); text-align:center; padding-bottom:6px; }
  .cand-item:hover { border-color:var(--brand-mid); }
  .cand-item img { width:100%; height:106px; object-fit:cover; display:block; }
  .cand-item span { font-size:10.5px; color:var(--muted); display:block; margin-top:4px; padding:0 4px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }

  .float-dock { position:fixed; right:20px; bottom:20px; z-index:90; display:flex; flex-direction:column; align-items:flex-end; gap:10px; max-width:min(420px, calc(100vw - 32px)); pointer-events:none; }
  .float-dock > * { pointer-events:auto; }
  .toast { background:var(--text); color:var(--bg); padding:10px 16px; border-radius:12px; font-size:13px; font-weight:600; display:none; box-shadow:0 12px 30px rgba(36,23,47,.3); max-width:100%; }
  .toast.show { display:block; }
  .task-bar { width:100%; min-width:300px; background:var(--surf); border:1px solid var(--line); border-radius:16px; padding:12px 14px; box-shadow:0 18px 42px rgba(36,23,47,.2); display:none; flex-direction:column; gap:10px; }
  .task-bar.show { display:flex; }
  .task-item { display:flex; flex-direction:column; gap:6px; }
  .task-top { display:flex; align-items:center; gap:8px; }
  .task-spin { width:14px; height:14px; border-radius:50%; border:2px solid var(--line); border-top-color:var(--brand-mid); animation:pdSpin .8s linear infinite; flex-shrink:0; }
  @keyframes pdSpin { to { transform:rotate(360deg); } }
  .task-prog { height:6px; border-radius:999px; background:var(--surf-alt); overflow:hidden; position:relative; }
  .task-prog > div { height:100%; background:var(--brand); transition:width .18s ease; border-radius:999px; }
  .task-prog.indet > div { width:40%; animation:pdIndet 1.2s ease-in-out infinite; }
  @keyframes pdIndet { 0% { transform:translateX(-100%); } 100% { transform:translateX(260%); } }

  .log-win { position:fixed; right:22px; bottom:22px; width:min(740px, calc(100vw - 32px)); height:min(500px, calc(100vh - 96px)); min-width:340px; min-height:200px; z-index:95; display:none; flex-direction:column; background:#14101C; color:#F2ECF7; border:1px solid #352946; border-radius:16px; box-shadow:0 24px 64px rgba(0,0,0,.55); resize:both; overflow:hidden; }
  .log-win.open { display:flex; }
  .log-win.min { height:auto !important; min-height:0 !important; resize:none; width:340px; }
  .log-win.min .log-win-bd { display:none; }
  .log-win-hd { display:flex; align-items:center; gap:8px; padding:10px 14px; background:#1E162A; border-bottom:1px solid #352946; cursor:move; user-select:none; font-size:13px; font-weight:700; }
  .log-win-bd { flex:1; min-height:0; display:flex; flex-direction:column; padding:10px 12px; gap:8px; }
  .log-win-bar { display:flex; align-items:center; gap:6px; flex-wrap:wrap; }
  .log-win-btn { border:1px solid #3A2D4A; background:#231A30; color:#E8DFF0; border-radius:8px; padding:4px 9px; font-size:11.5px; font-weight:600; cursor:pointer; }
  .log-win-btn:hover { border-color:var(--brand-mid); }
  .log-win-btn.on { background:var(--brand); background-origin:border-box; background-clip:border-box; background-repeat:no-repeat; border-color:transparent; color:#fff; }
  .log-win-btn.err { color:#ff7b72; }
  .log-win-in { flex:1; min-width:120px; border:1px solid #3A2D4A; background:#120D1A; color:#F2ECF7; border-radius:8px; padding:4px 9px; font-size:12px; outline:none; }
  .log-win-ft { display:flex; align-items:center; justify-content:space-between; gap:8px; font-size:11px; color:#9E8FB2; }
  .list-footer { display:flex; align-items:center; justify-content:center; gap:10px; padding:18px 0 8px; color:var(--muted); font-size:12.5px; }
  .list-footer:empty { display:none; padding:0; }

  @media (max-width: 899px) {
    .shell { flex-direction:column; }
    .side { width:100%; height:auto; position:sticky; top:0; z-index:20; flex-direction:row; flex-wrap:wrap; align-items:center; padding:12px 16px; border-right:0; border-bottom:1px solid var(--line); }
    .brand-wrap { padding:0 12px 0 0; }
    .nav-item { width:auto; height:36px; padding:0 12px; }
    .side-spacer, .dev-card { display:none; }
    .more-wrap { margin:0 0 0 auto; }
    .more-pop { left:auto; right:0; width:190px; top:42px; bottom:auto; }
    .main { padding:18px 16px 64px; }
  }
</style>
</head>
<body>
<div class="bg-flow"><i class="b1"></i><i class="b2"></i><i class="b3"></i></div>
<div class="shell">
  <aside class="side">
    <div class="brand-wrap">
      <div class="logo-sq">${brandLogoSvg(40)}</div>
      <div>
        <div class="brand">PocketDeck</div>
        <div class="brand-sub" data-i18n="console_sub">Web Console</div>
      </div>
    </div>

    <button class="nav-item active" data-tab="games">
      <svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M7 7h10a5 5 0 0 1 4.8 6.4l-1 3.4a2.8 2.8 0 0 1-4.9.9L14.5 16h-5l-1.4 1.7a2.8 2.8 0 0 1-4.9-.9l-1-3.4A5 5 0 0 1 7 7z"/></svg>
      <span data-i18n="tab_games">Library</span>
      <span id="navGamesCnt" class="nav-cnt"></span>
    </button>
    <button class="nav-item" data-tab="categories">
      <svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12V4h8l10 10-8 8z"/><circle cx="7.5" cy="8.5" r="1.4"/></svg>
      <span data-i18n="tab_categories">Tabs &amp; Tags</span>
      <span id="navCatsCnt" class="nav-cnt"></span>
    </button>
    <button class="nav-item" data-tab="files">
      <svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6.5A2.5 2.5 0 0 1 5.5 4H10l2 2.5h6.5A2.5 2.5 0 0 1 21 9v8.5a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17.5z"/></svg>
      <span data-i18n="tab_files">Files</span>
    </button>

    <div class="side-spacer"></div>

    <div class="more-wrap">
      <button class="nav-item" onclick="toggleMoreMenu(event)">
        <svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><circle cx="5" cy="12" r="1.3"/><circle cx="12" cy="12" r="1.3"/><circle cx="19" cy="12" r="1.3"/></svg>
        <span data-i18n="nav_more">More</span>
      </button>
      <div id="morePop" class="more-pop">
        <div style="padding:4px 6px;">
          <select id="langSel" onchange="setWebLang(this.value)" style="width:100%;font-size:12.5px;padding:6px 28px 6px 10px;">
            <option value="en">English</option>
            <option value="zh-CN">简体中文</option>
            <option value="zh-TW">繁體中文</option>
          </select>
        </div>
        <button class="more-row" onclick="closeMoreMenu();openScrapeLogsModal()">
          <span data-i18n="btn_logs">Logs</span>
        </button>
        <button class="more-row" onclick="closeMoreMenu();rescan()">
          <span data-i18n="btn_rescan">Rescan</span>
        </button>
        <button class="more-row danger" onclick="closeMoreMenu();logout()">
          <span data-i18n="btn_logout">Log out</span>
        </button>
      </div>
    </div>

    <div class="dev-card">
      <div id="devName" class="dev-name">PocketDeck</div>
      <div id="devInfo" class="dev-status" data-i18n="connecting">Connecting…</div>
      <div id="devStorageList">
        <div class="dev-bar"><i id="devStorageBar"></i></div>
        <div id="devStorageTxt" class="dev-store">—</div>
      </div>
    </div>
  </aside>

  <div class="main-wrap">
  <main class="main">
    <section id="tab-games">
      <div class="page-top">
        <div>
          <h1 data-i18n="tab_games">Library</h1>
          <div id="statsGrid" class="page-sub"></div>
        </div>
        <div class="search-box">
          <svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/></svg>
          <input id="gSearch" data-i18n-ph="search_games_ph" placeholder="Search games" oncompositionstart="onGameSearchCompStart()" oncompositionend="onGameSearchCompEnd()" oninput="onGameSearchInput()" onkeydown="onGameSearchKeyDown(event)"/>
          <button id="gSearchClear" type="button" onclick="clearGameSearch()" style="display:none;border:none;background:var(--surface2,rgba(0,0,0,0.08));color:var(--muted,#666);width:20px;height:20px;border-radius:999px;cursor:pointer;font-size:13px;line-height:20px;padding:0;flex-shrink:0;text-align:center;" title="Clear">×</button>
          <span id="gSearchKbd" class="kbd">/</span>
        </div>
        <button class="btn primary pbtn" onclick="goUploadRoms()">
          <svg class="ico" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round"><path d="M12 19V5M5 12l7-7 7 7"/></svg>
          <span data-i18n="btn_upload_roms">Upload ROMs</span>
        </button>
      </div>

      <div style="display:none">
        <select id="gVis" onchange="loadGames(true)"><option value="all"></option><option value="visible"></option><option value="fav"></option><option value="nocover"></option><option value="hidden"></option></select>
        <select id="gPlat" onchange="loadGames(true)"><option value=""></option></select>
        <select id="gCat" onchange="loadGames(true)"><option value=""></option></select>
      </div>

      <div class="filter-bar">
        <div id="filterChips" style="display:flex;gap:7px;flex-wrap:wrap;align-items:center;"></div>
        <div class="seg">
          <button id="vmGrid" class="active" onclick="setGameView('grid')" data-i18n="view_grid">Covers</button>
          <button id="vmList" onclick="setGameView('list')" data-i18n="view_list">List</button>
        </div>
      </div>

      <div id="gamesContainer"></div>
      <div id="gamesFooter" class="list-footer"></div>
      <div id="gamesSentinel" style="height:1px;"></div>
    </section>

    <section id="tab-categories" style="display:none">
      <div class="page-top">
        <div>
          <h1 data-i18n="cat_mgr_title">Home tabs</h1>
          <div class="page-sub" data-i18n="cat_mgr_desc">Drag to reorder. Changes sync instantly.</div>
        </div>
        <div style="margin-left:auto;display:flex;gap:8px;">
          <button class="btn" onclick="loadCategories()" data-i18n="cat_refresh">Refresh</button>
          <button class="btn primary" onclick="openNewCategoryModal()" data-i18n="cat_new">New category</button>
        </div>
      </div>
      <div id="catList" class="list"></div>
    </section>

    <section id="tab-files" style="display:none">
      <div class="page-top">
        <div>
          <h1 data-i18n="tab_files">Files</h1>
          <div id="dirStorageInfo" class="page-sub"></div>
        </div>
        <div style="margin-left:auto;display:flex;gap:8px;flex-wrap:wrap;">
          <button id="mkdirBtn" class="btn" onclick="mkdirPrompt()" disabled data-i18n="files_mkdir">New folder</button>
          <label class="btn" id="pickDirUploadLbl" style="display:none">
            <span data-i18n="files_upload_dir">Upload folder</span>
            <input id="dirInput" type="file" webkitdirectory multiple style="display:none" onchange="uploadFolderFiles(this.files)"/>
          </label>
          <label class="btn primary" id="pickUploadLbl" style="display:none">
            <span data-i18n="files_upload">Upload files</span>
            <input id="fileInput" type="file" multiple style="display:none" onchange="uploadFiles(this.files)"/>
          </label>
        </div>
      </div>
      <div class="toolbar">
        <button id="upBtn" class="btn" onclick="goParent()" disabled data-i18n="files_up">Up</button>
        <select id="fileSort" onchange="renderFiles()">
          <option value="name" data-i18n="sort_name">Name</option>
          <option value="size" data-i18n="sort_size">Size</option>
          <option value="modified" data-i18n="sort_modified">Modified</option>
          <option value="type" data-i18n="sort_type">Type</option>
        </select>
        <input id="fileFilter" class="search" style="min-width:160px;" data-i18n-ph="files_filter_ph" placeholder="Filter folder…" oninput="onFileFilterInput()"/>
      </div>
      <div id="crumbsBar" class="crumbs"></div>
      <div id="dropzone" class="dropzone" style="display:none">
        <span data-i18n="dropzone_txt">Drop files or folders to upload</span>
        <div id="progWrap" class="prog"><div id="progBar"></div></div>
        <div id="progText" class="sub" style="margin-top:6px;"></div>
        <div id="progCancel" style="display:none;margin-top:8px;">
          <button class="btn sm danger" onclick="cancelWebUpload()" data-i18n="cancel_upload">Cancel</button>
        </div>
      </div>
      <div id="filesHeaderBar" style="display:none;align-items:center;gap:10px;margin-bottom:8px;">
        <label style="display:flex;align-items:center;gap:6px;font-size:13px;font-weight:600;cursor:pointer;">
          <input type="checkbox" id="chkAllFiles" onchange="toggleSelectAllFiles(this.checked)"/> <span data-i18n="files_select_all">Select all</span>
        </label>
      </div>
      <div id="filesList" class="list"></div>
      <div id="filesFooter" class="list-footer"></div>
      <div id="filesSentinel" style="height:1px;"></div>
      <div id="batchBar" class="batch-bar">
        <strong id="batchCountText">0 selected</strong>
        <button class="btn sm" onclick="openBatchTransfer('move')" data-i18n="batch_move">Move…</button>
        <button class="btn sm" onclick="openBatchTransfer('copy')" data-i18n="batch_copy">Copy…</button>
        <button class="btn sm primary" onclick="compressSelected()" data-i18n="batch_zip">Zip</button>
        <button class="btn sm danger" onclick="batchDeleteSelected()" data-i18n="batch_del">Delete</button>
        <button class="btn sm" style="margin-left:auto;" onclick="clearFileSelection()" data-i18n="batch_clear">Deselect</button>
      </div>
    </section>
  </main>
  </div>
</div>

<div id="floatDock" class="float-dock">
  <div id="toast" class="toast"></div>
  <div id="fileTaskBar" class="task-bar"></div>
</div>

<div id="modalBg" class="modal-bg" onclick="if(event.target===this)closeModal()">
  <div class="modal">
    <div class="modal-hd"><span id="mTitle" style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;"></span><button class="btn sm" onclick="closeModal()" aria-label="Close">✕</button></div>
    <div id="mBody" class="modal-bd"></div>
    <div id="mFoot" class="modal-ft"></div>
  </div>
</div>

<div id="logWin" class="log-win">
  <div id="logWinHd" class="log-win-hd">
    <span id="logWinTitle" style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;"></span>
    <span id="logLiveDot" style="color:#3fb950;font-size:11.5px;font-weight:700;" data-i18n="logs_live">● LIVE</span>
    <button id="logMinBtn" class="log-win-btn" onclick="toggleLogWinMin()">—</button>
    <button id="logCloseBtn" class="log-win-btn" onclick="closeScrapeLogsWin()">✕</button>
  </div>
  <div class="log-win-bd">
    <div class="log-win-bar">
      <button class="log-win-btn on log-f-btn" data-k="all" onclick="setLogFilter('all')" id="logFAll"></button>
      <button class="log-win-btn log-f-btn" data-k="cover" onclick="setLogFilter('cover')" id="logFCover"></button>
      <button class="log-win-btn log-f-btn" data-k="meta" onclick="setLogFilter('meta')" id="logFMeta"></button>
      <button class="log-win-btn log-f-btn" data-k="err" onclick="setLogFilter('err')" id="logFErr"></button>
      <input id="logFilterIn" class="log-win-in" type="text" oninput="renderScrapeLogsBody()"/>
      <button class="log-win-btn" id="logPauseBtn" onclick="toggleLogPause()"></button>
      <button class="log-win-btn" id="logDownloadBtn" onclick="downloadScrapeLogs()"></button>
      <button class="log-win-btn err" id="logClearBtn" onclick="clearScrapeLogs()"></button>
    </div>
    <div id="logViewerBox" style="flex:1;min-height:0;overflow:auto;background:#0D0914;border:1px solid #2A2038;border-radius:10px;padding:8px 10px;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:11.5px;line-height:1.5;color:#DDD2EA;"></div>
    <div class="log-win-ft">
      <span id="logPathTxt" style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">cache/logs/scrape.log</span>
      <span id="logCountTxt" style="color:#9E8FB2;flex-shrink:0;">0</span>
    </div>
  </div>
</div>

<script>
const I18N = {
  'zh-CN': {
    console_sub: '网页控制台', nav_more: '更多', btn_upload_roms: '上传 ROM', filter_all: '全部', filter_fav: '♥ 收藏', filter_nocover: '无封面', filter_hidden: '已隐藏', summary_fmt: '{games} 款游戏 · {plats} 个平台 · {favs} 个收藏', free_of_fmt: '可用 {free} / 共 {total}', vol_internal: '内部存储', vol_external: 'TF 卡', logs_live: '● 实时', btn_find_cover: '自动刮削', btn_pick_cover_short: '在线选图', btn_upload_short: '上传图片', connecting: '连接中…', loaded_games_fmt: '已加载 {loaded} / 共 {total} 款游戏', loaded_all_games_fmt: '已显示全部 {total} 款游戏', loading_more: '正在加载更多…', btn_load_more: '加载更多', loaded_files_fmt: '已加载 {loaded} / 共 {total} 项',
    tab_games: '游戏库',
    tab_categories: '标签分类',
    tab_files: '文件管理',
    btn_rescan: '↻ 重新扫描',
    btn_logout: '退出登录',
    search_games_ph: '搜索游戏',
    vis_all: '全部状态',
    vis_visible: '仅已显示',
    vis_fav: '仅收藏 ♥',
    vis_hidden: '仅已隐藏',
    plat_all: '全部平台',
    cat_all: '全部分类',
    view_grid: '封面网格',
    view_list: '详情列表',
    cat_mgr_title: '掌机首页标签分类管理',
    cat_mgr_desc: '拖动左侧或整行卡片即可自由排序，编辑应用、显隐、删除或新建后将自动同步到掌机。',
    cat_new: '+ 新建分类',
    cat_refresh: '↺ 刷新',
    files_up: '↩ 上级',
    sort_name: '按名称',
    sort_size: '按大小',
    sort_modified: '按修改时间',
    sort_type: '按类型',
    files_filter_ph: '过滤当前目录…',
    files_mkdir: '+ 新建文件夹',
    files_upload: '上传文件',
    files_upload_dir: '上传文件夹',
    dropzone_txt: '将 ROM 文件或整个平台文件夹拖放到此处，直接高速流式上传到当前目录',
    cancel_upload: '✕ 取消上传',
    files_select_all: '全选当前目录',
    batch_move: '移动…',
    batch_copy: '复制…',
    batch_zip: '打包 ZIP',
    batch_del: '批量删除',
    batch_clear: '取消选择',
    conn_off: 'Web 服务已断开（请在掌机端重新开启）',
    conn_lan_only: '仅局域网（无外网）',
    conn_online: '已连接 · 可联网获取封面',
    conn_restored: '掌机 Web 服务已恢复连接',
    stat_games: '游戏总数',
    stat_plats: '活跃平台',
    stat_favs: '收藏游戏',
    stat_cats: '自定义标签',
    stat_free: '可用存储',
    no_games: '没有匹配的游戏',
    hidden_tag: '已隐藏',
    fav_tag: '♥ 收藏',
    btn_detail: '详情/封面',
    btn_fav: '收藏',
    btn_unfav: '取消收藏',
    btn_hide: '隐藏',
    btn_show: '显示',
    btn_restore: '恢复',
    btn_edit: '编辑',
    btn_del: '删除',
    btn_dl: '下载',
    btn_game_detail: '游戏详情',
    btn_preview: '预览',
    btn_play: '播放',
    btn_view_edit: '查看/编辑',
    btn_view_zip: '查看压缩包',
    btn_rename: '重命名',
    btn_move: '移动',
    btn_copy: '复制',
    dir_empty: '当前目录为空',
    folder_lbl: '文件夹',
    rom_root_lbl: 'ROM 根目录',
    free_lbl: '可用 ',
    tag_zip: '压缩包',
    tag_img: '图片',
    tag_audio: '音频',
    tag_font: '字体',
    tag_text: '文本',
    cat_none: '暂无标签',
    cat_custom: '自定义分类',
    cat_sys: '系统标签 · ',
    cat_plats_pre: '包含平台: ',
    cat_manual_only: '仅手动添加游戏',
    cat_sort_pre: ' · 排序: ',
    cat_games_cnt: ' 款游戏',
    cat_follow_sys: '跟随系统语言',
    cat_custom_name: '自定义名称',
    src_web: 'Web 端',
    src_handheld: '掌机端',
    sort_Recent: '最近游玩（默认）',
    sort_TitleAsc: '名称 A → Z',
    sort_TitleDesc: '名称 Z → A',
    sort_ReleaseNewest: '发行日期 新 → 旧',
    sort_ReleaseOldest: '发行日期 旧 → 新',
    sort_ModifiedNewest: '修改时间 新 → 旧',
    sort_SizeDesc: '文件大小 大 → 小',
    sort_SizeAsc: '文件大小 小 → 大',
    page_title: 'PocketDeck · 掌机游戏库控制台',
    storage_root: '存储根目录',
    selected_n: '已选 {n} 项',
    dir_space: '可用 {free} / 共 {total}',
    no_cover: '无封面',
    no_cover_yet: '暂无封面',
    fav_on: '♥ 已收藏',
    btn_fetch_meta: '掌机刮削封面与元数据',
    btn_fetching_meta: '掌机正在刮削…',
    btn_pick_cover: '选择候选封面',
    btn_upload_cover: '上传封面',
    btn_reset_cover: '恢复默认封面',
    kv_official: '官方名称',
    kv_release: '发行时间',
    kv_genre: '游戏类型',
    kv_dev: '开发商',
    kv_pub: '发行商',
    kv_size_crc: '大小 / CRC32',
    kv_meta_src: '元数据来源',
    kv_cover_src: '封面来源',
    det_cats_title: '所属自定义标签分类',
    det_via_plat: ' (平台关联)',
    det_no_cats: '暂无自定义分类，可在「标签分类」页新建。',
    det_path: '路径/包名：',
    btn_unhide: '恢复显示',
    btn_dl_rom: '下载 ROM',
    btn_del_file: '删除文件',
    btn_close: '关闭',
    btn_cancel: '取消',
    btn_confirm: '确定',
    btn_apply: '应用',
    btn_save: '保存',
    msg_offline: '掌机当前未连接互联网，无法抓取',
    msg_meta_done: '掌机抓取完成并已回传',
    msg_meta_none: '未找到新的封面或元数据',
    msg_meta_fail: '抓取失败: ',
    msg_searching_covers: '正在通过掌机查询在线封面源…',
    msg_offline_covers: '掌机离线，无法联网抓取封面',
    msg_no_cands: '未找到匹配的候选封面',
    msg_pick_cand: '点击应用掌机抓取到的候选封面：',
    msg_applying_cover: '掌机正在下载并保存封面…',
    msg_cover_applied: '封面已应用',
    msg_cover_fail: '应用封面失败',
    msg_cover_uploaded: '自定义封面已上传',
    msg_upload_fail: '上传失败: ',
    msg_cover_reset: '已重置为默认封面',
    msg_cats_saved: '已自动保存所属分类',
    msg_fav_added: '已加入收藏 ♥',
    msg_fav_removed: '已取消收藏',
    prompt_rename_game: '修改游戏显示名称（留空恢复文件名）',
    msg_game_renamed: '已更新游戏名称',
    msg_hidden: '已隐藏',
    msg_unhidden: '已恢复显示',
    cat_new_def: '新分类 ',
    cat_edit_sys: '编辑系统标签 · ',
    cat_sys_name_lbl: '自定义显示名称（留空则自动回退系统名称）',
    cat_sort_lbl: '排序方式',
    cat_follow_lbl: '跟随系统语言自动翻译',
    cat_follow_hint: '输入自定义名称时会自动取消勾选；若勾选则掌机始终显示系统语言名称',
    cat_new_title: '新建标签分类',
    cat_edit_title: '编辑分类 · ',
    cat_name_lbl: '分类名称（1~40 字符）',
    cat_bind_lbl: '绑定平台（合集包含以下平台的全部游戏）',
    cat_plat_ph: '筛选平台…',
    cat_name_empty: '分类名称不能为空',
    cat_del_confirm: '确认删除自定义分类「{name}」吗？（不会删除任何 ROM 文件）',
    cat_validate_fail: '校验失败: ',
    cat_synced: '已通过安全校验并同步到掌机',
    task_compress: '正在打包 ZIP',
    task_extract: '正在解压',
    task_copy: '正在复制',
    task_move: '正在移动',
    task_delete: '正在删除',
    task_upload: '正在上传',
    task_other: '正在处理',
    task_cancelled: '已取消：',
    task_fail: '操作失败（{title}）：{err}',
    task_unknown_err: '未知错误',
    task_done_compress: '打包完成：已创建 ',
    task_done_extract: '解压完成（{count} 个文件 → {name}）',
    task_done_copy: '复制完成（{count} 项）',
    task_done_move: '移动完成（{count} 项）',
    task_done_delete: '删除完成（{count} 项）',
    task_done_other: ' 已完成',
    task_processed: '已处理 ',
    task_processing: '处理中…',
    prompt_mkdir: '新建文件夹名称',
    msg_mkdir_ok: '文件夹已创建',
    msg_mkdir_fail: '创建失败',
    prompt_rename_file: '重命名（可修改扩展名或文件夹名）',
    msg_renamed: '已重命名',
    msg_rename_fail: '重命名失败',
    confirm_del_dir: '确认递归永久删除文件夹「{name}」及其全部内容吗？此操作不可恢复！',
    confirm_del_file: '确认永久删除「{name}」吗？此操作不可恢复！',
    msg_deleted: '已删除',
    msg_del_fail: '删除失败',
    n_items: '{n} 个项目',
    confirm_batch_del: '确认永久删除选中的 {n} 项（含文件夹内所有文件）吗？此操作不可恢复！',
    msg_batch_del_fail: '批量删除失败: ',
    prompt_zip_name: '输入生成的 ZIP 文件名',
    msg_zip_fail: '打包失败: ',
    arc_title: '压缩包 · ',
    arc_parsing: '正在解析压缩包内容…',
    arc_extract_here: '解压到当前目录',
    arc_extract_dir: '解压到同名文件夹',
    arc_parse_fail: '解析失败: ',
    arc_count: '共 {n} 个条目',
    msg_extract_fail: '解压失败: ',
    img_title: '图片预览 · ',
    btn_dl_orig: '下载原图',
    audio_title: '音频播放 · ',
    btn_dl_audio: '下载音频',
    txt_read_fail: '无法读取文本: ',
    txt_title: '编辑文本 · ',
    msg_file_saved: '已保存文件',
    msg_save_fail: '保存失败',
    tx_copy_title: '复制「{name}」到…',
    tx_move_title: '移动「{name}」到…',
    tx_mkdir_here: '+ 在此处新建文件夹',
    tx_target_dir: '目标目录：',
    tx_pick_root: '根目录列表（请选择一个根目录）',
    tx_no_subdirs: '此目录下无子文件夹',
    tx_copy_here: '复制到此处',
    tx_move_here: '移动到此处',
    msg_op_fail: '操作失败: ',
    msg_rescan_ok: '已触发掌机重新扫描',
    msg_upload_busy: '已有上传任务正在进行',
    msg_upload_cancelled: '已取消上传',
    msg_upload_done: '上传完成（{n} 个文件）',
    uploading_pct: '正在上传 {i}/{total}：{name} · {pct}%',
    btn_logs: '刮削日志',
    logs_title: '刮削请求日志（实时）',
    logs_empty: '暂无请求日志记录',
    logs_pause: '⏸ 暂停刷新',
    logs_resume: '▶ 继续实时',
    logs_clear: '清空日志',
    logs_download: '⬇ 下载',
    logs_cleared: '已清空日志',
    logs_filter_ph: '过滤关键词…',
    logs_f_all: '全部',
    logs_f_cover: '封面',
    logs_f_meta: '元数据',
    logs_f_err: '仅报错',
  },
  'zh-TW': {
    console_sub: '網頁主控台', nav_more: '更多', btn_upload_roms: '上傳 ROM', filter_all: '全部', filter_fav: '♥ 收藏', filter_nocover: '無封面', filter_hidden: '已隱藏', summary_fmt: '{games} 款遊戲 · {plats} 個平台 · {favs} 個收藏', free_of_fmt: '可用 {free} / 共 {total}', vol_internal: '內部儲存', vol_external: 'TF 卡', logs_live: '● 即時', btn_find_cover: '自動刮削', btn_pick_cover_short: '線上選圖', btn_upload_short: '上傳圖片', connecting: '連線中…', loaded_games_fmt: '已載入 {loaded} / 共 {total} 款遊戲', loaded_all_games_fmt: '已顯示全部 {total} 款遊戲', loading_more: '正在載入更多…', btn_load_more: '載入更多', loaded_files_fmt: '已載入 {loaded} / 共 {total} 項',
    tab_games: '遊戲庫',
    tab_categories: '標籤分類',
    tab_files: '檔案管理',
    btn_rescan: '↻ 重新掃描',
    btn_logout: '登出',
    search_games_ph: '搜尋遊戲',
    vis_all: '全部狀態',
    vis_visible: '僅已顯示',
    vis_fav: '僅收藏 ♥',
    vis_hidden: '僅已隱藏',
    plat_all: '全部平台',
    cat_all: '全部分類',
    view_grid: '封面網格',
    view_list: '詳細列表',
    cat_mgr_title: '掌機首頁標籤分類管理',
    cat_mgr_desc: '拖動左側或整列卡片即可自由排序，編輯套用、顯隱、刪除或新建後將自動同步至掌機。',
    cat_new: '+ 新建分類',
    cat_refresh: '↺ 重新整理',
    files_up: '↩ 上層',
    sort_name: '依名稱',
    sort_size: '依大小',
    sort_modified: '依修改時間',
    sort_type: '依類型',
    files_filter_ph: '過濾目前目錄…',
    files_mkdir: '+ 新建資料夾',
    files_upload: '上傳檔案',
    files_upload_dir: '上傳資料夾',
    dropzone_txt: '將 ROM 檔案或整個平台資料夾拖放至此，直接高速串流上傳到目前目錄',
    cancel_upload: '✕ 取消上傳',
    files_select_all: '全選目前目錄',
    batch_move: '移動…',
    batch_copy: '複製…',
    batch_zip: '打包 ZIP',
    batch_del: '批次刪除',
    batch_clear: '取消選取',
    conn_off: 'Web 服務已中斷（請在掌機端重新開啟）',
    conn_lan_only: '僅區域網路（無外網）',
    conn_online: '已連線 · 可聯網取得封面',
    conn_restored: '掌機 Web 服務已恢復連線',
    stat_games: '遊戲總數',
    stat_plats: '活躍平台',
    stat_favs: '收藏遊戲',
    stat_cats: '自訂標籤',
    stat_free: '可用空間',
    no_games: '沒有符合的遊戲',
    hidden_tag: '已隱藏',
    fav_tag: '♥ 收藏',
    btn_detail: '詳情/封面',
    btn_fav: '收藏',
    btn_unfav: '取消收藏',
    btn_hide: '隱藏',
    btn_show: '顯示',
    btn_restore: '恢復',
    btn_edit: '編輯',
    btn_del: '刪除',
    btn_dl: '下載',
    btn_game_detail: '遊戲詳情',
    btn_preview: '預覽',
    btn_play: '播放',
    btn_view_edit: '檢視/編輯',
    btn_view_zip: '檢視壓縮檔',
    btn_rename: '重新命名',
    btn_move: '移動',
    btn_copy: '複製',
    dir_empty: '目前目錄為空',
    folder_lbl: '資料夾',
    rom_root_lbl: 'ROM 根目錄',
    free_lbl: '可用 ',
    tag_zip: '壓縮檔',
    tag_img: '圖片',
    tag_audio: '音訊',
    tag_font: '字體',
    tag_text: '文字',
    cat_none: '暫無標籤',
    cat_custom: '自訂分類',
    cat_sys: '系統標籤 · ',
    cat_plats_pre: '包含平台: ',
    cat_manual_only: '僅手動加入遊戲',
    cat_sort_pre: ' · 排序: ',
    cat_games_cnt: ' 款遊戲',
    cat_follow_sys: '跟隨系統語言',
    cat_custom_name: '自訂名稱',
    src_web: 'Web 端',
    src_handheld: '掌機端',
    sort_Recent: '最近遊玩（預設）',
    sort_TitleAsc: '名稱 A → Z',
    sort_TitleDesc: '名稱 Z → A',
    sort_ReleaseNewest: '發行日期 新 → 舊',
    sort_ReleaseOldest: '發行日期 舊 → 新',
    sort_ModifiedNewest: '修改時間 新 → 舊',
    sort_SizeDesc: '檔案大小 大 → 小',
    sort_SizeAsc: '檔案大小 小 → 大',
    page_title: 'PocketDeck · 掌機遊戲庫控制台',
    storage_root: '儲存根目錄',
    selected_n: '已選 {n} 項',
    dir_space: '可用 {free} / 共 {total}',
    no_cover: '無封面',
    no_cover_yet: '暫無封面',
    fav_on: '♥ 已收藏',
    btn_fetch_meta: '掌機刮削封面與中繼資料',
    btn_fetching_meta: '掌機正在刮削…',
    btn_pick_cover: '選擇候選封面',
    btn_upload_cover: '上傳封面',
    btn_reset_cover: '恢復預設封面',
    kv_official: '官方名稱',
    kv_release: '發行時間',
    kv_genre: '遊戲類型',
    kv_dev: '開發商',
    kv_pub: '發行商',
    kv_size_crc: '大小 / CRC32',
    kv_meta_src: '中繼資料來源',
    kv_cover_src: '封面來源',
    det_cats_title: '所屬自訂標籤分類',
    det_via_plat: ' (平台關聯)',
    det_no_cats: '暫無自訂分類，可在「標籤分類」頁新建。',
    det_path: '路徑/套件名：',
    btn_unhide: '恢復顯示',
    btn_dl_rom: '下載 ROM',
    btn_del_file: '刪除檔案',
    btn_close: '關閉',
    btn_cancel: '取消',
    btn_confirm: '確定',
    btn_apply: '套用',
    btn_save: '儲存',
    msg_offline: '掌機目前未連線網際網路，無法抓取',
    msg_meta_done: '掌機抓取完成並已回傳',
    msg_meta_none: '未找到新的封面或中繼資料',
    msg_meta_fail: '抓取失敗: ',
    msg_searching_covers: '正在透過掌機查詢線上封面來源…',
    msg_offline_covers: '掌機離線，無法聯網抓取封面',
    msg_no_cands: '未找到符合的候選封面',
    msg_pick_cand: '點擊套用掌機抓取到的候選封面：',
    msg_applying_cover: '掌機正在下載並儲存封面…',
    msg_cover_applied: '封面已套用',
    msg_cover_fail: '套用封面失敗',
    msg_cover_uploaded: '自訂封面已上傳',
    msg_upload_fail: '上傳失敗: ',
    msg_cover_reset: '已重設為預設封面',
    msg_cats_saved: '已自動儲存所屬分類',
    msg_fav_added: '已加入收藏 ♥',
    msg_fav_removed: '已取消收藏',
    prompt_rename_game: '修改遊戲顯示名稱（留空恢復檔名）',
    msg_game_renamed: '已更新遊戲名稱',
    msg_hidden: '已隱藏',
    msg_unhidden: '已恢復顯示',
    cat_new_def: '新分類 ',
    cat_edit_sys: '編輯系統標籤 · ',
    cat_sys_name_lbl: '自訂顯示名稱（留空則自動退回系統名稱）',
    cat_sort_lbl: '排序方式',
    cat_follow_lbl: '跟隨系統語言自動翻譯',
    cat_follow_hint: '輸入自訂名稱時會自動取消勾選；若勾選則掌機始終顯示系統語言名稱',
    cat_new_title: '新建標籤分類',
    cat_edit_title: '編輯分類 · ',
    cat_name_lbl: '分類名稱（1~40 字元）',
    cat_bind_lbl: '綁定平台（合集包含以下平台的全部遊戲）',
    cat_plat_ph: '篩選平台…',
    cat_name_empty: '分類名稱不能為空',
    cat_del_confirm: '確認刪除自訂分類「{name}」嗎？（不會刪除任何 ROM 檔案）',
    cat_validate_fail: '校驗失敗: ',
    cat_synced: '已通過安全校驗並同步至掌機',
    task_compress: '正在打包 ZIP',
    task_extract: '正在解壓縮',
    task_copy: '正在複製',
    task_move: '正在移動',
    task_delete: '正在刪除',
    task_upload: '正在上傳',
    task_other: '正在處理',
    task_cancelled: '已取消：',
    task_fail: '操作失敗（{title}）：{err}',
    task_unknown_err: '未知錯誤',
    task_done_compress: '打包完成：已建立 ',
    task_done_extract: '解壓縮完成（{count} 個檔案 → {name}）',
    task_done_copy: '複製完成（{count} 項）',
    task_done_move: '移動完成（{count} 項）',
    task_done_delete: '刪除完成（{count} 項）',
    task_done_other: ' 已完成',
    task_processed: '已處理 ',
    task_processing: '處理中…',
    prompt_mkdir: '新建資料夾名稱',
    msg_mkdir_ok: '資料夾已建立',
    msg_mkdir_fail: '建立失敗',
    prompt_rename_file: '重新命名（可修改副檔名或資料夾名）',
    msg_renamed: '已重新命名',
    msg_rename_fail: '重新命名失敗',
    confirm_del_dir: '確認遞迴永久刪除資料夾「{name}」及其全部內容嗎？此操作無法恢復！',
    confirm_del_file: '確認永久刪除「{name}」嗎？此操作無法恢復！',
    msg_deleted: '已刪除',
    msg_del_fail: '刪除失敗',
    n_items: '{n} 個項目',
    confirm_batch_del: '確認永久刪除選取的 {n} 項（含資料夾內所有檔案）嗎？此操作無法恢復！',
    msg_batch_del_fail: '批次刪除失敗: ',
    prompt_zip_name: '輸入產生的 ZIP 檔名',
    msg_zip_fail: '打包失敗: ',
    arc_title: '壓縮檔 · ',
    arc_parsing: '正在解析壓縮檔內容…',
    arc_extract_here: '解壓縮到目前目錄',
    arc_extract_dir: '解壓縮到同名資料夾',
    arc_parse_fail: '解析失敗: ',
    arc_count: '共 {n} 個項目',
    msg_extract_fail: '解壓縮失敗: ',
    img_title: '圖片預覽 · ',
    btn_dl_orig: '下載原圖',
    audio_title: '音訊播放 · ',
    btn_dl_audio: '下載音訊',
    txt_read_fail: '無法讀取文字: ',
    txt_title: '編輯文字 · ',
    msg_file_saved: '已儲存檔案',
    msg_save_fail: '儲存失敗',
    tx_copy_title: '複製「{name}」到…',
    tx_move_title: '移動「{name}」到…',
    tx_mkdir_here: '+ 在此處新建資料夾',
    tx_target_dir: '目標目錄：',
    tx_pick_root: '根目錄列表（請選擇一個根目錄）',
    tx_no_subdirs: '此目錄下無子資料夾',
    tx_copy_here: '複製到此處',
    tx_move_here: '移動到此處',
    msg_op_fail: '操作失敗: ',
    msg_rescan_ok: '已觸發掌機重新掃描',
    msg_upload_busy: '已有上傳任務正在進行',
    msg_upload_cancelled: '已取消上傳',
    msg_upload_done: '上傳完成（{n} 個檔案）',
    uploading_pct: '正在上傳 {i}/{total}：{name} · {pct}%',
    btn_logs: '刮削日誌',
    logs_title: '刮削請求日誌（即時）',
    logs_empty: '暫無請求日誌記錄',
    logs_pause: '⏸ 暫停重新整理',
    logs_resume: '▶ 繼續即時',
    logs_clear: '清空日誌',
    logs_download: '⬇ 下載',
    logs_cleared: '已清空日誌',
    logs_filter_ph: '過濾關鍵字…',
    logs_f_all: '全部',
    logs_f_cover: '封面',
    logs_f_meta: '中繼資料',
    logs_f_err: '僅報錯',
  },
  en: {
    console_sub: 'Web Console', nav_more: 'More', btn_upload_roms: 'Upload ROMs', filter_all: 'All', filter_fav: '♥ Favorites', filter_nocover: 'No cover', filter_hidden: 'Hidden', summary_fmt: '{games} games · {plats} platforms · {favs} favorites', free_of_fmt: '{free} free of {total}', vol_internal: 'Internal', vol_external: 'SD / TF Card', logs_live: '● LIVE', btn_find_cover: 'Auto Scrape', btn_pick_cover_short: 'Pick Art', btn_upload_short: 'Upload', connecting: 'Connecting…', loaded_games_fmt: 'Showing {loaded} of {total} games', loaded_all_games_fmt: 'All {total} games loaded', loading_more: 'Loading more…', btn_load_more: 'Load more', loaded_files_fmt: 'Showing {loaded} of {total} items',
    tab_games: 'Library',
    tab_categories: 'Tabs & Tags',
    tab_files: 'Files',
    btn_rescan: 'Rescan',
    btn_logout: 'Log Out',
    search_games_ph: 'Search games',
    vis_all: 'All Status',
    vis_visible: 'Visible Only',
    vis_fav: 'Favorites ♥',
    vis_hidden: 'Hidden Only',
    plat_all: 'All Platforms',
    cat_all: 'All Categories',
    view_grid: 'Covers',
    view_list: 'List',
    cat_mgr_title: 'Home tabs',
    cat_mgr_desc: 'Drag cards to reorder. Edits, visibility, deletions, and new categories sync automatically.',
    cat_new: 'New category',
    cat_refresh: 'Refresh',
    files_up: 'Up',
    sort_name: 'Name',
    sort_size: 'Size',
    sort_modified: 'Modified',
    sort_type: 'Type',
    files_filter_ph: 'Filter current folder…',
    files_mkdir: 'New folder',
    files_upload: 'Upload Files',
    files_upload_dir: 'Upload Folder',
    dropzone_txt: 'Drag & drop ROM files or platform folders here to stream directly to this directory',
    cancel_upload: 'Cancel',
    files_select_all: 'Select all',
    batch_move: 'Move…',
    batch_copy: 'Copy…',
    batch_zip: 'Zip',
    batch_del: 'Delete',
    batch_clear: 'Deselect',
    conn_off: 'Disconnected',
    conn_lan_only: 'LAN only (offline)',
    conn_online: 'Connected',
    conn_restored: 'Handheld Web service reconnected',
    stat_games: 'Total Games',
    stat_plats: 'Platforms',
    stat_favs: 'Favorites',
    stat_cats: 'Custom Tabs',
    stat_free: 'Free Space',
    no_games: 'No matching games',
    hidden_tag: 'Hidden',
    fav_tag: '♥ Favorite',
    btn_detail: 'Details/Cover',
    btn_fav: 'Favorite',
    btn_unfav: 'Unfavorite',
    btn_hide: 'Hide',
    btn_show: 'Show',
    btn_restore: 'Unhide',
    btn_edit: 'Edit',
    btn_del: 'Delete',
    btn_dl: 'Download',
    btn_game_detail: 'Game Details',
    btn_preview: 'Preview',
    btn_play: 'Play',
    btn_view_edit: 'View/Edit',
    btn_view_zip: 'Inspect Archive',
    btn_rename: 'Rename',
    btn_move: 'Move',
    btn_copy: 'Copy',
    dir_empty: 'Folder is empty',
    folder_lbl: 'Folder',
    rom_root_lbl: 'ROM Root',
    free_lbl: 'Free ',
    tag_zip: 'Archive',
    tag_img: 'Image',
    tag_audio: 'Audio',
    tag_font: 'Font',
    tag_text: 'Text',
    cat_none: 'No categories',
    cat_custom: 'Custom',
    cat_sys: 'System · ',
    cat_plats_pre: 'Platforms: ',
    cat_manual_only: 'Manual games only',
    cat_sort_pre: ' · Sort: ',
    cat_games_cnt: ' games',
    cat_follow_sys: 'Follow system language',
    cat_custom_name: 'Custom name',
    src_web: 'Web',
    src_handheld: 'Handheld',
    sort_Recent: 'Recent (Default)',
    sort_TitleAsc: 'Title A → Z',
    sort_TitleDesc: 'Title Z → A',
    sort_ReleaseNewest: 'Release Newest',
    sort_ReleaseOldest: 'Release Oldest',
    sort_ModifiedNewest: 'Modified Newest',
    sort_SizeDesc: 'Size Largest',
    sort_SizeAsc: 'Size Smallest',
    page_title: 'PocketDeck',
    storage_root: 'Storage Roots',
    selected_n: '{n} selected',
    dir_space: 'Free {free} / Total {total}',
    no_cover: 'No cover',
    no_cover_yet: 'No cover yet',
    fav_on: '♥ Favorited',
    btn_fetch_meta: 'Find cover',
    btn_fetching_meta: 'Searching…',
    btn_pick_cover: 'Pick cover',
    btn_upload_cover: 'Upload',
    btn_reset_cover: 'Reset',
    kv_official: 'Official Title',
    kv_release: 'Release Date',
    kv_genre: 'Genre',
    kv_dev: 'Developer',
    kv_pub: 'Publisher',
    kv_size_crc: 'Size / CRC32',
    kv_meta_src: 'Metadata Source',
    kv_cover_src: 'Cover Source',
    det_cats_title: 'Custom Categories',
    det_via_plat: ' (Platform linked)',
    det_no_cats: 'No custom categories yet. Create one in Categories.',
    det_path: 'Path / Package: ',
    btn_unhide: 'Unhide',
    btn_dl_rom: 'Download ROM',
    btn_del_file: 'Delete File',
    btn_close: 'Close',
    btn_cancel: 'Cancel',
    btn_confirm: 'Confirm',
    btn_apply: 'Apply',
    btn_save: 'Save',
    msg_offline: 'Handheld is offline; cannot fetch',
    msg_meta_done: 'Fetched and synced from handheld',
    msg_meta_none: 'No new cover or metadata found',
    msg_meta_fail: 'Fetch failed: ',
    msg_searching_covers: 'Searching online cover sources via handheld…',
    msg_offline_covers: 'Handheld is offline; cannot fetch covers',
    msg_no_cands: 'No matching candidate covers found',
    msg_pick_cand: 'Click a candidate cover to apply:',
    msg_applying_cover: 'Downloading and saving cover on handheld…',
    msg_cover_applied: 'Cover applied',
    msg_cover_fail: 'Failed to apply cover',
    msg_cover_uploaded: 'Custom cover uploaded',
    msg_upload_fail: 'Upload failed: ',
    msg_cover_reset: 'Reset to default cover',
    msg_cats_saved: 'Categories saved automatically',
    msg_fav_added: 'Added to favorites ♥',
    msg_fav_removed: 'Removed from favorites',
    prompt_rename_game: 'Rename game (leave blank to restore filename)',
    msg_game_renamed: 'Game title updated',
    msg_hidden: 'Hidden',
    msg_unhidden: 'Unhidden',
    cat_new_def: 'New Category ',
    cat_edit_sys: 'Edit System Tab · ',
    cat_sys_name_lbl: 'Custom Display Name (blank to use system default)',
    cat_sort_lbl: 'Sort Order',
    cat_follow_lbl: 'Follow system language',
    cat_follow_hint: 'Typing a custom name unchecks this; when checked, handheld always shows system language',
    cat_new_title: 'New Category',
    cat_edit_title: 'Edit Category · ',
    cat_name_lbl: 'Category Name (1–40 chars)',
    cat_bind_lbl: 'Linked Platforms (includes all games from checked platforms)',
    cat_plat_ph: 'Filter platforms…',
    cat_name_empty: 'Category name cannot be empty',
    cat_del_confirm: 'Delete custom category "{name}"? (No ROM files will be deleted)',
    cat_validate_fail: 'Validation failed: ',
    cat_synced: 'Validated and synced to handheld',
    task_compress: 'Creating ZIP',
    task_extract: 'Extracting',
    task_copy: 'Copying',
    task_move: 'Moving',
    task_delete: 'Deleting',
    task_upload: 'Uploading',
    task_other: 'Processing',
    task_cancelled: 'Cancelled: ',
    task_fail: 'Failed ({title}): {err}',
    task_unknown_err: 'Unknown error',
    task_done_compress: 'ZIP created: ',
    task_done_extract: 'Extracted ({count} files → {name})',
    task_done_copy: 'Copied ({count} items)',
    task_done_move: 'Moved ({count} items)',
    task_done_delete: 'Deleted ({count} items)',
    task_done_other: ' completed',
    task_processed: 'Processed ',
    task_processing: 'Processing…',
    prompt_mkdir: 'New folder name',
    msg_mkdir_ok: 'Folder created',
    msg_mkdir_fail: 'Create failed',
    prompt_rename_file: 'Rename (extension or folder name)',
    msg_renamed: 'Renamed',
    msg_rename_fail: 'Rename failed',
    confirm_del_dir: 'Permanently delete folder "{name}" and all its contents? This cannot be undone!',
    confirm_del_file: 'Permanently delete "{name}"? This cannot be undone!',
    msg_deleted: 'Deleted',
    msg_del_fail: 'Delete failed',
    n_items: '{n} items',
    confirm_batch_del: 'Permanently delete {n} selected items (including folder contents)? This cannot be undone!',
    msg_batch_del_fail: 'Batch delete failed: ',
    prompt_zip_name: 'Enter ZIP filename',
    msg_zip_fail: 'ZIP failed: ',
    arc_title: 'Archive · ',
    arc_parsing: 'Reading archive entries…',
    arc_extract_here: 'Extract Here',
    arc_extract_dir: 'Extract to Folder',
    arc_parse_fail: 'Failed to read archive: ',
    arc_count: '{n} entries',
    msg_extract_fail: 'Extract failed: ',
    img_title: 'Image Preview · ',
    btn_dl_orig: 'Download Original',
    audio_title: 'Audio Player · ',
    btn_dl_audio: 'Download Audio',
    txt_read_fail: 'Failed to read text: ',
    txt_title: 'Edit Text · ',
    msg_file_saved: 'File saved',
    msg_save_fail: 'Save failed',
    tx_copy_title: 'Copy "{name}" to…',
    tx_move_title: 'Move "{name}" to…',
    tx_mkdir_here: '+ New Folder Here',
    tx_target_dir: 'Destination: ',
    tx_pick_root: 'Storage Roots (pick a root folder)',
    tx_no_subdirs: 'No subfolders here',
    tx_copy_here: 'Copy Here',
    tx_move_here: 'Move Here',
    msg_op_fail: 'Operation failed: ',
    msg_rescan_ok: 'Rescan triggered on handheld',
    msg_upload_busy: 'An upload is already in progress',
    msg_upload_cancelled: 'Upload cancelled',
    msg_upload_done: 'Upload complete ({n} files)',
    uploading_pct: 'Uploading {i}/{total}: {name} · {pct}%',
    btn_logs: 'Logs',
    logs_title: 'Logs',
    logs_empty: 'No request logs recorded yet',
    logs_pause: '⏸ Pause',
    logs_resume: 'Resume',
    logs_clear: 'Clear Logs',
    logs_download: '⬇ Download',
    logs_cleared: 'Logs cleared',
    logs_filter_ph: 'Filter…',
    logs_f_all: 'All',
    logs_f_cover: 'Cover',
    logs_f_meta: 'Metadata',
    logs_f_err: 'Errors',
  }
};
function detectWebLang() {
  const saved = localStorage.getItem('pd_web_lang');
  if (saved && I18N[saved]) return saved;
  const nav = (navigator.language || '').toLowerCase();
  if (nav.startsWith('zh')) {
    return (nav.includes('tw') || nav.includes('hk') || nav.includes('mo') || nav.includes('hant')) ? 'zh-TW' : 'zh-CN';
  }
  return 'en';
}
let curLang = detectWebLang();
function tr(k, vars) {
  const dict = I18N[curLang] || I18N['zh-CN'];
  let s = dict[k] || I18N['zh-CN'][k] || k;
  if (vars) {
    for (const [vk, vv] of Object.entries(vars)) {
      s = s.replaceAll('{' + vk + '}', String(vv));
    }
  }
  return s;
}
function sortLabel(key) {
  return tr('sort_' + key) || key;
}
function applyStaticI18n() {
  document.documentElement.lang = curLang;
  document.title = tr('page_title');
  const sel = document.getElementById('langSel');
  if (sel) sel.value = curLang;
  document.querySelectorAll('[data-i18n]').forEach(n => {
    const k = n.getAttribute('data-i18n');
    if (k) n.textContent = tr(k);
  });
  document.querySelectorAll('[data-i18n-ph]').forEach(n => {
    const k = n.getAttribute('data-i18n-ph');
    if (k) n.placeholder = tr(k);
  });
}
function setWebLang(lang) {
  if (!I18N[lang]) return;
  curLang = lang;
  localStorage.setItem('pd_web_lang', lang);
  applyStaticI18n();
  renderDevInfo(webConnected, deviceInfo.online);
  renderStats();
  renderGames();
  renderCategories();
  renderFiles();
  updateBatchBar();
  if (curTab === 'files') loadFiles(curDir, true, true);
  const lw = el('logWin');
  if (lw && lw.classList.contains('open')) {
    el('logWinTitle').textContent = tr('logs_title');
    el('logFAll').textContent = tr('logs_f_all');
    el('logFCover').textContent = tr('logs_f_cover');
    el('logFMeta').textContent = tr('logs_f_meta');
    el('logFErr').textContent = tr('logs_f_err');
    el('logFilterIn').placeholder = tr('logs_filter_ph');
    el('logDownloadBtn').textContent = tr('logs_download');
    el('logClearBtn').textContent = tr('logs_clear');
    el('logPauseBtn').textContent = tr(logPollPaused ? 'logs_resume' : 'logs_pause');
  }
}
applyStaticI18n();
const GAME_PAGE_SIZE = 60;
const FILE_BATCH_SIZE = 100;
let games = [];
let gamesById = Object.create(null);
let gamesTotal = 0;
let gamesHasMore = false;
let gamesLoading = false;
let gamesReqSeq = 0;
let gamesStats = { totalGames: 0, platformCount: 0, favCount: 0, hasHidden: false, platforms: [] };
let gSearchTimer = null;
let gamesObserver = null;
let filteredFileEntries = [];
let filesRenderedCount = 0;
let fileFilterTimer = null;
let filesObserver = null;
let roots = [];
let deviceInfo = {};
let curDir = '';
let parentDir = '';
let curEntries = [];
let selectedPaths = new Set();
let gameViewMode = localStorage.getItem('pd_web_gview') || 'grid';

let catTabs = [];
let catPlatforms = [];
let catSortOrders = [];
let catDirty = false;

function el(id){ return document.getElementById(id); }
function fmtBytes(n) {
  if (!n || n <= 0) return '0 B';
  const u = ['B','KB','MB','GB','TB'];
  let i = 0, v = n;
  while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
  return (i === 0 ? v : v.toFixed(1)) + ' ' + u[i];
}
function toast(msg) {
  const t = el('toast');
  t.textContent = msg; t.style.display = 'block';
  clearTimeout(t._t); t._t = setTimeout(() => t.style.display = 'none', 2800);
}
async function api(url, opts) {
  const r = await fetch(url, opts);
  if (r.status === 401) { location.reload(); throw new Error('unauthorized'); }
  if (r.ok) {
    lastAliveAt = Date.now();
    hbFailCount = 0;
  }
  return r;
}

function applyThemeObj(t){
  if(!t) return;
  const r = document.documentElement;
  r.dataset.dark = t.isDark ? '1' : '0';
  const bs = t.brandStart || t.glow, bm = t.brandMid || t.primary, be = t.brandEnd || t.tertiary;
  r.style.setProperty('--accent', t.primary);
  r.style.setProperty('--glow', t.glow);
  r.style.setProperty('--tertiary', t.tertiary);
  r.style.setProperty('--brand-start', bs);
  r.style.setProperty('--brand-mid', bm);
  r.style.setProperty('--brand-end', be);
  r.style.setProperty('--brand', 'linear-gradient(120deg,' + bs + ' 0%,' + bm + ' 48%,' + be + ' 100%)');
  r.style.setProperty('--bg', t.bg);
  r.style.setProperty('--panel', t.panel);
  r.style.setProperty('--surf', t.surf);
  r.style.setProperty('--surf-alt', t.surfaceAlt || t.surf);
  r.style.setProperty('--border', t.line || t.border);
  r.style.setProperty('--line', t.line || t.border);
  r.style.setProperty('--text', t.text);
  r.style.setProperty('--muted', t.muted);
  r.style.setProperty('--label', t.label || t.muted);
  r.style.setProperty('--accent-soft', t.accentSoft || 'rgba(232,62,108,0.12)');
  if(t.flowA) r.style.setProperty('--flow-a', t.flowA);
  if(t.flowB) r.style.setProperty('--flow-b', t.flowB);
  if(t.flowC) r.style.setProperty('--flow-c', t.flowC);
  r.style.setProperty('--side-bg', t.isDark ? 'rgba(35,26,46,.68)' : 'rgba(255,255,255,.62)');
  r.style.setProperty('--shadow', t.isDark ? '0 8px 24px rgba(0,0,0,.45)' : '0 6px 18px rgba(120,40,70,.09),0 1px 3px rgba(120,40,70,.06)');
  r.style.setProperty('--input-bg', t.isDark ? '#231A2E' : '#FFFFFF');
}
let webConnected = true;
function renderDevInfo(connected, hasInternet) {
  webConnected = connected;
  const nameEl = el('devName');
  if (nameEl) nameEl.textContent = deviceInfo.deviceName || 'PocketDeck';
  const box = el('devInfo');
  if (box) {
    const net = [deviceInfo.interface || ''].filter(Boolean).join(' · ');
    if (!connected) {
      box.innerHTML = '<span class="conn-dot off"></span><span class="conn-off-txt">' + esc(tr('conn_off')) + '</span>' + (net ? ' · ' + esc(net) : '');
    } else {
      const netTxt = (hasInternet === false) ? tr('conn_lan_only') : tr('conn_online');
      box.innerHTML = '<span class="conn-dot ok"></span><strong style="color:var(--ok)">' + esc(netTxt) + '</strong>' + (net ? ' · ' + esc(net) : '');
    }
  }
  const vols = (deviceInfo.storageVolumes && deviceInfo.storageVolumes.length)
    ? deviceInfo.storageVolumes
    : (deviceInfo.storage || []);
  const listEl = el('devStorageList');
  if (listEl && vols.length) {
    const showLabel = vols.length > 1;
    listEl.innerHTML = vols.map((v, idx) => {
      const total = v.totalBytes || 0;
      const free = v.freeBytes || 0;
      const pct = total > 0 ? Math.min(100, Math.max(4, Math.round(((total - free) / total) * 100))) : 35;
      const lbl = v.kind === 'internal'
        ? tr('vol_internal')
        : (v.label && v.label !== 'TF / SD' ? tr('vol_external') + ' · ' + v.label : tr('vol_external'));
      const txt = total > 0
        ? tr('free_of_fmt', { free: fmtBytes(free), total: fmtBytes(total) })
        : fmtBytes(free);
      return '<div style="' + (idx > 0 ? 'margin-top:8px;' : '') + '">' +
        (showLabel ? '<div class="dev-store" style="color:var(--text);font-weight:700;margin-bottom:4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="' + esc(v.path || '') + '">' + esc(lbl) + '</div>' : '') +
        '<div class="dev-bar" style="margin-bottom:4px;"><i style="width:' + pct + '%"></i></div>' +
        '<div class="dev-store">' + esc(txt) + '</div>' +
      '</div>';
    }).join('');
  }
}
function toggleMoreMenu(e) {
  e.stopPropagation();
  const m = el('morePop');
  if (m) m.classList.toggle('open');
}
function closeMoreMenu() {
  const m = el('morePop');
  if (m) m.classList.remove('open');
}
document.addEventListener('click', closeMoreMenu);
document.addEventListener('keydown', e => {
  if (e.key === '/' && document.activeElement.tagName !== 'INPUT' && document.activeElement.tagName !== 'TEXTAREA') {
    const gs = el('gSearch');
    if (gs && curTab === 'games') { e.preventDefault(); gs.focus(); }
  }
  if (e.key === 'Escape') closeModal();
});
function switchNavTab(t) {
  document.querySelectorAll('.side .nav-item[data-tab]').forEach(x => x.classList.toggle('active', x.dataset.tab === t));
  curTab = t;
  updateBatchBar();
  el('tab-games').style.display = t === 'games' ? '' : 'none';
  el('tab-categories').style.display = t === 'categories' ? '' : 'none';
  el('tab-files').style.display = t === 'files' ? '' : 'none';
  if (t === 'files' && !curDir) loadFiles('');
  if (t === 'categories' && !catTabs.length) loadCategories();
}
function goUploadRoms() {
  switchNavTab('files');
  setTimeout(() => {
    const fi = el('fileInput');
    if (fi && curDir) fi.click();
  }, 260);
}
let curTab = 'games';
let lastKnownFilesRev = -1;
let lastTaskEventSeq = 0;
let hadActiveTasks = false;
let taskPollTimer = null;

function fmtDuration(ms) {
  const s = Math.max(0, Math.floor(ms / 1000));
  if (s < 60) return s + 's';
  const m = Math.floor(s / 60);
  return m + 'm' + (s % 60) + 's';
}
function taskKindLabel(kind) {
  if (kind === 'compress') return tr('task_compress');
  if (kind === 'extract') return tr('task_extract');
  if (kind === 'copy') return tr('task_copy');
  if (kind === 'move') return tr('task_move');
  if (kind === 'delete') return tr('task_delete');
  if (kind === 'upload') return tr('task_upload');
  return tr('task_other');
}
function formatTaskEventToast(ev) {
  if (!ev) return '';
  if (ev.cancelled) return tr('task_cancelled') + ev.title;
  if (!ev.success) return tr('task_fail', { title: ev.title, err: ev.error || tr('task_unknown_err') });
  if (ev.kind === 'compress') return tr('task_done_compress') + (ev.createdName || ev.title);
  if (ev.kind === 'extract') return tr('task_done_extract', { count: ev.count || 0, name: ev.createdName || '' });
  if (ev.kind === 'copy') return tr('task_done_copy', { count: ev.count || 1 });
  if (ev.kind === 'move') return tr('task_done_move', { count: ev.count || 1 });
  if (ev.kind === 'delete') return tr('task_done_delete', { count: ev.count || 1 });
  return ev.title + tr('task_done_other');
}
function renderFileTasks(tasks) {
  const bar = el('fileTaskBar');
  if (!bar) return;
  if (!tasks || !tasks.length) {
    bar.classList.remove('show');
    bar.innerHTML = '';
    return;
  }
  bar.classList.add('show');
  bar.innerHTML = tasks.map(t => {
    const hasTotal = t.totalBytes > 0;
    const pct = hasTotal ? Math.min(100, Math.round((t.bytesDone / t.totalBytes) * 100)) : 0;
    const sizeText = hasTotal
      ? (pct + '% · ' + fmtBytes(t.bytesDone) + ' / ' + fmtBytes(t.totalBytes))
      : (t.bytesDone > 0 ? (tr('task_processed') + fmtBytes(t.bytesDone)) : tr('task_processing'));
    const srcChip = '<span class="chip">' + esc(t.source === 'web' ? tr('src_web') : tr('src_handheld')) + '</span>';
    const cancelBtn = t.cancellable
      ? '<button class="btn sm danger" ' + (t.cancelling ? 'disabled ' : '') + 'onclick="cancelFileTask(\'' + escJs(t.id) + '\',\'' + escJs(t.kind) + '\')">' + esc(tr('btn_cancel')) + '</button>'
      : '';
    return '<div class="task-item">' +
      '<div class="task-top">' +
        '<div class="task-spin"></div>' +
        '<div style="flex:1;min-width:0;">' +
          '<div style="display:flex;align-items:center;gap:6px;flex-wrap:wrap;font-size:13px;font-weight:700;">' +
            '<span>' + taskKindLabel(t.kind) + ' · ' + esc(t.title) + '</span>' + srcChip +
          '</div>' +
          '<div class="sub" style="margin-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">' +
            esc(sizeText) + (t.detail ? (' · ' + esc(t.detail)) : '') + ' · ' + fmtDuration(t.elapsedMs) +
          '</div>' +
        '</div>' +
        cancelBtn +
      '</div>' +
      '<div class="task-prog ' + (hasTotal ? '' : 'indet') + '"><div style="width:' + (hasTotal ? pct + '%' : '40%') + '"></div></div>' +
    '</div>';
  }).join('');
}
function scheduleTaskPoll(delayMs) {
  clearTimeout(taskPollTimer);
  taskPollTimer = setTimeout(pollFileTasks, delayMs);
}
async function pollFileTasks() {
  clearTimeout(taskPollTimer);
  try {
    const r = await api('/api/files/tasks');
    const d = await r.json();
    if (!d || !d.ok) return;
    const tasks = d.tasks || [];
    renderFileTasks(tasks);
    if (d.lastEvent && d.lastEvent.seq > lastTaskEventSeq) {
      const firstInit = lastTaskEventSeq === 0 && !hadActiveTasks && !tasks.length;
      lastTaskEventSeq = d.lastEvent.seq;
      if (!firstInit) {
        toast(formatTaskEventToast(d.lastEvent));
        if (curTab === 'files') loadFiles(curDir, true);
        loadGames();
      }
    }
    if (typeof d.filesRevision === 'number') {
      if (lastKnownFilesRev >= 0 && d.filesRevision !== lastKnownFilesRev) {
        lastKnownFilesRev = d.filesRevision;
        if (curTab === 'files') loadFiles(curDir, true);
        loadGames();
      } else {
        lastKnownFilesRev = d.filesRevision;
      }
    }
    if (tasks.length > 0) {
      hadActiveTasks = true;
      scheduleTaskPoll(650);
    } else if (hadActiveTasks) {
      hadActiveTasks = false;
      if (curTab === 'files') loadFiles(curDir, true);
      loadGames();
    }
  } catch {}
}
async function cancelFileTask(id, kind) {
  if (kind === 'upload' && activeUploadCtrl) {
    cancelWebUpload();
  }
  await api('/api/files/tasks/cancel', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id}) });
  pollFileTasks();
}

let hbInFlight = false;
let hbFailCount = 0;
let lastAliveAt = Date.now();

async function heartbeat() {
  if (hbInFlight || document.hidden) return;
  hbInFlight = true;
  const busy = (typeof activeUploadCtrl !== 'undefined' && !!activeUploadCtrl) || hadActiveTasks;
  const timeoutMs = busy ? 25000 : 15000;
  const ctrl = typeof AbortController !== 'undefined' ? new AbortController() : null;
  const timer = ctrl ? setTimeout(() => ctrl.abort(), timeoutMs) : null;
  try {
    const r = await fetch('/api/theme', { cache: 'no-store', signal: ctrl ? ctrl.signal : undefined });
    if (!r.ok) throw new Error('http_' + r.status);
    const d = await r.json();
    if (!d.ok) throw new Error('not_ok');
    if (d.authed === false) { location.reload(); return; }
    hbFailCount = 0;
    lastAliveAt = Date.now();
    applyThemeObj(d.theme);
    const wasOff = !webConnected;
    renderDevInfo(true, typeof d.online === 'boolean' ? d.online : deviceInfo.online);
    if (wasOff) toast(tr('conn_restored'));
    if (typeof d.filesRevision === 'number') {
      if (lastKnownFilesRev >= 0 && d.filesRevision !== lastKnownFilesRev) {
        lastKnownFilesRev = d.filesRevision;
        if (curTab === 'files') loadFiles(curDir, true);
        loadGames();
      } else {
        lastKnownFilesRev = d.filesRevision;
      }
    }
    if (d.activeTaskCount > 0 || hadActiveTasks) pollFileTasks();
  } catch (err) {
    if (Date.now() - lastAliveAt < 12000) return;
    const isTimeout = err && err.name === 'AbortError';
    const failThreshold = (busy || isTimeout) ? 6 : 3;
    hbFailCount++;
    if (hbFailCount >= failThreshold) {
      if (webConnected) {
        renderDevInfo(false, false);
        toast(tr('conn_off'));
      } else {
        renderDevInfo(false, false);
      }
    }
  } finally {
    if (timer) clearTimeout(timer);
    hbInFlight = false;
  }
}
setInterval(heartbeat, 5000);

document.querySelectorAll('.side .nav-item[data-tab]').forEach(b => b.onclick = () => switchNavTab(b.dataset.tab));

function setGameView(m) {
  gameViewMode = m;
  localStorage.setItem('pd_web_gview', m);
  el('vmGrid').classList.toggle('active', m === 'grid');
  el('vmList').classList.toggle('active', m === 'list');
  renderGames();
}

async function loadInit() {
  el('vmGrid').classList.toggle('active', gameViewMode === 'grid');
  el('vmList').classList.toggle('active', gameViewMode === 'list');
  const info = await (await api('/api/info')).json();
  deviceInfo = info;
  roots = info.roots || [];
  if (info.theme) applyThemeObj(info.theme);
  renderDevInfo(true, !!info.online);
  await Promise.all([loadGames(), loadCategories()]);
}

function buildGamesQueryUrl(offset, limit) {
  const params = new URLSearchParams();
  params.set('offset', String(offset));
  params.set('limit', String(limit));
  const q = (el('gSearch') ? el('gSearch').value : '').trim();
  if (q) params.set('q', q);
  if (activeFilterMode && activeFilterMode !== 'all') params.set('vis', activeFilterMode);
  if (activeFilterPlat) params.set('plat', activeFilterPlat);
  const catFilter = el('gCat') ? el('gCat').value : '';
  if (catFilter) params.set('cat', catFilter);
  return '/api/games?' + params.toString();
}

function applyGamesStats(stats) {
  if (!stats) return;
  gamesStats = {
    totalGames: stats.totalGames || 0,
    platformCount: stats.platformCount || 0,
    favCount: stats.favCount || 0,
    hasHidden: !!stats.hasHidden,
    platforms: Array.isArray(stats.platforms) ? stats.platforms : [],
  };
  const sel = el('gPlat');
  if (sel) {
    const prev = sel.value;
    sel.innerHTML = '<option value="" data-i18n="plat_all">' + esc(tr('plat_all')) + '</option>' +
      gamesStats.platforms.map(p => '<option value="' + esc(p) + '">' + esc(p) + '</option>').join('');
    sel.value = prev;
  }
}

let gLastSearchedQuery = '';

function syncGameSearchClearBtn() {
  const inp = el('gSearch');
  const clr = el('gSearchClear');
  const kbd = el('gSearchKbd');
  const hasVal = !!(inp && inp.value.length > 0);
  if (clr) clr.style.display = hasVal ? 'inline-block' : 'none';
  if (kbd) kbd.style.display = hasVal ? 'none' : '';
}

let gSearchComposing = false;
function onGameSearchCompStart() {
  gSearchComposing = true;
  if (gSearchTimer) { clearTimeout(gSearchTimer); gSearchTimer = null; }
}

function onGameSearchCompEnd() {
  gSearchComposing = false;
  scheduleGameSearchDebounce();
}

function scheduleGameSearchDebounce() {
  if (gSearchTimer) { clearTimeout(gSearchTimer); gSearchTimer = null; }
  const q = (el('gSearch')?.value || '').trim();
  if (!q) {
    if (gLastSearchedQuery) {
      gLastSearchedQuery = '';
      loadGames(true);
    }
    return;
  }
  if (q === gLastSearchedQuery) return;
  gSearchTimer = setTimeout(() => {
    gSearchTimer = null;
    if (gSearchComposing) return;
    const cur = (el('gSearch')?.value || '').trim();
    if (cur === gLastSearchedQuery) return;
    gLastSearchedQuery = cur;
    loadGames(true);
  }, 1000);
}

function onGameSearchInput() {
  syncGameSearchClearBtn();
  if (gSearchComposing) return;
  scheduleGameSearchDebounce();
}

function onGameSearchKeyDown(e) {
  if (e.key === 'Enter' && !e.isComposing && !gSearchComposing) {
    e.preventDefault();
    if (gSearchTimer) { clearTimeout(gSearchTimer); gSearchTimer = null; }
    const q = (el('gSearch')?.value || '').trim();
    gLastSearchedQuery = q;
    loadGames(true);
  } else if (e.key === 'Escape') {
    clearGameSearch();
  }
}

function clearGameSearch() {
  if (gSearchTimer) { clearTimeout(gSearchTimer); gSearchTimer = null; }
  gSearchComposing = false;
  const inp = el('gSearch');
  if (inp) inp.value = '';
  syncGameSearchClearBtn();
  if (gLastSearchedQuery) {
    gLastSearchedQuery = '';
    loadGames(true);
  }
  if (inp) inp.focus();
}

function ensureGamesObserver() {
  if (gamesObserver || typeof IntersectionObserver === 'undefined') return;
  const sentinel = el('gamesSentinel');
  if (!sentinel) return;
  gamesObserver = new IntersectionObserver(entries => {
    if (entries.some(e => e.isIntersecting) && curTab === 'games' && gamesHasMore && !gamesLoading) {
      loadMoreGames();
    }
  }, { rootMargin: '420px 0px' });
  gamesObserver.observe(sentinel);
}

async function loadGames(reset = true) {
  if (!reset) return loadMoreGames();
  const seq = ++gamesReqSeq;
  gamesLoading = true;
  renderGamesFooter();
  try {
    const data = await (await api(buildGamesQueryUrl(0, GAME_PAGE_SIZE))).json();
    if (seq !== gamesReqSeq) return;
    const batch = data.games || [];
    games = batch;
    gamesById = Object.create(null);
    batch.forEach(g => { gamesById[g.id] = g; });
    gamesTotal = typeof data.total === 'number' ? data.total : batch.length;
    gamesHasMore = !!data.hasMore;
    applyGamesStats(data.stats);
    renderStats();
    renderGames();
    ensureGamesObserver();
  } finally {
    if (seq === gamesReqSeq) {
      gamesLoading = false;
      renderGamesFooter();
    }
  }
}

async function loadMoreGames() {
  if (gamesLoading || !gamesHasMore) return;
  const seq = gamesReqSeq;
  gamesLoading = true;
  renderGamesFooter();
  try {
    const offset = games.length;
    const data = await (await api(buildGamesQueryUrl(offset, GAME_PAGE_SIZE))).json();
    if (seq !== gamesReqSeq) return;
    const batch = data.games || [];
    const fresh = [];
    batch.forEach(g => {
      if (!gamesById[g.id]) {
        games.push(g);
        fresh.push(g);
      }
      gamesById[g.id] = g;
    });
    gamesTotal = typeof data.total === 'number' ? data.total : games.length;
    gamesHasMore = !!data.hasMore && batch.length > 0;
    if (data.stats) {
      applyGamesStats(data.stats);
      renderStats();
    }
    if (fresh.length) appendGamesBatch(fresh);
  } finally {
    if (seq === gamesReqSeq) {
      gamesLoading = false;
      renderGamesFooter();
    }
  }
}

function svgIco(name, size) {
  const s = size || 16;
  const p = {
    gamepad: '<rect x="2" y="6" width="20" height="12" rx="5"/><path d="M6 12h4M8 10v4"/><circle cx="15.5" cy="13" r=".7" fill="currentColor"/><circle cx="17.8" cy="11" r=".7" fill="currentColor"/>',
    cpu: '<rect x="6" y="6" width="12" height="12" rx="2"/><rect x="9.5" y="9.5" width="5" height="5" rx="1"/><path d="M9 2v4M15 2v4M9 18v4M15 18v4M2 9h4M2 15h4M18 9h4M18 15h4"/>',
    heart: '<path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"/>',
    heartFill: '<path fill="currentColor" d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"/>',
    tag: '<path d="M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z"/><circle cx="7.5" cy="7.5" r=".75" fill="currentColor"/>',
    drive: '<line x1="22" y1="12" x2="2" y2="12"/><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"/>',
    folder: '<path d="M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"/>',
    file: '<path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/>',
    archive: '<rect width="20" height="5" x="2" y="3" rx="1"/><path d="M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8"/><path d="M10 12h4"/>',
    image: '<rect width="18" height="18" x="3" y="3" rx="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/>',
    music: '<path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>',
    type: '<polyline points="4 7 4 4 20 4 20 7"/><line x1="9" y1="20" x2="15" y2="20"/><line x1="12" y1="4" x2="12" y2="20"/>'
  }[name] || '';
  return '<svg class="ico" width="' + s + '" height="' + s + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">' + p + '</svg>';
}

let activeFilterMode = 'all';
let activeFilterPlat = '';

function setFilterChip(mode, plat) {
  activeFilterMode = mode || 'all';
  activeFilterPlat = plat || '';
  el('gVis').value = activeFilterMode;
  el('gPlat').value = activeFilterPlat;
  renderFilterChips();
  loadGames(true);
}

function renderFilterChips() {
  const box = el('filterChips');
  if (!box) return;
  const plats = gamesStats.platforms || [];
  const hasHidden = !!gamesStats.hasHidden;
  const chips = [
    '<button class="fchip' + (activeFilterMode === 'all' && !activeFilterPlat ? ' on' : '') + '" onclick="setFilterChip(\'all\',\'\')">' + esc(tr('filter_all')) + '</button>'
  ];
  plats.forEach(p => {
    const on = activeFilterMode === 'all' && activeFilterPlat === p;
    chips.push('<button class="fchip' + (on ? ' on' : '') + '" onclick="setFilterChip(\'all\',\'' + escJs(p) + '\')">' + esc(p) + '</button>');
  });
  chips.push('<button class="fchip' + (activeFilterMode === 'fav' ? ' on' : '') + '" onclick="setFilterChip(\'fav\',\'\')">' + esc(tr('filter_fav')) + '</button>');
  chips.push('<button class="fchip' + (activeFilterMode === 'nocover' ? ' on' : '') + '" onclick="setFilterChip(\'nocover\',\'\')">' + esc(tr('filter_nocover')) + '</button>');
  if (hasHidden) {
    chips.push('<button class="fchip' + (activeFilterMode === 'hidden' ? ' on' : '') + '" onclick="setFilterChip(\'hidden\',\'\')">' + esc(tr('filter_hidden')) + '</button>');
  }
  box.innerHTML = chips.join('');
}

function renderStats() {
  const sg = el('statsGrid');
  if (sg) sg.textContent = tr('summary_fmt', {
    games: gamesStats.totalGames,
    plats: gamesStats.platformCount,
    favs: gamesStats.favCount,
  });
  const gc = el('navGamesCnt');
  if (gc) gc.textContent = gamesStats.totalGames ? String(gamesStats.totalGames) : '';
  const cc = el('navCatsCnt');
  if (cc) cc.textContent = catTabs.length ? String(catTabs.length) : '';
  renderFilterChips();
  renderDevInfo(webConnected, deviceInfo.online);
}

function _buildWebCoverChain(g, safeId) {
  const ver = (g && g.coverVersion) ? String(g.coverVersion) : '0';
  const localUrl = '/api/cover?id=' + safeId + '&v=' + encodeURIComponent(ver);
  const raw = (g && g.coverUrl) ? String(g.coverUrl).trim() : '';
  if (!raw || !/^https?:\/\//i.test(raw)) return [localUrl];
  const chain = [];
  const push = u => { if (u && !chain.includes(u)) chain.push(u); };
  const m0 = raw.match(/^https:\/\/thumbnails\.libretro\.com\/([^/]+)\/(Named_[^/]+)\/(.+)$/);
  const m1 = raw.match(/^https:\/\/cdn\.jsdelivr\.net\/gh\/libretro-thumbnails\/([^/@]+)@master\/(Named_[^/]+)\/(.+)$/);
  const m2 = raw.match(/^https:\/\/raw\.githubusercontent\.com\/libretro-thumbnails\/([^/]+)\/master\/(Named_[^/]+)\/(.+)$/);
  if (m0 || m1 || m2) {
    const repo = m0 ? encodeURIComponent(decodeURIComponent(m0[1]).replace(/ /g, '_')) : (m1 ? m1[1] : m2[1]);
    const kind = m0 ? m0[2] : (m1 ? m1[2] : m2[2]);
    const file = m0 ? m0[3] : (m1 ? m1[3] : m2[3]);
    push('https://cdn.jsdelivr.net/gh/libretro-thumbnails/' + repo + '@master/' + kind + '/' + file);
    push('https://raw.githubusercontent.com/libretro-thumbnails/' + repo + '/master/' + kind + '/' + file);
  } else {
    push(raw);
  }
  // 外链失效/过期或电脑无外网时，自动回退向掌机请求本地缓存图片
  push(localUrl);
  return chain;
}

function _onWebCoverErr(img, hideNext, titleText) {
  try {
    const list = JSON.parse(img.dataset.fb || '[]');
    const nextIdx = (parseInt(img.dataset.fbi || '0', 10) || 0) + 1;
    if (nextIdx < list.length) {
      img.dataset.fbi = String(nextIdx);
      img.src = list[nextIdx];
      return;
    }
  } catch (e) {}
  if (hideNext) {
    img.style.display = 'none';
    if (img.nextElementSibling) img.nextElementSibling.style.display = 'flex';
  } else if (titleText) {
    img.outerHTML = '<span>' + esc(titleText) + '</span>';
  } else {
    img.style.display = 'none';
  }
}

function _webCoverImgHtml(g, safeId, opts) {
  const chain = _buildWebCoverChain(g, safeId);
  const src = chain[0];
  const fbAttr = esc(JSON.stringify(chain));
  const idAttr = opts.id ? (' id="' + opts.id + '"') : '';
  const clsAttr = opts.cls ? (' class="' + opts.cls + '"') : '';
  const lazyAttr = opts.lazy ? ' loading="lazy"' : '';
  const onErr = opts.hideNext
    ? '_onWebCoverErr(this,true)'
    : (opts.titleFallback ? ('_onWebCoverErr(this,false,\'' + escJs(g.title) + '\')') : '_onWebCoverErr(this,false)');
  return '<img' + idAttr + clsAttr + lazyAttr + ' src="' + esc(src) + '" data-fb="' + fbAttr + '" data-fbi="0" onerror="' + onErr + '"/>';
}

function gameCardHtml(g) {
  const safeId = encodeURIComponent(g.id);
  const m = g.metadata || {};
  const metaLine = [m.publisher || m.developer || null, m.releaseDate || null].filter(Boolean).join(' · ') || (g.isRom ? fmtBytes(g.size) : (m.genre || '—'));
  const cov = g.hasCover
    ? _webCoverImgHtml(g, safeId, { lazy: true, hideNext: true })
    : '';
  const fbStyle = g.hasCover ? 'style="display:none"' : '';
  return '<div class="gcard' + (g.hidden ? ' is-hidden' : '') + '" data-gid="' + esc(g.id) + '" onclick="openGameDetail(\'' + escJs(g.id) + '\')">' +
    '<div class="gcover-box">' + cov +
      '<div class="gcover-fallback" ' + fbStyle + '><span class="tt">' + esc(g.title) + '</span></div>' +
      '<span class="badge-plat">' + esc(g.platformName) + '</span>' +
      '<button class="badge-fav' + (g.favorite ? ' on' : '') + '" onclick="event.stopPropagation();toggleFav(\'' + escJs(g.id) + '\',' + (!g.favorite) + ')" title="' + esc(tr('btn_fav')) + '">' + svgIco(g.favorite ? 'heartFill' : 'heart', 14) + '</button>' +
    '</div>' +
    '<div class="gbody">' +
      '<div class="gtitle" title="' + esc(g.title) + '">' + esc(g.title) + '</div>' +
      '<div class="gmeta" title="' + esc(metaLine) + '">' + esc(metaLine) + '</div>' +
    '</div></div>';
}

function gameRowHtml(g) {
  const safeId = encodeURIComponent(g.id);
  const m = g.metadata || {};
  const metaSummary = [m.releaseDate, m.developer, m.publisher].filter(Boolean).join(' · ');
  const cov = g.hasCover
    ? _webCoverImgHtml(g, safeId, { cls: 'cover-sm', lazy: true })
    : '<div class="cover-sm">' + esc(g.platformName) + '</div>';
  return '<div class="row" data-gid="' + esc(g.id) + '">' + cov +
    '<div class="meta" style="cursor:pointer" onclick="openGameDetail(\'' + escJs(g.id) + '\')">' +
      '<div class="title">' + esc(g.title) +
        (g.favorite ? '<span class="chip">' + esc(tr('fav_tag')) + '</span>' : '') +
        (g.hidden ? '<span class="chip warn">' + esc(tr('hidden_tag')) + '</span>' : '') +
      '</div>' +
      '<div class="desc">' + esc(g.platformName) + (g.isRom ? ' · ' + fmtBytes(g.size) : ' · Android') + (metaSummary ? ' · ' + esc(metaSummary) : '') + '</div>' +
    '</div>' +
    '<div class="actions">' +
      '<button class="btn sm" onclick="openGameDetail(\'' + escJs(g.id) + '\')">' + esc(tr('btn_detail')) + '</button>' +
      '<button class="btn sm" onclick="toggleFav(\'' + escJs(g.id) + '\',' + (!g.favorite) + ')">' + esc(g.favorite ? tr('btn_unfav') : tr('btn_fav')) + '</button>' +
      '<button class="btn sm" onclick="toggleHide(\'' + escJs(g.id) + '\',' + (!g.hidden) + ')">' + esc(g.hidden ? tr('btn_restore') : tr('btn_hide')) + '</button>' +
      (g.isRom ? '<a class="btn sm" href="/api/files/download?path=' + safeId + '">' + esc(tr('btn_dl')) + '</a>' : '') +
    '</div></div>';
}

function renderGamesFooter() {
  const ft = el('gamesFooter');
  if (!ft) return;
  if (!gamesTotal && !gamesLoading) { ft.innerHTML = ''; return; }
  if (gamesLoading) {
    ft.innerHTML = '<span class="task-spin"></span><span>' + esc(tr('loading_more')) + '</span>';
    return;
  }
  if (gamesHasMore) {
    ft.innerHTML = '<span>' + esc(tr('loaded_games_fmt', { loaded: games.length, total: gamesTotal })) + '</span>' +
      '<button class="btn sm" onclick="loadMoreGames()">' + esc(tr('btn_load_more')) + '</button>';
  } else if (gamesTotal > GAME_PAGE_SIZE) {
    ft.innerHTML = '<span>' + esc(tr('loaded_all_games_fmt', { total: gamesTotal })) + '</span>';
  } else {
    ft.innerHTML = '';
  }
}

function renderGames() {
  const container = el('gamesContainer');
  if (!games.length) {
    container.innerHTML = '<div class="sub" style="padding:28px 0;">' + esc(tr('no_games')) + '</div>';
    renderGamesFooter();
    return;
  }
  if (gameViewMode === 'grid') {
    container.innerHTML = '<div id="gamesInner" class="game-grid">' + games.map(gameCardHtml).join('') + '</div>';
  } else {
    container.innerHTML = '<div id="gamesInner" class="list">' + games.map(gameRowHtml).join('') + '</div>';
  }
  renderGamesFooter();
}

function appendGamesBatch(batch) {
  const inner = el('gamesInner');
  if (!inner) { renderGames(); return; }
  const html = batch.map(gameViewMode === 'grid' ? gameCardHtml : gameRowHtml).join('');
  inner.insertAdjacentHTML('beforeend', html);
  renderGamesFooter();
}

// 封面模式：鼠标停留 120ms 后与掌机网格一致的微幅 3D 立体倾斜呼吸（rotateY ±3° / 5.6s、rotateX ±2.2° / 4.4s）
(function setupCardHoverTilt() {
  const reduceMq = window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;
  let activeCard = null, delayTimer = null, rafId = 0, startTs = 0;
  const TWO_PI = Math.PI * 2;
  function stop() {
    clearTimeout(delayTimer); delayTimer = null;
    if (rafId) cancelAnimationFrame(rafId);
    rafId = 0;
    if (activeCard) { activeCard.style.transform = ''; activeCard.style.transition = ''; }
    activeCard = null;
  }
  function frame(ts) {
    if (!activeCard || !activeCard.isConnected) { stop(); return; }
    if (!startTs) startTs = ts;
    const t = (ts - startTs) / 1000;
    const env = Math.min(1, t / 0.65);
    const ry = Math.sin(t * TWO_PI / 5.6) * 3.0 * env;
    const rx = Math.cos(t * TWO_PI / 4.4) * 2.2 * env;
    const ty = -3 + Math.sin(t * TWO_PI / 4.4) * 0.8 * env;
    activeCard.style.transform = 'perspective(900px) translateY(' + ty.toFixed(2) + 'px) rotateX(' + rx.toFixed(3) + 'deg) rotateY(' + ry.toFixed(3) + 'deg)';
    rafId = requestAnimationFrame(frame);
  }
  function start(card) {
    stop();
    if (reduceMq && reduceMq.matches) return;
    activeCard = card;
    delayTimer = setTimeout(() => {
      if (activeCard !== card) return;
      card.style.transition = 'box-shadow .16s ease, border-color .16s';
      startTs = 0;
      rafId = requestAnimationFrame(frame);
    }, 120);
  }
  document.addEventListener('mouseover', e => {
    const card = e.target.closest ? e.target.closest('.gcard') : null;
    if (card && card !== activeCard) start(card);
  });
  document.addEventListener('mouseout', e => {
    if (!activeCard) return;
    const to = e.relatedTarget;
    if (!to || !activeCard.contains(to)) stop();
  });
  document.addEventListener('visibilitychange', () => { if (document.hidden) stop(); });
})();

function matchesCurrentGameFilter(g) {
  const vis = activeFilterMode;
  if (vis === 'visible' && g.hidden) return false;
  if (vis === 'hidden' && !g.hidden) return false;
  if (vis === 'fav' && !g.favorite) return false;
  if (vis === 'nocover' && g.hasCover) return false;
  if (activeFilterPlat && g.platformName !== activeFilterPlat) return false;
  const catFilter = el('gCat') ? el('gCat').value : '';
  if (catFilter && !(g.categories || []).includes(catFilter)) return false;
  const q = (el('gSearch') ? el('gSearch').value : '').trim().toLowerCase();
  if (q) {
    const m = g.metadata || {};
    const hay = (g.title + ' ' + g.officialName + ' ' + g.fileName + ' ' + g.platformName + ' ' + (m.developer||'') + ' ' + (m.publisher||'') + ' ' + (m.genre||'')).toLowerCase();
    if (!hay.includes(q)) return false;
  }
  return true;
}

function patchSingleGame(updatedGame, stats) {
  if (stats) {
    applyGamesStats(stats);
    renderStats();
  }
  if (!updatedGame || !updatedGame.id) return;
  gamesById[updatedGame.id] = updatedGame;
  const idx = games.findIndex(x => x.id === updatedGame.id);
  const stillMatches = matchesCurrentGameFilter(updatedGame);
  const inner = el('gamesInner');
  let domNode = null;
  if (inner) {
    for (const child of inner.children) {
      if (child.dataset && child.dataset.gid === updatedGame.id) { domNode = child; break; }
    }
  }
  if (idx >= 0) {
    if (stillMatches) {
      games[idx] = updatedGame;
      if (domNode) domNode.outerHTML = gameViewMode === 'grid' ? gameCardHtml(updatedGame) : gameRowHtml(updatedGame);
    } else {
      games.splice(idx, 1);
      gamesTotal = Math.max(0, gamesTotal - 1);
      if (domNode) domNode.remove();
      if (!games.length) renderGames(); else renderGamesFooter();
    }
  }
}

async function openGameDetail(id) {
  let g = gamesById[id];
  if (!g) {
    try {
      const r = await api('/api/games/item?id=' + encodeURIComponent(id));
      if (r.ok) {
        const d = await r.json();
        if (d.ok && d.game) {
          g = d.game;
          gamesById[g.id] = g;
        }
      }
    } catch (_) {}
  }
  if (!g) return;
  const m = g.metadata || {};
  const mInfo = g.metaInfo || {};
  const safeId = encodeURIComponent(g.id);
  const customCats = catTabs.filter(t => t.type === 'Custom');

  el('mTitle').textContent = g.title;
  el('mBody').innerHTML =
    '<div class="detail-hero">' +
      '<div class="drawer-cover">' +
        (g.hasCover
          ? _webCoverImgHtml(g, safeId, { id: 'detCoverImg', titleFallback: true })
          : '<span>' + esc(g.title) + '</span>') +
      '</div>' +
      '<div class="detail-info">' +
        '<div class="drawer-tags">' +
          (g.favorite ? '<span class="chip">' + esc(tr('fav_on')) + '</span>' : '') +
          '<span class="chip muted">' + esc(g.platformName) + (g.platformFullName ? ' · ' + esc(g.platformFullName) : '') + '</span>' +
          (g.isRom ? '<span class="chip muted">' + fmtBytes(g.size) + '</span>' : '') +
          (g.hidden ? '<span class="chip warn">' + esc(tr('hidden_tag')) + '</span>' : '') +
        '</div>' +
        '<div class="drawer-acts">' +
          (g.isRom ? '<button class="btn sm primary" id="btnRefreshMeta" onclick="refreshGameMetaFromHandheld(\'' + escJs(g.id) + '\')">' + esc(tr('btn_find_cover')) + '</button>' : '') +
          (g.isRom ? '<button class="btn sm" onclick="scrapeCoverCandidates(\'' + escJs(g.id) + '\')">' + esc(tr('btn_pick_cover_short')) + '</button>' : '') +
          '<label class="btn sm" style="justify-content:center;">' + esc(tr('btn_upload_short')) + '<input type="file" accept="image/*" style="display:none" onchange="uploadCustomCover(\'' + escJs(g.id) + '\', this.files[0])"/></label>' +
        '</div>' +
        '<div class="meta-kv">' +
          '<div class="kv-box"><div class="kv-k">' + esc(tr('kv_release')) + '</div><div class="kv-v" id="detKvRelease">' + esc(m.releaseDate || '—') + '</div></div>' +
          '<div class="kv-box"><div class="kv-k">' + esc(tr('kv_genre')) + '</div><div class="kv-v" id="detKvGenre">' + esc(m.genre || '—') + '</div></div>' +
          '<div class="kv-box"><div class="kv-k">' + esc(tr('kv_dev')) + '</div><div class="kv-v" id="detKvDev">' + esc(m.developer || '—') + '</div></div>' +
          '<div class="kv-box"><div class="kv-k">' + esc(tr('kv_pub')) + '</div><div class="kv-v" id="detKvPub">' + esc(m.publisher || '—') + '</div></div>' +
        '</div>' +
        '<div class="detail-path" id="detPath">' +
          (mInfo.crc32 ? 'CRC32 ' + esc(mInfo.crc32) + ' · ' : '') + esc(g.id) +
        '</div>' +
      '</div>' +
    '</div>' +
    '<div id="candArea"></div>' +
    (customCats.length
      ? '<div style="margin-top:10px;padding-top:10px;border-top:1px solid var(--line);">' +
          '<div style="font-weight:700;font-size:12.5px;color:var(--label);margin-bottom:8px;">' + esc(tr('det_cats_title')) + '</div>' +
          '<div class="plat-grid" style="max-height:130px;">' + customCats.map(c => {
            const viaPlat = (c.sources || []).includes(g.platformId);
            const manual = (c.games || []).includes(g.id);
            const checked = viaPlat || manual;
            return '<label class="plat-chk' + (checked ? ' checked' : '') + '">' +
              '<input type="checkbox" class="gcat-chk" value="' + esc(c.id) + '" ' + (checked ? 'checked' : '') + ' ' + (viaPlat ? 'disabled' : 'onchange="saveGameCats(\'' + escJs(g.id) + '\')"') + '/>' +
              '<span>' + esc(c.name) + (viaPlat ? esc(tr('det_via_plat')) : '') + '</span></label>';
          }).join('') + '</div></div>'
      : '');

  el('mFoot').innerHTML =
    '<button class="btn sm" onclick="toggleFav(\'' + escJs(g.id) + '\',' + (!g.favorite) + ',true)">' + esc(g.favorite ? tr('btn_unfav') : ('♥ ' + tr('btn_fav'))) + '</button>' +
    '<button class="btn sm" onclick="renameGame(\'' + escJs(g.id) + '\',\'' + escJs(g.title) + '\')">' + esc(tr('btn_rename')) + '</button>' +
    '<button class="btn sm" onclick="toggleHide(\'' + escJs(g.id) + '\',' + (!g.hidden) + ',true)">' + esc(g.hidden ? tr('btn_unhide') : tr('btn_hide')) + '</button>' +
    (g.isRom ? '<a class="btn sm" href="/api/files/download?path=' + safeId + '">' + esc(tr('btn_dl')) + '</a>' : '') +
    (g.isRom ? '<button class="btn sm danger" onclick="deleteFile(\'' + escJs(g.id) + '\',\'' + escJs(g.fileName) + '\',true)">' + esc(tr('btn_del_file')) + '</button>' : '') +
    '<button class="btn sm primary" onclick="closeModal()">' + esc(tr('btn_close')) + '</button>';

  el('modalBg').classList.add('open');
  window._curDetailGameId = g.id;
  if (!window._webMetaFetchedIds) window._webMetaFetchedIds = new Set();
  const metaEmpty = !m.releaseDate && !m.genre && !m.developer && !m.publisher && !g.officialName;
  if (g.isRom && metaEmpty && !window._webMetaFetchedIds.has(g.id)) {
    window._webMetaFetchedIds.add(g.id);
    api('/api/games/item?id=' + safeId + '&ensureMeta=1').then(r => r.ok ? r.json() : null).then(d => {
      if (!d || !d.ok || !d.game) return;
      patchSingleGame(d.game, d.stats);
      if (window._curDetailGameId === d.game.id && el('modalBg').classList.contains('open')) {
        const nm = d.game.metadata || {};
        const nInfo = d.game.metaInfo || {};
        const setT = (eid, val) => { const n = el(eid); if (n) n.textContent = val; };
        setT('detKvRelease', nm.releaseDate || '—');
        setT('detKvGenre', nm.genre || '—');
        setT('detKvDev', nm.developer || '—');
        setT('detKvPub', nm.publisher || '—');
        setT('detPath', (nInfo.crc32 ? 'CRC32 ' + nInfo.crc32 + ' · ' : '') + d.game.id);
      }
    }).catch(() => {});
  }
}

async function refreshGameMetaFromHandheld(id) {
  const btn = el('btnRefreshMeta');
  if (btn) { btn.disabled = true; btn.textContent = tr('btn_fetching_meta'); }
  try {
    const r = await api('/api/games/refresh-meta', {
      method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id})
    });
    const d = await r.json();
    if (!d.ok && d.error === 'offline') {
      toast(tr('msg_offline'));
    } else if (d.ok) {
      toast((d.coverUpdated || d.metaUpdated) ? tr('msg_meta_done') : tr('msg_meta_none'));
      if (d.game) patchSingleGame(d.game, d.stats); else await loadGames(true);
      openGameDetail(id);
    } else {
      toast(tr('msg_meta_fail') + (d.error || r.status));
    }
  } finally {
    if (btn) { btn.disabled = false; btn.textContent = tr('btn_fetch_meta'); }
  }
}

// ==================== 浏览器端本地封面搜索与直传（不依赖掌机联网） ====================
const _webLibretroIdx = {};
const _webCjkAliases = [
  ['超级马里奥合集','Super Mario All-Stars'],['马里奥合集','Super Mario All-Stars'],['超级马里奥全明星','Super Mario All-Stars'],
  ['超级马里奥世界2','Super Mario World 2 - Yoshi\'s Island'],['超级马里奥世界','Super Mario World'],
  ['超级马里奥兄弟3','Super Mario Bros. 3'],['超级马里奥兄弟2','Super Mario Bros. 2'],['超级马里奥兄弟','Super Mario Bros.'],
  ['超级马里奥RPG','Super Mario RPG'],['超级马里奥64','Super Mario 64'],['超级马里奥乐园2','Super Mario Land 2'],['超级马里奥乐园','Super Mario Land'],
  ['马里奥赛车','Mario Kart'],['纸片马里奥','Paper Mario'],['耀西岛','Yoshi\'s Island'],['马里奥医生','Dr. Mario'],['超级马里奥','Super Mario'],
  ['口袋妖怪 红宝石','Pokemon - Ruby Version'],['口袋妖怪 蓝宝石','Pokemon - Sapphire Version'],['口袋妖怪 绿宝石','Pokemon - Emerald Version'],
  ['口袋妖怪 火红','Pokemon - FireRed Version'],['口袋妖怪 叶绿','Pokemon - LeafGreen Version'],
  ['口袋妖怪 金','Pokemon - Gold Version'],['口袋妖怪 银','Pokemon - Silver Version'],['口袋妖怪 水晶','Pokemon - Crystal Version'],
  ['口袋妖怪 黄','Pokemon - Yellow Version'],['口袋妖怪 红','Pokemon - Red Version'],['口袋妖怪 蓝','Pokemon - Blue Version'],
  ['宝可梦','Pokemon'],['口袋妖怪','Pokemon'],
  ['塞尔达传说 缩小帽','The Legend of Zelda - The Minish Cap'],['塞尔达传说 织梦岛','The Legend of Zelda - Link\'s Awakening'],
  ['塞尔达传说 众神的三角力量','The Legend of Zelda - A Link to the Past'],['塞尔达传说 时之笛','The Legend of Zelda - Ocarina of Time'],['塞尔达传说','Legend of Zelda'],
  ['恶魔城 晓月圆舞曲','Castlevania - Aria of Sorrow'],['恶魔城 白夜协奏曲','Castlevania - Harmony of Dissonance'],
  ['恶魔城 月下夜想曲','Castlevania - Symphony of the Night'],['恶魔城','Castlevania'],
  ['火焰纹章 封印之剑','Fire Emblem - Fuuin no Tsurugi'],['火焰纹章 烈火之剑','Fire Emblem'],['火焰纹章 圣魔之光石','Fire Emblem - The Sacred Stones'],['火焰纹章','Fire Emblem'],
  ['银河战士 零点任务','Metroid - Zero Mission'],['银河战士 融合','Metroid Fusion'],['超级银河战士','Super Metroid'],['银河战士','Metroid'],
  ['星之卡比 镜之大迷宫','Kirby & the Amazing Mirror'],['星之卡比 超级豪华版','Kirby Super Star'],['星之卡比','Kirby'],
  ['魂斗罗','Contra'],['超级魂斗罗','Super C'],['坦克大战','Battle City'],['冒险岛','Adventure Island'],
  ['松鼠大战','Chip \'n Dale Rescue Rangers'],['赤影战士','Shadow of the Ninja'],['双截龙','Double Dragon'],['热血物语','River City Ransom'],
  ['洛克人X','Mega Man X'],['洛克人','Mega Man'],['最终幻想','Final Fantasy'],['勇者斗恶龙','Dragon Quest'],
  ['时空之轮','Chrono Trigger'],['黄金太阳','Golden Sun'],['逆转裁判','Phoenix Wright'],['牧场物语','Harvest Moon'],
  ['大金刚国度','Donkey Kong Country'],['大金刚','Donkey Kong'],['索尼克','Sonic'],['街头霸王','Street Fighter'],
  ['拳皇','King of Fighters'],['合金弹头','Metal Slug'],['游戏王','Yu-Gi-Oh'],['超级机器人大战','Super Robot Taisen'],
  ['俄罗斯方块','Tetris'],['吃豆人','Pac-Man'],['炸弹人','Bomberman'],['三国志','Romance of the Three Kingdoms']
];

function _webStripTags(s) {
  return String(s || '').replace(/_/g, ' ').replace(/\s*[\(\[][^\)\]]*[\)\]]\s*/g, ' ').replace(/\s+/g, ' ').trim();
}
function _webCompact(s) {
  return _webStripTags(s).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9\u4e00-\u9fff]/g, '');
}
function _webRegionScore(name) {
  let sc = name.includes('(USA)') ? 10 : name.includes('(World)') ? 15 : name.includes('(USA, Europe)') ? 20 : name.includes('(Europe)') ? 30 : name.includes('(Japan)') ? 40 : 50;
  if (/\((Namcot Collection|Namco Museum|Virtual Console|Switch Online|Evercade|Arcade Archives|Classic Mini|Retro-Bit|Collector's Edition|Anniversary Collection|Legacy Collection|Cowabunga Collection)/i.test(name)) sc += 60;
  if (/\[(b|h|tr|p)/i.test(name)) sc += 100;
  return sc;
}
function _webExpandQueries(kw) {
  const clean = _webStripTags(kw);
  const compact = clean.replace(/\s+/g, '');
  const out = [];
  const push = v => { if (v && !out.includes(v)) out.push(v); };
  push(clean);
  if (/[\u4e00-\u9fff]/.test(compact)) {
    for (const [zh, en] of _webCjkAliases) {
      const zc = zh.replace(/\s+/g, '');
      if (compact.includes(zc)) push(en);
    }
  }
  return out;
}
function _webSuggestKeywords(kw, system) {
  const q = String(kw || '').trim();
  if (q.length < 2) return [];
  const out = [];
  const seen = new Set();
  const add = v => { const k = v.toLowerCase(); if (!seen.has(k) && k !== q.toLowerCase()) { seen.add(k); out.push(v); } };
  if (/[\u4e00-\u9fff]/.test(q)) {
    const qc = q.replace(/\s+/g, '');
    const seenEn = new Set();
    for (const [zh, en] of _webCjkAliases) {
      const zc = zh.replace(/\s+/g, '');
      if (zc !== qc && (zc.startsWith(qc) || zc.includes(qc)) && !seenEn.has(en)) {
        seenEn.add(en); add(zh);
        if (out.length >= 8) break;
      }
    }
  } else {
    const qc = _webCompact(q);
    const idx = _webLibretroIdx[system];
    if (idx && idx.byCompact) {
      const pre = [], sub = [];
      for (const [ck, files] of idx.byCompact.entries()) {
        if (ck === qc || !files.length) continue;
        const clean = _webStripTags(files[0].replace(/\.png$/i, ''));
        if (!clean) continue;
        if (ck.startsWith(qc)) pre.push(clean);
        else if (ck.includes(qc)) sub.push(clean);
      }
      pre.sort((a,b) => a.length - b.length);
      sub.sort((a,b) => a.length - b.length);
      for (const t of [...pre, ...sub]) { add(t); if (out.length >= 8) break; }
    }
  }
  return out;
}
const _WEB_IDX_IDB_NAME = 'pd_web_cover_idx_v1';
const _WEB_IDX_IDB_STORE = 'indexes';
const _WEB_IDX_TTL_MS = 30 * 24 * 60 * 60 * 1000;
let _webIdxDbPromise = null;
function _openWebIdxDb() {
  if (_webIdxDbPromise) return _webIdxDbPromise;
  if (typeof indexedDB === 'undefined') return Promise.resolve(null);
  _webIdxDbPromise = new Promise(resolve => {
    try {
      const req = indexedDB.open(_WEB_IDX_IDB_NAME, 1);
      req.onupgradeneeded = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains(_WEB_IDX_IDB_STORE)) db.createObjectStore(_WEB_IDX_IDB_STORE);
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => resolve(null);
    } catch (e) { resolve(null); }
  });
  return _webIdxDbPromise;
}
async function _webIdxIdbGet(system) {
  const db = await _openWebIdxDb();
  if (!db) return null;
  return new Promise(resolve => {
    try {
      const tx = db.transaction(_WEB_IDX_IDB_STORE, 'readonly');
      const req = tx.objectStore(_WEB_IDX_IDB_STORE).get(system);
      req.onsuccess = () => {
        const rec = req.result;
        if (rec && Array.isArray(rec.names) && rec.names.length && (Date.now() - (rec.ts || 0)) < _WEB_IDX_TTL_MS) {
          resolve(rec.names);
        } else {
          resolve(null);
        }
      };
      req.onerror = () => resolve(null);
    } catch (e) { resolve(null); }
  });
}
async function _webIdxIdbSet(system, names) {
  const db = await _openWebIdxDb();
  if (!db || !Array.isArray(names) || !names.length) return;
  try {
    const tx = db.transaction(_WEB_IDX_IDB_STORE, 'readwrite');
    tx.objectStore(_WEB_IDX_IDB_STORE).put({ names, ts: Date.now() }, system);
  } catch (e) {}
}

async function _ensureWebLibretroIndex(system, statusEl) {
  if (!system) return null;
  if (_webLibretroIdx[system]) return _webLibretroIdx[system];
  const repo = encodeURIComponent(system.replace(/ /g, '_'));
  let names = await _webIdxIdbGet(system) || [];
  let fromCache = names.length > 0;
  if (!names.length) {
    if (statusEl) statusEl.textContent = '正在从掌机缓存读取 ' + system + ' 封面目录…';
    try {
      const r = await api('/api/cover-index?system=' + encodeURIComponent(system));
      if (r.ok) {
        const txt = await r.text();
        names = txt.split(/\r?\n/).map(s => s.trim()).filter(Boolean);
      }
    } catch (e) {}
  }
  if (!names.length) {
    if (statusEl) statusEl.textContent = '正在从 GitHub 拉取 ' + system + ' 封面目录…';
    try {
      const r = await fetch('https://api.github.com/repos/libretro-thumbnails/' + repo + '/git/trees/master?recursive=1');
      if (r.ok) {
        const d = await r.json();
        names = (d.tree || []).map(t => t.path || '').filter(p => p.startsWith('Named_Boxarts/') && /\.png$/i.test(p)).map(p => p.slice('Named_Boxarts/'.length));
      }
    } catch (e) {}
  }
  if (!names.length) return null;
  if (!fromCache) _webIdxIdbSet(system, names);
  const byCompact = new Map();
  for (const fn of names) {
    const ck = _webCompact(fn.replace(/\.png$/i, ''));
    if (!ck) continue;
    let arr = byCompact.get(ck);
    if (!arr) { arr = []; byCompact.set(ck, arr); }
    arr.push(fn);
  }
  for (const arr of byCompact.values()) arr.sort((a, b) => _webRegionScore(a) - _webRegionScore(b));
  const idx = { repo, byCompact };
  _webLibretroIdx[system] = idx;
  return idx;
}
function _searchWebLibretro(idx, kw, limit) {
  const queries = _webExpandQueries(kw);
  const qCompacts = queries.map(q => _webCompact(q)).filter(c => c.length >= 2);
  const qTokenSets = queries.map(q => _webStripTags(q).toLowerCase().split(/[^a-z0-9]+/).filter(t => t.length >= 2 || /^\d+$/.test(t))).filter(a => a.length);
  if (!qCompacts.length && !qTokenSets.length) return [];
  const scored = [];
  for (const [ck, files] of idx.byCompact.entries()) {
    const fn = files[0];
    const base = fn.replace(/\.png$/i, '');
    const ckNoVer = ck.replace(/(version|edition)$/, '');
    let score = 0;
    for (const qc of qCompacts) {
      const qcNoVer = qc.replace(/(version|edition)$/, '');
      if (ck === qc) score = Math.max(score, 100);
      else if (ckNoVer && ckNoVer === qcNoVer) score = Math.max(score, 96);
      else if (ck.startsWith(qc)) score = Math.max(score, 85 - Math.min(20, (ck.length - qc.length) * 0.5));
      else if (qc.length >= 3 && ck.includes(qc)) score = Math.max(score, 72 - Math.min(20, (ck.length - qc.length) * 0.4));
    }
    if (!score && qTokenSets.length) {
      const toks = _webStripTags(base).toLowerCase().split(/[^a-z0-9]+/).filter(Boolean);
      for (const qToks of qTokenSets) {
        if (qToks.every(qt => toks.some(t => t === qt || (qt.length >= 3 && t.includes(qt))))) {
          score = Math.max(score, 65 - Math.abs(toks.length - qToks.length) * 2);
        }
      }
    }
    if (score > 0) scored.push({ fn, title: base, score, rScore: _webRegionScore(fn) });
  }
  scored.sort((a, b) => (b.score - a.score) || (a.rScore - b.rScore));
  return scored.slice(0, limit || 16).map(x => {
    const enc = encodeURIComponent(x.fn);
    return {
      title: x.title,
      primaryUrl: 'https://cdn.jsdelivr.net/gh/libretro-thumbnails/' + idx.repo + '@master/Named_Boxarts/' + enc,
      fallbackUrl: 'https://raw.githubusercontent.com/libretro-thumbnails/' + idx.repo + '/master/Named_Boxarts/' + enc,
    };
  });
}

function scrapeCoverCandidates(id) {
  const g = gamesById[id];
  if (!g) return;
  const area = el('candArea');
  if (!area) return;
  const sys = g.libretroSystem || (Array.isArray(catPlatforms) ? (catPlatforms.find(p => p.id === g.platformId) || {}).libretroSystem : '') || '';
  if (!sys) {
    area.innerHTML = '<div class="sub" style="margin-top:12px;">' + esc(tr('msg_no_cands')) + '</div>';
    return;
  }
  const initKw = g.officialName || g.title || '';
  area.innerHTML =
    '<div style="margin-top:14px;padding-top:12px;border-top:1px solid var(--line);">' +
      '<div style="display:flex;gap:8px;align-items:center;">' +
        '<input id="webCoverKw" class="input" style="flex:1;" value="' + esc(initKw) + '" placeholder="输入英文或中文游戏名搜索封面…" ' +
          'oninput="renderWebCoverSuggest(\'' + escJs(id) + '\',\'' + escJs(sys) + '\')" ' +
          'onkeydown="if(event.key===\'Enter\')runWebCoverSearch(\'' + escJs(id) + '\',\'' + escJs(sys) + '\')"/>' +
        '<button class="btn sm primary" onclick="runWebCoverSearch(\'' + escJs(id) + '\',\'' + escJs(sys) + '\')">' + esc(tr('btn_pick_cover_short')) + '</button>' +
      '</div>' +
      '<div id="webCoverSugs" style="display:flex;flex-wrap:wrap;gap:6px;margin-top:8px;"></div>' +
      '<div id="webCoverStatus" class="sub" style="margin-top:8px;"></div>' +
      '<div id="webCoverGrid" class="cand-grid" style="margin-top:8px;"></div>' +
    '</div>';
  runWebCoverSearch(id, sys);
}

function renderWebCoverSuggest(id, sys) {
  const inp = el('webCoverKw');
  const box = el('webCoverSugs');
  if (!inp || !box) return;
  const sugs = _webSuggestKeywords(inp.value, sys);
  box.innerHTML = sugs.map(s =>
    '<button class="btn sm" style="padding:3px 9px;font-size:11.5px;" onclick="pickWebCoverSuggest(\'' + escJs(id) + '\',\'' + escJs(sys) + '\',\'' + escJs(s) + '\')">' + esc(s) + '</button>'
  ).join('');
}

function pickWebCoverSuggest(id, sys, sug) {
  const inp = el('webCoverKw');
  if (inp) inp.value = sug;
  const box = el('webCoverSugs');
  if (box) box.innerHTML = '';
  runWebCoverSearch(id, sys);
}

const _webHashCoverCache = Object.create(null);
async function runWebCoverSearch(id, sys) {
  const inp = el('webCoverKw');
  const statusEl = el('webCoverStatus');
  const grid = el('webCoverGrid');
  if (!inp || !grid) return;
  const kw = inp.value.trim();
  if (!kw) return;
  grid.innerHTML = '';
  const hashPromise = Array.isArray(_webHashCoverCache[id])
    ? Promise.resolve(_webHashCoverCache[id])
    : api('/api/games/hash-covers?id=' + encodeURIComponent(id))
        .then(r => r.ok ? r.json() : null)
        .then(d => {
          const list = (d && d.ok && Array.isArray(d.candidates)) ? d.candidates : [];
          _webHashCoverCache[id] = list;
          return list;
        })
        .catch(() => []);
  const idx = await _ensureWebLibretroIndex(sys, statusEl);
  const libItems = idx ? _searchWebLibretro(idx, kw, 16) : [];
  const hashRaw = await hashPromise;
  const merged = [];
  const seen = new Set();
  const normKey = u => {
    const s = String(u || '').trim();
    const bi = s.indexOf('/Named_');
    if (bi >= 0) {
      try { return 'libretro|' + decodeURIComponent(s.slice(bi)).toLowerCase(); } catch (e) { return 'libretro|' + s.slice(bi).toLowerCase(); }
    }
    return s.toLowerCase();
  };
  const pushItem = it => {
    const k = normKey(it.primaryUrl);
    if (!k || seen.has(k)) return;
    seen.add(k);
    merged.push(it);
  };
  for (const h of hashRaw) {
    pushItem({
      title: h.title || kw,
      primaryUrl: h.url,
      fallbackUrl: h.fallbackUrl || h.url,
      matchedByHash: true,
    });
  }
  for (const c of libItems) pushItem(c);
  if (!merged.length) {
    if (statusEl) statusEl.textContent = idx ? tr('msg_no_cands') : '无法连接封面目录源（jsDelivr / GitHub），请检查电脑网络。';
    if (idx) renderWebCoverSuggest(id, sys);
    return;
  }
  if (statusEl) statusEl.textContent = '点击应用候选封面（浏览器直连加载，选定后同步到掌机）';
  grid.innerHTML = merged.map(c =>
    '<div class="cand-item" style="position:relative;" onclick="applyWebDirectCover(\'' + escJs(id) + '\',\'' + escJs(c.primaryUrl) + '\',\'' + escJs(c.fallbackUrl) + '\',this)">' +
      (c.matchedByHash ? '<em style="position:absolute;top:5px;left:5px;z-index:2;background:var(--brand);color:#fff;font-style:normal;font-size:10px;font-weight:700;padding:2px 7px;border-radius:99px;box-shadow:0 2px 6px rgba(0,0,0,.22);">Hash</em>' : '') +
      '<img loading="lazy" src="' + esc(c.primaryUrl) + '" data-fb="' + esc(c.fallbackUrl) + '" onerror="if(!this.dataset.tried&&this.dataset.fb&&this.dataset.fb!==this.src){this.dataset.tried=\'1\';this.src=this.dataset.fb;}"/>' +
      '<span title="' + esc(c.title) + '">' + esc(c.title) + '</span>' +
    '</div>'
  ).join('');
}

async function applyWebDirectCover(id, primaryUrl, fallbackUrl, cardEl) {
  toast('浏览器正在下载封面并发送给掌机…');
  if (cardEl) cardEl.style.opacity = '0.55';
  let blob = null;
  let usedUrl = primaryUrl || fallbackUrl || '';
  for (const url of [primaryUrl, fallbackUrl]) {
    if (!url) continue;
    try {
      const resp = await fetch(url, { mode: 'cors' });
      if (resp.ok) { blob = await resp.blob(); usedUrl = url; break; }
    } catch (e) {}
  }
  const uploadUrl = '/api/games/upload-cover?id=' + encodeURIComponent(id) + (usedUrl ? ('&sourceUrl=' + encodeURIComponent(usedUrl)) : '');
  const r = await api(uploadUrl, {
    method: 'POST', body: (blob && blob.size >= 32) ? blob : ''
  });
  if (r.ok) {
    const d = await r.json().catch(() => ({}));
    toast(tr('msg_cover_applied'));
    if (d.game) patchSingleGame(d.game, d.stats); else await loadGames(true);
    openGameDetail(id);
  } else {
    if (cardEl) cardEl.style.opacity = '1';
    toast(tr('msg_cover_fail'));
  }
}

async function uploadCustomCover(id, file) {
  if (!file) return;
  const r = await api('/api/games/upload-cover?id=' + encodeURIComponent(id), {
    method:'POST', body:file
  });
  if (r.ok) {
    const d = await r.json().catch(() => ({}));
    toast(tr('msg_cover_uploaded'));
    if (d.game) patchSingleGame(d.game, d.stats); else await loadGames(true);
    openGameDetail(id);
  } else {
    const j = await r.json().catch(()=>({}));
    toast(tr('msg_upload_fail') + (j.error || r.status));
  }
}

async function resetGameCover(id) {
  const r = await api('/api/games/reset-cover', {
    method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id})
  });
  if (r.ok) {
    const d = await r.json().catch(() => ({}));
    toast(tr('msg_cover_reset'));
    if (d.game) patchSingleGame(d.game, d.stats); else await loadGames(true);
    openGameDetail(id);
  }
}

async function saveGameCats(id) {
  const chks = [...document.querySelectorAll('.gcat-chk')];
  chks.forEach(c => {
    const lbl = c.closest('label');
    if (lbl) lbl.classList.toggle('checked', c.checked);
  });
  const categoryIds = chks.filter(c => c.checked && !c.disabled).map(c => c.value);
  const r = await api('/api/games/categories', {
    method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id, categoryIds})
  });
  if (r.ok) {
    const d = await r.json().catch(() => ({}));
    toast(tr('msg_cats_saved'));
    const bodyEl = el('mBody');
    const prevScroll = bodyEl ? bodyEl.scrollTop : 0;
    if (d.game) {
      patchSingleGame(d.game, d.stats);
      await loadCategories();
    } else {
      await Promise.all([loadCategories(), loadGames(true)]);
    }
    openGameDetail(id);
    if (el('mBody')) el('mBody').scrollTop = prevScroll;
  }
}

async function toggleFav(id, fav, reopen) {
  const r = await api('/api/games/favorite', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id, favorite:fav}) });
  const d = await r.json().catch(() => ({}));
  toast(fav ? tr('msg_fav_added') : tr('msg_fav_removed'));
  if (d.game) patchSingleGame(d.game, d.stats); else await loadGames(true);
  if (reopen) openGameDetail(id);
}

async function renameGame(id, cur) {
  const title = prompt(tr('prompt_rename_game'), cur);
  if (title === null) return;
  const r = await api('/api/games/rename', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id, title}) });
  const d = await r.json().catch(() => ({}));
  toast(tr('msg_game_renamed'));
  if (d.game) patchSingleGame(d.game, d.stats); else await loadGames(true);
  if (el('modalBg').classList.contains('open')) openGameDetail(id);
}

async function toggleHide(id, hidden, reopen) {
  const r = await api('/api/games/hide', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id, hidden}) });
  const d = await r.json().catch(() => ({}));
  toast(hidden ? tr('msg_hidden') : tr('msg_unhidden'));
  if (d.game) patchSingleGame(d.game, d.stats); else await loadGames(true);
  if (curTab === 'files' && curDir) await loadFiles(curDir);
  if (reopen) openGameDetail(id);
}

// ───────────────────────── Categories Tab ─────────────────────────

function markCatDirty(d) {
  catDirty = d;
  const b = el('catDirtyBadge');
  if (b) b.style.display = d ? '' : 'none';
}

async function loadCategories() {
  const d = await (await api('/api/categories')).json();
  if (!d.ok) return;
  catTabs = d.tabs || [];
  catPlatforms = d.platforms || [];
  catSortOrders = d.sortOrders || [];
  markCatDirty(false);

  const gCat = el('gCat');
  const prevCat = gCat.value;
  gCat.innerHTML = '<option value="" data-i18n="cat_all">' + esc(tr('cat_all')) + '</option>' +
    catTabs.filter(t => t.type === 'Custom').map(c => '<option value="' + esc(c.id) + '">' + esc(c.name) + '</option>').join('');
  gCat.value = prevCat;

  renderCategories();
  renderStats();
}

const SORT_LABELS = {
  Recent: tr('sort_Recent'),
  TitleAsc: tr('sort_TitleAsc'),
  TitleDesc: tr('sort_TitleDesc'),
  ReleaseNewest: tr('sort_ReleaseNewest'),
  ReleaseOldest: tr('sort_ReleaseOldest'),
  ModifiedNewest: tr('sort_ModifiedNewest'),
  SizeDesc: tr('sort_SizeDesc'),
  SizeAsc: tr('sort_SizeAsc'),
};

let dragCatIdx = -1;
function renderCategories() {
  const box = el('catList');
  if (!catTabs.length) { box.innerHTML = '<div class="sub">' + esc(tr('cat_none')) + '</div>'; return; }
  box.innerHTML = catTabs.map((t, idx) => {
    const isCustom = t.type === 'Custom';
    const platNames = (t.sources || []).map(sid => {
      const p = catPlatforms.find(x => x.id === sid);
      return p ? p.shortName : sid.toUpperCase();
    }).join(', ');
    const typeTag = isCustom
      ? '<span class="chip">' + esc(tr('cat_custom')) + '</span>'
      : '<span class="chip muted">' + esc(tr('cat_sys') + t.type) + '</span>';
    const sysSortKey = t.sortOrder || (t.id === 'now' ? 'Recent' : 'TitleAsc');
    const desc = isCustom
      ? ((platNames ? tr('cat_plats_pre') + esc(platNames) : tr('cat_manual_only')) + tr('cat_sort_pre') + esc(sortLabel(t.sortOrder || 'TitleAsc')) + ' · ' + (t.gameCount || 0) + tr('cat_games_cnt'))
      : ((t.followSystemLanguage ? tr('cat_follow_sys') : tr('cat_custom_name')) + tr('cat_sort_pre') + esc(sortLabel(sysSortKey)) + ' · ' + (t.gameCount || 0) + tr('cat_games_cnt'));
    return '<div class="cat-row' + (t.hidden ? ' is-hidden' : '') + '" draggable="true" data-cat-idx="' + idx + '">' +
      '<span class="drag-handle" title="Drag">⋮⋮</span>' +
      '<div class="meta">' +
        '<div class="title">' + esc(t.name) + typeTag + (t.hidden ? '<span class="chip warn">' + esc(tr('hidden_tag')) + '</span>' : '') + '</div>' +
        '<div class="desc">' + desc + '</div>' +
      '</div>' +
      '<div class="actions">' +
        '<button class="btn sm" onclick="toggleCatHidden(' + idx + ')">' + esc(t.hidden ? tr('btn_show') : tr('btn_hide')) + '</button>' +
        '<button class="btn sm primary" onclick="openEditCategoryModal(' + idx + ')">' + esc(tr('btn_edit')) + '</button>' +
        (isCustom ? '<button class="btn sm danger" onclick="deleteCatTab(' + idx + ')">' + esc(tr('btn_del')) + '</button>' : '') +
      '</div></div>';
  }).join('');

  const clearIndicators = () => box.querySelectorAll('.cat-row').forEach(r => r.classList.remove('drop-above', 'drop-below', 'dragging'));
  box.querySelectorAll('.cat-row').forEach(row => {
    row.addEventListener('dragstart', e => {
      if (e.target && e.target.closest('button')) { e.preventDefault(); return; }
      dragCatIdx = Number(row.dataset.catIdx);
      row.classList.add('dragging');
      if (e.dataTransfer) {
        e.dataTransfer.effectAllowed = 'move';
        e.dataTransfer.setData('text/plain', String(dragCatIdx));
      }
    });
    row.addEventListener('dragover', e => {
      if (dragCatIdx < 0) return;
      e.preventDefault();
      const targetIdx = Number(row.dataset.catIdx);
      box.querySelectorAll('.cat-row').forEach(r => r.classList.remove('drop-above', 'drop-below'));
      if (targetIdx === dragCatIdx) return;
      const rect = row.getBoundingClientRect();
      const above = e.clientY < rect.top + rect.height / 2;
      row.classList.add(above ? 'drop-above' : 'drop-below');
    });
    row.addEventListener('dragleave', () => {
      row.classList.remove('drop-above', 'drop-below');
    });
    row.addEventListener('drop', e => {
      e.preventDefault();
      if (dragCatIdx < 0) return;
      const targetIdx = Number(row.dataset.catIdx);
      const rect = row.getBoundingClientRect();
      const above = e.clientY < rect.top + rect.height / 2;
      clearIndicators();
      if (Number.isNaN(targetIdx) || dragCatIdx === targetIdx) { dragCatIdx = -1; return; }
      let insertAt = above ? targetIdx : targetIdx + 1;
      const [moved] = catTabs.splice(dragCatIdx, 1);
      if (dragCatIdx < insertAt) insertAt--;
      catTabs.splice(insertAt, 0, moved);
      dragCatIdx = -1;
      renderCategories();
      saveCategoriesToDevice();
    });
    row.addEventListener('dragend', () => {
      dragCatIdx = -1;
      clearIndicators();
    });
  });
}

function toggleCatHidden(idx) {
  catTabs[idx].hidden = !catTabs[idx].hidden;
  renderCategories();
  saveCategoriesToDevice();
}

function deleteCatTab(idx) {
  const t = catTabs[idx];
  if (!t || t.type !== 'Custom') return;
  if (!confirm(tr('cat_del_confirm', { name: t.name }))) return;
  catTabs.splice(idx, 1);
  renderCategories();
  saveCategoriesToDevice();
}

function openNewCategoryModal() {
  openCategoryModalCore(null);
}
function openEditCategoryModal(idx) {
  openCategoryModalCore(idx);
}
function openCategoryModalCore(idx) {
  const isNew = idx === null;
  const tab = isNew
    ? { id:'', type:'Custom', name: tr('cat_new_def') + (catTabs.filter(x=>x.type==='Custom').length + 1), hidden:false, sources:[], games:[], sortOrder:'TitleAsc' }
    : JSON.parse(JSON.stringify(catTabs[idx]));

  if (tab.type !== 'Custom') {
    const sysOpts = (tab.id === 'now' ? ['Recent', ...catSortOrders] : catSortOrders);
    const curSysSort = tab.sortOrder || (tab.id === 'now' ? 'Recent' : 'TitleAsc');
    el('mTitle').textContent = tr('cat_edit_sys') + tab.name;
    el('mBody').innerHTML =
      '<div style="display:flex;flex-direction:column;gap:12px;">' +
        '<div><div class="kv-k" style="margin-bottom:4px;">' + esc(tr('cat_sys_name_lbl')) + '</div>' +
        '<input id="sysTabName" type="text" maxlength="30" style="width:100%" value="' + esc(tab.customName || '') + '"/></div>' +
        '<div><div class="kv-k" style="margin-bottom:4px;">' + esc(tr('cat_sort_lbl')) + '</div>' +
        '<select id="sysTabSort" style="width:100%">' +
          sysOpts.map(s => '<option value="' + esc(s) + '" ' + (s === curSysSort ? 'selected' : '') + '>' + esc(sortLabel(s)) + '</option>').join('') +
        '</select></div>' +
        '<div>' +
          '<label style="display:flex;align-items:center;gap:8px;cursor:pointer;">' +
            '<input id="sysTabFollow" type="checkbox" ' + (tab.followSystemLanguage ? 'checked' : '') + '/>' +
            '<span>' + esc(tr('cat_follow_lbl')) + '</span>' +
          '</label>' +
          '<div class="kv-k" style="margin:4px 0 0 24px;">' + esc(tr('cat_follow_hint')) + '</div>' +
        '</div>' +
      '</div>';
    el('sysTabName').addEventListener('input', () => {
      el('sysTabFollow').checked = !el('sysTabName').value.trim();
    });
    el('mFoot').innerHTML = '<button class="btn" onclick="closeModal()">' + esc(tr('btn_cancel')) + '</button><button class="btn primary" id="saveSysTabBtn">' + esc(tr('btn_apply')) + '</button>';
    el('saveSysTabBtn').onclick = () => {
      const cname = el('sysTabName').value.trim().slice(0, 30);
      const follow = cname ? el('sysTabFollow').checked : true;
      const sortOrder = el('sysTabSort').value;
      catTabs[idx].customName = cname;
      catTabs[idx].followSystemLanguage = follow;
      catTabs[idx].sortOrder = sortOrder;
      catTabs[idx].name = (!follow && cname) ? cname : tab.id.toUpperCase();
      closeModal();
      renderCategories();
      saveCategoriesToDevice();
    };
    el('modalBg').classList.add('open');
    return;
  }

  const sortedPlats = [...catPlatforms].sort((a,b) => (b.inLibrary?1:0) - (a.inLibrary?1:0) || a.shortName.localeCompare(b.shortName));
  el('mTitle').textContent = isNew ? tr('cat_new_title') : (tr('cat_edit_title') + tab.name);
  el('mBody').innerHTML =
    '<div style="display:flex;flex-direction:column;gap:14px;">' +
      '<div style="display:flex;gap:10px;flex-wrap:wrap;">' +
        '<div style="flex:1;min-width:180px;"><div class="kv-k" style="margin-bottom:4px;">' + esc(tr('cat_name_lbl')) + '</div>' +
          '<input id="catNameIn" type="text" maxlength="40" style="width:100%" value="' + esc(tab.name) + '"/></div>' +
        '<div style="min-width:180px;"><div class="kv-k" style="margin-bottom:4px;">' + esc(tr('cat_sort_lbl')) + '</div>' +
          '<select id="catSortIn" style="width:100%">' +
            catSortOrders.map(s => '<option value="' + esc(s) + '" ' + (s === tab.sortOrder ? 'selected' : '') + '>' + esc(sortLabel(s)) + '</option>').join('') +
          '</select></div>' +
      '</div>' +
      '<div>' +
        '<div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:6px;">' +
          '<span style="font-weight:700;font-size:13px;">' + esc(tr('cat_bind_lbl')) + '</span>' +
          '<input id="platFilterIn" type="text" placeholder="' + esc(tr('cat_plat_ph')) + '" style="padding:5px 10px;font-size:12px;" oninput="filterCatPlatList(this.value)"/>' +
        '</div>' +
        '<div id="catPlatGrid" class="plat-grid">' +
          sortedPlats.map(p => {
            const chk = (tab.sources || []).includes(p.id);
            return '<label class="plat-chk' + (chk ? ' checked' : '') + '" data-label="' + esc((p.shortName + ' ' + p.fullName + ' ' + p.id).toLowerCase()) + '">' +
              '<input type="checkbox" class="cat-plat-box" value="' + esc(p.id) + '" ' + (chk ? 'checked' : '') + ' onchange="this.parentElement.classList.toggle(\'checked\', this.checked)"/>' +
              '<span style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;"><strong>' + esc(p.shortName) + '</strong> · ' + esc(p.fullName) + '</span>' +
              (p.inLibrary ? '<span class="chip ok">' + p.gameCount + '</span>' : '') +
            '</label>';
          }).join('') +
        '</div>' +
      '</div>' +
    '</div>';
  el('mFoot').innerHTML = '<button class="btn" onclick="closeModal()">' + esc(tr('btn_cancel')) + '</button><button class="btn primary" id="applyCatBtn">' + esc(tr('btn_confirm')) + '</button>';
  el('applyCatBtn').onclick = () => {
    const nm = el('catNameIn').value.trim();
    if (!nm) { toast(tr('cat_name_empty')); return; }
    const sortOrder = el('catSortIn').value;
    const sources = [...document.querySelectorAll('.cat-plat-box')].filter(x => x.checked).map(x => x.value);
    if (isNew) {
      catTabs.splice(Math.max(0, catTabs.length - 1), 0, {
        id: '', type: 'Custom', name: nm, customName: nm, followSystemLanguage: false,
        hidden: false, sources, games: [], sortOrder, gameCount: 0
      });
    } else {
      catTabs[idx].name = nm;
      catTabs[idx].customName = nm;
      catTabs[idx].sortOrder = sortOrder;
      catTabs[idx].sources = sources;
    }
    closeModal();
    renderCategories();
    saveCategoriesToDevice();
  };
  el('modalBg').classList.add('open');
}

function filterCatPlatList(q) {
  const lq = q.trim().toLowerCase();
  document.querySelectorAll('#catPlatGrid .plat-chk').forEach(node => {
    node.style.display = (!lq || node.dataset.label.includes(lq)) ? '' : 'none';
  });
}

async function saveCategoriesToDevice() {
  const customCats = catTabs.filter(t => t.type === 'Custom').map(t => ({
    id: t.id || '',
    name: t.name,
    sources: t.sources || [],
    games: t.games || [],
    sortOrder: t.sortOrder || 'TitleAsc'
  }));
  const systemTabs = {};
  let nowTab = true, favsTab = true;
  const hiddenTabs = [];
  const tabOrder = [];

  for (const t of catTabs) {
    if (t.id) tabOrder.push(t.id);
    if (t.type === 'Now') {
      nowTab = !t.hidden;
      systemTabs.now = { customName: t.customName || '', followSystemLanguage: !!t.followSystemLanguage, sortOrder: t.sortOrder || 'Recent' };
    } else if (t.type === 'Favs') {
      favsTab = !t.hidden;
      systemTabs.favs = { customName: t.customName || '', followSystemLanguage: !!t.followSystemLanguage, sortOrder: t.sortOrder || 'TitleAsc' };
    } else if (t.type === 'Other') {
      if (t.hidden) hiddenTabs.push('other');
      systemTabs.other = { customName: t.customName || '', followSystemLanguage: !!t.followSystemLanguage, sortOrder: t.sortOrder || 'TitleAsc' };
    } else if (t.hidden && t.id) {
      hiddenTabs.push(t.id);
    }
  }

  const r = await api('/api/categories/save', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ categories: customCats, systemTabs, nowTab, favsTab, hiddenTabs, tabOrder })
  });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) {
    toast(tr('cat_validate_fail') + (d.message || d.error || r.status));
    return;
  }
  toast(tr('cat_synced'));
  await loadCategories();
  await loadGames();
}

// ───────────────────────── File Manager Tab ─────────────────────────

const dirScrollMemory = Object.create(null);

async function loadFiles(dir, keepSel, restoreScroll) {
  const prevDir = curDir;
  const sameDir = dir === prevDir;
  if (!sameDir) {
    if (curTab === 'files') {
      dirScrollMemory[prevDir] = {
        y: window.scrollY,
        filtered: !!((el('fileFilter').value || '').trim()),
      };
    }
    el('fileFilter').value = '';
  }
  const data = await (await api('/api/files?dir=' + encodeURIComponent(dir))).json();
  curDir = data.dir || '';
  parentDir = data.parent || '';
  curEntries = data.entries || [];
  if (keepSel) {
    const valid = new Set(curEntries.map(e => e.path));
    selectedPaths.forEach(p => { if (!valid.has(p)) selectedPaths.delete(p); });
  } else {
    selectedPaths.clear();
  }
  updateBatchBar();

  el('upBtn').disabled = !curDir;
  el('mkdirBtn').disabled = !curDir;
  el('pickUploadLbl').style.display = curDir ? '' : 'none';
  el('pickDirUploadLbl').style.display = curDir ? '' : 'none';
  el('dropzone').style.display = curDir ? '' : 'none';
  el('filesHeaderBar').style.display = curDir ? 'flex' : 'none';
  el('chkAllFiles').checked = false;

  const crumbs = data.breadcrumbs || [];
  let crumbHtml = '<span class="crumb" onclick="loadFiles(\'\',false,true)">' + esc(tr('storage_root')) + '</span>';
  for (const c of crumbs) {
    crumbHtml += ' <span style="color:var(--muted)">›</span> <span class="crumb" onclick="loadFiles(\'' + escJs(c.path) + '\',false,true)">' + esc(c.name) + '</span>';
  }
  el('crumbsBar').innerHTML = crumbHtml;
  el('dirStorageInfo').textContent = (data.freeBytes ? tr('dir_space', { free: fmtBytes(data.freeBytes), total: fmtBytes(data.totalBytes) }) : '');

  renderFiles();
  if (!sameDir) {
    requestAnimationFrame(() => {
      const mem = dirScrollMemory[curDir];
      if (restoreScroll && mem) {
        if (prevDir) {
          const targetIdx = filteredFileEntries.findIndex(e => e.path === prevDir);
          if (targetIdx >= filesRenderedCount) appendMoreFiles(targetIdx + 20);
        }
        if (!mem.filtered) window.scrollTo(0, mem.y || 0);
        if (prevDir) {
          const rows = el('filesList').querySelectorAll('.row');
          for (const row of rows) {
            if (row.dataset.path === prevDir) {
              if (mem.filtered) row.scrollIntoView({ block: 'center' });
              row.classList.add('flash-back');
              setTimeout(() => row.classList.remove('flash-back'), 1400);
              break;
            }
          }
        }
      } else if (!restoreScroll) {
        window.scrollTo(0, 0);
      }
    });
  }
}

function onFileFilterInput() {
  clearTimeout(fileFilterTimer);
  fileFilterTimer = setTimeout(() => renderFiles(), 150);
}

function fileRowHtml(f) {
  const icon = f.isDir ? svgIco('folder', 16) : (f.isArchive ? svgIco('archive', 16) : (f.isImage ? svgIco('image', 16) : (f.isAudio ? svgIco('music', 16) : (f.isFont ? svgIco('type', 16) : svgIco('file', 16)))));
  const romTag = f.isRom ? '<span class="chip">ROM</span>' : '';
  const hiddenTag = (f.isRom && f.isHidden) ? '<span class="chip">' + esc(tr('hidden_tag')) + '</span>' : '';
  const typeTag = f.isArchive ? '<span class="chip">' + esc(tr('tag_zip')) + '</span>' : (f.isImage ? '<span class="chip">' + esc(tr('tag_img')) + '</span>' : (f.isAudio ? '<span class="chip">' + esc(tr('tag_audio')) + '</span>' : (f.isFont ? '<span class="chip">' + esc(tr('tag_font')) + '</span>' : (f.isText ? '<span class="chip">' + esc(tr('tag_text')) + '</span>' : ''))));
  const sub = f.isRoot
    ? (tr('rom_root_lbl') + (f.freeBytes ? ' · ' + tr('free_lbl') + fmtBytes(f.freeBytes) : ''))
    : (f.isDir ? tr('folder_lbl') : fmtBytes(f.size));
  const p = escJs(f.path), n = escJs(f.name);
  const checked = selectedPaths.has(f.path);

  return '<div class="row" data-path="' + esc(f.path) + '">' +
    (!f.isRoot ? '<input type="checkbox" ' + (checked ? 'checked' : '') + ' onchange="toggleFileSelect(\'' + p + '\',this.checked)"/>' : '') +
    '<div style="font-size:19px">' + icon + '</div>' +
    '<div class="meta" style="' + (f.isDir ? 'cursor:pointer' : '') + '" ' + (f.isDir ? 'onclick="loadFiles(\'' + p + '\')"' : '') + '>' +
      '<div class="title">' + esc(f.name) + romTag + hiddenTag + typeTag + '</div>' +
      '<div class="desc">' + sub + '</div>' +
    '</div>' +
    '<div class="actions">' +
      (f.isRom ? '<button class="btn sm" onclick="openGameDetail(\'' + p + '\')">' + esc(tr('btn_game_detail')) + '</button>' : '') +
      (f.isImage ? '<button class="btn sm" onclick="previewImage(\'' + p + '\',\'' + n + '\')">' + esc(tr('btn_preview')) + '</button>' : '') +
      (f.isAudio ? '<button class="btn sm" onclick="playAudio(\'' + p + '\',\'' + n + '\')">' + esc(tr('btn_play')) + '</button>' : '') +
      (f.isText ? '<button class="btn sm" onclick="openTextEditor(\'' + p + '\',\'' + n + '\')">' + esc(tr('btn_view_edit')) + '</button>' : '') +
      (f.isArchive ? '<button class="btn sm primary" onclick="inspectArchive(\'' + p + '\',\'' + n + '\')">' + esc(tr('btn_view_zip')) + '</button>' : '') +
      (!f.isDir ? '<a class="btn sm" href="/api/files/download?path=' + encodeURIComponent(f.path) + '">' + esc(tr('btn_dl')) + '</a>' : '') +
      (!f.isRoot ? '<button class="btn sm" onclick="renameFile(\'' + p + '\',\'' + n + '\')">' + esc(tr('btn_rename')) + '</button>' : '') +
      (!f.isRoot ? '<button class="btn sm" onclick="openMoveCopyModal(\'move\',[\'' + p + '\'],\'' + n + '\')">' + esc(tr('btn_move')) + '</button>' : '') +
      (!f.isRoot ? '<button class="btn sm" onclick="openMoveCopyModal(\'copy\',[\'' + p + '\'],\'' + n + '\')">' + esc(tr('btn_copy')) + '</button>' : '') +
      (!f.isRoot ? '<button class="btn sm danger" onclick="deleteFile(\'' + p + '\',\'' + n + '\',false,' + !!f.isDir + ')">' + esc(tr('btn_del')) + '</button>' : '') +
    '</div></div>';
}

function renderFilesFooter() {
  const ft = el('filesFooter');
  if (!ft) return;
  const total = filteredFileEntries.length;
  if (filesRenderedCount < total) {
    ft.innerHTML = '<span>' + esc(tr('loaded_files_fmt', { loaded: filesRenderedCount, total })) + '</span>' +
      '<button class="btn sm" onclick="appendMoreFiles()">' + esc(tr('btn_load_more')) + '</button>';
  } else {
    ft.innerHTML = '';
  }
}

function ensureFilesObserver() {
  if (filesObserver || typeof IntersectionObserver === 'undefined') return;
  const sentinel = el('filesSentinel');
  if (!sentinel) return;
  filesObserver = new IntersectionObserver(entries => {
    if (entries.some(e => e.isIntersecting) && curTab === 'files' && filesRenderedCount < filteredFileEntries.length) {
      appendMoreFiles();
    }
  }, { rootMargin: '420px 0px' });
  filesObserver.observe(sentinel);
}

function appendMoreFiles(targetCount) {
  const total = filteredFileEntries.length;
  if (filesRenderedCount >= total) return;
  const nextEnd = Math.min(total, targetCount ? Math.max(targetCount, filesRenderedCount + FILE_BATCH_SIZE) : (filesRenderedCount + FILE_BATCH_SIZE));
  const slice = filteredFileEntries.slice(filesRenderedCount, nextEnd);
  el('filesList').insertAdjacentHTML('beforeend', slice.map(fileRowHtml).join(''));
  filesRenderedCount = nextEnd;
  renderFilesFooter();
}

function renderFiles() {
  const filterQ = (el('fileFilter').value || '').trim().toLowerCase();
  const sortBy = el('fileSort').value;
  let entries = curEntries.filter(f => !filterQ || f.name.toLowerCase().includes(filterQ));
  entries.sort((a, b) => {
    if (a.isDir !== b.isDir) return a.isDir ? -1 : 1;
    if (sortBy === 'size') return (b.size || 0) - (a.size || 0);
    if (sortBy === 'modified') return (b.modified || 0) - (a.modified || 0);
    if (sortBy === 'type') {
      const ea = (a.name.split('.').pop() || '').toLowerCase();
      const eb = (b.name.split('.').pop() || '').toLowerCase();
      return ea.localeCompare(eb) || a.name.localeCompare(b.name);
    }
    return a.name.toLowerCase().localeCompare(b.name.toLowerCase());
  });

  filteredFileEntries = entries;
  const listEl = el('filesList');
  if (!entries.length) {
    filesRenderedCount = 0;
    listEl.innerHTML = '<div class="sub" style="padding:18px 0;">' + esc(tr('dir_empty')) + '</div>';
    renderFilesFooter();
    return;
  }
  const initialCount = Math.min(entries.length, FILE_BATCH_SIZE);
  listEl.innerHTML = entries.slice(0, initialCount).map(fileRowHtml).join('');
  filesRenderedCount = initialCount;
  renderFilesFooter();
  ensureFilesObserver();
}

function toggleFileSelect(path, checked) {
  if (checked) selectedPaths.add(path); else selectedPaths.delete(path);
  updateBatchBar();
}
function toggleSelectAllFiles(checked) {
  selectedPaths.clear();
  if (checked) {
    const q = (el('fileFilter') ? el('fileFilter').value : '').trim().toLowerCase();
    curEntries.filter(e => !e.isRoot && (!q || e.name.toLowerCase().includes(q))).forEach(e => selectedPaths.add(e.path));
  }
  updateBatchBar();
  renderFiles();
}
function clearFileSelection() {
  selectedPaths.clear();
  el('chkAllFiles').checked = false;
  updateBatchBar();
  renderFiles();
}
function updateBatchBar() {
  const n = selectedPaths.size;
  const show = n > 0 && curTab === 'files';
  el('batchBar').classList.toggle('show', n > 0);
  el('batchCountText').textContent = tr('selected_n', { n });
  const dock = el('floatDock');
  if (dock) dock.classList.toggle('lift', show);
}

function goParent() { if (curDir) loadFiles(parentDir, false, true); }

async function mkdirPrompt() {
  const name = prompt(tr('prompt_mkdir'));
  if (!name || !name.trim()) return;
  const r = await api('/api/files/mkdir', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({dir:curDir, name:name.trim()}) });
  if (r.ok) { toast(tr('msg_mkdir_ok')); loadFiles(curDir); } else toast(tr('msg_mkdir_fail'));
}
async function renameFile(path, cur) {
  const newName = prompt(tr('prompt_rename_file'), cur);
  if (!newName || !newName.trim() || newName.trim() === cur) return;
  const r = await api('/api/files/rename', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({path, newName:newName.trim()}) });
  if (r.ok) { toast(tr('msg_renamed')); loadFiles(curDir); loadGames(); } else toast(tr('msg_rename_fail'));
}
async function deleteFile(path, name, fromGames, isDir) {
  const warn = isDir ? tr('confirm_del_dir', { name }) : tr('confirm_del_file', { name });
  if (!confirm(warn)) return;
  const r = await api('/api/files/delete', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({path}) });
  if (r.ok) {
    toast(tr('msg_deleted'));
    if (fromGames) closeModal();
    await loadGames();
    if (!fromGames && curDir) loadFiles(curDir);
  } else toast(tr('msg_del_fail'));
}

function openBatchTransfer(mode) {
  const paths = [...selectedPaths];
  if (!paths.length) return;
  openMoveCopyModal(mode, paths, tr('n_items', { n: paths.length }));
}

async function batchDeleteSelected() {
  const paths = [...selectedPaths];
  if (!paths.length) return;
  if (!confirm(tr('confirm_batch_del', { n: paths.length }))) return;
  hadActiveTasks = true;
  const r = await api('/api/files/batch-delete?async=1', {
    method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({paths})
  });
  const j = await r.json().catch(() => ({}));
  if (r.ok && j.ok) {
    clearFileSelection();
    pollFileTasks();
  } else {
    hadActiveTasks = false;
    toast(tr('msg_batch_del_fail') + (j.error || r.status));
  }
}

async function compressSelected() {
  const paths = [...selectedPaths];
  if (!paths.length || !curDir) return;
  const name = prompt(tr('prompt_zip_name'), 'pocketdeck_bundle');
  if (!name || !name.trim()) return;
  hadActiveTasks = true;
  const r = await api('/api/files/compress?async=1', {
    method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({paths, targetDir:curDir, zipName:name.trim()})
  });
  const j = await r.json().catch(() => ({}));
  if (r.ok && j.ok) {
    clearFileSelection();
    pollFileTasks();
  } else {
    hadActiveTasks = false;
    toast(tr('msg_zip_fail') + (j.error || r.status));
  }
}

async function inspectArchive(path, name) {
  el('mTitle').textContent = tr('arc_title') + name;
  el('mBody').innerHTML = '<div class="sub">' + esc(tr('arc_parsing')) + '</div>';
  el('mFoot').innerHTML =
    '<button class="btn" onclick="extractArchive(\'' + escJs(path) + '\',\'' + escJs(name) + '\',\'here\')">' + esc(tr('arc_extract_here')) + '</button>' +
    '<button class="btn primary" onclick="extractArchive(\'' + escJs(path) + '\',\'' + escJs(name) + '\',\'folder\')">' + esc(tr('arc_extract_dir')) + '</button>';
  el('modalBg').classList.add('open');

  const r = await api('/api/files/archive-entries?path=' + encodeURIComponent(path));
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) {
    el('mBody').innerHTML = '<div class="sub" style="color:var(--danger)">' + esc(tr('arc_parse_fail') + (d.error || r.status)) + '</div>';
    return;
  }
  const entries = d.entries || [];
  el('mBody').innerHTML = '<div class="sub" style="margin-bottom:8px;">' + esc(tr('arc_count', { n: entries.length })) + '</div>' +
    '<div class="list">' + entries.slice(0, 300).map(e =>
      '<div class="row" style="padding:7px 10px;"><span>' + (e.isDir ? svgIco('folder', 15) : svgIco('file', 15)) + '</span><span style="flex:1;font-size:12.5px;word-break:break-all;">' + esc(e.name) + '</span><span class="sub">' + (e.isDir ? '' : fmtBytes(e.size)) + '</span></div>'
    ).join('') + '</div>';
}

async function extractArchive(path, name, mode) {
  hadActiveTasks = true;
  const r = await api('/api/files/extract?async=1', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({path, mode}) });
  const j = await r.json().catch(() => ({}));
  if (r.ok && j.ok) {
    closeModal();
    pollFileTasks();
  } else {
    hadActiveTasks = false;
    toast(tr('msg_extract_fail') + (j.error || r.status));
  }
}

let logPollTimer = null;
let logPollPaused = false;
let logFetchCtrl = null;
let logLinesCache = [];
let logFilterKind = 'all';

function stopScrapeLogsPoll() {
  if (logPollTimer) { clearInterval(logPollTimer); logPollTimer = null; }
  if (logFetchCtrl) { try { logFetchCtrl.abort(); } catch {} logFetchCtrl = null; }
}

function setLogFilter(kind) {
  logFilterKind = kind;
  document.querySelectorAll('.log-f-btn').forEach(b => {
    const on = b.dataset.k === kind;
    b.classList.toggle('on', on);
  });
  renderScrapeLogsBody();
}

function formatTerminalLogLine(raw, idx) {
  const parts = raw.split(' | ');
  const head = parts[0] || raw;
  const rest = parts.slice(1);
  const headHtml = esc(head)
    .replace(/^(\[[^\]]+\])/, '<span style="color:#6e7681">$1</span>')
    .replace(/\[(META)\]/g, '<span style="color:#58a6ff;font-weight:700">[META]</span>')
    .replace(/\[(COVER)\]/g, '<span style="color:#d2a8ff;font-weight:700">[COVER]</span>')
    .replace(/\[([a-z0-9_.-]+)\]/gi, (m, g) => (g === 'META' || g === 'COVER') ? m : '<span style="color:#79c0ff">[' + g + ']</span>');
  const restHtml = rest.map(seg => {
    const e = esc(seg);
    if (/^HTTP 2\d\d/.test(seg)) return '<span style="color:#3fb950;font-weight:700">' + e + '</span>';
    if (/^HTTP [45]\d\d/.test(seg) || seg.startsWith('error=')) return '<span style="color:#f85149;font-weight:700">' + e + '</span>';
    if (/^\d+ms$/.test(seg)) return '<span style="color:#d29922">' + e + '</span>';
    if (seg.startsWith('url=')) return '<span style="color:#a5d6ff">' + e + '</span>';
    return '<span style="color:#8b949e">' + e + '</span>';
  }).join('<span style="color:#30363d"> | </span>');
  const lineNo = '<span style="color:#484f58;user-select:none;display:inline-block;min-width:34px;text-align:right;margin-right:10px;">' + (idx + 1) + '</span>';
  return '<div style="padding:2px 0;white-space:pre-wrap;word-break:break-all;">' + lineNo + headHtml + (restHtml ? '<span style="color:#30363d"> | </span>' + restHtml : '') + '</div>';
}

function renderScrapeLogsBody() {
  const box = el('logViewerBox');
  if (!box) return;
  const q = (el('logFilterIn')?.value || '').trim().toLowerCase();
  const filtered = logLinesCache.filter(l => {
    if (logFilterKind === 'cover' && !l.includes('[COVER]')) return false;
    if (logFilterKind === 'meta' && !l.includes('[META]')) return false;
    if (logFilterKind === 'err' && !(l.includes('error=') || l.includes('HTTP 4') || l.includes('HTTP 5'))) return false;
    return !q || l.toLowerCase().includes(q);
  });
  const cntEl = el('logCountTxt');
  if (cntEl) cntEl.textContent = String(filtered.length);
  if (!filtered.length) {
    box.innerHTML = '<div style="padding:18px;color:#6e7681;">' + esc(tr('logs_empty')) + '</div>';
    return;
  }
  box.innerHTML = filtered.map((l, i) => formatTerminalLogLine(l, i)).join('');
}

let logLinesSig = '';
async function pollScrapeLogs() {
  const win = el('logWin');
  if (logPollPaused || !el('logViewerBox') || (win && win.classList.contains('min'))) return;
  if (logFetchCtrl) { try { logFetchCtrl.abort(); } catch {} }
  logFetchCtrl = typeof AbortController !== 'undefined' ? new AbortController() : null;
  try {
    const r = await api('/api/scrape-logs', { cache: 'no-store', signal: logFetchCtrl ? logFetchCtrl.signal : undefined });
    const d = await r.json();
    if (d.ok) {
      const nextLines = d.lines || [];
      const sig = nextLines.length + '|' + (nextLines[0] || '') + '|' + (nextLines[nextLines.length - 1] || '');
      const pEl = el('logPathTxt');
      if (pEl && d.path) pEl.textContent = d.path;
      if (sig !== logLinesSig) {
        logLinesSig = sig;
        logLinesCache = nextLines;
        renderScrapeLogsBody();
      }
    }
  } catch {}
}

function initLogWinDrag() {
  const win = el('logWin'), hd = el('logWinHd');
  if (!win || !hd || hd._dragInit) return;
  hd._dragInit = true;
  let dragging = false, sx = 0, sy = 0, sl = 0, st = 0;
  hd.addEventListener('pointerdown', e => {
    if (e.target.closest('button')) return;
    dragging = true;
    const rect = win.getBoundingClientRect();
    win.style.left = rect.left + 'px';
    win.style.top = rect.top + 'px';
    win.style.right = 'auto';
    win.style.bottom = 'auto';
    sx = e.clientX; sy = e.clientY; sl = rect.left; st = rect.top;
    hd.setPointerCapture(e.pointerId);
  });
  hd.addEventListener('pointermove', e => {
    if (!dragging) return;
    const nl = Math.max(0, Math.min(window.innerWidth - 120, sl + (e.clientX - sx)));
    const nt = Math.max(0, Math.min(window.innerHeight - 44, st + (e.clientY - sy)));
    win.style.left = nl + 'px';
    win.style.top = nt + 'px';
  });
  const endDrag = () => { dragging = false; };
  hd.addEventListener('pointerup', endDrag);
  hd.addEventListener('pointercancel', endDrag);
}

function toggleLogWinMin() {
  const win = el('logWin');
  if (!win) return;
  const isMin = win.classList.toggle('min');
  const btn = el('logMinBtn');
  if (btn) btn.textContent = isMin ? '▢' : '—';
}

function toggleLogPause() {
  logPollPaused = !logPollPaused;
  const pBtn = el('logPauseBtn');
  if (pBtn) pBtn.textContent = tr(logPollPaused ? 'logs_resume' : 'logs_pause');
  const dot = el('logLiveDot');
  if (dot) {
    dot.textContent = logPollPaused ? '❚❚ PAUSED' : '● LIVE';
    dot.style.color = logPollPaused ? '#d29922' : '#3fb950';
  }
  if (!logPollPaused) pollScrapeLogs();
}

async function downloadScrapeLogs() {
  try {
    const r = await api('/api/scrape-logs/download', { cache: 'no-store' });
    if (!r.ok) throw new Error('HTTP ' + r.status);
    const blob = await r.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    const ts = new Date().toISOString().replace(/[:T]/g, '-').slice(0, 19);
    a.href = url;
    a.download = 'pocketdeck-scrape-' + ts + '.log';
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (e) {
    toast(e.message || String(e));
  }
}

async function clearScrapeLogs() {
  await api('/api/scrape-logs/clear', { method: 'POST' });
  logLinesSig = '';
  logLinesCache = [];
  renderScrapeLogsBody();
  toast(tr('logs_cleared'));
}

function closeScrapeLogsWin() {
  stopScrapeLogsPoll();
  const win = el('logWin');
  if (win) win.classList.remove('open', 'min');
}

function openScrapeLogsModal() {
  const win = el('logWin');
  if (!win) return;
  initLogWinDrag();
  win.classList.remove('min');
  if (win.style.left && win.style.top) {
    const l = Math.max(0, Math.min(window.innerWidth - 160, parseFloat(win.style.left) || 0));
    const t = Math.max(0, Math.min(window.innerHeight - 60, parseFloat(win.style.top) || 0));
    win.style.left = l + 'px';
    win.style.top = t + 'px';
  }
  const mBtn = el('logMinBtn');
  if (mBtn) mBtn.textContent = '—';
  el('logWinTitle').textContent = tr('logs_title');
  el('logFAll').textContent = tr('logs_f_all');
  el('logFCover').textContent = tr('logs_f_cover');
  el('logFMeta').textContent = tr('logs_f_meta');
  el('logFErr').textContent = tr('logs_f_err');
  el('logFilterIn').placeholder = tr('logs_filter_ph');
  el('logDownloadBtn').textContent = tr('logs_download');
  el('logClearBtn').textContent = tr('logs_clear');
  logPollPaused = false;
  logLinesSig = '';
  el('logPauseBtn').textContent = tr('logs_pause');
  const dot = el('logLiveDot');
  if (dot) { dot.textContent = '● LIVE'; dot.style.color = '#3fb950'; }
  setLogFilter(logFilterKind || 'all');
  win.classList.add('open');
  stopScrapeLogsPoll();
  pollScrapeLogs();
  logPollTimer = setInterval(pollScrapeLogs, 1200);
}

function closeModal() {
  el('modalBg').classList.remove('open');
  el('mBody').innerHTML = '';
  el('mFoot').innerHTML = '';
}
function previewImage(path, name) {
  el('mTitle').textContent = tr('img_title') + name;
  el('mBody').innerHTML = '<img class="img-view" src="/api/files/raw?path=' + encodeURIComponent(path) + '"/>';
  el('mFoot').innerHTML = '<a class="btn" href="/api/files/download?path=' + encodeURIComponent(path) + '">' + esc(tr('btn_dl_orig')) + '</a><button class="btn primary" onclick="closeModal()">' + esc(tr('btn_close')) + '</button>';
  el('modalBg').classList.add('open');
}
function playAudio(path, name) {
  el('mTitle').textContent = tr('audio_title') + name;
  el('mBody').innerHTML = '<audio controls autoplay style="width:100%;margin:20px 0;" src="/api/files/raw?path=' + encodeURIComponent(path) + '"></audio>';
  el('mFoot').innerHTML = '<a class="btn" href="/api/files/download?path=' + encodeURIComponent(path) + '">' + esc(tr('btn_dl_audio')) + '</a><button class="btn primary" onclick="closeModal()">' + esc(tr('btn_close')) + '</button>';
  el('modalBg').classList.add('open');
}
async function openTextEditor(path, name) {
  const r = await api('/api/files/text?path=' + encodeURIComponent(path));
  const j = await r.json().catch(() => ({}));
  if (!r.ok || !j.ok) { toast(tr('txt_read_fail') + (j.error || r.status)); return; }
  el('mTitle').textContent = tr('txt_title') + name;
  el('mBody').innerHTML = '<textarea id="txtEditor" class="editor"></textarea>';
  el('txtEditor').value = j.content || '';
  el('mFoot').innerHTML = '<button class="btn" onclick="closeModal()">' + esc(tr('btn_cancel')) + '</button><button class="btn primary" id="saveTxtBtn">' + esc(tr('btn_save')) + '</button>';
  el('saveTxtBtn').onclick = async () => {
    const content = el('txtEditor').value;
    const sr = await api('/api/files/text', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({path, content}) });
    if (sr.ok) { toast(tr('msg_file_saved')); closeModal(); } else toast(tr('msg_save_fail'));
  };
  el('modalBg').classList.add('open');
}

let transferPaths = [];
let transferMode = 'move';
let transferBrowseDir = '';
async function openMoveCopyModal(mode, paths, label) {
  transferMode = mode;
  transferPaths = paths;
  transferBrowseDir = curDir || '';
  el('mTitle').textContent = tr(mode === 'copy' ? 'tx_copy_title' : 'tx_move_title', { name: label });
  el('modalBg').classList.add('open');
  await renderTransferBrowser(transferBrowseDir);
}
async function renderTransferBrowser(dir) {
  transferBrowseDir = dir;
  const r = await api('/api/files?dir=' + encodeURIComponent(dir));
  const data = await r.json();
  const pathSet = new Set(transferPaths);
  const dirs = (data.entries || []).filter(e => e.isDir && !pathSet.has(e.path));
  const upHtml = data.dir ? '<div class="dir-pick" onclick="renderTransferBrowser(\'' + escJs(data.parent || '') + '\')"><span>' + esc(tr('files_up')) + '</span><span class="sub">..</span></div>' : '';
  const listHtml = dirs.map(d => '<div class="dir-pick" onclick="renderTransferBrowser(\'' + escJs(d.path) + '\')"><span>' + esc(d.name) + '</span><span>›</span></div>').join('');
  const newFolderBtn = data.dir ? '<button class="btn sm" onclick="mkdirInTransfer()">' + esc(tr('tx_mkdir_here')) + '</button>' : '';
  el('mBody').innerHTML =
    '<div style="display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:10px;flex-wrap:wrap;">' +
      '<div class="sub">' + esc(tr('tx_target_dir')) + esc(data.dir || tr('tx_pick_root')) + '</div>' + newFolderBtn +
    '</div>' +
    upHtml + (listHtml || '<div class="sub">' + esc(tr('tx_no_subdirs')) + '</div>');
  el('mFoot').innerHTML = '<button class="btn" onclick="closeModal()">' + esc(tr('btn_cancel')) + '</button>' +
    (data.dir ? '<button class="btn primary" id="confirmTxBtn">' + esc(tr(transferMode === 'copy' ? 'tx_copy_here' : 'tx_move_here')) + '</button>' : '');
  const btn = el('confirmTxBtn');
  if (btn) btn.onclick = async () => {
    const endpoint = (transferMode === 'copy' ? '/api/files/batch-copy' : '/api/files/batch-move') + '?async=1';
    hadActiveTasks = true;
    const mr = await api(endpoint, { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({paths:transferPaths, targetDir:data.dir}) });
    const mj = await mr.json().catch(() => ({}));
    if (mr.ok && mj.ok) {
      clearFileSelection();
      closeModal();
      pollFileTasks();
    } else {
      hadActiveTasks = false;
      toast(tr('msg_op_fail') + (mj.error || mr.status));
    }
  };
}
async function mkdirInTransfer() {
  const name = prompt(tr('prompt_mkdir'));
  if (!name || !name.trim()) return;
  const r = await api('/api/files/mkdir', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({dir:transferBrowseDir, name:name.trim()}) });
  if (r.ok) renderTransferBrowser(transferBrowseDir); else toast(tr('msg_mkdir_fail'));
}

async function rescan() {
  await api('/api/rescan', { method:'POST' });
  toast(tr('msg_rescan_ok'));
  setTimeout(() => { loadGames(); loadCategories(); }, 1300);
}
async function logout() {
  await fetch('/api/logout', { method:'POST' });
  location.reload();
}

let activeUploadCtrl = null;
function cancelWebUpload() {
  if (!activeUploadCtrl) return;
  activeUploadCtrl.cancelled = true;
  try { if (activeUploadCtrl.xhr) activeUploadCtrl.xhr.abort(); } catch {}
}

async function uploadFileItems(items) {
  if (!curDir || !items || !items.length) return;
  if (activeUploadCtrl) { toast(tr('msg_upload_busy')); return; }
  const wrap = el('progWrap'), bar = el('progBar'), txt = el('progText'), cancelBox = el('progCancel');
  const ctrl = { cancelled: false, xhr: null };
  activeUploadCtrl = ctrl;
  wrap.style.display = 'block';
  cancelBox.style.display = 'block';
  hadActiveTasks = true;
  scheduleTaskPoll(250);
  const targetDir = curDir;
  let uploadedCount = 0;
  for (let i = 0; i < items.length; i++) {
    if (ctrl.cancelled) break;
    const { file, subpath } = items[i];
    const ok = await new Promise((resolve) => {
      const xhr = new XMLHttpRequest();
      ctrl.xhr = xhr;
      let url = '/api/files/upload?dir=' + encodeURIComponent(targetDir) + '&name=' + encodeURIComponent(file.name);
      if (subpath) url += '&subpath=' + encodeURIComponent(subpath);
      xhr.open('POST', url);
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) {
          const pct = Math.round(e.loaded * 100 / e.total);
          bar.style.width = pct + '%';
          txt.textContent = '(' + (i+1) + '/' + items.length + ') ' + (subpath ? subpath + '/' : '') + file.name + ' · ' + pct + '% (' + fmtBytes(e.loaded) + '/' + fmtBytes(e.total) + ')';
        }
      };
      xhr.onload = () => {
        if (xhr.status === 401) { location.reload(); resolve(false); return; }
        resolve(xhr.status === 200);
      };
      xhr.onerror = () => resolve(false);
      xhr.onabort = () => resolve(false);
      xhr.send(file);
    });
    ctrl.xhr = null;
    if (ctrl.cancelled) break;
    if (ok) uploadedCount++;
    else toast(tr('msg_upload_fail') + file.name);
  }
  const wasCancelled = ctrl.cancelled;
  activeUploadCtrl = null;
  bar.style.width = '0%'; wrap.style.display = 'none'; txt.textContent = ''; cancelBox.style.display = 'none';
  el('fileInput').value = ''; el('dirInput').value = '';
  if (wasCancelled) toast(tr('msg_upload_cancelled'));
  else if (uploadedCount > 0) toast(tr('msg_upload_done', { n: uploadedCount }));
  loadFiles(curDir, true); loadGames();
  pollFileTasks();
}

function uploadFiles(fileList) {
  const items = [...(fileList || [])].map(file => ({ file, subpath: '' }));
  uploadFileItems(items);
}
function uploadFolderFiles(fileList) {
  const items = [...(fileList || [])].map(file => {
    const rel = (file.webkitRelativePath || '').split('/');
    const subpath = rel.length > 1 ? rel.slice(0, -1).join('/') : '';
    return { file, subpath };
  });
  uploadFileItems(items);
}

async function readDropEntries(dataTransfer) {
  const items = dataTransfer.items;
  if (!items || !items.length || !items[0].webkitGetAsEntry) {
    return [...(dataTransfer.files || [])].map(file => ({ file, subpath: '' }));
  }
  const out = [];
  async function walk(entry, prefix) {
    if (entry.isFile) {
      await new Promise(res => entry.file(file => { out.push({ file, subpath: prefix }); res(); }, () => res()));
    } else if (entry.isDirectory) {
      const nextPrefix = prefix ? (prefix + '/' + entry.name) : entry.name;
      const reader = entry.createReader();
      let batch;
      do {
        batch = await new Promise(res => reader.readEntries(res, () => res([])));
        for (const child of batch) await walk(child, nextPrefix);
      } while (batch.length > 0);
    }
  }
  const entries = [...items].map(it => it.webkitGetAsEntry()).filter(Boolean);
  for (const en of entries) await walk(en, '');
  return out;
}

const dz = el('dropzone');
dz.ondragover = (e) => { e.preventDefault(); dz.classList.add('drag'); };
dz.ondragleave = () => dz.classList.remove('drag');
dz.ondrop = async (e) => {
  e.preventDefault(); dz.classList.remove('drag');
  const items = await readDropEntries(e.dataTransfer);
  uploadFileItems(items);
};

function esc(s) { return String(s||'').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
function escJs(s) {
  return String(s||'')
    .replace(/\\/g, '\\\\')
    .replace(/'/g, '\\x27')
    .replace(/"/g, '\\x22')
    .replace(/</g, '\\x3c')
    .replace(/>/g, '\\x3e')
    .replace(/&/g, '\\x26')
    .replace(/\n/g, '\\n')
    .replace(/\r/g, '\\r')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029');
}
// ── I7: Batch Operations & Drag-and-Drop Platform ROM Upload ──
(function initBatchAndPlatformDrop() {
  const tabGames = el('tab-games');
  const gamesContainer = el('gamesContainer');
  if (!tabGames || !gamesContainer) return;

  let batchMode = false;
  const selectedIds = new Set();

  const bar = document.createElement('div');
  bar.id = 'batchOpsBar';
  bar.style.cssText = 'display:flex;flex-wrap:wrap;align-items:center;gap:8px;margin:8px 0 12px 0;padding:10px 14px;border-radius:14px;background:var(--surface,#fff);border:1px solid var(--line,rgba(0,0,0,0.08));';
  const isZh = (curLang || 'zh-CN').startsWith('zh');
  bar.innerHTML =
    '<button id="btnBatchToggle" class="btn sm">' + (isZh ? '☑ 批量选择' : '☑ Batch Select') + '</button>' +
    '<span id="batchControls" style="display:none;flex-wrap:wrap;align-items:center;gap:6px;flex:1;">' +
      '<span id="batchCountLbl" style="font-size:12px;font-weight:700;color:var(--accent,#E9327A);padding:0 4px;">' + (isZh ? '已选 0 项' : '0 selected') + '</span>' +
      '<button id="btnBatchAll" class="btn sm">' + (isZh ? '全选' : 'Select All') + '</button>' +
      '<button id="btnBatchHide" class="btn sm">' + (isZh ? '隐藏' : 'Hide') + '</button>' +
      '<button id="btnBatchUnhide" class="btn sm">' + (isZh ? '取消隐藏' : 'Unhide') + '</button>' +
      '<button id="btnBatchRescrape" class="btn sm">' + (isZh ? '重新刮削' : 'Rescrape') + '</button>' +
      '<select id="batchCatSel" style="padding:5px 10px;font-size:12px;border-radius:999px;border:1px solid var(--line,#ddd);background:var(--surface,#fff);color:var(--text,#222);min-width:88px;"></select>' +
      '<button id="btnBatchCat" class="btn sm primary">' + (isZh ? '+ 加入分类' : '+ Category') + '</button>' +
    '</span>' +
    '<div id="platDropZone" style="margin-left:auto;display:flex;align-items:center;gap:6px;padding:5px 10px;border-radius:10px;border:1px dashed var(--accent,#E9327A);font-size:12px;cursor:pointer;">' +
      '<span>' + (isZh ? '拖入 ROM →' : 'Drop ROMs →') + '</span>' +
      '<select id="platUploadSel" style="padding:3px 8px;font-size:12px;border-radius:999px;border:1px solid var(--line,#ddd);background:var(--surface,#fff);color:var(--text,#222);">' +
        '<option value="gba">gba</option><option value="snes">snes</option><option value="nes">nes</option><option value="nds">nds</option><option value="psp">psp</option><option value="psx">psx</option><option value="ps2">ps2</option><option value="n64">n64</option><option value="gb">gb</option><option value="gbc">gbc</option><option value="megadrive">megadrive</option><option value="arcade">arcade</option><option value="switch">switch</option><option value="3ds">3ds</option><option value="gamecube">gamecube</option>' +
      '</select>' +
      '<input id="platUploadInput" type="file" multiple style="display:none"/>' +
    '</div>';
  tabGames.insertBefore(bar, gamesContainer);

  function syncBatchUi() {
    const zh = (curLang || 'zh-CN').startsWith('zh');
    el('batchControls').style.display = batchMode ? 'flex' : 'none';
    const tog = el('btnBatchToggle');
    tog.textContent = batchMode ? (zh ? '✕ 退出批量' : '✕ Exit Batch') : (zh ? '☑ 批量选择' : '☑ Batch Select');
    tog.className = batchMode ? 'btn sm primary' : 'btn sm';
    el('batchCountLbl').textContent = zh ? ('已选 ' + selectedIds.size + ' 项') : (selectedIds.size + ' selected');
    const sel = el('batchCatSel');
    if (sel && typeof catTabs !== 'undefined' && Array.isArray(catTabs)) {
      const customCats = catTabs.filter(c => c.type === 'Custom');
      const prev = sel.value;
      sel.innerHTML = customCats.length
        ? customCats.map(c => '<option value="' + esc(c.id) + '">' + esc(c.name) + '</option>').join('')
        : '<option value="">' + (zh ? '暂无分类' : 'No categories') + '</option>';
      if (prev && customCats.some(c => c.id === prev)) sel.value = prev;
    }
    gamesContainer.querySelectorAll('[data-gid]').forEach(node => {
      const gid = node.getAttribute('data-gid');
      const on = batchMode && selectedIds.has(gid);
      node.style.outline = on ? '2.5px solid var(--accent,#E9327A)' : '';
      node.style.outlineOffset = on ? '2px' : '';
    });
  }

  el('btnBatchToggle').onclick = () => {
    batchMode = !batchMode;
    if (!batchMode) selectedIds.clear();
    syncBatchUi();
  };
  el('btnBatchAll').onclick = () => {
    gamesContainer.querySelectorAll('[data-gid]').forEach(n => selectedIds.add(n.getAttribute('data-gid')));
    syncBatchUi();
  };

  async function runBatch(action, extra) {
    if (!selectedIds.size) return;
    const payload = Object.assign({ action, ids: [...selectedIds] }, extra || {});
    const r = await api('/api/games/batch', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
    if (r && r.ok) {
      toast('Updated ' + (r.count || selectedIds.size) + ' games');
      selectedIds.clear();
      batchMode = false;
      syncBatchUi();
      if (typeof loadGames === 'function') loadGames(true);
    }
  }
  el('btnBatchHide').onclick = () => runBatch('hide');
  el('btnBatchUnhide').onclick = () => runBatch('unhide');
  el('btnBatchRescrape').onclick = () => runBatch('rescrape');
  el('btnBatchCat').onclick = () => {
    const cid = el('batchCatSel').value;
    if (cid) runBatch('setCategories', { categoryIds: [cid] });
  };

  gamesContainer.addEventListener('click', (e) => {
    if (!batchMode) return;
    const card = e.target.closest('[data-gid]');
    if (!card) return;
    e.preventDefault();
    e.stopPropagation();
    const gid = card.getAttribute('data-gid');
    if (selectedIds.has(gid)) selectedIds.delete(gid); else selectedIds.add(gid);
    syncBatchUi();
  }, true);

  async function uploadToPlatform(fileList) {
    const plat = el('platUploadSel').value || 'misc';
    const files = [...fileList];
    if (!files.length) return;
    let okCount = 0;
    for (const f of files) {
      const url = '/api/upload?platform=' + encodeURIComponent(plat) + '&name=' + encodeURIComponent(f.name);
      const resp = await fetch(url, { method: 'POST', body: f });
      if (resp.ok) okCount++;
    }
    toast('Uploaded ' + okCount + '/' + files.length + ' ROM(s) to ' + plat);
    if (typeof loadGames === 'function') loadGames(true);
  }

  const pdz = el('platDropZone');
  const pin = el('platUploadInput');
  pdz.onclick = (e) => { if (e.target.tagName !== 'SELECT') pin.click(); };
  pin.onchange = () => { uploadToPlatform(pin.files); pin.value = ''; };
  [pdz, gamesContainer].forEach(target => {
    target.addEventListener('dragover', (e) => { e.preventDefault(); pdz.style.background = 'rgba(233,50,122,0.12)'; });
    target.addEventListener('dragleave', () => { pdz.style.background = ''; });
    target.addEventListener('drop', (e) => {
      if (!e.dataTransfer || !e.dataTransfer.files || !e.dataTransfer.files.length) return;
      e.preventDefault();
      pdz.style.background = '';
      uploadToPlatform(e.dataTransfer.files);
    });
  });
})();

loadInit();
pollFileTasks();
</script>
</body>
</html>"""
    }
}
