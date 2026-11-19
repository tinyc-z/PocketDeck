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
<title>PocketDeck OS · 登录</title>
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
  </div>
  <p>请输入掌机屏幕上显示的 4 位动态访问密码，连接后即可在浏览器管理游戏封面、元数据、标签分类与文件库。</p>
  <form id="f">
    <div class="pin-wrap">
      <input id="pwd" type="password" inputmode="numeric" maxlength="4" placeholder="••••" autofocus autocomplete="off"/>
    </div>
    <button id="btn" type="submit">🎮 解锁掌机控制台</button>
    <div id="err" class="err"></div>
  </form>
  <div class="hint-pills">
    <span class="pill">🔒 局域网直连</span>
    <span class="pill">🎨 主题实时同步</span>
  </div>
</div>
<script>
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
  try{ const d = await (await fetch('/api/theme', { cache:'no-store' })).json(); if(d.ok) applyThemeObj(d.theme); }catch{}
}, 4000);

document.getElementById('f').addEventListener('submit', async (e) => {
  e.preventDefault();
  const btn = document.getElementById('btn');
  const err = document.getElementById('err');
  const password = document.getElementById('pwd').value.trim();
  if (!password) return;
  btn.disabled = true; err.textContent = '';
  try {
    const r = await fetch('/api/login', {
      method: 'POST',
      headers: {'Content-Type':'application/json'},
      body: JSON.stringify({password})
    });
    const d = await r.json();
    if (r.ok && d.ok) { location.reload(); return; }
    if (r.status === 429) {
      err.textContent = '尝试过多，请 ' + (d.retryAfterSec || 30) + ' 秒后重试';
    } else if (typeof d.remainingAttempts === 'number') {
      err.textContent = '密码错误，还可尝试 ' + d.remainingAttempts + ' 次';
    } else {
      err.textContent = '动态密码错误，请核对掌机屏幕';
    }
  } catch {
    err.textContent = '连接失败，请确认与掌机在同一局域网';
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
<title>PocketDeck · 掌机游戏库控制台</title>
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

  .toast {
    position:fixed; bottom:24px; right:24px; z-index:60;
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
      <div id="devInfo" class="sub">正在连接掌机…</div>
    </div>
  </div>
  <div class="tabs">
    <button class="tab active" data-tab="games">🎮 游戏库</button>
    <button class="tab" data-tab="categories">🏷️ 标签分类</button>
    <button class="tab" data-tab="files">🗂️ 文件管理</button>
  </div>
  <button class="btn" onclick="rescan()">↻ 重新扫描</button>
  <button class="btn danger" onclick="logout()">退出登录</button>
</header>

<div class="wrap">
  <!-- TAB 1: GAMES -->
  <section id="tab-games">
    <div id="statsGrid" class="stats-grid"></div>
    <div class="toolbar">
      <input id="gSearch" class="search" placeholder="搜索游戏名、官方名、文件名、厂商、类型…" oninput="renderGames()"/>
      <select id="gVis" onchange="renderGames()">
        <option value="all">全部状态</option>
        <option value="visible">仅已显示</option>
        <option value="fav">仅收藏 ♥</option>
        <option value="hidden">仅已隐藏</option>
      </select>
      <select id="gPlat" onchange="renderGames()"><option value="">全部平台</option></select>
      <select id="gCat" onchange="renderGames()"><option value="">全部分类</option></select>
      <div class="tabs" style="padding:3px;">
        <button id="vmGrid" class="tab active" style="padding:5px 11px;font-size:12px;" onclick="setGameView('grid')">封面网格</button>
        <button id="vmList" class="tab" style="padding:5px 11px;font-size:12px;" onclick="setGameView('list')">详情列表</button>
      </div>
    </div>
    <div id="gamesContainer"></div>
  </section>

  <!-- TAB 2: CATEGORIES -->
  <section id="tab-categories" style="display:none">
    <div class="cat-banner">
      <div style="flex:1;min-width:220px;">
        <div style="font-weight:800;font-size:15px;">掌机首页标签分类管理</div>
        <div class="sub">拖动左侧或整行卡片即可自由排序，支持编辑、显隐或新建分类标签，点击「保存到掌机」后同步生效。</div>
      </div>
      <span id="catDirtyBadge" class="chip warn" style="display:none;">● 有未保存修改</span>
      <button class="btn" onclick="openNewCategoryModal()">+ 新建分类</button>
      <button class="btn" onclick="loadCategories()">↺ 重新加载</button>
      <button class="btn primary" onclick="saveCategoriesToDevice()">💾 保存到掌机</button>
    </div>
    <div id="catList" class="list"></div>
  </section>

  <!-- TAB 3: FILES -->
  <section id="tab-files" style="display:none">
    <div class="toolbar">
      <button id="upBtn" class="btn" onclick="goParent()" disabled>↩ 上级</button>
      <select id="fileSort" onchange="renderFiles()">
        <option value="name">按名称</option>
        <option value="size">按大小</option>
        <option value="modified">按修改时间</option>
        <option value="type">按类型</option>
      </select>
      <input id="fileFilter" class="search" style="min-width:140px;" placeholder="过滤当前目录…" oninput="renderFiles()"/>
      <button id="mkdirBtn" class="btn" onclick="mkdirPrompt()" disabled>+ 新建文件夹</button>
      <label class="btn primary" id="pickUploadLbl" style="display:none">
        📤 上传文件
        <input id="fileInput" type="file" multiple style="display:none" onchange="uploadFiles(this.files)"/>
      </label>
      <label class="btn" id="pickDirUploadLbl" style="display:none">
        📁 上传文件夹
        <input id="dirInput" type="file" webkitdirectory multiple style="display:none" onchange="uploadFolderFiles(this.files)"/>
      </label>
    </div>
    <div id="crumbsBar" class="crumbs"></div>
    <div id="dropzone" class="dropzone" style="display:none">
      将 ROM 文件或整个平台文件夹拖放到此处，直接高速流式上传到当前目录
      <div id="progWrap" class="prog"><div id="progBar"></div></div>
      <div id="progText" class="sub" style="margin-top:6px;"></div>
    </div>
    <div id="filesHeaderBar" style="display:none;align-items:center;gap:10px;margin-bottom:8px;">
      <label style="display:flex;align-items:center;gap:6px;font-size:12.5px;cursor:pointer;">
        <input type="checkbox" id="chkAllFiles" onchange="toggleSelectAllFiles(this.checked)"/> 全选当前目录
      </label>
      <span id="dirStorageInfo" class="sub"></span>
    </div>
    <div id="filesList" class="list"></div>
    <div id="batchBar" class="batch-bar">
      <strong id="batchCountText">已选 0 项</strong>
      <button class="btn sm" onclick="openBatchTransfer('move')">移动…</button>
      <button class="btn sm" onclick="openBatchTransfer('copy')">复制…</button>
      <button class="btn sm primary" onclick="compressSelected()">🗜️ 打包 ZIP</button>
      <button class="btn sm danger" onclick="batchDeleteSelected()">🗑 批量删除</button>
      <button class="btn sm" style="margin-left:auto;" onclick="clearFileSelection()">取消选择</button>
    </div>
  </section>
</div>

<div id="toast" class="toast"></div>

<div id="modalBg" class="modal-bg" onclick="if(event.target===this)closeModal()">
  <div class="modal">
    <div class="modal-hd"><span id="mTitle" style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;"></span><button class="btn sm" onclick="closeModal()">✕</button></div>
    <div id="mBody" class="modal-bd"></div>
    <div id="mFoot" class="modal-ft"></div>
  </div>
</div>

<script>
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
      '<span class="conn-off-txt">Web 服务已断开（请在掌机端重新开启）</span>';
    return;
  }
  const netTxt = (hasInternet === false) ? '仅局域网（无外网）' : '已连接 · 可联网刮削';
  box.innerHTML = '<span class="conn-dot ok"></span>' +
    (base ? esc(base) + ' · ' : '') + esc(netTxt);
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
    if (wasOff) toast('掌机 Web 服务已恢复连接');
  } catch {
    if (webConnected) {
      renderDevInfo(false, false);
      toast('掌机 Web 服务已断开');
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
  sel.innerHTML = '<option value="">全部平台</option>' + plats.map(p => '<option value="' + esc(p) + '">' + esc(p) + '</option>').join('');
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
    '<div class="stat-card"><div class="stat-ico">🎮</div><div><div class="stat-val">' + games.length + '</div><div class="stat-lbl">游戏总数</div></div></div>' +
    '<div class="stat-card"><div class="stat-ico">🕹️</div><div><div class="stat-val">' + platCount + '</div><div class="stat-lbl">活跃平台</div></div></div>' +
    '<div class="stat-card"><div class="stat-ico">♥</div><div><div class="stat-val">' + favCount + '</div><div class="stat-lbl">收藏游戏</div></div></div>' +
    '<div class="stat-card"><div class="stat-ico">🏷️</div><div><div class="stat-val">' + customCats + '</div><div class="stat-lbl">自定义标签</div></div></div>' +
    '<div class="stat-card"><div class="stat-ico">💾</div><div><div class="stat-val">' + (freeBytes ? fmtBytes(freeBytes) : '—') + '</div><div class="stat-lbl">可用存储</div></div></div>';
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
    container.innerHTML = '<div class="sub" style="padding:24px 0;">没有匹配的游戏</div>';
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
          '<button class="badge-fav' + (g.favorite ? ' on' : '') + '" onclick="event.stopPropagation();toggleFav(\'' + escJs(g.id) + '\',' + (!g.favorite) + ')" title="收藏">' + (g.favorite ? '♥' : '♡') + '</button>' +
        '</div>' +
        '<div class="gbody">' +
          '<div class="gtitle" title="' + esc(g.title) + '">' + esc(g.title) + '</div>' +
          '<div class="gmeta">' + esc(metaLine) + '</div>' +
          '<div class="gchips">' +
            (g.hidden ? '<span class="chip warn">已隐藏</span>' : '') +
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
            (g.favorite ? '<span class="chip">♥ 收藏</span>' : '') +
            (g.hidden ? '<span class="chip warn">已隐藏</span>' : '') +
          '</div>' +
          '<div class="desc">' + esc(g.platformName) + (g.isRom ? ' · ' + fmtBytes(g.size) : ' · Android App') + (metaSummary ? ' · ' + esc(metaSummary) : '') + '</div>' +
        '</div>' +
        '<div class="actions">' +
          '<button class="btn sm" onclick="openGameDetail(\'' + escJs(g.id) + '\')">详情/刮削</button>' +
          '<button class="btn sm" onclick="toggleFav(\'' + escJs(g.id) + '\',' + (!g.favorite) + ')">' + (g.favorite ? '取消收藏' : '收藏') + '</button>' +
          '<button class="btn sm" onclick="toggleHide(\'' + escJs(g.id) + '\',' + (!g.hidden) + ')">' + (g.hidden ? '恢复' : '隐藏') + '</button>' +
          (g.isRom ? '<a class="btn sm" href="/api/files/download?path=' + safeId + '">下载</a>' : '') +
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
          ? '<img id="detCoverImg" src="/api/cover?id=' + safeId + '&v=' + coverVer + '" onerror="this.outerHTML=\'<div class=sub>无封面</div>\'"/>'
          : '<div class="sub">暂无封面</div>') +
      '</div>' +
      '<div class="detail-info">' +
        '<div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">' +
          '<span class="chip">' + esc(g.platformName) + ' · ' + esc(g.platformFullName || '') + '</span>' +
          (g.favorite ? '<span class="chip ok">♥ 已收藏</span>' : '') +
          (g.hidden ? '<span class="chip warn">已隐藏</span>' : '') +
        '</div>' +
        '<div style="margin-top:10px;display:flex;gap:8px;flex-wrap:wrap;">' +
          '<button class="btn sm primary" id="btnRefreshMeta" onclick="refreshGameMetaFromHandheld(\'' + escJs(g.id) + '\')">✨ 掌机抓取封面与元数据</button>' +
          '<button class="btn sm" onclick="scrapeCoverCandidates(\'' + escJs(g.id) + '\')">🖼️ 选择候选封面</button>' +
          '<label class="btn sm">📁 上传封面<input type="file" accept="image/*" style="display:none" onchange="uploadCustomCover(\'' + escJs(g.id) + '\', this.files[0])"/></label>' +
          (g.hasCustomCover ? '<button class="btn sm" onclick="resetGameCover(\'' + escJs(g.id) + '\')">恢复默认封面</button>' : '') +
        '</div>' +
        '<div class="meta-kv">' +
          '<div class="kv-box"><div class="kv-k">官方名称</div><div class="kv-v">' + esc(g.officialName || '—') + '</div></div>' +
          '<div class="kv-box"><div class="kv-k">发行时间</div><div class="kv-v">' + esc(m.releaseDate || '—') + '</div></div>' +
          '<div class="kv-box"><div class="kv-k">游戏类型</div><div class="kv-v">' + esc(m.genre || '—') + '</div></div>' +
          '<div class="kv-box"><div class="kv-k">开发商</div><div class="kv-v">' + esc(m.developer || '—') + '</div></div>' +
          '<div class="kv-box"><div class="kv-k">发行商</div><div class="kv-v">' + esc(m.publisher || '—') + '</div></div>' +
          '<div class="kv-box"><div class="kv-k">大小 / CRC32</div><div class="kv-v">' + (g.isRom ? fmtBytes(g.size) : fmtBytes(g.size) + ' (APK)') + (mInfo.crc32 ? ' · ' + esc(mInfo.crc32) : '') + '</div></div>' +
          '<div class="kv-box"><div class="kv-k">元数据来源</div><div class="kv-v">' + esc((mInfo.providers || []).join(', ') || '—') + '</div></div>' +
          '<div class="kv-box"><div class="kv-k">封面来源</div><div class="kv-v">' + esc(g.coverProvider || '—') + '</div></div>' +
        '</div>' +
      '</div>' +
    '</div>' +
    '<div style="margin-top:8px;padding-top:12px;border-top:1px solid var(--border);">' +
      '<div style="font-weight:700;font-size:13px;margin-bottom:8px;">所属自定义标签分类</div>' +
      (customCats.length
        ? '<div class="plat-grid" style="max-height:140px;">' + customCats.map(c => {
            const viaPlat = (c.sources || []).includes(g.platformId);
            const manual = (c.games || []).includes(g.id);
            const checked = viaPlat || manual;
            return '<label class="plat-chk' + (checked ? ' checked' : '') + '">' +
              '<input type="checkbox" class="gcat-chk" value="' + esc(c.id) + '" ' + (checked ? 'checked' : '') + ' ' + (viaPlat ? 'disabled' : '') + '/>' +
              '<span>' + esc(c.name) + (viaPlat ? ' (平台关联)' : '') + '</span></label>';
          }).join('') + '</div>' +
          '<div style="margin-top:8px;"><button class="btn sm primary" onclick="saveGameCats(\'' + escJs(g.id) + '\')">保存所属分类</button></div>'
        : '<div class="sub">暂无自定义分类，可在「标签分类」页新建。</div>') +
    '</div>' +
    '<div id="candArea"></div>' +
    '<div class="sub" style="margin-top:12px;word-break:break-all;">路径/包名：' + esc(g.id) + '</div>';

  el('mFoot').innerHTML =
    '<button class="btn" onclick="renameGame(\'' + escJs(g.id) + '\',\'' + escJs(g.title) + '\')">重命名</button>' +
    '<button class="btn" onclick="toggleFav(\'' + escJs(g.id) + '\',' + (!g.favorite) + ',true)">' + (g.favorite ? '取消收藏' : '♥ 收藏') + '</button>' +
    '<button class="btn" onclick="toggleHide(\'' + escJs(g.id) + '\',' + (!g.hidden) + ',true)">' + (g.hidden ? '恢复显示' : '隐藏') + '</button>' +
    (g.isRom ? '<a class="btn" href="/api/files/download?path=' + safeId + '">下载 ROM</a>' : '') +
    (g.isRom ? '<button class="btn danger" onclick="deleteFile(\'' + escJs(g.id) + '\',\'' + escJs(g.fileName) + '\',true)">删除文件</button>' : '') +
    '<button class="btn primary" onclick="closeModal()">关闭</button>';

  el('modalBg').classList.add('open');
}

async function refreshGameMetaFromHandheld(id) {
  const btn = el('btnRefreshMeta');
  if (btn) { btn.disabled = true; btn.textContent = '⏳ 掌机正在抓取…'; }
  try {
    const r = await api('/api/games/refresh-meta', {
      method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id})
    });
    const d = await r.json();
    if (!d.ok && d.error === 'offline') {
      toast('掌机当前未连接互联网，无法抓取');
    } else if (d.ok) {
      coverVer++;
      toast((d.coverUpdated || d.metaUpdated) ? '掌机抓取完成并已回传' : '未找到新的封面或元数据');
      await loadGames();
      openGameDetail(id);
    } else {
      toast('抓取失败: ' + (d.error || r.status));
    }
  } finally {
    if (btn) { btn.disabled = false; btn.textContent = '✨ 掌机抓取封面与元数据'; }
  }
}

