import React, { useState, useEffect, useCallback, useRef } from 'react';
import { translations, t } from './i18n';
import SearchBar from './components/SearchBar';
import ResultCard from './components/ResultCard';
import Sidebar from './components/Sidebar';
import Pagination from './components/Pagination';
import SubpageModal from './components/SubpageModal';
import SearchHistory from './components/SearchHistory';
import LanguageSelector from './components/LanguageSelector';
import RelatedSearches from './components/RelatedSearches';
import './index.css';

// API基礎URL - 開發環境使用代理，生產環境使用環境變量
const API_BASE = import.meta.env.PROD
  ? (import.meta.env.VITE_API_BASE_URL || 'https://ds-project-o9lk.onrender.com') + '/api'
  : '/api';

const cities = {
  'zh-TW': ['台北', '新北', '桃園', '台中', '台南', '高雄', '基隆', '新竹', '苗栗', '彰化', '南投', '雲林', '嘉義', '屏東', '宜蘭', '花蓮', '台東'],
  'en': ['Taipei', 'New Taipei', 'Taoyuan', 'Taichung', 'Tainan', 'Kaohsiung', 'Keelung', 'Hsinchu', 'Miaoli', 'Changhua', 'Nantou', 'Yunlin', 'Chiayi', 'Pingtung', 'Yilan', 'Hualien', 'Taitung'],
  'ja': ['台北', '新北', '桃園', '台中', '台南', '高雄', '基隆', '新竹', '苗栗', '彰化', '南投', '雲林', '嘉義', '屏東', '宜蘭', '花蓮', '台東'],
  'ko': ['타이베이', '신베이', '타오위안', '타이중', '타이난', '가오슝', '지룽', '신주', '먀오리', '장화', '난터우', '윈린', '자이', '핑둥', '이란', '화롄', '타이둥']
};

// 城市對應（用於 API 查詢）
const cityMapping = {
  'Taipei': '台北', 'New Taipei': '新北', 'Taoyuan': '桃園', 'Taichung': '台中',
  'Tainan': '台南', 'Kaohsiung': '高雄', 'Keelung': '基隆', 'Hsinchu': '新竹',
  '타이베이': '台北', '신베이': '新北', '타오위안': '桃園', '타이중': '台中'
};

// ============ 穩定性工具函數 ============

// 防抖動
function useDebounce(value, delay) {
  const [debouncedValue, setDebouncedValue] = useState(value);
  useEffect(() => {
    const handler = setTimeout(() => setDebouncedValue(value), delay);
    return () => clearTimeout(handler);
  }, [value, delay]);
  return debouncedValue;
}

// 簡易快取
const searchCache = new Map();
const CACHE_DURATION = 5 * 60 * 1000; // 5 分鐘

function getCachedResult(key) {
  const cached = searchCache.get(key);
  if (cached && Date.now() - cached.timestamp < CACHE_DURATION) {
    console.log('[Cache] Hit:', key);
    return cached.data;
  }
  return null;
}

function setCachedResult(key, data) {
  searchCache.set(key, { data, timestamp: Date.now() });
  // 限制快取大小
  if (searchCache.size > 50) {
    const firstKey = searchCache.keys().next().value;
    searchCache.delete(firstKey);
  }
}

// ============ 主應用 ============

