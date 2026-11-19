package com.pocketdeck.launcher.lan

import org.json.JSONObject

/**
 * PocketDeck Web 管理后台前端界面（游戏风格 UI，100% 自包含零外网请求，支持实时跟随 App 亮/暗主题与主题色）。
 */
internal object WebManagerUi {

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
        val headerBg = if (dark) "rgba(22,22,28,.86)" else "rgba(255,255,255,.88)"
        val cardGlow = if (dark) "rgba(0,0,0,.45)" else "rgba(18,24,40,.08)"
        val inputBg = if (dark) "#101014" else "#FFFFFF"
        return """
            --accent:$primary;
            --glow:$glow;
            --tertiary:$tertiary;
            --bg:$bg;
            --panel:$panel;
            --surf:$surf;
            --border:$border;
            --text:$text;
            --muted:$muted;
            --header-bg:$headerBg;
            --shadow:$cardGlow;
            --input-bg:$inputBg;
            --danger:#e11d48;
            --ok:#10b981;
            --warn:#f59e0b;
        """.trimIndent().replace("\n", "")
    }

    fun renderLoginHtml(theme: WebThemeConfig): String {
        val vars = cssVars(theme)
        val darkAttr = if (theme.isDark) "1" else "0"
        return """<!doctype html>
<html lang="zh-CN" data-dark="$darkAttr">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>PocketDeck OS · Login</title>
<style>
  :root { $vars }
  * { box-sizing:border-box; }
  body {
    margin:0; min-height:100vh; display:grid; place-items:center;
    background:
      radial-gradient(circle at 15% 15%, color-mix(in srgb, var(--accent) 18%, transparent), transparent 46%),
      radial-gradient(circle at 85% 85%, color-mix(in srgb, var(--glow) 15%, transparent), transparent 48%),
      var(--bg);
    color:var(--text);
    font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;
    padding:20px;
    transition:background .25s ease, color .25s ease;
  }
  .deck-card {
    width:100%; max-width:410px;
    background:var(--panel);
    border:1.5px solid var(--border);
    border-radius:24px;
    padding:30px 28px 26px;
    box-shadow:0 24px 60px var(--shadow);
    position:relative; overflow:hidden;
  }
  .deck-card::before {
    content:""; position:absolute; top:0; left:0; right:0; height:4px;
    background:linear-gradient(90deg, var(--accent), var(--glow), var(--tertiary));
  }
  .logo-row { display:flex; align-items:center; gap:12px; margin-bottom:14px; }
  .logo-badge {
    width:44px; height:44px; border-radius:14px;
    background:linear-gradient(135deg, var(--accent), var(--glow));
    display:grid; place-items:center; color:#fff; flex-shrink:0;
    box-shadow:0 8px 20px color-mix(in srgb, var(--accent) 35%, transparent);
  }
  .brand-title { font-size:20px; font-weight:800; letter-spacing:-.3px; }
  .brand-sub { font-size:12px; color:var(--muted); margin-top:2px; }
  p { margin:0 0 20px; color:var(--muted); font-size:13px; line-height:1.55; }
  .pin-wrap { position:relative; }
  input {
    width:100%; padding:14px 16px; border-radius:14px;
    border:1.5px solid var(--border); background:var(--input-bg); color:var(--text);
    font-size:24px; font-weight:700; letter-spacing:8px; text-align:center; outline:none;
    transition:border-color .15s, box-shadow .15s;
  }
  input:focus {
    border-color:var(--accent);
    box-shadow:0 0 0 3px color-mix(in srgb, var(--accent) 22%, transparent);
  }
  button {
    width:100%; margin-top:16px; padding:13px; border:0; border-radius:14px;
    background:linear-gradient(135deg, var(--accent), var(--glow));
    color:#fff; font-weight:700; font-size:15px; cursor:pointer;
    box-shadow:0 8px 22px color-mix(in srgb, var(--accent) 30%, transparent);
    transition:transform .12s, opacity .15s;
  }
  button:hover { transform:translateY(-1px); }
  button:active { transform:translateY(0); }
  button:disabled { opacity:.6; cursor:default; transform:none; }
  .err { margin-top:12px; color:var(--danger); font-size:13px; text-align:center; min-height:18px; font-weight:600; }
  .hint-pills { display:flex; justify-content:center; gap:8px; margin-top:16px; }
  .pill { font-size:11px; color:var(--muted); background:var(--surf); border:1px solid var(--border); padding:3px 10px; border-radius:999px; }
</style>
</head>
<body>
<div class="deck-card">
  <div class="logo-row">
    <div class="logo-badge">
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
        <rect x="2" y="6" width="20" height="12" rx="4"/>
        <path d="M7 10v4M5 12h4"/>
        <circle cx="15.5" cy="11" r="1" fill="currentColor"/>
        <circle cx="17.5" cy="13" r="1" fill="currentColor"/>
      </svg>
    </div>
    <div>
      <div class="brand-title">PocketDeck OS</div>
      <div class="brand-sub">Handheld Web Game Library</div>
    </div>
    <select id="langSel" onchange="setLoginLang(this.value)" style="margin-left:auto;appearance:none;-webkit-appearance:none;-moz-appearance:none;border:1px solid var(--border);background-color:var(--surf);color:var(--text);border-radius:999px;padding:6px 30px 6px 12px;font-size:12px;cursor:pointer;outline:none;background-image:linear-gradient(45deg,transparent 50%,var(--muted) 50%),linear-gradient(135deg,var(--muted) 50%,transparent 50%);background-position:calc(100% - 15px) 52%,calc(100% - 10px) 52%;background-size:5px 5px,5px 5px;background-repeat:no-repeat;">
      <option value="zh-CN">简体中文</option>
      <option value="zh-TW">繁體中文</option>
      <option value="en">English</option>
    </select>
  </div>
  <p id="promptTxt"></p>
  <form id="f">
    <div class="pin-wrap">
      <input id="pwd" type="password" inputmode="numeric" maxlength="4" placeholder="••••" autofocus autocomplete="off"/>
    </div>
    <button id="btn" type="submit"></button>
    <div id="err" class="err"></div>
  </form>
  <div class="hint-pills">
    <span id="pillLan" class="pill"></span>
    <span id="pillTheme" class="pill"></span>
  </div>
</div>
<script>
const LOGIN_I18N = {
  'zh-CN': {
    title: 'PocketDeck OS · 登录',
    prompt: '请输入掌机屏幕上显示的动态访问密码，连接后即可在浏览器管理游戏封面、元数据、标签分类与文件库。',
    btn: '登录',
    lan: '🔒 局域网直连',
    theme: '🎨 主题实时同步',
    tooMany: s => '尝试过多，请 ' + s + ' 秒后重试',
    remain: n => '密码错误，还可尝试 ' + n + ' 次',
    invalid: '动态密码错误，请核对掌机屏幕',
    conn: '连接失败，请确认与掌机在同一局域网'
  },
  'zh-TW': {
    title: 'PocketDeck OS · 登入',
    prompt: '請輸入掌機螢幕上顯示的動態存取密碼，連線後即可在瀏覽器管理遊戲封面、中繼資料、標籤分類與檔案庫。',
    btn: '登入',
    lan: '🔒 區域網路直連',
    theme: '🎨 主題即時同步',
    tooMany: s => '嘗試過多，請 ' + s + ' 秒後重試',
    remain: n => '密碼錯誤，還可嘗試 ' + n + ' 次',
    invalid: '動態密碼錯誤，請核對掌機螢幕',
    conn: '連線失敗，請確認與掌機在同一區域網路'
  },
  en: {
    title: 'PocketDeck OS · Login',
    prompt: 'Enter the dynamic PIN shown on your handheld screen to manage covers, metadata, categories, and files.',
    btn: 'Log In',
    lan: '🔒 LAN Direct',
    theme: '🎨 Live Theme Sync',
    tooMany: s => 'Too many attempts. Retry in ' + s + 's',
    remain: n => 'Incorrect PIN. ' + n + ' attempts left',
    invalid: 'Incorrect PIN. Check your handheld screen.',
    conn: 'Connection failed. Ensure you are on the same LAN.'
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
  document.getElementById('promptTxt').textContent = L.prompt;
  document.getElementById('btn').textContent = L.btn;
  document.getElementById('pillLan').textContent = L.lan;
  document.getElementById('pillTheme').textContent = L.theme;
}
setLoginLang(curLang);

function applyThemeObj(t){
  if(!t) return;
  const r = document.documentElement;
  r.dataset.dark = t.isDark ? '1' : '0';
  r.style.setProperty('--accent', t.primary);
  r.style.setProperty('--glow', t.glow);
  r.style.setProperty('--tertiary', t.tertiary);
  r.style.setProperty('--bg', t.bg);
  r.style.setProperty('--panel', t.panel);
  r.style.setProperty('--surf', t.surf);
  r.style.setProperty('--border', t.border);
  r.style.setProperty('--text', t.text);
  r.style.setProperty('--muted', t.muted);
  r.style.setProperty('--input-bg', t.isDark ? '#101014' : '#FFFFFF');
  r.style.setProperty('--shadow', t.isDark ? 'rgba(0,0,0,.45)' : 'rgba(18,24,40,.08)');
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
  const L = LOGIN_I18N[curLang] || LOGIN_I18N['zh-CN'];
  try {
    const r = await fetch('/api/login', {
      method: 'POST',
      headers: {'Content-Type':'application/json'},
      body: JSON.stringify({password})
    });
    const d = await r.json();
    if (r.ok && d.ok) { location.reload(); return; }
    if (r.status === 429) {
      err.textContent = L.tooMany(d.retryAfterSec || 30);
    } else if (typeof d.remainingAttempts === 'number') {
      err.textContent = L.remain(d.remainingAttempts);
    } else {
      err.textContent = L.invalid;
    }
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
<html lang="zh-CN" data-dark="$darkAttr">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>PocketDeck · Web Game Console</title>
<style>
  :root { $vars }
  * { box-sizing:border-box; }
  body {
    margin:0;
    background:
      radial-gradient(ellipse 80% 45% at 15% 0%, color-mix(in srgb, var(--accent) 11%, transparent), transparent 60%),
      radial-gradient(ellipse 65% 40% at 90% 100%, color-mix(in srgb, var(--glow) 9%, transparent), transparent 60%),
      var(--bg);
    color:var(--text);
    font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;
    min-height:100vh;
    transition:background .25s ease, color .25s ease;
  }
  header {
    position:sticky; top:0; z-index:20;
    background:var(--header-bg); backdrop-filter:blur(14px);
    border-bottom:1.5px solid var(--border);
    padding:12px 20px; display:flex; align-items:center; gap:12px; flex-wrap:wrap;
  }
  .brand-wrap { display:flex; align-items:center; gap:11px; margin-right:auto; }
  .logo-sq {
    width:36px; height:36px; border-radius:11px;
    background:linear-gradient(135deg, var(--accent), var(--glow));
    display:grid; place-items:center; color:#fff; flex-shrink:0;
    box-shadow:0 6px 16px color-mix(in srgb, var(--accent) 32%, transparent);
  }
  .brand { font-weight:800; font-size:16px; letter-spacing:-.2px; display:flex; align-items:center; gap:8px; }
  .sub { font-size:12px; color:var(--muted); margin-top:1px; display:flex; align-items:center; gap:6px; flex-wrap:wrap; }
  .conn-dot { width:8px; height:8px; border-radius:999px; display:inline-block; flex-shrink:0; }
  .conn-dot.ok { background:var(--ok); box-shadow:0 0 6px color-mix(in srgb, var(--ok) 65%, transparent); }
  .conn-dot.off { background:var(--danger); box-shadow:0 0 6px color-mix(in srgb, var(--danger) 65%, transparent); }
  .conn-off-txt { color:var(--danger); font-weight:600; }
  .tabs {
    display:flex; background:var(--surf); padding:4px; border-radius:999px;
    border:1px solid var(--border); gap:4px;
  }
  .tab {
    border:0; background:transparent; color:var(--muted);
    padding:7px 15px; border-radius:999px; font-weight:700; font-size:13px;
    cursor:pointer; transition:.15s;
  }
  .tab:hover { color:var(--text); }
  .tab.active {
    background:linear-gradient(135deg, var(--accent), var(--glow));
    color:#fff;
    box-shadow:0 4px 12px color-mix(in srgb, var(--accent) 32%, transparent);
  }
  .btn {
    border:1px solid var(--border); background:var(--surf); color:var(--text);
    padding:7px 12px; border-radius:11px; font-size:12.5px; font-weight:600;
    cursor:pointer; text-decoration:none; display:inline-flex; align-items:center; gap:5px;
    transition:.14s;
  }
  .btn:hover:not(:disabled) { border-color:var(--accent); transform:translateY(-1px); }
  .btn:disabled { opacity:.45; cursor:default; transform:none; }
  .btn.primary {
    background:linear-gradient(135deg, var(--accent), var(--glow));
    color:#fff; border-color:transparent;
    box-shadow:0 4px 14px color-mix(in srgb, var(--accent) 28%, transparent);
  }
  .btn.danger { color:var(--danger); border-color:color-mix(in srgb, var(--danger) 38%, var(--border)); }
  .btn.sm { padding:5px 10px; font-size:12px; border-radius:9px; }

  .wrap { max-width:1240px; margin:0 auto; padding:18px 20px 80px; }

  /* Stats strip */
  .stats-grid { display:grid; grid-template-columns:repeat(auto-fit, minmax(150px, 1fr)); gap:10px; margin-bottom:16px; }
  .stat-card {
    background:var(--panel); border:1px solid var(--border); border-radius:14px;
    padding:12px 14px; display:flex; align-items:center; gap:10px;
    box-shadow:0 4px 14px var(--shadow);
  }
  .stat-ico {
    width:34px; height:34px; border-radius:10px;
    background:color-mix(in srgb, var(--accent) 14%, transparent);
    color:var(--accent); display:grid; place-items:center; font-size:16px; flex-shrink:0;
  }
  .stat-val { font-size:17px; font-weight:800; line-height:1.15; }
  .stat-lbl { font-size:11px; color:var(--muted); margin-top:2px; }

  .toolbar { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:14px; align-items:center; }
  .search, select, input[type=text] {
    padding:8px 12px; border-radius:11px; border:1px solid var(--border);
    background-color:var(--input-bg); color:var(--text); outline:none; font-size:13px;
  }
  select {
    appearance:none; -webkit-appearance:none; -moz-appearance:none;
    padding:8px 34px 8px 14px; cursor:pointer;
    background-image:linear-gradient(45deg, transparent 50%, var(--muted) 50%), linear-gradient(135deg, var(--muted) 50%, transparent 50%);
    background-position:calc(100% - 17px) 52%, calc(100% - 12px) 52%;
    background-size:5px 5px, 5px 5px;
    background-repeat:no-repeat;
  }
  .search { flex:1; min-width:200px; }
  .search:focus, select:focus, input[type=text]:focus { border-color:var(--accent); }

  /* Games Grid */
  .game-grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(178px, 1fr)); gap:14px; }
  .gcard {
    background:var(--panel); border:1.5px solid var(--border); border-radius:16px;
    overflow:hidden; display:flex; flex-direction:column; cursor:pointer;
    transition:transform .16s, border-color .16s, box-shadow .16s;
    box-shadow:0 6px 18px var(--shadow); position:relative;
  }
  .gcard:hover {
    transform:translateY(-3px);
    border-color:var(--accent);
    box-shadow:0 12px 28px color-mix(in srgb, var(--accent) 20%, var(--shadow));
  }
  .gcard.is-hidden { opacity:.62; }
  .gcover-box {
    width:100%; height:186px; background:var(--surf); position:relative;
    display:grid; place-items:center; overflow:hidden;
  }
  .gcover-box img { width:100%; height:100%; object-fit:cover; display:block; }
  .gcover-fallback {
    width:100%; height:100%; padding:14px;
    background:linear-gradient(145deg, color-mix(in srgb, var(--accent) 75%, #111), color-mix(in srgb, var(--tertiary) 65%, #111));
    color:#fff; display:flex; flex-direction:column; justify-content:flex-end; align-items:flex-start;
  }
  .gcover-fallback .tt { font-size:13px; font-weight:700; line-height:1.3; display:-webkit-box; -webkit-line-clamp:3; -webkit-box-orient:vertical; overflow:hidden; }
  .badge-plat {
    position:absolute; top:8px; left:8px;
    background:rgba(0,0,0,.68); color:#fff; backdrop-filter:blur(6px);
    font-size:10px; font-weight:700; padding:3px 8px; border-radius:999px;
  }
  .badge-fav {
    position:absolute; top:8px; right:8px;
    width:26px; height:26px; border-radius:999px;
    background:rgba(0,0,0,.62); color:#fff; border:0; cursor:pointer;
    display:grid; place-items:center; font-size:13px;
  }
  .badge-fav.on { background:var(--accent); color:#fff; }
  .gbody { padding:10px 12px 12px; display:flex; flex-direction:column; gap:4px; flex:1; }
  .gtitle { font-weight:700; font-size:13.5px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .gmeta { font-size:11.5px; color:var(--muted); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .gchips { display:flex; gap:5px; flex-wrap:wrap; margin-top:4px; }
  .chip {
    font-size:10.5px; padding:2px 7px; border-radius:999px; font-weight:600;
    background:color-mix(in srgb, var(--accent) 13%, transparent); color:var(--accent);
  }
  .chip.warn { background:color-mix(in srgb, var(--warn) 16%, transparent); color:var(--warn); }
  .chip.ok { background:color-mix(in srgb, var(--ok) 16%, transparent); color:var(--ok); }
  .chip.muted { background:var(--surf); color:var(--muted); border:1px solid var(--border); }

  /* List rows */
  .list { display:flex; flex-direction:column; gap:8px; }
  .row {
    display:flex; align-items:center; gap:12px;
    background:var(--panel); border:1px solid var(--border);
    border-radius:14px; padding:10px 14px; box-shadow:0 3px 10px var(--shadow);
  }
  .row:hover { border-color:color-mix(in srgb, var(--accent) 55%, var(--border)); }
  .cover-sm {
    width:46px; height:46px; border-radius:10px; object-fit:cover;
    background:var(--surf); display:grid; place-items:center; font-size:11px; font-weight:700; color:var(--muted); flex-shrink:0;
  }
  .meta { flex:1; min-width:0; }
  .title { font-weight:700; font-size:14px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; display:flex; align-items:center; gap:6px; }
  .desc { font-size:12px; color:var(--muted); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; margin-top:2px; }
  .actions { display:flex; gap:6px; flex-wrap:wrap; align-items:center; }

  /* Categories manager */
  .cat-banner {
    background:var(--panel); border:1.5px solid var(--border); border-radius:16px;
    padding:14px 16px; margin-bottom:14px; display:flex; align-items:center; gap:12px; flex-wrap:wrap;
  }
  .cat-row {
    display:flex; align-items:center; gap:12px;
    background:var(--panel); border:1.5px solid var(--border); border-radius:14px;
    padding:12px 14px; cursor:grab; transition:opacity .15s, border-color .15s, box-shadow .15s;
    user-select:none;
  }
  .cat-row:active { cursor:grabbing; }
  .cat-row.dragging { opacity:.42; border-style:dashed; }
  .cat-row.drop-above { box-shadow:0 -3px 0 0 var(--accent); border-top-color:var(--accent); }
  .cat-row.drop-below { box-shadow:0 3px 0 0 var(--accent); border-bottom-color:var(--accent); }
  .drag-handle {
    color:var(--muted); font-size:16px; line-height:1; letter-spacing:-2px;
    padding:4px 2px; flex-shrink:0; opacity:.75; cursor:grab;
  }
  .cat-row:hover .drag-handle { color:var(--accent); opacity:1; }
  .cat-row.is-hidden { opacity:.58; }
  .plat-grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(190px, 1fr)); gap:8px; max-height:260px; overflow:auto; padding:4px 0; }
  .plat-chk {
    display:flex; align-items:center; gap:8px; padding:8px 10px;
    border-radius:10px; border:1px solid var(--border); background:var(--surf); cursor:pointer; font-size:12.5px;
  }
  .plat-chk.checked { border-color:var(--accent); background:color-mix(in srgb, var(--accent) 12%, transparent); }

  /* File Manager */
  .crumbs { display:flex; align-items:center; gap:6px; flex-wrap:wrap; padding:8px 12px; background:var(--panel); border:1px solid var(--border); border-radius:12px; margin-bottom:12px; font-size:12.5px; }
  .crumb { cursor:pointer; color:var(--accent); font-weight:600; }
  .crumb:hover { text-decoration:underline; }
  .row.flash-back { border-color:var(--accent); box-shadow:0 0 0 2px color-mix(in srgb, var(--accent) 22%, transparent); transition:border-color .25s, box-shadow .25s; }
  .dropzone {
    border:2px dashed var(--border); border-radius:16px; padding:18px;
    text-align:center; color:var(--muted); margin-bottom:14px; transition:.15s;
    background:color-mix(in srgb, var(--panel) 70%, transparent);
  }
  .dropzone.drag { border-color:var(--accent); background:color-mix(in srgb, var(--accent) 10%, transparent); color:var(--text); }
  .prog { margin-top:8px; height:7px; background:var(--surf); border-radius:999px; overflow:hidden; display:none; }
  .prog > div { height:100%; width:0; background:linear-gradient(90deg, var(--accent), var(--glow)); transition:width .1s; }
  .batch-bar {
    position:sticky; bottom:14px; z-index:18;
    background:var(--panel); border:1.5px solid var(--accent); border-radius:16px;
    padding:10px 16px; margin-top:14px;
    display:none; align-items:center; gap:10px; flex-wrap:wrap;
    box-shadow:0 12px 32px var(--shadow);
  }
  .batch-bar.show { display:flex; }
  .float-dock {
    position:fixed; right:20px; bottom:20px; z-index:60;
    width:min(420px, calc(100vw - 32px));
    display:flex; flex-direction:column; align-items:flex-end; gap:10px;
    pointer-events:none; transition:bottom .18s ease;
  }
  .float-dock.lift { bottom:78px; }
  .task-bar { width:100%; display:none; flex-direction:column; gap:8px; pointer-events:auto; max-height:48vh; overflow-y:auto; }
  .task-bar.show { display:flex; }
  .task-item {
    background:var(--panel); border:1.5px solid var(--accent); border-radius:14px;
    padding:10px 14px; box-shadow:0 12px 30px var(--shadow);
  }
  .task-top { display:flex; align-items:center; gap:10px; }
  .task-spin {
    width:15px; height:15px; border-radius:50%;
    border:2px solid rgba(128,128,128,.25); border-top-color:var(--accent);
    animation:taskspin .8s linear infinite; flex-shrink:0;
  }
  @keyframes taskspin { to { transform:rotate(360deg); } }
  .task-prog { width:100%; height:5px; border-radius:999px; background:var(--surf); overflow:hidden; margin-top:7px; }
  .task-prog > div { height:100%; background:linear-gradient(90deg, var(--accent), var(--glow)); transition:width .2s ease; }
  .task-prog.indet > div { width:40%; animation:taskindet 1.2s ease-in-out infinite; }
  @keyframes taskindet { 0%{transform:translateX(-110%);} 100%{transform:translateX(270%);} }

  .toast {
    pointer-events:auto; max-width:100%;
    background:var(--panel); color:var(--text);
    border:1.5px solid var(--accent); padding:10px 16px; border-radius:12px;
    font-size:13px; font-weight:600; display:none;
    box-shadow:0 12px 30px var(--shadow);
  }
  .modal-bg { position:fixed; inset:0; background:rgba(0,0,0,.62); backdrop-filter:blur(4px); display:none; align-items:center; justify-content:center; padding:18px; z-index:50; }
  .modal-bg.open { display:flex; }
  .modal { width:min(840px, 100%); max-height:90vh; background:var(--panel); border:1.5px solid var(--border); border-radius:20px; display:flex; flex-direction:column; overflow:hidden; box-shadow:0 24px 60px rgba(0,0,0,.4); }
  .modal-hd { padding:14px 18px; border-bottom:1px solid var(--border); display:flex; align-items:center; gap:10px; font-weight:800; }
  .modal-bd { padding:18px; overflow:auto; flex:1; }
  .modal-ft { padding:12px 18px; border-top:1px solid var(--border); display:flex; justify-content:flex-end; gap:8px; flex-wrap:wrap; }
  .log-win {
    position:fixed; right:20px; bottom:20px; width:min(680px, 92vw); height:420px;
    min-width:340px; min-height:220px; max-width:96vw; max-height:90vh;
    z-index:60; display:none; flex-direction:column; resize:both; overflow:hidden;
    background:#0d1117; color:#c9d1d9; border:1.5px solid #30363d; border-radius:14px;
    box-shadow:0 20px 54px rgba(0,0,0,.48);
  }
  .log-win.open { display:flex; }
  .log-win.min { height:auto !important; min-height:0 !important; resize:none; width:360px !important; }
  .log-win.min .log-win-bd { display:none; }
  .log-win-hd {
    display:flex; align-items:center; gap:8px; padding:9px 12px;
    background:#161b22; border-bottom:1px solid #30363d; cursor:move; user-select:none;
    font-size:12.5px; font-weight:700; color:#e6edf3; flex-shrink:0;
  }
  .log-win-bd { display:flex; flex-direction:column; flex:1; min-height:0; padding:8px 10px 6px; gap:7px; }
  .log-win-bar { display:flex; align-items:center; gap:5px; flex-wrap:wrap; flex-shrink:0; }
  .log-win-btn {
    border:1px solid #30363d; background:#21262d; color:#c9d1d9;
    border-radius:7px; padding:4px 8px; font-size:11.5px; font-weight:600; cursor:pointer;
    white-space:nowrap; line-height:1.25;
  }
  .log-win-btn:hover { border-color:#58a6ff; color:#fff; }
  .log-win-btn.on { background:var(--accent); border-color:var(--accent); color:#fff; }
  .log-win-btn.err { color:#f85149; border-color:rgba(248,81,73,.4); }
  .log-win input.log-win-in {
    flex:1 1 110px; min-width:90px; max-width:210px; width:140px; height:26px;
    padding:3px 9px; border-radius:7px; margin:0;
    border:1px solid #30363d; background:#010409; color:#e6edf3; font-size:11.5px; outline:none;
  }
  .log-win input.log-win-in::placeholder { color:#6e7681; }
  .log-win input.log-win-in:focus { border-color:#58a6ff; }
  .log-win-ft {
    display:flex; align-items:center; gap:8px; flex-shrink:0;
    padding:2px 14px 0 2px; font-family:ui-monospace,SFMono-Regular,Menlo,monospace; font-size:11px; color:#6e7681;
  }
  .editor { width:100%; min-height:360px; background:var(--input-bg); color:var(--text); border:1px solid var(--border); border-radius:12px; padding:12px; font-family:ui-monospace,SFMono-Regular,Menlo,monospace; font-size:13px; line-height:1.5; resize:vertical; }
  .img-view { max-width:100%; max-height:68vh; display:block; margin:0 auto; border-radius:12px; background:var(--surf); }
  .dir-pick { display:flex; align-items:center; justify-content:space-between; padding:10px 12px; border:1px solid var(--border); border-radius:11px; background:var(--surf); margin-bottom:6px; cursor:pointer; }
  .dir-pick:hover { border-color:var(--accent); }

  /* Game detail modal */
  .detail-hero { display:flex; gap:18px; flex-wrap:wrap; margin-bottom:16px; }
  .detail-cover { width:170px; height:210px; border-radius:14px; object-fit:cover; background:var(--surf); border:1px solid var(--border); flex-shrink:0; display:grid; place-items:center; overflow:hidden; }
  .detail-cover img { width:100%; height:100%; object-fit:cover; }
  .detail-info { flex:1; min-width:240px; }
  .meta-kv { display:grid; grid-template-columns:repeat(auto-fit, minmax(150px, 1fr)); gap:10px; margin-top:12px; }
  .kv-box { background:var(--surf); border:1px solid var(--border); border-radius:10px; padding:8px 11px; }
  .kv-k { font-size:11px; color:var(--muted); }
  .kv-v { font-size:13px; font-weight:700; margin-top:2px; word-break:break-word; }
  .cand-grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(112px, 1fr)); gap:10px; margin-top:12px; }
  .cand-item { border:1.5px solid var(--border); border-radius:10px; overflow:hidden; cursor:pointer; background:var(--surf); text-align:center; padding-bottom:6px; }
  .cand-item:hover { border-color:var(--accent); }
  .cand-item img { width:100%; height:110px; object-fit:cover; display:block; }
  .cand-item span { font-size:10.5px; color:var(--muted); display:block; margin-top:4px; padding:0 4px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
</style>
</head>
<body>
<header>
  <div class="brand-wrap">
    <div class="logo-sq">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
        <rect x="2" y="6" width="20" height="12" rx="4"/>
        <path d="M7 10v4M5 12h4"/>
        <circle cx="15.5" cy="11" r="1" fill="currentColor"/>
        <circle cx="17.5" cy="13" r="1" fill="currentColor"/>
      </svg>
    </div>
    <div>
      <div class="brand">PocketDeck OS</div>
      <div id="devInfo" class="sub" data-i18n="connecting">正在连接掌机…</div>
    </div>
  </div>
  <div class="tabs">
    <button class="tab active" data-tab="games" data-i18n="tab_games">🎮 游戏库</button>
    <button class="tab" data-tab="categories" data-i18n="tab_categories">🏷️ 标签分类</button>
    <button class="tab" data-tab="files" data-i18n="tab_files">🗂️ 文件管理</button>
  </div>
  <select id="langSel" class="btn" onchange="setWebLang(this.value)" aria-label="Language">
    <option value="zh-CN">简体中文</option>
    <option value="zh-TW">繁體中文</option>
    <option value="en">English</option>
  </select>
  <button class="btn" onclick="openScrapeLogsModal()" data-i18n="btn_logs">📋 请求日志</button>
  <button class="btn" onclick="rescan()" data-i18n="btn_rescan">↻ 重新扫描</button>
  <button class="btn danger" onclick="logout()" data-i18n="btn_logout">退出登录</button>
</header>

<div class="wrap">
  <!-- TAB 1: GAMES -->
  <section id="tab-games">
    <div id="statsGrid" class="stats-grid"></div>
    <div class="toolbar">
      <input id="gSearch" class="search" data-i18n-ph="search_games_ph" placeholder="搜索游戏名、官方名、文件名、厂商、类型…" oninput="renderGames()"/>
      <select id="gVis" onchange="renderGames()">
        <option value="all" data-i18n="vis_all">全部状态</option>
        <option value="visible" data-i18n="vis_visible">仅已显示</option>
        <option value="fav" data-i18n="vis_fav">仅收藏 ♥</option>
        <option value="hidden" data-i18n="vis_hidden">仅已隐藏</option>
      </select>
      <select id="gPlat" onchange="renderGames()"><option value="" data-i18n="plat_all">全部平台</option></select>
      <select id="gCat" onchange="renderGames()"><option value="" data-i18n="cat_all">全部分类</option></select>
      <div class="tabs" style="padding:3px;">
        <button id="vmGrid" class="tab active" style="padding:5px 11px;font-size:12px;" onclick="setGameView('grid')" data-i18n="view_grid">封面网格</button>
        <button id="vmList" class="tab" style="padding:5px 11px;font-size:12px;" onclick="setGameView('list')" data-i18n="view_list">详情列表</button>
      </div>
    </div>
    <div id="gamesContainer"></div>
  </section>

  <!-- TAB 2: CATEGORIES -->
  <section id="tab-categories" style="display:none">
    <div class="cat-banner">
      <div style="flex:1;min-width:220px;">
        <div style="font-weight:800;font-size:15px;" data-i18n="cat_mgr_title">掌机首页标签分类管理</div>
        <div class="sub" data-i18n="cat_mgr_desc">拖动左侧或整行卡片即可自由排序，编辑应用、显隐、删除或新建后将自动同步到掌机。</div>
      </div>
      <button class="btn primary" onclick="openNewCategoryModal()" data-i18n="cat_new">+ 新建分类</button>
      <button class="btn" onclick="loadCategories()" data-i18n="cat_refresh">↺ 刷新</button>
    </div>
    <div id="catList" class="list"></div>
  </section>

  <!-- TAB 3: FILES -->
  <section id="tab-files" style="display:none">
    <div class="toolbar">
      <button id="upBtn" class="btn" onclick="goParent()" disabled data-i18n="files_up">↩ 上级</button>
      <select id="fileSort" onchange="renderFiles()">
        <option value="name" data-i18n="sort_name">按名称</option>
        <option value="size" data-i18n="sort_size">按大小</option>
        <option value="modified" data-i18n="sort_modified">按修改时间</option>
        <option value="type" data-i18n="sort_type">按类型</option>
      </select>
      <input id="fileFilter" class="search" style="min-width:140px;" data-i18n-ph="files_filter_ph" placeholder="过滤当前目录…" oninput="renderFiles()"/>
      <button id="mkdirBtn" class="btn" onclick="mkdirPrompt()" disabled data-i18n="files_mkdir">+ 新建文件夹</button>
      <label class="btn primary" id="pickUploadLbl" style="display:none">
        <span data-i18n="files_upload">📤 上传文件</span>
        <input id="fileInput" type="file" multiple style="display:none" onchange="uploadFiles(this.files)"/>
      </label>
      <label class="btn" id="pickDirUploadLbl" style="display:none">
        <span data-i18n="files_upload_dir">📁 上传文件夹</span>
        <input id="dirInput" type="file" webkitdirectory multiple style="display:none" onchange="uploadFolderFiles(this.files)"/>
      </label>
    </div>
    <div id="crumbsBar" class="crumbs"></div>
    <div id="dropzone" class="dropzone" style="display:none">
      <span data-i18n="dropzone_txt">将 ROM 文件或整个平台文件夹拖放到此处，直接高速流式上传到当前目录</span>
      <div id="progWrap" class="prog"><div id="progBar"></div></div>
      <div id="progText" class="sub" style="margin-top:6px;"></div>
      <div id="progCancel" style="display:none;margin-top:8px;">
        <button class="btn sm danger" onclick="cancelWebUpload()" data-i18n="cancel_upload">✕ 取消上传</button>
      </div>
    </div>
    <div id="filesHeaderBar" style="display:none;align-items:center;gap:10px;margin-bottom:8px;">
      <label style="display:flex;align-items:center;gap:6px;font-size:12.5px;cursor:pointer;">
        <input type="checkbox" id="chkAllFiles" onchange="toggleSelectAllFiles(this.checked)"/> <span data-i18n="files_select_all">全选当前目录</span>
      </label>
      <span id="dirStorageInfo" class="sub"></span>
    </div>
    <div id="filesList" class="list"></div>
    <div id="batchBar" class="batch-bar">
      <strong id="batchCountText">已选 0 项</strong>
      <button class="btn sm" onclick="openBatchTransfer('move')" data-i18n="batch_move">移动…</button>
      <button class="btn sm" onclick="openBatchTransfer('copy')" data-i18n="batch_copy">复制…</button>
      <button class="btn sm primary" onclick="compressSelected()" data-i18n="batch_zip">🗜️ 打包 ZIP</button>
      <button class="btn sm danger" onclick="batchDeleteSelected()" data-i18n="batch_del">🗑 批量删除</button>
      <button class="btn sm" style="margin-left:auto;" onclick="clearFileSelection()" data-i18n="batch_clear">取消选择</button>
    </div>
  </section>
</div>

<div id="floatDock" class="float-dock">
  <div id="toast" class="toast"></div>
  <div id="fileTaskBar" class="task-bar"></div>
</div>

<div id="modalBg" class="modal-bg" onclick="if(event.target===this)closeModal()">
  <div class="modal">
    <div class="modal-hd"><span id="mTitle" style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;"></span><button class="btn sm" onclick="closeModal()">✕</button></div>
    <div id="mBody" class="modal-bd"></div>
    <div id="mFoot" class="modal-ft"></div>
  </div>
</div>

<div id="logWin" class="log-win">
  <div id="logWinHd" class="log-win-hd">
    <span id="logWinTitle" style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;"></span>
    <span id="logLiveDot" style="color:#3fb950;font-size:11.5px;font-weight:700;">● LIVE</span>
    <button id="logMinBtn" class="log-win-btn" onclick="toggleLogWinMin()" title="Minimize">—</button>
    <button class="log-win-btn" onclick="closeScrapeLogsWin()" title="Close">✕</button>
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
    <div id="logViewerBox" style="flex:1;min-height:0;overflow:auto;background:#010409;border:1px solid #21262d;border-radius:10px;padding:8px 10px;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:11.5px;line-height:1.5;color:#c9d1d9;"></div>
    <div class="log-win-ft">
      <span id="logPathTxt" style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">cache/logs/scrape.log</span>
      <span id="logCountTxt" style="color:#8b949e;flex-shrink:0;">0</span>
    </div>
  </div>
</div>

<script>
const I18N = {
  'zh-CN': {
    connecting: '正在连接掌机…',
    tab_games: '🎮 游戏库',
    tab_categories: '🏷️ 标签分类',
    tab_files: '🗂️ 文件管理',
    btn_rescan: '↻ 重新扫描',
    btn_logout: '退出登录',
    search_games_ph: '搜索游戏名、官方名、文件名、厂商、类型…',
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
    files_upload: '📤 上传文件',
    files_upload_dir: '📁 上传文件夹',
    dropzone_txt: '将 ROM 文件或整个平台文件夹拖放到此处，直接高速流式上传到当前目录',
    cancel_upload: '✕ 取消上传',
    files_select_all: '全选当前目录',
    batch_move: '移动…',
    batch_copy: '复制…',
    batch_zip: '🗜️ 打包 ZIP',
    batch_del: '🗑 批量删除',
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
    storage_root: '🏠 存储根目录',
    selected_n: '已选 {n} 项',
    dir_space: '可用 {free} / 共 {total}',
    no_cover: '无封面',
    no_cover_yet: '暂无封面',
    fav_on: '♥ 已收藏',
    btn_fetch_meta: '✨ 掌机抓取封面与元数据',
    btn_fetching_meta: '⏳ 掌机正在抓取…',
    btn_pick_cover: '🖼️ 选择候选封面',
    btn_upload_cover: '📁 上传封面',
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
    task_compress: '🗜️ 正在打包 ZIP',
    task_extract: '📦 正在解压',
    task_copy: '📋 正在复制',
    task_move: '📦 正在移动',
    task_delete: '🗑️ 正在删除',
    task_upload: '📤 正在上传',
    task_other: '⏳ 正在处理',
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
    arc_title: '🗜️ 压缩包 · ',
    arc_parsing: '正在解析压缩包内容…',
    arc_extract_here: '解压到当前目录',
    arc_extract_dir: '解压到同名文件夹',
    arc_parse_fail: '解析失败: ',
    arc_count: '共 {n} 个条目',
    msg_extract_fail: '解压失败: ',
    img_title: '🖼️ 图片预览 · ',
    btn_dl_orig: '下载原图',
    audio_title: '🎵 音频播放 · ',
    btn_dl_audio: '下载音频',
    txt_read_fail: '无法读取文本: ',
    txt_title: '📝 编辑文本 · ',
    msg_file_saved: '已保存文件',
    msg_save_fail: '保存失败',
    tx_copy_title: '📋 复制「{name}」到…',
    tx_move_title: '📦 移动「{name}」到…',
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
    btn_logs: '📋 请求日志',
    logs_title: '📋 封面与元数据请求日志（实时）',
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
    connecting: '正在連線掌機…',
    tab_games: '🎮 遊戲庫',
    tab_categories: '🏷️ 標籤分類',
    tab_files: '🗂️ 檔案管理',
    btn_rescan: '↻ 重新掃描',
    btn_logout: '登出',
    search_games_ph: '搜尋遊戲名稱、官方名稱、檔名、廠商、類型…',
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
    files_upload: '📤 上傳檔案',
    files_upload_dir: '📁 上傳資料夾',
    dropzone_txt: '將 ROM 檔案或整個平台資料夾拖放至此，直接高速串流上傳到目前目錄',
    cancel_upload: '✕ 取消上傳',
    files_select_all: '全選目前目錄',
    batch_move: '移動…',
    batch_copy: '複製…',
    batch_zip: '🗜️ 打包 ZIP',
    batch_del: '🗑 批次刪除',
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
    storage_root: '🏠 儲存根目錄',
    selected_n: '已選 {n} 項',
    dir_space: '可用 {free} / 共 {total}',
    no_cover: '無封面',
    no_cover_yet: '暫無封面',
    fav_on: '♥ 已收藏',
    btn_fetch_meta: '✨ 掌機抓取封面與中繼資料',
    btn_fetching_meta: '⏳ 掌機正在抓取…',
    btn_pick_cover: '🖼️ 選擇候選封面',
    btn_upload_cover: '📁 上傳封面',
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
    task_compress: '🗜️ 正在打包 ZIP',
    task_extract: '📦 正在解壓縮',
    task_copy: '📋 正在複製',
    task_move: '📦 正在移動',
    task_delete: '🗑️ 正在刪除',
    task_upload: '📤 正在上傳',
    task_other: '⏳ 正在處理',
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
    arc_title: '🗜️ 壓縮檔 · ',
    arc_parsing: '正在解析壓縮檔內容…',
    arc_extract_here: '解壓縮到目前目錄',
    arc_extract_dir: '解壓縮到同名資料夾',
    arc_parse_fail: '解析失敗: ',
    arc_count: '共 {n} 個項目',
    msg_extract_fail: '解壓縮失敗: ',
    img_title: '🖼️ 圖片預覽 · ',
    btn_dl_orig: '下載原圖',
    audio_title: '🎵 音訊播放 · ',
    btn_dl_audio: '下載音訊',
    txt_read_fail: '無法讀取文字: ',
    txt_title: '📝 編輯文字 · ',
    msg_file_saved: '已儲存檔案',
    msg_save_fail: '儲存失敗',
    tx_copy_title: '📋 複製「{name}」到…',
    tx_move_title: '📦 移動「{name}」到…',
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
    btn_logs: '📋 請求日誌',
    logs_title: '📋 封面與中繼資料請求日誌（即時）',
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
    connecting: 'Connecting to handheld…',
    tab_games: '🎮 Library',
    tab_categories: '🏷️ Categories',
    tab_files: '🗂️ Files',
    btn_rescan: '↻ Rescan',
    btn_logout: 'Log Out',
    search_games_ph: 'Search title, official name, filename, developer, genre…',
    vis_all: 'All Status',
    vis_visible: 'Visible Only',
    vis_fav: 'Favorites ♥',
    vis_hidden: 'Hidden Only',
    plat_all: 'All Platforms',
    cat_all: 'All Categories',
    view_grid: 'Cover Grid',
    view_list: 'Detail List',
    cat_mgr_title: 'Home Tab & Category Manager',
    cat_mgr_desc: 'Drag cards to reorder. Edits, visibility, deletions, and new categories sync automatically.',
    cat_new: '+ New Category',
    cat_refresh: '↺ Refresh',
    files_up: '↩ Up',
    sort_name: 'Name',
    sort_size: 'Size',
    sort_modified: 'Modified',
    sort_type: 'Type',
    files_filter_ph: 'Filter current folder…',
    files_mkdir: '+ New Folder',
    files_upload: '📤 Upload Files',
    files_upload_dir: '📁 Upload Folder',
    dropzone_txt: 'Drag & drop ROM files or platform folders here to stream directly to this directory',
    cancel_upload: '✕ Cancel Upload',
    files_select_all: 'Select all in folder',
    batch_move: 'Move…',
    batch_copy: 'Copy…',
    batch_zip: '🗜️ Zip',
    batch_del: '🗑 Delete',
    batch_clear: 'Clear Selection',
    conn_off: 'Web service disconnected (re-enable on handheld)',
    conn_lan_only: 'LAN only (offline)',
    conn_online: 'Connected · Online cover fetch ready',
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
    page_title: 'PocketDeck · Web Game Console',
    storage_root: '🏠 Storage Roots',
    selected_n: '{n} selected',
    dir_space: 'Free {free} / Total {total}',
    no_cover: 'No cover',
    no_cover_yet: 'No cover yet',
    fav_on: '♥ Favorited',
    btn_fetch_meta: '✨ Fetch Cover & Metadata',
    btn_fetching_meta: '⏳ Fetching on handheld…',
    btn_pick_cover: '🖼️ Pick Candidate Cover',
    btn_upload_cover: '📁 Upload Cover',
    btn_reset_cover: 'Reset Default Cover',
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
    task_compress: '🗜️ Creating ZIP',
    task_extract: '📦 Extracting',
    task_copy: '📋 Copying',
    task_move: '📦 Moving',
    task_delete: '🗑️ Deleting',
    task_upload: '📤 Uploading',
    task_other: '⏳ Processing',
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
    arc_title: '🗜️ Archive · ',
    arc_parsing: 'Reading archive entries…',
    arc_extract_here: 'Extract Here',
    arc_extract_dir: 'Extract to Folder',
    arc_parse_fail: 'Failed to read archive: ',
    arc_count: '{n} entries',
    msg_extract_fail: 'Extract failed: ',
    img_title: '🖼️ Image Preview · ',
    btn_dl_orig: 'Download Original',
    audio_title: '🎵 Audio Player · ',
    btn_dl_audio: 'Download Audio',
    txt_read_fail: 'Failed to read text: ',
    txt_title: '📝 Edit Text · ',
    msg_file_saved: 'File saved',
    msg_save_fail: 'Save failed',
    tx_copy_title: '📋 Copy "{name}" to…',
    tx_move_title: '📦 Move "{name}" to…',
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
    btn_logs: '📋 Logs',
    logs_title: '📋 Cover & Metadata Request Logs (Live)',
    logs_empty: 'No request logs recorded yet',
    logs_pause: '⏸ Pause',
    logs_resume: '▶ Resume Live',
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
let games = [];
let roots = [];
let deviceInfo = {};
let curDir = '';
let parentDir = '';
let curEntries = [];
let selectedPaths = new Set();
let gameViewMode = localStorage.getItem('pd_web_gview') || 'grid';
let coverVer = 0;

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
  return r;
}

function applyThemeObj(t){
  if(!t) return;
  const r = document.documentElement;
  r.dataset.dark = t.isDark ? '1' : '0';
  r.style.setProperty('--accent', t.primary);
  r.style.setProperty('--glow', t.glow);
  r.style.setProperty('--tertiary', t.tertiary);
  r.style.setProperty('--bg', t.bg);
  r.style.setProperty('--panel', t.panel);
  r.style.setProperty('--surf', t.surf);
  r.style.setProperty('--border', t.border);
  r.style.setProperty('--text', t.text);
  r.style.setProperty('--muted', t.muted);
  r.style.setProperty('--header-bg', t.isDark ? 'rgba(22,22,28,.86)' : 'rgba(255,255,255,.88)');
  r.style.setProperty('--shadow', t.isDark ? 'rgba(0,0,0,.45)' : 'rgba(18,24,40,.08)');
  r.style.setProperty('--input-bg', t.isDark ? '#101014' : '#FFFFFF');
}
let webConnected = true;
function renderDevInfo(connected, hasInternet) {
  webConnected = connected;
  const box = el('devInfo');
  if (!box) return;
  const base = [deviceInfo.deviceName || '', deviceInfo.interface || ''].filter(Boolean).join(' · ');
  if (!connected) {
    box.innerHTML = '<span class="conn-dot off"></span>' +
      (base ? esc(base) + ' · ' : '') +
      '<span class="conn-off-txt">' + esc(tr('conn_off')) + '</span>';
    return;
  }
  const netTxt = (hasInternet === false) ? tr('conn_lan_only') : tr('conn_online');
  box.innerHTML = '<span class="conn-dot ok"></span>' +
    (base ? esc(base) + ' · ' : '') + esc(netTxt);
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

async function heartbeat() {
  const ctrl = typeof AbortController !== 'undefined' ? new AbortController() : null;
  const timer = ctrl ? setTimeout(() => ctrl.abort(), 2200) : null;
  try {
    const r = await fetch('/api/theme', { cache: 'no-store', signal: ctrl ? ctrl.signal : undefined });
    if (!r.ok) throw new Error('http_' + r.status);
    const d = await r.json();
    if (!d.ok) throw new Error('not_ok');
    if (d.authed === false) { location.reload(); return; }
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
  } catch {
    if (webConnected) {
      renderDevInfo(false, false);
      toast(tr('conn_off'));
    } else {
      renderDevInfo(false, false);
    }
  } finally {
    if (timer) clearTimeout(timer);
  }
}
setInterval(heartbeat, 3000);

document.querySelectorAll('header .tabs .tab').forEach(b => b.onclick = () => {
  document.querySelectorAll('header .tabs .tab').forEach(x => x.classList.remove('active'));
  b.classList.add('active');
  const t = b.dataset.tab;
  curTab = t;
  updateBatchBar();
  el('tab-games').style.display = t === 'games' ? '' : 'none';
  el('tab-categories').style.display = t === 'categories' ? '' : 'none';
  el('tab-files').style.display = t === 'files' ? '' : 'none';
  if (t === 'files' && !curDir) loadFiles('');
  if (t === 'categories' && !catTabs.length) loadCategories();
});

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

async function loadGames() {
  const data = await (await api('/api/games')).json();
  games = data.games || [];
  const sel = el('gPlat');
  const prev = sel.value;
  const plats = [...new Set(games.map(g => g.platformName))].sort();
  sel.innerHTML = '<option value="" data-i18n="plat_all">' + esc(tr('plat_all')) + '</option>' + plats.map(p => '<option value="' + esc(p) + '">' + esc(p) + '</option>').join('');
  sel.value = prev;
  renderStats();
  renderGames();
}

function renderStats() {
  const platCount = new Set(games.map(g => g.platformId)).size;
  const favCount = games.filter(g => g.favorite).length;
  const customCats = catTabs.filter(t => t.type === 'Custom').length;
  const freeBytes = (deviceInfo.storage || []).reduce((acc, s) => acc + (s.freeBytes || 0), 0);
  el('statsGrid').innerHTML =
    '<div class="stat-card"><div class="stat-ico">🎮</div><div><div class="stat-val">' + games.length + '</div><div class="stat-lbl">' + esc(tr('stat_games')) + '</div></div></div>' +
    '<div class="stat-card"><div class="stat-ico">🕹️</div><div><div class="stat-val">' + platCount + '</div><div class="stat-lbl">' + esc(tr('stat_plats')) + '</div></div></div>' +
    '<div class="stat-card"><div class="stat-ico">♥</div><div><div class="stat-val">' + favCount + '</div><div class="stat-lbl">' + esc(tr('stat_favs')) + '</div></div></div>' +
    '<div class="stat-card"><div class="stat-ico">🏷️</div><div><div class="stat-val">' + customCats + '</div><div class="stat-lbl">' + esc(tr('stat_cats')) + '</div></div></div>' +
    '<div class="stat-card"><div class="stat-ico">💾</div><div><div class="stat-val">' + (freeBytes ? fmtBytes(freeBytes) : '—') + '</div><div class="stat-lbl">' + esc(tr('stat_free')) + '</div></div></div>';
}

function renderGames() {
  const q = el('gSearch').value.trim().toLowerCase();
  const vis = el('gVis').value;
  const plat = el('gPlat').value;
  const catFilter = el('gCat').value;
  const list = games.filter(g => {
    if (vis === 'visible' && g.hidden) return false;
    if (vis === 'hidden' && !g.hidden) return false;
    if (vis === 'fav' && !g.favorite) return false;
    if (plat && g.platformName !== plat) return false;
    if (catFilter && !(g.categories || []).includes(catFilter)) return false;
    if (q) {
      const m = g.metadata || {};
      const hay = (g.title + ' ' + g.officialName + ' ' + g.fileName + ' ' + g.platformName + ' ' + (m.developer||'') + ' ' + (m.publisher||'') + ' ' + (m.genre||'')).toLowerCase();
      if (!hay.includes(q)) return false;
    }
    return true;
  });
  const container = el('gamesContainer');
  if (!list.length) {
    container.innerHTML = '<div class="sub" style="padding:24px 0;">' + esc(tr('no_games')) + '</div>';
    return;
  }

  if (gameViewMode === 'grid') {
    container.innerHTML = '<div class="game-grid">' + list.map(g => {
      const safeId = encodeURIComponent(g.id);
      const m = g.metadata || {};
      const metaLine = [m.releaseDate, m.genre, m.developer].filter(Boolean).join(' · ') || (g.isRom ? fmtBytes(g.size) : 'Android App');
      const cov = g.hasCover
        ? '<img loading="lazy" src="/api/cover?id=' + safeId + '&v=' + coverVer + '" onerror="this.style.display=\'none\';this.nextElementSibling.style.display=\'flex\'"/>'
        : '';
      const fbStyle = g.hasCover ? 'style="display:none"' : '';
      return '<div class="gcard' + (g.hidden ? ' is-hidden' : '') + '" onclick="openGameDetail(\'' + escJs(g.id) + '\')">' +
        '<div class="gcover-box">' + cov +
          '<div class="gcover-fallback" ' + fbStyle + '><span class="tt">' + esc(g.title) + '</span></div>' +
          '<span class="badge-plat">' + esc(g.platformName) + '</span>' +
          '<button class="badge-fav' + (g.favorite ? ' on' : '') + '" onclick="event.stopPropagation();toggleFav(\'' + escJs(g.id) + '\',' + (!g.favorite) + ')" title="' + esc(tr('btn_fav')) + '">' + (g.favorite ? '♥' : '♡') + '</button>' +
        '</div>' +
        '<div class="gbody">' +
          '<div class="gtitle" title="' + esc(g.title) + '">' + esc(g.title) + '</div>' +
          '<div class="gmeta">' + esc(metaLine) + '</div>' +
          '<div class="gchips">' +
            (g.hidden ? '<span class="chip warn">' + esc(tr('hidden_tag')) + '</span>' : '') +
            (m.publisher ? '<span class="chip muted">' + esc(m.publisher) + '</span>' : '') +
          '</div>' +
        '</div></div>';
    }).join('') + '</div>';
  } else {
    container.innerHTML = '<div class="list">' + list.map(g => {
      const safeId = encodeURIComponent(g.id);
      const m = g.metadata || {};
      const metaSummary = [m.releaseDate, m.genre, m.developer, m.publisher].filter(Boolean).join(' · ');
      const cov = g.hasCover
        ? '<img class="cover-sm" loading="lazy" src="/api/cover?id=' + safeId + '&v=' + coverVer + '"/>'
        : '<div class="cover-sm">' + esc(g.platformName) + '</div>';
      return '<div class="row">' + cov +
        '<div class="meta" style="cursor:pointer" onclick="openGameDetail(\'' + escJs(g.id) + '\')">' +
          '<div class="title">' + esc(g.title) +
            (g.favorite ? '<span class="chip">' + esc(tr('fav_tag')) + '</span>' : '') +
            (g.hidden ? '<span class="chip warn">' + esc(tr('hidden_tag')) + '</span>' : '') +
          '</div>' +
          '<div class="desc">' + esc(g.platformName) + (g.isRom ? ' · ' + fmtBytes(g.size) : ' · Android App') + (metaSummary ? ' · ' + esc(metaSummary) : '') + '</div>' +
        '</div>' +
        '<div class="actions">' +
          '<button class="btn sm" onclick="openGameDetail(\'' + escJs(g.id) + '\')">' + esc(tr('btn_detail')) + '</button>' +
          '<button class="btn sm" onclick="toggleFav(\'' + escJs(g.id) + '\',' + (!g.favorite) + ')">' + esc(g.favorite ? tr('btn_unfav') : tr('btn_fav')) + '</button>' +
          '<button class="btn sm" onclick="toggleHide(\'' + escJs(g.id) + '\',' + (!g.hidden) + ')">' + esc(g.hidden ? tr('btn_restore') : tr('btn_hide')) + '</button>' +
          (g.isRom ? '<a class="btn sm" href="/api/files/download?path=' + safeId + '">' + esc(tr('btn_dl')) + '</a>' : '') +
        '</div></div>';
    }).join('') + '</div>';
  }
}

async function openGameDetail(id) {
  const g = games.find(x => x.id === id);
  if (!g) return;
  const m = g.metadata || {};
  const mInfo = g.metaInfo || {};
  const safeId = encodeURIComponent(g.id);
  const customCats = catTabs.filter(t => t.type === 'Custom');

  el('mTitle').textContent = '🎮 ' + g.title;
  el('mBody').innerHTML =
    '<div class="detail-hero">' +
      '<div class="detail-cover">' +
        (g.hasCover
          ? '<img id="detCoverImg" src="/api/cover?id=' + safeId + '&v=' + coverVer + '" onerror="this.outerHTML=\'<div class=sub>' + esc(tr('no_cover')) + '</div>\'"/>'
          : '<div class="sub">' + esc(tr('no_cover_yet')) + '</div>') +
      '</div>' +
      '<div class="detail-info">' +
        '<div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">' +
          '<span class="chip">' + esc(g.platformName) + ' · ' + esc(g.platformFullName || '') + '</span>' +
          (g.favorite ? '<span class="chip ok">' + esc(tr('fav_on')) + '</span>' : '') +
          (g.hidden ? '<span class="chip warn">' + esc(tr('hidden_tag')) + '</span>' : '') +
        '</div>' +
        '<div style="margin-top:10px;display:flex;gap:8px;flex-wrap:wrap;">' +
          '<button class="btn sm primary" id="btnRefreshMeta" onclick="refreshGameMetaFromHandheld(\'' + escJs(g.id) + '\')">' + esc(tr('btn_fetch_meta')) + '</button>' +
          '<button class="btn sm" onclick="scrapeCoverCandidates(\'' + escJs(g.id) + '\')">' + esc(tr('btn_pick_cover')) + '</button>' +
          '<label class="btn sm">' + esc(tr('btn_upload_cover')) + '<input type="file" accept="image/*" style="display:none" onchange="uploadCustomCover(\'' + escJs(g.id) + '\', this.files[0])"/></label>' +
          (g.hasCustomCover ? '<button class="btn sm" onclick="resetGameCover(\'' + escJs(g.id) + '\')">' + esc(tr('btn_reset_cover')) + '</button>' : '') +
        '</div>' +
        '<div class="meta-kv">' +
          '<div class="kv-box"><div class="kv-k">' + esc(tr('kv_official')) + '</div><div class="kv-v">' + esc(g.officialName || '—') + '</div></div>' +
          '<div class="kv-box"><div class="kv-k">' + esc(tr('kv_release')) + '</div><div class="kv-v">' + esc(m.releaseDate || '—') + '</div></div>' +
          '<div class="kv-box"><div class="kv-k">' + esc(tr('kv_genre')) + '</div><div class="kv-v">' + esc(m.genre || '—') + '</div></div>' +
          '<div class="kv-box"><div class="kv-k">' + esc(tr('kv_dev')) + '</div><div class="kv-v">' + esc(m.developer || '—') + '</div></div>' +
          '<div class="kv-box"><div class="kv-k">' + esc(tr('kv_pub')) + '</div><div class="kv-v">' + esc(m.publisher || '—') + '</div></div>' +
          '<div class="kv-box"><div class="kv-k">' + esc(tr('kv_size_crc')) + '</div><div class="kv-v">' + (g.isRom ? fmtBytes(g.size) : fmtBytes(g.size) + ' (APK)') + (mInfo.crc32 ? ' · ' + esc(mInfo.crc32) : '') + '</div></div>' +
          '<div class="kv-box"><div class="kv-k">' + esc(tr('kv_meta_src')) + '</div><div class="kv-v">' + esc((mInfo.providers || []).join(', ') || '—') + '</div></div>' +
          '<div class="kv-box"><div class="kv-k">' + esc(tr('kv_cover_src')) + '</div><div class="kv-v">' + esc(g.coverProvider || '—') + '</div></div>' +
        '</div>' +
      '</div>' +
    '</div>' +
    '<div style="margin-top:8px;padding-top:12px;border-top:1px solid var(--border);">' +
      '<div style="font-weight:700;font-size:13px;margin-bottom:8px;">' + esc(tr('det_cats_title')) + '</div>' +
      (customCats.length
        ? '<div class="plat-grid" style="max-height:140px;">' + customCats.map(c => {
            const viaPlat = (c.sources || []).includes(g.platformId);
            const manual = (c.games || []).includes(g.id);
            const checked = viaPlat || manual;
            return '<label class="plat-chk' + (checked ? ' checked' : '') + '">' +
              '<input type="checkbox" class="gcat-chk" value="' + esc(c.id) + '" ' + (checked ? 'checked' : '') + ' ' + (viaPlat ? 'disabled' : 'onchange="saveGameCats(\'' + escJs(g.id) + '\')"') + '/>' +
              '<span>' + esc(c.name) + (viaPlat ? esc(tr('det_via_plat')) : '') + '</span></label>';
          }).join('') + '</div>'
        : '<div class="sub">' + esc(tr('det_no_cats')) + '</div>') +
    '</div>' +
    '<div id="candArea"></div>' +
    '<div class="sub" style="margin-top:12px;word-break:break-all;">' + esc(tr('det_path')) + esc(g.id) + '</div>';

  el('mFoot').innerHTML =
    '<button class="btn" onclick="renameGame(\'' + escJs(g.id) + '\',\'' + escJs(g.title) + '\')">' + esc(tr('btn_rename')) + '</button>' +
    '<button class="btn" onclick="toggleFav(\'' + escJs(g.id) + '\',' + (!g.favorite) + ',true)">' + esc(g.favorite ? tr('btn_unfav') : ('♥ ' + tr('btn_fav'))) + '</button>' +
    '<button class="btn" onclick="toggleHide(\'' + escJs(g.id) + '\',' + (!g.hidden) + ',true)">' + esc(g.hidden ? tr('btn_unhide') : tr('btn_hide')) + '</button>' +
    (g.isRom ? '<a class="btn" href="/api/files/download?path=' + safeId + '">' + esc(tr('btn_dl_rom')) + '</a>' : '') +
    (g.isRom ? '<button class="btn danger" onclick="deleteFile(\'' + escJs(g.id) + '\',\'' + escJs(g.fileName) + '\',true)">' + esc(tr('btn_del_file')) + '</button>' : '') +
    '<button class="btn primary" onclick="closeModal()">' + esc(tr('btn_close')) + '</button>';

  el('modalBg').classList.add('open');
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
      coverVer++;
      toast((d.coverUpdated || d.metaUpdated) ? tr('msg_meta_done') : tr('msg_meta_none'));
      await loadGames();
      openGameDetail(id);
    } else {
      toast(tr('msg_meta_fail') + (d.error || r.status));
    }
  } finally {
    if (btn) { btn.disabled = false; btn.textContent = tr('btn_fetch_meta'); }
  }
}

async function scrapeCoverCandidates(id) {
  const area = el('candArea');
  if (area) area.innerHTML = '<div class="sub" style="margin-top:12px;">' + esc(tr('msg_searching_covers')) + '</div>';
  const r = await api('/api/games/scrape-covers', {
    method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id})
  });
  const d = await r.json();
  if (!d.ok && d.error === 'offline') {
    if (area) area.innerHTML = '<div class="sub" style="margin-top:12px;color:var(--warn);">' + esc(tr('msg_offline_covers')) + '</div>';
    return;
  }
  const cands = d.candidates || [];
  if (!cands.length) {
    if (area) area.innerHTML = '<div class="sub" style="margin-top:12px;">' + esc(tr('msg_no_cands')) + '</div>';
    return;
  }
  if (area) {
    area.innerHTML = '<div style="font-weight:700;font-size:13px;margin-top:14px;">' + esc(tr('msg_pick_cand')) + '</div>' +
      '<div class="cand-grid">' + cands.map(c =>
        '<div class="cand-item" onclick="applyCandidateCover(\'' + escJs(id) + '\',\'' + escJs(c.token) + '\')">' +
          '<img loading="lazy" src="/api/cover-candidate?token=' + encodeURIComponent(c.token) + '"/>' +
          '<span>' + esc(c.nodeId) + '</span>' +
        '</div>'
      ).join('') + '</div>';
  }
}

async function applyCandidateCover(id, token) {
  toast(tr('msg_applying_cover'));
  const r = await api('/api/games/set-cover', {
    method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id, token})
  });
  if (r.ok) {
    coverVer++;
    toast(tr('msg_cover_applied'));
    await loadGames();
    openGameDetail(id);
  } else {
    toast(tr('msg_cover_fail'));
  }
}

async function uploadCustomCover(id, file) {
  if (!file) return;
  const r = await api('/api/games/upload-cover?id=' + encodeURIComponent(id), {
    method:'POST', body:file
  });
  if (r.ok) {
    coverVer++;
    toast(tr('msg_cover_uploaded'));
    await loadGames();
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
    coverVer++;
    toast(tr('msg_cover_reset'));
    await loadGames();
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
    toast(tr('msg_cats_saved'));
    const bodyEl = el('mBody');
    const prevScroll = bodyEl ? bodyEl.scrollTop : 0;
    await Promise.all([loadCategories(), loadGames()]);
    openGameDetail(id);
    if (el('mBody')) el('mBody').scrollTop = prevScroll;
  }
}

async function toggleFav(id, fav, reopen) {
  await api('/api/games/favorite', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id, favorite:fav}) });
  toast(fav ? tr('msg_fav_added') : tr('msg_fav_removed'));
  await loadGames();
  if (reopen) openGameDetail(id);
}

async function renameGame(id, cur) {
  const title = prompt(tr('prompt_rename_game'), cur);
  if (title === null) return;
  await api('/api/games/rename', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id, title}) });
  toast(tr('msg_game_renamed'));
  await loadGames();
  if (el('modalBg').classList.contains('open')) openGameDetail(id);
}

async function toggleHide(id, hidden, reopen) {
  await api('/api/games/hide', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id, hidden}) });
  toast(hidden ? tr('msg_hidden') : tr('msg_unhidden'));
  await loadGames();
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

  const listEl = el('filesList');
  if (!entries.length) {
    listEl.innerHTML = '<div class="sub" style="padding:18px 0;">' + esc(tr('dir_empty')) + '</div>';
    return;
  }
  listEl.innerHTML = entries.map(f => {
    const icon = f.isDir ? '📁' : (f.isArchive ? '🗜️' : (f.isImage ? '🖼️' : (f.isAudio ? '🎵' : (f.isFont ? '🔤' : (f.isText ? '📝' : '📄')))));
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
  }).join('');
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
      '<div class="row" style="padding:7px 10px;"><span>' + (e.isDir ? '📁' : '📄') + '</span><span style="flex:1;font-size:12.5px;word-break:break-all;">' + esc(e.name) + '</span><span class="sub">' + (e.isDir ? '' : fmtBytes(e.size)) + '</span></div>'
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
  const listHtml = dirs.map(d => '<div class="dir-pick" onclick="renderTransferBrowser(\'' + escJs(d.path) + '\')"><span>📁 ' + esc(d.name) + '</span><span>›</span></div>').join('');
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
loadInit();
pollFileTasks();
</script>
</body>
</html>"""
    }
}