async function scrapeCoverCandidates(id) {
  const area = el('candArea');
  if (area) area.innerHTML = '<div class="sub" style="margin-top:12px;">正在通过掌机查询在线封面源…</div>';
  const r = await api('/api/games/scrape-covers', {
    method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id})
  });
  const d = await r.json();
  if (!d.ok && d.error === 'offline') {
    if (area) area.innerHTML = '<div class="sub" style="margin-top:12px;color:var(--warn);">掌机离线，无法联网抓取封面</div>';
    return;
  }
  const cands = d.candidates || [];
  if (!cands.length) {
    if (area) area.innerHTML = '<div class="sub" style="margin-top:12px;">未找到匹配的候选封面</div>';
    return;
  }
  if (area) {
    area.innerHTML = '<div style="font-weight:700;font-size:13px;margin-top:14px;">点击应用掌机抓取到的候选封面：</div>' +
      '<div class="cand-grid">' + cands.map(c =>
        '<div class="cand-item" onclick="applyCandidateCover(\'' + escJs(id) + '\',\'' + escJs(c.token) + '\')">' +
          '<img loading="lazy" src="/api/cover-candidate?token=' + encodeURIComponent(c.token) + '"/>' +
          '<span>' + esc(c.nodeId) + '</span>' +
        '</div>'
      ).join('') + '</div>';
  }
}

