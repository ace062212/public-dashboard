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

function renderSources(sources) {
  const box = $('sources');
  box.innerHTML = '';
  const labels = { weather: '기상청', fineDust: '대기오염도', subway: '지하철 혼잡도' };
  Object.entries(sources).forEach(([key, snap]) => {
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

  const series = [];
  const seen = new Set();
  feed.items
    .filter((p) => p.category === 'TMP' && p.value !== null)
    .sort((a, b) => `${a.fcstDate}${a.fcstTime}`.localeCompare(`${b.fcstDate}${b.fcstTime}`))
    .forEach((p) => {
      const key = p.fcstTime;
      if (seen.has(key)) return;
      seen.add(key);
      series.push({ axis: key, value: p.value });
    });
  drawLine('weather-chart', series.map((s) => s.axis), series.map((s) => s.value));
}

function drawLine(elementId, categories, values) {
  const el = $(elementId);
  if (typeof echarts === 'undefined' || categories.length === 0) {
    el.innerHTML = categories.length === 0 ? '<p class="empty">표시할 예보 값이 없습니다.</p>' : '';
    return;
  }
  if (!state.charts[elementId]) {
    state.charts[elementId] = echarts.init(el, null, { renderer: 'canvas' });
  }
  state.charts[elementId].setOption({
    grid: { left: 34, right: 12, top: 18, bottom: 24 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: categories.map((t) => `${String(t).padStart(4, '0').slice(0, 2)}:${String(t).padStart(4, '0').slice(2)}`),
      axisLine: { lineStyle: { color: '#262d3a' } },
      axisLabel: { color: '#8b949e', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#1c2230' } },
      axisLabel: { color: '#8b949e', fontSize: 11 },
    },
    series: [{
      type: 'line',
      data: values,
      smooth: true,
      symbolSize: 6,
      lineStyle: { color: '#4da3ff', width: 2 },
      itemStyle: { color: '#4da3ff' },
      areaStyle: { color: 'rgba(77,163,255,0.15)' },
    }],
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
    val.append(grade, document.createTextNode(` ${fmt(r.value, 3)}`));
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
    const [data, sources] = await Promise.all([
      getJson('/api/dashboard'),
      getJson('/api/health/upstreams'),
    ]);
    render(data);
    renderSources(sources);
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

document.addEventListener('DOMContentLoaded', () => {
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
});
