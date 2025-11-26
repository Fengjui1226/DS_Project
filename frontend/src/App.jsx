import React, { useState, useEffect } from 'react';

const API_BASE = '/api';

const cities = [
  '台北', '新北', '桃園', '台中', '台南', '高雄',
  '基隆', '新竹', '苗栗', '彰化', '南投', '雲林',
  '嘉義', '屏東', '宜蘭', '花蓮', '台東'
];

export default function App() {
  const [view, setView] = useState('home');
  const [query, setQuery] = useState('');
  const [city, setCity] = useState('台北');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [categories, setCategories] = useState([]);
  const [semantic, setSemantic] = useState(null);
  const [tree, setTree] = useState(null);

  useEffect(() => {
    fetch(`${API_BASE}/categories`)
      .then(res => res.json())
      .then(data => setCategories(data.categories || []))
      .catch(console.error);
  }, []);

  const handleSearch = async (q = query, c = city) => {
    if (!q.trim()) return;
    
    setLoading(true);
    setError(null);
    setQuery(q);
    setCity(c);
    
    try {
      const res = await fetch(`${API_BASE}/search?query=${encodeURIComponent(q)}&city=${encodeURIComponent(c)}`);
      const data = await res.json();
      
      if (data.success) {
        setResults(data.results);
        setView('results');
        loadSemantic();
        loadTree();
      } else {
        setError(data.error || '搜尋失敗');
      }
    } catch (err) {
      setError('無法連接伺服器');
    } finally {
      setLoading(false);
    }
  };

  const loadSemantic = async () => {
    try {
      const res = await fetch(`${API_BASE}/semantic`);
      const data = await res.json();
      if (data.success) setSemantic(data);
    } catch (e) {}
  };

  const loadTree = async () => {
    try {
      const res = await fetch(`${API_BASE}/tree`);
      const data = await res.json();
      if (data.success) setTree(data);
    } catch (e) {}
  };

  const handleLocate = () => {
    if (!navigator.geolocation) return alert('瀏覽器不支援定位');
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const { latitude: lat, longitude: lng } = pos.coords;
        let detected = '台北';
        if (lat > 25.0 && lng > 121.4 && lng < 121.7) detected = '台北';
        else if (lat > 24.9 && lat < 25.3 && lng > 121.3 && lng < 121.5) detected = '新北';
        else if (lat > 24.0 && lat < 24.3 && lng > 120.5 && lng < 120.8) detected = '台中';
        else if (lat > 22.9 && lat < 23.1 && lng > 120.1 && lng < 120.3) detected = '台南';
        else if (lat > 22.5 && lat < 22.8 && lng > 120.2 && lng < 120.4) detected = '高雄';
        setCity(detected);
      },
      () => alert('定位失敗')
    );
  };

  if (view === 'home') {
    return (
      <div className="app">
        <div className="bg" />
        <div className="home">
          <div className="brand">
            <div className="logo">🎪</div>
            <h1>EventFinder</h1>
            <p className="tagline">探索台灣精彩活動</p>
          </div>

          <div className="search-card">
            <div className="categories">
              {categories.map(cat => (
                <button 
                  key={cat.id} 
                  className="cat-btn"
                  onClick={() => handleSearch(cat.query, city)}
                >
                  <span>{cat.icon}</span> {cat.name}
                </button>
              ))}
            </div>

            <div className="search-row">
              <input
                type="text"
                value={query}
                onChange={e => setQuery(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && handleSearch()}
                placeholder="搜尋音樂會、展覽、市集..."
                className="search-input"
              />
              <select 
                value={city} 
                onChange={e => setCity(e.target.value)}
                className="city-select"
              >
                {cities.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </div>

            <div className="btn-row">
              <button className="btn-locate" onClick={handleLocate}>📍</button>
              <button className="btn-search" onClick={() => handleSearch()}>
                {loading ? '搜尋中...' : '探索活動'}
              </button>
            </div>

            {error && <div className="error">{error}</div>}
          </div>

          <div className="features">
            <div className="feature">
              <span>⚡</span>
              <h3>即時更新</h3>
              <p>自動過濾過期活動</p>
            </div>
            <div className="feature">
              <span>🎯</span>
              <h3>精準排序</h3>
              <p>AI 智慧權重計算</p>
            </div>
            <div className="feature">
              <span>📍</span>
              <h3>在地優先</h3>
              <p>依你的位置排序</p>
            </div>
          </div>

          <footer>Built with ❤️ for Taiwan</footer>
        </div>
      </div>
    );
  }

  return (
    <div className="app">
      <div className="bg" />
      
      <header className="header">
        <div className="header-brand" onClick={() => setView('home')}>
          <span className="header-logo">🎪</span>
          <span className="header-title">EventFinder</span>
        </div>
        
        <div className="header-search">
          <input
            type="text"
            value={query}
            onChange={e => setQuery(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSearch()}
            placeholder="搜尋活動..."
            className="search-input compact"
          />
          <select 
            value={city} 
            onChange={e => setCity(e.target.value)}
            className="city-select compact"
          >
            {cities.map(c => <option key={c} value={c}>{c}</option>)}
          </select>
          <button className="btn-search compact" onClick={() => handleSearch()}>
            搜尋
          </button>
        </div>
      </header>

      <main className="main">
        <div className="results-section">
          <div className="results-header">
            <h2>搜尋結果</h2>
            <p>關鍵字：{query} | 城市：{city} | 共 {results.length} 筆</p>
          </div>

          {loading && (
            <div className="loading">
              <div className="spinner" />
              <p>搜尋中...</p>
            </div>
          )}

          {error && <div className="error-box">⚠️ {error}</div>}

          {!loading && results.length === 0 && (
            <div className="no-results">
              <span>🔍</span>
              <p>沒有找到相關活動</p>
            </div>
          )}

          <div className="results-list">
            {results.map(r => (
              <div key={r.rank} className="result-card">
                <div className="result-top">
                  <span className="rank">#{r.rank}</span>
                  <span className="score">{r.score.toFixed(1)}</span>
                </div>
                <h3>
                  <a href={r.url} target="_blank" rel="noopener noreferrer">
                    {r.title}
                  </a>
                </h3>
                <div className="result-meta">
                  {r.city && <span>📍 {r.city}</span>}
                  {r.eventDate && <span>📅 {r.eventDate}</span>}
                  <span>🌐 {r.domain}</span>
                </div>
                <a href={r.url} target="_blank" rel="noopener noreferrer" className="result-link">
                  前往官方網站 →
                </a>
              </div>
            ))}
          </div>
        </div>

        <aside className="sidebar">
          {tree && tree.domains && (
            <div className="panel">
              <h3>📊 網站結構</h3>
              <div className="domain-list">
                {tree.domains.map((d, i) => (
                  <div key={i} className="domain-item">
                    <span>{d.domain}</span>
                    <span className="domain-score">{d.totalScore.toFixed(1)}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {semantic && (
            <div className="panel">
              <h3>🧠 語意分析</h3>
              {semantic.extractedKeywords?.length > 0 && (
                <div className="keyword-section">
                  <h4>提取的關鍵字</h4>
                  <div className="keywords">
                    {semantic.extractedKeywords.slice(0, 8).map((kw, i) => (
                      <button 
                        key={i} 
                        className="keyword"
                        onClick={() => handleSearch(kw, city)}
                      >
                        {kw}
                      </button>
                    ))}
                  </div>
                </div>
              )}
              {semantic.suggestedKeywords?.length > 0 && (
                <div className="keyword-section">
                  <h4>建議關鍵字</h4>
                  <div className="keywords">
                    {semantic.suggestedKeywords.slice(0, 5).map((kw, i) => (
                      <button 
                        key={i} 
                        className="keyword suggested"
                        onClick={() => handleSearch(kw, city)}
                      >
                        {kw}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          <div className="panel tips">
            <h3>💡 搜尋提示</h3>
            <ul>
              <li>點擊關鍵字可快速搜尋</li>
              <li>分數越高表示活動越相關</li>
              <li>支援多關鍵字搜尋</li>
            </ul>
          </div>
        </aside>
      </main>
    </div>
  );
}