async function applyCandidateCover(id, token) {
  toast('掌机正在下载并保存封面…');
  const r = await api('/api/games/set-cover', {
    method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id, token})
  });
  if (r.ok) {
    coverVer++;
    toast('封面已应用');
    await loadGames();
    openGameDetail(id);
  } else {
    toast('应用封面失败');
  }
}

async function uploadCustomCover(id, file) {
  if (!file) return;
  const r = await api('/api/games/upload-cover?id=' + encodeURIComponent(id), {
    method:'POST', body:file
  });
  if (r.ok) {
    coverVer++;
    toast('自定义封面已上传');
    await loadGames();
    openGameDetail(id);
  } else {
    const j = await r.json().catch(()=>({}));
    toast('上传失败: ' + (j.error || r.status));
  }
}

async function resetGameCover(id) {
  const r = await api('/api/games/reset-cover', {
    method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id})
  });
  if (r.ok) {
    coverVer++;
    toast('已重置为默认封面');
    await loadGames();
    openGameDetail(id);
  }
}

async function saveGameCats(id) {
  const chks = [...document.querySelectorAll('.gcat-chk')];
  const categoryIds = chks.filter(c => c.checked && !c.disabled).map(c => c.value);
  const r = await api('/api/games/categories', {
    method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id, categoryIds})
  });
  if (r.ok) {
    toast('已更新所属分类');
    await Promise.all([loadCategories(), loadGames()]);
    openGameDetail(id);
  }
}

