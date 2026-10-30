const REFRESH_MS = 60_000;

const state = {
  data: null,
  dustItem: 'PM10',
  subwayFilter: '',
  charts: {},
};

const $ = (id) => document.getElementById(id);

async function getJson(url, options) {
  const res = await fetch(url, options);
  if (!res.ok) {
    let detail = '';
    try {
      const body = await res.json();
      detail = body.message || body.error || '';
    } catch (e) {
      detail = await res.text().catch(() => '');
    }
    throw new Error(`${url} → ${res.status} ${detail}`);
  }
  return res.json();
}

function badge(el, feed) {
  el.textContent = feed.source === 'LIVE' ? '실데이터' : feed.source === 'MOCK' ? '목 데이터' : '대체 데이터';
  el.className = `badge badge-${feed.source}`;
  if (feed.message) {
    el.title = feed.message;
  }
}

function fmt(value, digits = 1) {
  if (value === null || value === undefined || Number.isNaN(value)) return '-';
  return Number(value).toFixed(digits).replace(/\.0$/, '');
}

// 미세먼지/초미세먼지는 µg/m³ 정수, 기체상은 ppm 소수 셋째 자리까지가 사람이 읽는 단위다.
const GAS_ITEMS = new Set(['O3', 'NO2', 'CO', 'SO2']);

function dustDigits(itemName) {
  return GAS_ITEMS.has(itemName) ? 3 : 0;
}

function dustUnit(itemName) {
  return GAS_ITEMS.has(itemName) ? ' ppm' : ' µg/m³';
}

function renderSources(sources) {
  const box = $('sources');
  box.innerHTML = '';
  // 백그라운드 맵은 순서가 없으니 화면에서 쓸 순서로 고정해 읽는 위치가 흔들리지 않게 한다.
  const labels = { weather: '기상청', fineDust: '대기오염도', subway: '지하철 혼잡도' };
  const entries = Object.keys(labels)
    .filter((key) => key in sources)
    .map((key) => [key, sources[key]]);
  if (entries.length === 0) {
    box.innerHTML = '<div class="source-chip"><span class="dot dot-INIT"></span><span>상태 확인 중…</span></div>';
    return;
  }
  entries.forEach(([key, snap]) => {
    const chip = document.createElement('div');
    chip.className = 'source-chip';
    const dot = document.createElement('span');
    dot.className = `dot dot-${snap.status}`;
    const text = document.createElement('span');
    const counts = `성공 ${snap.successCount} / 실패 ${snap.failureCount}`;
    text.textContent = `${labels[key] || key} · ${snap.status} · ${counts}`;
    chip.title = snap.lastError || `마지막 시도 ${snap.lastAttemptAt || '-'}`;
    chip.append(dot, text);
    box.append(chip);
  });
}

function renderWeather(feed, headline) {
  badge($('weather-source'), feed);
  $('temp').innerHTML = `${fmt(headline.temperature)}<span class="unit">℃</span>`;
  $('feels').textContent = `${fmt(headline.feelsLike)}℃`;
  $('pop').textContent = headline.precipitationProbability === null ? '-' : `${fmt(headline.precipitationProbability, 0)}%`;
  $('sky').textContent = headline.skyLabel || '-';
  $('fcst-at').textContent = headline.fcstDate ? `${headline.fcstDate} ${headline.fcstTime || ''}` : '-';

  drawLine('weather-chart', buildForecastRows(feed.items));
}

/** 시간대별로 예보 항목을 한 행으로 묶는다. 호버할 때 이 행을 통째로 보여준다. */
function buildForecastRows(items) {
  const rows = new Map();
  (items || []).forEach((p) => {
    if (!p || !p.fcstTime) return;
    const row = rows.get(p.fcstTime) || { time: p.fcstTime };
    row[p.category] = p.value;
    rows.set(p.fcstTime, row);
  });
  return [...rows.values()].sort((a, b) => a.time.localeCompare(b.time));
}

function clockLabel(time) {
  const padded = String(time || '').padStart(4, '0');
  return `${padded.slice(0, 2)}:${padded.slice(2)}`;
}

const SKY_TEXT = { 1: '맑음', 2: '구름조금', 3: '구름많음', 4: '흐림' };

function skyText(value) {
  if (value === null || value === undefined) return null;
  return SKY_TEXT[Math.round(value)] || `기타(${Math.round(value)})`;
}

