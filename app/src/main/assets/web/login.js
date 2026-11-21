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