async function toggleFav(id, fav, reopen) {
  await api('/api/games/favorite', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id, favorite:fav}) });
  toast(fav ? '已加入收藏 ♥' : '已取消收藏');
  await loadGames();
  if (reopen) openGameDetail(id);
}

async function renameGame(id, cur) {
  const title = prompt('修改游戏显示名称（留空恢复文件名）', cur);
  if (title === null) return;
  await api('/api/games/rename', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id, title}) });
  toast('已更新游戏名称');
  await loadGames();
  if (el('modalBg').classList.contains('open')) openGameDetail(id);
}

async function toggleHide(id, hidden, reopen) {
  await api('/api/games/hide', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id, hidden}) });
  toast(hidden ? '已隐藏' : '已恢复显示');
  await loadGames();
  if (reopen) openGameDetail(id);
}

// ───────────────────────── Categories Tab ─────────────────────────

function markCatDirty(d) {
  catDirty = d;
  el('catDirtyBadge').style.display = d ? '' : 'none';
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
  gCat.innerHTML = '<option value="">全部分类</option>' +
    catTabs.filter(t => t.type === 'Custom').map(c => '<option value="' + esc(c.id) + '">' + esc(c.name) + '</option>').join('');
  gCat.value = prevCat;

  renderCategories();
  renderStats();
}

const SORT_LABELS = {
  Recent: '最近游玩（默认）',
  TitleAsc: '名称 A → Z',
  TitleDesc: '名称 Z → A',
  ReleaseNewest: '发行日期 新 → 旧',
  ReleaseOldest: '发行日期 旧 → 新',
  ModifiedNewest: '修改时间 新 → 旧',
  SizeDesc: '文件大小 大 → 小',
  SizeAsc: '文件大小 小 → 大',
};

let dragCatIdx = -1;
function renderCategories() {
  const box = el('catList');
  if (!catTabs.length) { box.innerHTML = '<div class="sub">暂无标签</div>'; return; }
  box.innerHTML = catTabs.map((t, idx) => {
    const isCustom = t.type === 'Custom';
    const platNames = (t.sources || []).map(sid => {
      const p = catPlatforms.find(x => x.id === sid);
      return p ? p.shortName : sid.toUpperCase();
    }).join(', ');
    const typeTag = isCustom
      ? '<span class="chip">自定义分类</span>'
      : '<span class="chip muted">系统标签 · ' + esc(t.type) + '</span>';
    const sysSortKey = t.sortOrder || (t.id === 'now' ? 'Recent' : 'TitleAsc');
    const desc = isCustom
      ? ((platNames ? '包含平台: ' + esc(platNames) : '仅手动添加游戏') + ' · 排序: ' + esc(SORT_LABELS[t.sortOrder] || t.sortOrder) + ' · ' + (t.gameCount || 0) + ' 款游戏')
      : ((t.followSystemLanguage ? '跟随系统语言' : '自定义名称') + ' · 排序: ' + esc(SORT_LABELS[sysSortKey] || sysSortKey) + ' · ' + (t.gameCount || 0) + ' 款游戏');
    return '<div class="cat-row' + (t.hidden ? ' is-hidden' : '') + '" draggable="true" data-cat-idx="' + idx + '">' +
      '<span class="drag-handle" title="拖动排序">⋮⋮</span>' +
      '<div class="meta">' +
        '<div class="title">' + esc(t.name) + typeTag + (t.hidden ? '<span class="chip warn">已隐藏</span>' : '') + '</div>' +
        '<div class="desc">' + desc + '</div>' +
      '</div>' +
      '<div class="actions">' +
        '<button class="btn sm" onclick="toggleCatHidden(' + idx + ')">' + (t.hidden ? '显示' : '隐藏') + '</button>' +
        '<button class="btn sm primary" onclick="openEditCategoryModal(' + idx + ')">编辑</button>' +
        (isCustom ? '<button class="btn sm danger" onclick="deleteCatTab(' + idx + ')">删除</button>' : '') +
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
      markCatDirty(true);
      renderCategories();
    });
    row.addEventListener('dragend', () => {
      dragCatIdx = -1;
      clearIndicators();
    });
  });
}