/** 호버 툴팁 본문. 숫자에 단위를 붙여 무엇의 값인지 바로 알아본다. */
function tooltipHtml(row) {
  if (!row) return '';
  const line = (label, value, digits, unit) => {
    const shown = value === null || value === undefined ? '-' : `${fmt(value, digits)}${unit}`;
    return `<div class="tt-row"><span>${label}</span><b>${shown}</b></div>`;
  };
  const sky = skyText(row.SKY);
  return `<div class="tt-title">${clockLabel(row.time)}</div>`
    + line('기온', row.TMP, 1, '℃')
    + line('체감온도', row.TMP_FEEL, 1, '℃')
    + line('강수확률', row.POP, 0, '%')
    + line('습도', row.REH, 0, '%')
    + `<div class="tt-row"><span>하늘상태</span><b>${sky === null ? '-' : sky}</b></div>`;
}

function drawLine(elementId, rows) {
  const el = $(elementId);
  if (typeof echarts === 'undefined') {
    return;
  }
  if (!rows || rows.length === 0) {
    el.innerHTML = '<p class="empty">표시할 예보 값이 없습니다.</p>';
    return;
  }
  if (!state.charts[elementId]) {
    state.charts[elementId] = echarts.init(el, null, { renderer: 'canvas' });
  }
  state.charts[elementId].setOption({
    grid: { left: 34, right: 14, top: 28, bottom: 24 },
    legend: {
      data: ['기온', '체감온도'],
      top: 0,
      right: 0,
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 3,
      itemGap: 14,
      textStyle: { color: '#86868b', fontSize: 11 },
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#ffffff',
      borderColor: '#e8e8ed',
      borderWidth: 1,
      padding: [10, 12],
      extraCssText: 'border-radius:12px;box-shadow:0 8px 24px rgba(0,0,0,.12);',
      textStyle: { color: '#1d1d1f', fontSize: 12 },
      axisPointer: { type: 'line', lineStyle: { color: '#d2d2d7', type: 'dashed' } },
      formatter: (params) => tooltipHtml(rows[(Array.isArray(params) ? params[0] : params).dataIndex]),
    },
    xAxis: {
      type: 'category',
      data: rows.map((r) => clockLabel(r.time)),
      axisLine: { lineStyle: { color: '#d2d2d7' } },
      axisTick: { show: false },
      axisLabel: { color: '#86868b', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      scale: true, // 0 고정 눈금이면 11~15℃ 같은 좁은 범위가 납작한 직선이 된다
      splitNumber: 4,
      splitLine: { lineStyle: { color: '#ececf0' } },
      axisLabel: { color: '#86868b', fontSize: 11 },
    },
    series: [
      {
        name: '기온',
        type: 'line',
        data: rows.map((r) => (r.TMP === undefined ? null : r.TMP)),
        smooth: true,
        symbolSize: 6,
        connectNulls: true,
        z: 3,
        lineStyle: { color: '#0071e3', width: 2 },
        itemStyle: { color: '#0071e3' },
        areaStyle: { color: 'rgba(0,113,227,0.10)' },
      },
      {
        name: '체감온도',
        type: 'line',
        data: rows.map((r) => (r.TMP_FEEL === undefined ? null : r.TMP_FEEL)),
        smooth: true,
        symbol: 'none',
        connectNulls: true,
        lineStyle: { color: '#c7c7cc', width: 1.5, type: 'dashed' },
        itemStyle: { color: '#c7c7cc' },
      },
    ],
  }, true);
}

const GRADE_CLASS = {
  좋음: 'grade-좋음', 보통: 'grade-보통', 나쁨: 'grade-나쁨', 매우나쁨: 'grade-매우나쁨',
  여유: 'grade-여유', 혼잡: 'grade-혼잡', 매우혼잡: 'grade-매우혼잡',
};

function renderDust(feed, itemName) {
  badge($('dust-source'), feed);
  const list = $('dust-list');
  const rows = feed.items.filter((r) => r.itemName === itemName && r.value !== null);
  if (rows.length === 0) {
    list.innerHTML = '<li class="empty">표시할 실측 값이 없습니다.</li>';
    return;
  }
  const max = Math.max(...rows.map((r) => r.value), 1);
  list.innerHTML = '';
  rows.sort((a, b) => b.value - a.value).forEach((r) => {
    const li = document.createElement('li');
    const name = document.createElement('span');
    name.textContent = r.regionName || '-';
    const bar = document.createElement('div');
    bar.className = 'bar';
    const fill = document.createElement('span');
    fill.style.width = `${Math.min(100, (r.value / max) * 100)}%`;
    bar.append(fill);
    const val = document.createElement('span');
    val.className = 'val';
    const grade = document.createElement('span');
    grade.className = `grade ${GRADE_CLASS[r.grade] || ''}`;
    grade.textContent = r.grade || '-';
    val.append(grade, document.createTextNode(` ${fmt(r.value, dustDigits(itemName))}${dustUnit(itemName)}`));
    li.append(name, bar, val);
    list.append(li);
  });
}

function renderSubway(feed) {
  badge($('subway-source'), feed);
  const list = $('subway-list');
  if (!feed.items || feed.items.length === 0) {
    list.innerHTML = '<li class="empty">조건에 맞는 역이 없습니다.</li>';
    return;
  }
  list.innerHTML = '';
  feed.items
    .slice()
    .sort((a, b) => (b.congestionRate || 0) - (a.congestionRate || 0))
    .slice(0, 12)
    .forEach((s) => {
      const li = document.createElement('li');
      const name = document.createElement('span');
      name.textContent = `${s.lineName || s.lineId || ''} ${s.stationName}`;
      const bar = document.createElement('div');
      bar.className = 'bar';
      const fill = document.createElement('span');
      fill.style.width = `${Math.min(100, ((s.congestionRate || 0) / 200) * 100)}%`;
      bar.append(fill);
      const val = document.createElement('span');
      val.className = 'val';
      const grade = document.createElement('span');
      grade.className = `grade ${GRADE_CLASS[s.level] || ''}`;
      grade.textContent = s.level || '-';
      val.append(grade, document.createTextNode(` ${fmt(s.congestionRate, 0)}%`));
      li.append(name, bar, val);
      list.append(li);
    });
}

function render(data) {
  state.data = data;
  $('updated-at').textContent = `갱신 ${new Date(data.generatedAt).toLocaleTimeString('ko-KR')}`;
  renderWeather(data.weather, data.headline);
  renderDust(data.fineDust, state.dustItem);
  renderSubway(data.subway);
  $('foot-note').textContent =
    '목 데이터는 서비스 키 미설정 상태의 대체 값입니다. 실데이터는 WEATHER_SERVICE_KEY 등 환경 변수를 설정하세요.';
}

async function load() {
  try {
    // 상태 칩은 실제 호출이 일어난 뒤에만 값이 채워지므로, 화면 데이터를 먼저 받고 조회한다.
    const data = await getJson('/api/dashboard');
    render(data);
    renderSources(await getJson('/api/health/upstreams'));
  } catch (e) {
    $('updated-at').textContent = '조회 실패';
    $('foot-note').textContent = e.message;
    $('foot-note').className = 'error';
  }
}

async function forceRefresh() {
  $('refresh-btn').disabled = true;
  try {
    render(await getJson('/api/refresh', { method: 'POST' }));
    renderSources(await getJson('/api/health/upstreams'));
  } catch (e) {
    $('foot-note').textContent = e.message;
    $('foot-note').className = 'error';
  } finally {
    $('refresh-btn').disabled = false;
  }
}

async function loadSubway(keyword) {
  state.subwayFilter = keyword;
  const url = keyword ? `/api/subway?stationName=${encodeURIComponent(keyword)}` : '/api/subway';
  try {
    renderSubway(await getJson(url));
  } catch (e) {
    $('subway-list').innerHTML = `<li class="error">${e.message}</li>`;
  }
}

function bootstrap() {
  $('refresh-btn').addEventListener('click', forceRefresh);

  document.querySelectorAll('.seg').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.seg').forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');
      state.dustItem = btn.dataset.item;
      if (state.data) renderDust(state.data.fineDust, state.dustItem);
    });
  });

  $('subway-form').addEventListener('submit', (event) => {
    event.preventDefault();
    loadSubway($('subway-input').value.trim());
  });

  window.addEventListener('resize', () => {
    Object.values(state.charts).forEach((chart) => chart.resize());
  });

  load();
  setInterval(load, REFRESH_MS);
}

// 브라우저 밖(노드 검사)에서는 DOM을 건드리지 않는다.
if (typeof document !== 'undefined') {
  document.addEventListener('DOMContentLoaded', bootstrap);
}

// 노드에서 순수 함수만 꺼내 확인할 수 있게 한다.
if (typeof module !== 'undefined' && module.exports) {
  module.exports = { buildForecastRows, tooltipHtml, clockLabel, skyText, fmt, dustDigits, dustUnit };
}
