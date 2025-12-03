import React, { useState, useEffect, useRef, useCallback } from 'react';
import SearchBar from './components/SearchBar';
import ResultCard from './components/ResultCard';
import Sidebar from './components/Sidebar';
import Pagination from './components/Pagination';
import SubpageModal from './components/SubpageModal';
import SearchHistory from './components/SearchHistory';
import './index.css';

const API_BASE = '/api';

const cities = [
  '台北', '新北', '桃園', '台中', '台南', '高雄',
  '基隆', '新竹', '苗栗', '彰化', '南投', '雲林',
  '嘉義', '屏東', '宜蘭', '花蓮', '台東'
];

export default function App() {
  // 狀態管理
  const [view, setView] = useState('home');
  const [query, setQuery] = useState('');
  const [city, setCity] = useState('台北');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [categories, setCategories] = useState([]);
  const [semantic, setSemantic] = useState(null);
  const [tree, setTree] = useState(null);
  
  // 分頁
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalCount, setTotalCount] = useState(0);
  
  // 收藏
  const [favorites, setFavorites] = useState(() => {
    const saved = localStorage.getItem('eventfinder_favorites');
    return saved ? JSON.parse(saved) : [];
  });
  
  // 搜尋歷史
  const [searchHistory, setSearchHistory] = useState(() => {
    const saved = localStorage.getItem('eventfinder_history');
    return saved ? JSON.parse(saved) : [];
  });
  
  // 子網頁彈窗
  const [subpageModal, setSubpageModal] = useState({ open: false, domain: '', data: null });
  
  // 搜尋建議
  const [suggestions, setSuggestions] = useState([]);
  const [showSuggestions, setShowSuggestions] = useState(false);

  // 載入分類
  useEffect(() => {
    fetch(`${API_BASE}/categories`)
      .then(res => res.json())
      .then(data => setCategories(data.categories || []))
      .catch(console.error);
  }, []);

  // 儲存收藏到 localStorage
  useEffect(() => {
    localStorage.setItem('eventfinder_favorites', JSON.stringify(favorites));
  }, [favorites]);

  // 儲存歷史到 localStorage
  useEffect(() => {
    localStorage.setItem('eventfinder_history', JSON.stringify(searchHistory));
  }, [searchHistory]);

  // 搜尋建議
  const fetchSuggestions = useCallback(async (q) => {
    if (!q || q.length < 1) {
      setSuggestions([]);
      return;
    }
    try {
      const res = await fetch(`${API_BASE}/suggestions?q=${encodeURIComponent(q)}`);
      const data = await res.json();
      setSuggestions(data.suggestions || []);
    } catch (e) {
      console.error(e);
    }
  }, []);

  // 搜尋
  const handleSearch = async (q = query, c = city, page = 1) => {
    if (!q.trim()) return;
    
    setLoading(true);
    setError(null);
    setQuery(q);
    setCity(c);
    setCurrentPage(page);
    setShowSuggestions(false);
    
    // 更新搜尋歷史
    const newHistory = [q, ...searchHistory.filter(h => h !== q)].slice(0, 10);
    setSearchHistory(newHistory);
    
    try {
      const res = await fetch(
        `${API_BASE}/search?query=${encodeURIComponent(q)}&city=${encodeURIComponent(c)}&page=${page}`
      );
      const data = await res.json();
      
      if (data.success) {
        setResults(data.results);
        setTotalPages(data.totalPages);
        setTotalCount(data.totalCount);
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

  // 子網頁查詢
  const handleSubpageQuery = async (domain) => {
    try {
      const res = await fetch(`${API_BASE}/subpages?domain=${encodeURIComponent(domain)}`);
      const data = await res.json();
      if (data.success) {
        setSubpageModal({ open: true, domain, data });
      }
    } catch (e) {
      console.error(e);
    }
  };

  // 收藏功能
  const toggleFavorite = (result) => {
    const exists = favorites.find(f => f.url === result.url);
    if (exists) {
      setFavorites(favorites.filter(f => f.url !== result.url));
    } else {
      setFavorites([...favorites, { ...result, savedAt: new Date().toISOString() }]);
    }
  };

  const isFavorite = (url) => favorites.some(f => f.url === url);

  // 定位
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

  // 清除歷史
  const clearHistory = () => {
    setSearchHistory([]);
  };

  // 首頁
  if (view === 'home') {
    return (
      <div className="app">
        <div className="bg" />
        <div className="home animate-fade-in">
          <div className="brand">
            <div className="logo">🎪</div>
            <h1>EventFinder</h1>
            <p className="tagline">探索台灣精彩活動</p>
          </div>

          <div className="search-card glass">
            <div className="categories">
              {categories.map(cat => (
                <button 
                  key={cat.id} 
                  className="cat-btn"
                  style={{ '--cat-color': cat.color }}
                  onClick={() => handleSearch(cat.query, city)}
                >
                  <span>{cat.icon}</span> {cat.name}
                </button>
              ))}
            </div>

            <SearchBar
              query={query}
              setQuery={setQuery}
              city={city}
              setCity={setCity}
              cities={cities}
              onSearch={handleSearch}
              onLocate={handleLocate}
              loading={loading}
              suggestions={suggestions}
              showSuggestions={showSuggestions}
              setShowSuggestions={setShowSuggestions}
              fetchSuggestions={fetchSuggestions}
            />

            {error && <div className="error">{error}</div>}
            
            {searchHistory.length > 0 && (
              <SearchHistory 
                history={searchHistory} 
                onSelect={(q) => handleSearch(q, city)}
                onClear={clearHistory}
              />
            )}
          </div>

          <div className="features">
            <div className="feature animate-slide-up" style={{ animationDelay: '0.1s' }}>
              <span>⚡</span>
              <h3>即時更新</h3>
              <p>自動過濾過期活動</p>
            </div>
            <div className="feature animate-slide-up" style={{ animationDelay: '0.2s' }}>
              <span>🎯</span>
              <h3>精準排序</h3>
              <p>AI 智慧權重計算</p>
            </div>
            <div className="feature animate-slide-up" style={{ animationDelay: '0.3s' }}>
              <span>📍</span>
              <h3>在地優先</h3>
              <p>依你的位置排序</p>
            </div>
          </div>

          {favorites.length > 0 && (
            <div className="favorites-preview glass">
              <h3>❤️ 我的收藏 ({favorites.length})</h3>
              <div className="favorites-list">
                {favorites.slice(0, 3).map((fav, i) => (
                  <a key={i} href={fav.url} target="_blank" rel="noopener noreferrer" className="fav-item">
                    {fav.title}
                  </a>
                ))}
              </div>
            </div>
          )}

          <footer>Built with ❤️ for Taiwan</footer>
        </div>
      </div>
    );
  }

  // 結果頁
  return (
    <div className="app">
      <div className="bg" />
      
      <header className="header glass">
        <div className="header-brand" onClick={() => setView('home')}>
          <span className="header-logo">🎪</span>
          <span className="header-title">EventFinder</span>
        </div>
        
        <SearchBar
          query={query}
          setQuery={setQuery}
          city={city}
          setCity={setCity}
          cities={cities}
          onSearch={handleSearch}
          onLocate={handleLocate}
          loading={loading}
          compact
          suggestions={suggestions}
          showSuggestions={showSuggestions}
          setShowSuggestions={setShowSuggestions}
          fetchSuggestions={fetchSuggestions}
        />
      </header>

      <main className="main">
        <div className="results-section">
          <div className="results-header animate-fade-in">
            <h2>搜尋結果</h2>
            <p>
              關鍵字：<strong>{query}</strong> | 
              城市：<strong>{city}</strong> | 
              共 <strong>{totalCount}</strong> 筆
            </p>
          </div>

          {loading && (
            <div className="loading">
              <div className="spinner" />
              <p>搜尋中...</p>
            </div>
          )}

          {error && <div className="error-box animate-shake">⚠️ {error}</div>}

          {!loading && results.length === 0 && (
            <div className="no-results animate-fade-in">
              <span>🔍</span>
              <p>沒有找到相關活動</p>
            </div>
          )}

          <div className="results-list">
            {results.map((r, index) => (
              <ResultCard 
                key={r.rank} 
                result={r}
                index={index}
                isFavorite={isFavorite(r.url)}
                onToggleFavorite={() => toggleFavorite(r)}
                onSubpageQuery={() => handleSubpageQuery(r.domain)}
              />
            ))}
          </div>

          {totalPages > 1 && (
            <Pagination
              currentPage={currentPage}
              totalPages={totalPages}
              onPageChange={(page) => handleSearch(query, city, page)}
            />
          )}
        </div>

        <Sidebar 
          semantic={semantic}
          tree={tree}
          favorites={favorites}
          onKeywordClick={(kw) => handleSearch(kw, city)}
          onDomainClick={handleSubpageQuery}
          onFavoriteRemove={(url) => setFavorites(favorites.filter(f => f.url !== url))}
        />
      </main>

      {subpageModal.open && (
        <SubpageModal
          domain={subpageModal.domain}
          data={subpageModal.data}
          onClose={() => setSubpageModal({ open: false, domain: '', data: null })}
        />
      )}
    </div>
  );
}