function toggleCatHidden(idx) {
  catTabs[idx].hidden = !catTabs[idx].hidden;
  markCatDirty(true);
  renderCategories();
}

function deleteCatTab(idx) {
  const t = catTabs[idx];
  if (!t || t.type !== 'Custom') return;
  if (!confirm('确认删除自定义分类「' + t.name + '」吗？（不会删除任何 ROM 文件）')) return;
  catTabs.splice(idx, 1);
  markCatDirty(true);
  renderCategories();
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
    ? { id:'', type:'Custom', name:'新分类 ' + (catTabs.filter(x=>x.type==='Custom').length + 1), hidden:false, sources:[], games:[], sortOrder:'TitleAsc' }
    : JSON.parse(JSON.stringify(catTabs[idx]));

  if (tab.type !== 'Custom') {
    const sysOpts = (tab.id === 'now' ? ['Recent', ...catSortOrders] : catSortOrders);
    const curSysSort = tab.sortOrder || (tab.id === 'now' ? 'Recent' : 'TitleAsc');
    el('mTitle').textContent = '编辑系统标签 · ' + tab.name;
    el('mBody').innerHTML =
      '<div style="display:flex;flex-direction:column;gap:12px;">' +
        '<div><div class="kv-k" style="margin-bottom:4px;">自定义显示名称（留空则自动回退系统名称）</div>' +
        '<input id="sysTabName" type="text" maxlength="30" style="width:100%" value="' + esc(tab.customName || '') + '"/></div>' +
        '<div><div class="kv-k" style="margin-bottom:4px;">排序方式</div>' +
        '<select id="sysTabSort" style="width:100%">' +
          sysOpts.map(s => '<option value="' + esc(s) + '" ' + (s === curSysSort ? 'selected' : '') + '>' + esc(SORT_LABELS[s] || s) + '</option>').join('') +
        '</select></div>' +
        '<div>' +
          '<label style="display:flex;align-items:center;gap:8px;cursor:pointer;">' +
            '<input id="sysTabFollow" type="checkbox" ' + (tab.followSystemLanguage ? 'checked' : '') + '/>' +
            '<span>跟随系统语言自动翻译</span>' +
          '</label>' +
          '<div class="kv-k" style="margin:4px 0 0 24px;">输入自定义名称时会自动取消勾选；若勾选则掌机始终显示系统语言名称</div>' +
        '</div>' +
      '</div>';
    el('sysTabName').addEventListener('input', () => {
      el('sysTabFollow').checked = !el('sysTabName').value.trim();
    });
    el('mFoot').innerHTML = '<button class="btn" onclick="closeModal()">取消</button><button class="btn primary" id="saveSysTabBtn">应用</button>';
    el('saveSysTabBtn').onclick = () => {
      const cname = el('sysTabName').value.trim().slice(0, 30);
      const follow = cname ? el('sysTabFollow').checked : true;
      const sortOrder = el('sysTabSort').value;
      catTabs[idx].customName = cname;
      catTabs[idx].followSystemLanguage = follow;
      catTabs[idx].sortOrder = sortOrder;
      catTabs[idx].name = (!follow && cname) ? cname : tab.id.toUpperCase();
      markCatDirty(true);
      closeModal();
      renderCategories();
    };
    el('modalBg').classList.add('open');
    return;
  }

  const sortedPlats = [...catPlatforms].sort((a,b) => (b.inLibrary?1:0) - (a.inLibrary?1:0) || a.shortName.localeCompare(b.shortName));
  el('mTitle').textContent = isNew ? '新建标签分类' : ('编辑分类 · ' + tab.name);
  el('mBody').innerHTML =
    '<div style="display:flex;flex-direction:column;gap:14px;">' +
      '<div style="display:flex;gap:10px;flex-wrap:wrap;">' +
        '<div style="flex:1;min-width:180px;"><div class="kv-k" style="margin-bottom:4px;">分类名称（1~40 字符）</div>' +
          '<input id="catNameIn" type="text" maxlength="40" style="width:100%" value="' + esc(tab.name) + '"/></div>' +
        '<div style="min-width:180px;"><div class="kv-k" style="margin-bottom:4px;">排序方式</div>' +
          '<select id="catSortIn" style="width:100%">' +
            catSortOrders.map(s => '<option value="' + esc(s) + '" ' + (s === tab.sortOrder ? 'selected' : '') + '>' + esc(SORT_LABELS[s] || s) + '</option>').join('') +
          '</select></div>' +
      '</div>' +
      '<div>' +
        '<div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:6px;">' +
          '<span style="font-weight:700;font-size:13px;">绑定平台（合集包含以下平台的全部游戏）</span>' +
          '<input id="platFilterIn" type="text" placeholder="筛选平台…" style="padding:5px 10px;font-size:12px;" oninput="filterCatPlatList(this.value)"/>' +
        '</div>' +
        '<div id="catPlatGrid" class="plat-grid">' +
          sortedPlats.map(p => {
            const chk = (tab.sources || []).includes(p.id);
            return '<label class="plat-chk' + (chk ? ' checked' : '') + '" data-label="' + esc((p.shortName + ' ' + p.fullName + ' ' + p.id).toLowerCase()) + '">' +
              '<input type="checkbox" class="cat-plat-box" value="' + esc(p.id) + '" ' + (chk ? 'checked' : '') + '/>' +
              '<span style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;"><strong>' + esc(p.shortName) + '</strong> · ' + esc(p.fullName) + '</span>' +
              (p.inLibrary ? '<span class="chip ok">' + p.gameCount + '</span>' : '') +
            '</label>';
          }).join('') +
        '</div>' +
      '</div>' +
    '</div>';
  el('mFoot').innerHTML = '<button class="btn" onclick="closeModal()">取消</button><button class="btn primary" id="applyCatBtn">确定</button>';
  el('applyCatBtn').onclick = () => {
    const nm = el('catNameIn').value.trim();
    if (!nm) { toast('分类名称不能为空'); return; }
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
    markCatDirty(true);
    closeModal();
    renderCategories();
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
    toast('校验失败: ' + (d.message || d.error || r.status));
    return;
  }
  toast('已通过安全校验并同步到掌机');
  await loadCategories();
  await loadGames();
}