export default function App() {
  // 語言狀態
  const [lang, setLang] = useState(() => {
    const saved = localStorage.getItem('eventfinder_lang');
    return saved || 'zh-TW';
  });

  // 視圖狀態
  const [view, setView] = useState('home');
  const [query, setQuery] = useState('');
  const [city, setCity] = useState(() => cities[lang]?.[0] || '台北');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [categories, setCategories] = useState([]);
  const [relatedSearches, setRelatedSearches] = useState([]);

  // 分頁
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalCount, setTotalCount] = useState(0);

  // 收藏 & 歷史
  const [favorites, setFavorites] = useState(() => {
    try {
      const saved = localStorage.getItem('eventfinder_favorites');
      return saved ? JSON.parse(saved) : [];
    } catch { return []; }
  });

  const [searchHistory, setSearchHistory] = useState(() => {
    try {
      const saved = localStorage.getItem('eventfinder_history');
      return saved ? JSON.parse(saved) : [];
    } catch { return []; }
  });

  // 彈窗 & 建議
  const [subpageModal, setSubpageModal] = useState({ open: false, domain: '', data: null });
  const [suggestions, setSuggestions] = useState([]);
  const [showSuggestions, setShowSuggestions] = useState(false); // ✅ 修正：boolean

  // ============ 穩定性：請求限流 ============
  const lastRequestTime = useRef(0);
  const requestCount = useRef(0);
  const RATE_LIMIT_WINDOW = 10000; // 10 秒
  const MAX_REQUESTS = 5; // 每 10 秒最多 5 次

  const checkRateLimit = useCallback(() => {
    const now = Date.now();
    if (now - lastRequestTime.current > RATE_LIMIT_WINDOW) {
      requestCount.current = 0;
      lastRequestTime.current = now;
    }

    if (requestCount.current >= MAX_REQUESTS) return false;

    requestCount.current++;
    return true;
  }, []);

  // 儲存語言設定
  useEffect(() => {
    localStorage.setItem('eventfinder_lang', lang);
  }, [lang]);

  // 載入分類
  useEffect(() => {
    fetch(`${API_BASE}/categories`)
      .then(res => res.json())
      .then(data => setCategories(data.categories || []))
      .catch(() => {});
  }, []);

  // 儲存收藏
  useEffect(() => {
    try {
      localStorage.setItem('eventfinder_favorites', JSON.stringify(favorites));
    } catch {}
  }, [favorites]);

  // 儲存歷史
  useEffect(() => {
    try {
      localStorage.setItem('eventfinder_history', JSON.stringify(searchHistory));
    } catch {}
  }, [searchHistory]);

  // 搜尋建議（防抖動）
  const debouncedQuery = useDebounce(query, 300);

  useEffect(() => {
    if (!debouncedQuery || debouncedQuery.trim().length < 1) {
      setSuggestions([]);
      return;
    }

    // ✅ 修正：後端吃 query，不是 q
    fetch(`${API_BASE}/suggestions?query=${encodeURIComponent(debouncedQuery.trim())}`)
      .then(res => res.json())
      .then(data => setSuggestions(data.suggestions || []))
      .catch(() => {});
  }, [debouncedQuery]);

  // 載入推薦關鍵字（Google 風格）
  const loadRelatedSearches = useCallback(async (q, c) => {
    try {
      const res = await fetch(`${API_BASE}/suggestions?query=${encodeURIComponent(q)}&city=${encodeURIComponent(c)}`);
      const data = await res.json();
      if (data.success && data.suggestions) {
        setRelatedSearches(data.suggestions);
      } else {
        setRelatedSearches([]);
      }
    } catch {
      setRelatedSearches([]);
    }
  }, []);

  // ============ 搜尋功能（穩定性增強）============
  const handleSearch = async (q = query, c = city, page = 1) => {
    // 輸入驗證
    if (!q || !q.trim()) {
      setError(t(lang, 'errors.empty'));
      return;
    }
    if (q.trim().length < 2) {
      setError(t(lang, 'errors.tooShort'));
      return;
    }

    // 限流檢查
    if (!checkRateLimit()) {
      setError(t(lang, 'errors.rateLimit'));
      return;
    }

    // 城市轉換（非中文轉中文）
    const apiCity = cityMapping[c] || c;

    // 檢查快取
    const cacheKey = `${q}-${apiCity}-${page}`;
    const cachedData = getCachedResult(cacheKey);

    if (cachedData) {
      setResults(cachedData.results);
      setTotalPages(cachedData.totalPages);
      setTotalCount(cachedData.totalCount);
      setQuery(q);
      setCity(c);
      setCurrentPage(page);
      setView('results');
      setError(null);
      setShowSuggestions(false);

      // ✅ 修正：這兩個可能不存在，避免炸掉
      if (typeof window.loadSemantic === 'function') window.loadSemantic();
      if (typeof window.loadTree === 'function') window.loadTree();

      // ✅ 修正：cache 命中也載入 relatedSearches，體感一致
      loadRelatedSearches(q, apiCity);
      return;
    }

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
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 15000); // ✅ 15 秒

      const res = await fetch(
        `${API_BASE}/search?query=${encodeURIComponent(q)}&city=${encodeURIComponent(apiCity)}&page=${page}`,
        { signal: controller.signal }
      );

      clearTimeout(timeoutId);

      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }

      const data = await res.json();

      if (data.success) {
        setResults(data.results || []);
        setTotalPages(data.totalPages || 1);
        setTotalCount(data.totalCount || 0);
        setView('results');

        // 存入快取
        setCachedResult(cacheKey, {
          results: data.results || [],
          totalPages: data.totalPages || 1,
          totalCount: data.totalCount || 0
        });

        // 載入推薦關鍵字
        loadRelatedSearches(q, apiCity);
      } else {
        setError(data.error || t(lang, 'errors.unknown'));
      }
    } catch (err) {
      console.error('Search error:', err);

      if (err.name === 'AbortError') {
        setError(t(lang, 'errors.server'));
      } else if (String(err.message || '').includes('fetch')) {
        setError(t(lang, 'errors.network'));
      } else {
        setError(t(lang, 'errors.unknown'));
      }
    } finally {
      setLoading(false);
    }
  };

  // 子網頁查詢（✅ 修正：後端要 url）
  const handleSubpageQuery = async (url, domainLabel = '') => {
    if (!url) return;

    try {
      const res = await fetch(`${API_BASE}/subpages?url=${encodeURIComponent(url)}`);
      const data = await res.json();
      if (data.success) {
        setSubpageModal({ open: true, domain: domainLabel || url, data });
      }
    } catch {}
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
    if (!navigator.geolocation) {
      alert(lang === 'zh-TW' ? '瀏覽器不支援定位' : 'Geolocation not supported');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const { latitude: lat, longitude: lng } = pos.coords;
        let detected = cities[lang][0];
        if (lat > 25.0 && lng > 121.4 && lng < 121.7) detected = cities[lang][0]; // 台北
        else if (lat > 24.0 && lat < 24.3) detected = cities[lang][3]; // 台中
        else if (lat > 22.5 && lat < 22.8) detected = cities[lang][5]; // 高雄
        setCity(detected);
      },
      () => {}
    );
  };

  // 清除歷史
  const clearHistory = () => setSearchHistory([]);

  // 語言切換
  const handleLanguageChange = (newLang) => {
    setLang(newLang);
    setCity(cities[newLang][0]);
  };

  // 翻譯快捷函數
  const T = (key) => t(lang, key);

  // ============ 首頁 ============
  if (view === 'home') {
    return (
      <div className="app">
        <div className="bg" />

        <LanguageSelector
          lang={lang}
          onChange={handleLanguageChange}
          T={T}
        />

        <div className="home animate-fade-in">
          <div className="brand">
            <div className="logo">🎪</div>
            <h1>{T('brand')}</h1>
            <p className="tagline">{T('tagline')}</p>
          </div>

          <div className="search-card glass">
            <div className="categories">
              {categories.map(cat => (
                <button
                  key={cat.id}
                  className="cat-btn"
                  style={{ '--cat-color': cat.color }}
                  onClick={() => handleSearch(cat.query, city)}
                  disabled={loading}
                >
                  <span>{cat.icon}</span> {T(`categories.${cat.id}`) || cat.name}
                </button>
              ))}
            </div>

            <SearchBar
              query={query}
              setQuery={setQuery}
              city={city}
              setCity={setCity}
              cities={cities[lang]}
              onSearch={handleSearch}
              onLocate={handleLocate}
              loading={loading}
              suggestions={suggestions}
              showSuggestions={showSuggestions}
              setShowSuggestions={setShowSuggestions}
              T={T}
            />

            {error && <div className="error animate-shake">⚠️ {error}</div>}

            {searchHistory.length > 0 && (
              <SearchHistory
                history={searchHistory}
                onSelect={(q) => handleSearch(q, city)}
                onClear={clearHistory}
                T={T}
              />
            )}
          </div>

          <div className="features">
            <div className="feature animate-slide-up" style={{ animationDelay: '0.1s' }}>
              <span>⚡</span>
              <h3>{T('features.realtime')}</h3>
              <p>{T('features.realtimeDesc')}</p>
            </div>
            <div className="feature animate-slide-up" style={{ animationDelay: '0.2s' }}>
              <span>🎯</span>
              <h3>{T('features.smart')}</h3>
              <p>{T('features.smartDesc')}</p>
            </div>
            <div className="feature animate-slide-up" style={{ animationDelay: '0.3s' }}>
              <span>📍</span>
              <h3>{T('features.local')}</h3>
              <p>{T('features.localDesc')}</p>
            </div>
          </div>

          {favorites.length > 0 && (
            <div className="favorites-preview glass">
              <h3>❤️ {T('myFavorites')} ({favorites.length})</h3>
              <div className="favorites-list">
                {favorites.slice(0, 3).map((fav, i) => (
                  <a key={i} href={fav.url} target="_blank" rel="noopener noreferrer" className="fav-item">
                    {fav.title}
                  </a>
                ))}
              </div>
            </div>
          )}

          <footer>{T('footer')}</footer>
        </div>
      </div>
    );
  }

  // ============ 結果頁 ============
  return (
    <div className="app">
      <div className="bg" />

      <header className="header glass">
        <div className="header-brand" onClick={() => setView('home')}>
          <span className="header-logo">🎪</span>
          <span className="header-title">{T('brand')}</span>
        </div>

        <SearchBar
          query={query}
          setQuery={setQuery}
          city={city}
          setCity={setCity}
          cities={cities[lang]}
          onSearch={handleSearch}
          onLocate={handleLocate}
          loading={loading}
          compact
          suggestions={suggestions}
          showSuggestions={showSuggestions}
          setShowSuggestions={setShowSuggestions}
          T={T}
        />

        <LanguageSelector
          lang={lang}
          onChange={handleLanguageChange}
          T={T}
          compact
        />
      </header>

      <main className="main">
        <div className="results-section">
          <div className="results-header animate-fade-in">
            <h2>{T('results')}</h2>
            <p>
              {T('keyword')}：<strong>{query}</strong> |
              {T('city')}：<strong>{city}</strong> |
              {T('total')} <strong>{totalCount}</strong> {T('items')}
            </p>
          </div>

          {loading && (
            <div className="loading">
              <div className="spinner" />
              <p>{T('searching')}</p>
            </div>
          )}

          {error && <div className="error-box animate-shake">⚠️ {error}</div>}

          {!loading && results.length === 0 && !error && (
            <div className="no-results animate-fade-in">
              <span>🔍</span>
              <p>{T('noResults')}</p>
              <p className="hint">{T('tryAgain')}</p>
            </div>
          )}

          <div className="results-list">
            {results.map((r, index) => (
              <ResultCard
                key={`${r.rank}-${r.url}`}
                result={r}
                index={index}
                isFavorite={isFavorite(r.url)}
                onToggleFavorite={() => toggleFavorite(r)}
                onSubpageQuery={(url) => handleSubpageQuery(url, r.domain)}
                T={T}
              />
            ))}
          </div>

          {totalPages > 1 && (
            <Pagination
              currentPage={currentPage}
              totalPages={totalPages}
              onPageChange={(page) => handleSearch(query, city, page)}
              T={T}
            />
          )}

          <RelatedSearches
            suggestions={relatedSearches}
            onSearch={(kw) => handleSearch(kw, city)}
            T={T}
          />
        </div>

        <Sidebar
          favorites={favorites}
          onFavoriteRemove={(url) => setFavorites(favorites.filter(f => f.url !== url))}
          T={T}
        />
      </main>

      {subpageModal.open && (
        <SubpageModal
          domain={subpageModal.domain}
          data={subpageModal.data}
          onClose={() => setSubpageModal({ open: false, domain: '', data: null })}
          T={T}
        />
      )}
    </div>
  );
}