// ───────────────────────── File Manager Tab ─────────────────────────

async function loadFiles(dir) {
  const data = await (await api('/api/files?dir=' + encodeURIComponent(dir))).json();
  curDir = data.dir || '';
  parentDir = data.parent || '';
  curEntries = data.entries || [];
  selectedPaths.clear();
  updateBatchBar();

  el('upBtn').disabled = !curDir;
  el('mkdirBtn').disabled = !curDir;
  el('pickUploadLbl').style.display = curDir ? '' : 'none';
  el('pickDirUploadLbl').style.display = curDir ? '' : 'none';
  el('dropzone').style.display = curDir ? '' : 'none';
  el('filesHeaderBar').style.display = curDir ? 'flex' : 'none';
  el('chkAllFiles').checked = false;

  const crumbs = data.breadcrumbs || [];
  let crumbHtml = '<span class="crumb" onclick="loadFiles(\'\')">🏠 存储根目录</span>';
  for (const c of crumbs) {
    crumbHtml += ' <span style="color:var(--muted)">›</span> <span class="crumb" onclick="loadFiles(\'' + escJs(c.path) + '\')">' + esc(c.name) + '</span>';
  }
  el('crumbsBar').innerHTML = crumbHtml;
  el('dirStorageInfo').textContent = (data.freeBytes ? ('可用 ' + fmtBytes(data.freeBytes) + ' / 共 ' + fmtBytes(data.totalBytes)) : '');

  renderFiles();
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
    listEl.innerHTML = '<div class="sub" style="padding:18px 0;">当前目录为空</div>';
    return;
  }
  listEl.innerHTML = entries.map(f => {
    const icon = f.isDir ? '📁' : (f.isArchive ? '🗜️' : (f.isImage ? '🖼️' : (f.isAudio ? '🎵' : (f.isFont ? '🔤' : (f.isText ? '📝' : '📄')))));
    const romTag = f.isRom ? '<span class="chip">ROM</span>' : '';
    const typeTag = f.isArchive ? '<span class="chip">压缩包</span>' : (f.isImage ? '<span class="chip">图片</span>' : (f.isAudio ? '<span class="chip">音频</span>' : (f.isFont ? '<span class="chip">字体</span>' : (f.isText ? '<span class="chip">文本</span>' : ''))));
    const sub = f.isRoot
      ? ('ROM 根目录' + (f.freeBytes ? ' · 可用 ' + fmtBytes(f.freeBytes) : ''))
      : (f.isDir ? '文件夹' : fmtBytes(f.size));
    const p = escJs(f.path), n = escJs(f.name);
    const checked = selectedPaths.has(f.path);

    return '<div class="row">' +
      (!f.isRoot ? '<input type="checkbox" ' + (checked ? 'checked' : '') + ' onchange="toggleFileSelect(\'' + p + '\',this.checked)"/>' : '') +
      '<div style="font-size:19px">' + icon + '</div>' +
      '<div class="meta" style="' + (f.isDir ? 'cursor:pointer' : '') + '" ' + (f.isDir ? 'onclick="loadFiles(\'' + p + '\')"' : '') + '>' +
        '<div class="title">' + esc(f.name) + romTag + typeTag + '</div>' +
        '<div class="desc">' + sub + '</div>' +
      '</div>' +
      '<div class="actions">' +
        (f.isRom ? '<button class="btn sm" onclick="openGameDetail(\'' + p + '\')">游戏详情</button>' : '') +
        (f.isImage ? '<button class="btn sm" onclick="previewImage(\'' + p + '\',\'' + n + '\')">预览</button>' : '') +
        (f.isAudio ? '<button class="btn sm" onclick="playAudio(\'' + p + '\',\'' + n + '\')">播放</button>' : '') +
        (f.isText ? '<button class="btn sm" onclick="openTextEditor(\'' + p + '\',\'' + n + '\')">查看/编辑</button>' : '') +
        (f.isArchive ? '<button class="btn sm primary" onclick="inspectArchive(\'' + p + '\',\'' + n + '\')">查看压缩包</button>' : '') +
        (!f.isDir ? '<a class="btn sm" href="/api/files/download?path=' + encodeURIComponent(f.path) + '">下载</a>' : '') +
        (!f.isRoot ? '<button class="btn sm" onclick="renameFile(\'' + p + '\',\'' + n + '\')">重命名</button>' : '') +
        (!f.isRoot ? '<button class="btn sm" onclick="openMoveCopyModal(\'move\',[\'' + p + '\'],\'' + n + '\')">移动</button>' : '') +
        (!f.isRoot ? '<button class="btn sm" onclick="openMoveCopyModal(\'copy\',[\'' + p + '\'],\'' + n + '\')">复制</button>' : '') +
        (!f.isRoot ? '<button class="btn sm danger" onclick="deleteFile(\'' + p + '\',\'' + n + '\',false,' + !!f.isDir + ')">删除</button>' : '') +
      '</div></div>';
  }).join('');
}

function toggleFileSelect(path, checked) {
  if (checked) selectedPaths.add(path); else selectedPaths.delete(path);
  updateBatchBar();
}
function toggleSelectAllFiles(checked) {
  selectedPaths.clear();
  if (checked) curEntries.filter(e => !e.isRoot).forEach(e => selectedPaths.add(e.path));
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
  el('batchBar').classList.toggle('show', n > 0);
  el('batchCountText').textContent = '已选 ' + n + ' 项';
}

function goParent() { if (curDir) loadFiles(parentDir); }

async function mkdirPrompt() {
  const name = prompt('新建文件夹名称');
  if (!name || !name.trim()) return;
  const r = await api('/api/files/mkdir', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({dir:curDir, name:name.trim()}) });
  if (r.ok) { toast('文件夹已创建'); loadFiles(curDir); } else toast('创建失败');
}
async function renameFile(path, cur) {
  const newName = prompt('重命名（可修改扩展名或文件夹名）', cur);
  if (!newName || !newName.trim() || newName.trim() === cur) return;
  const r = await api('/api/files/rename', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({path, newName:newName.trim()}) });
  if (r.ok) { toast('已重命名'); loadFiles(curDir); loadGames(); } else toast('重命名失败');
}
async function deleteFile(path, name, fromGames, isDir) {
  const warn = isDir ? '确认递归永久删除文件夹「' + name + '」及其全部内容吗？此操作不可恢复！' : '确认永久删除「' + name + '」吗？此操作不可恢复！';
  if (!confirm(warn)) return;
  const r = await api('/api/files/delete', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({path}) });
  if (r.ok) {
    toast('已删除');
    if (fromGames) closeModal();
    await loadGames();
    if (!fromGames && curDir) loadFiles(curDir);
  } else toast('删除失败');
}

function openBatchTransfer(mode) {
  const paths = [...selectedPaths];
  if (!paths.length) return;
  openMoveCopyModal(mode, paths, paths.length + ' 个项目');
}

async function batchDeleteSelected() {
  const paths = [...selectedPaths];
  if (!paths.length) return;
  if (!confirm('确认永久删除选中的 ' + paths.length + ' 项（含文件夹内所有文件）吗？此操作不可恢复！')) return;
  const r = await api('/api/files/batch-delete', {
    method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({paths})
  });
  if (r.ok) { toast('批量删除完成'); loadFiles(curDir); loadGames(); }
  else toast('批量删除失败');
}

async function compressSelected() {
  const paths = [...selectedPaths];
  if (!paths.length || !curDir) return;
  const name = prompt('输入生成的 ZIP 文件名', 'pocketdeck_bundle');
  if (!name || !name.trim()) return;
  toast('正在打包 ZIP…');
  const r = await api('/api/files/compress', {
    method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({paths, targetDir:curDir, zipName:name.trim()})
  });
  const j = await r.json().catch(() => ({}));
  if (r.ok && j.ok) { toast('已创建 ' + j.name); loadFiles(curDir); loadGames(); }
  else toast('打包失败: ' + (j.error || r.status));
}

async function inspectArchive(path, name) {
  el('mTitle').textContent = '🗜️ 压缩包 · ' + name;
  el('mBody').innerHTML = '<div class="sub">正在解析压缩包内容…</div>';
  el('mFoot').innerHTML =
    '<button class="btn" onclick="extractArchive(\'' + escJs(path) + '\',\'' + escJs(name) + '\',\'here\')">解压到当前目录</button>' +
    '<button class="btn primary" onclick="extractArchive(\'' + escJs(path) + '\',\'' + escJs(name) + '\',\'folder\')">解压到同名文件夹</button>';
  el('modalBg').classList.add('open');

  const r = await api('/api/files/archive-entries?path=' + encodeURIComponent(path));
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) {
    el('mBody').innerHTML = '<div class="sub" style="color:var(--danger)">解析失败: ' + esc(d.error || r.status) + '</div>';
    return;
  }
  const entries = d.entries || [];
  el('mBody').innerHTML = '<div class="sub" style="margin-bottom:8px;">共 ' + entries.length + ' 个条目</div>' +
    '<div class="list">' + entries.slice(0, 300).map(e =>
      '<div class="row" style="padding:7px 10px;"><span>' + (e.isDir ? '📁' : '📄') + '</span><span style="flex:1;font-size:12.5px;word-break:break-all;">' + esc(e.name) + '</span><span class="sub">' + (e.isDir ? '' : fmtBytes(e.size)) + '</span></div>'
    ).join('') + '</div>';
}

async function extractArchive(path, name, mode) {
  toast('正在解压 ' + name + ' ...');
  const r = await api('/api/files/extract', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({path, mode}) });
  const j = await r.json().catch(() => ({}));
  if (r.ok && j.ok) {
    toast('解压完成（' + (j.count || 0) + ' 个文件 → ' + (j.dest || '') + '）');
    closeModal(); loadFiles(curDir); loadGames();
  } else {
    toast('解压失败: ' + (j.error || r.status));
  }
}

function closeModal() {
  el('modalBg').classList.remove('open');
  el('mBody').innerHTML = '';
  el('mFoot').innerHTML = '';
}
function previewImage(path, name) {
  el('mTitle').textContent = '🖼️ 图片预览 · ' + name;
  el('mBody').innerHTML = '<img class="img-view" src="/api/files/raw?path=' + encodeURIComponent(path) + '"/>';
  el('mFoot').innerHTML = '<a class="btn" href="/api/files/download?path=' + encodeURIComponent(path) + '">下载原图</a><button class="btn primary" onclick="closeModal()">关闭</button>';
  el('modalBg').classList.add('open');
}
function playAudio(path, name) {
  el('mTitle').textContent = '🎵 音频播放 · ' + name;
  el('mBody').innerHTML = '<audio controls autoplay style="width:100%;margin:20px 0;" src="/api/files/raw?path=' + encodeURIComponent(path) + '"></audio>';
  el('mFoot').innerHTML = '<a class="btn" href="/api/files/download?path=' + encodeURIComponent(path) + '">下载音频</a><button class="btn primary" onclick="closeModal()">关闭</button>';
  el('modalBg').classList.add('open');
}
async function openTextEditor(path, name) {
  const r = await api('/api/files/text?path=' + encodeURIComponent(path));
  const j = await r.json().catch(() => ({}));
  if (!r.ok || !j.ok) { toast('无法读取文本: ' + (j.error || r.status)); return; }
  el('mTitle').textContent = '📝 编辑文本 · ' + name;
  el('mBody').innerHTML = '<textarea id="txtEditor" class="editor"></textarea>';
  el('txtEditor').value = j.content || '';
  el('mFoot').innerHTML = '<button class="btn" onclick="closeModal()">取消</button><button class="btn primary" id="saveTxtBtn">保存修改</button>';
  el('saveTxtBtn').onclick = async () => {
    const content = el('txtEditor').value;
    const sr = await api('/api/files/text', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({path, content}) });
    if (sr.ok) { toast('已保存文件'); closeModal(); } else toast('保存失败');
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
  el('mTitle').textContent = (mode === 'copy' ? '📋 复制「' : '📦 移动「') + label + '」到…';
  el('modalBg').classList.add('open');
  await renderTransferBrowser(transferBrowseDir);
}
async function renderTransferBrowser(dir) {
  transferBrowseDir = dir;
  const r = await api('/api/files?dir=' + encodeURIComponent(dir));
  const data = await r.json();
  const pathSet = new Set(transferPaths);
  const dirs = (data.entries || []).filter(e => e.isDir && !pathSet.has(e.path));
  const upHtml = data.dir ? '<div class="dir-pick" onclick="renderTransferBrowser(\'' + escJs(data.parent || '') + '\')"><span>↩ 返回上级</span><span class="sub">..</span></div>' : '';
  const listHtml = dirs.map(d => '<div class="dir-pick" onclick="renderTransferBrowser(\'' + escJs(d.path) + '\')"><span>📁 ' + esc(d.name) + '</span><span class="sub">进入 →</span></div>').join('');
  const newFolderBtn = data.dir ? '<button class="btn sm" onclick="mkdirInTransfer()">+ 在此处新建文件夹</button>' : '';
  el('mBody').innerHTML =
    '<div style="display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:10px;flex-wrap:wrap;">' +
      '<div class="sub">目标目录：' + esc(data.dir || '根目录列表（请选择一个根目录）') + '</div>' + newFolderBtn +
    '</div>' +
    upHtml + (listHtml || '<div class="sub">此目录下无子文件夹</div>');
  el('mFoot').innerHTML = '<button class="btn" onclick="closeModal()">取消</button>' +
    (data.dir ? '<button class="btn primary" id="confirmTxBtn">' + (transferMode === 'copy' ? '复制到此处' : '移动到此处') + '</button>' : '');
  const btn = el('confirmTxBtn');
  if (btn) btn.onclick = async () => {
    const endpoint = transferMode === 'copy' ? '/api/files/batch-copy' : '/api/files/batch-move';
    const mr = await api(endpoint, { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({paths:transferPaths, targetDir:data.dir}) });
    const mj = await mr.json().catch(() => ({}));
    if (mr.ok && mj.ok) {
      toast((transferMode === 'copy' ? '复制成功' : '移动成功') + '（' + (mj.count || transferPaths.length) + ' 项）');
      closeModal(); loadFiles(curDir); loadGames();
    } else {
      toast('操作失败: ' + (mj.error || mr.status));
    }
  };
}
async function mkdirInTransfer() {
  const name = prompt('新建文件夹名称');
  if (!name || !name.trim()) return;
  const r = await api('/api/files/mkdir', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({dir:transferBrowseDir, name:name.trim()}) });
  if (r.ok) renderTransferBrowser(transferBrowseDir); else toast('创建失败');
}

async function rescan() {
  await api('/api/rescan', { method:'POST' });
  toast('已触发掌机重新扫描');
  setTimeout(() => { loadGames(); loadCategories(); }, 1300);
}
async function logout() {
  await fetch('/api/logout', { method:'POST' });
  location.reload();
}

async function uploadFileItems(items) {
  if (!curDir || !items || !items.length) return;
  const wrap = el('progWrap'), bar = el('progBar'), txt = el('progText');
  wrap.style.display = 'block';
  for (let i = 0; i < items.length; i++) {
    const { file, subpath } = items[i];
    await new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      let url = '/api/files/upload?dir=' + encodeURIComponent(curDir) + '&name=' + encodeURIComponent(file.name);
      if (subpath) url += '&subpath=' + encodeURIComponent(subpath);
      xhr.open('POST', url);
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) {
          const pct = Math.round(e.loaded * 100 / e.total);
          bar.style.width = pct + '%';
          txt.textContent = '(' + (i+1) + '/' + items.length + ') ' + (subpath ? subpath + '/' : '') + file.name + ' · ' + pct + '% (' + fmtBytes(e.loaded) + '/' + fmtBytes(e.total) + ')';
        }
      };
      xhr.onload = () => xhr.status === 200 ? resolve() : reject(new Error(xhr.status));
      xhr.onerror = () => reject(new Error('network'));
      xhr.send(file);
    }).catch(() => toast('上传失败: ' + file.name));
  }
  bar.style.width = '0%'; wrap.style.display = 'none'; txt.textContent = '';
  el('fileInput').value = ''; el('dirInput').value = '';
  toast('上传完成（' + items.length + ' 个文件）');
  loadFiles(curDir); loadGames();
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
</script>
</body>
</html>"""
    }
}
