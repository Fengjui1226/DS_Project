import React, { useState } from 'react';

export default function Sidebar({ 
  semantic, 
  tree, 
  favorites,
  onKeywordClick, 
  onDomainClick,
  onFavoriteRemove 
}) {
  const [activeTab, setActiveTab] = useState('analysis');

  return (
    <aside className="sidebar">
      {/* 標籤切換 */}
      <div className="sidebar-tabs">
        <button 
          className={`tab ${activeTab === 'analysis' ? 'active' : ''}`}
          onClick={() => setActiveTab('analysis')}
        >
          🔬 分析
        </button>
        <button 
          className={`tab ${activeTab === 'favorites' ? 'active' : ''}`}
          onClick={() => setActiveTab('favorites')}
        >
          ❤️ 收藏 {favorites.length > 0 && `(${favorites.length})`}
        </button>
      </div>

      {activeTab === 'analysis' && (
        <>
          {/* 網站結構 */}
          {tree && tree.domains && (
            <div className="panel animate-fade-in">
              <h3>📊 網站結構</h3>
              <div className="domain-list">
                {tree.domains.map((d, i) => (
                  <div 
                    key={i} 
                    className="domain-item"
                    onClick={() => onDomainClick(d.domain)}
                    title="點擊查看子網頁"
                  >
                    <span className="domain-name">{d.domain}</span>
                    <div className="domain-info">
                      <span className="domain-count">{d.pageCount} 頁</span>
                      <span className="domain-score">{d.totalScore.toFixed(1)}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* 語意分析 */}
          {semantic && (
            <div className="panel animate-fade-in">
              <h3>🧠 語意分析</h3>
              {semantic.extractedKeywords?.length > 0 && (
                <div className="keyword-section">
                  <h4>提取的關鍵字</h4>
                  <div className="keywords">
                    {semantic.extractedKeywords.slice(0, 8).map((kw, i) => (
                      <button 
                        key={i} 
                        className="keyword"
                        onClick={() => onKeywordClick(kw)}
                      >
                        {kw}
                      </button>
                    ))}
                  </div>
                </div>
              )}
              {semantic.suggestedKeywords?.length > 0 && (
                <div className="keyword-section">
                  <h4>💡 推薦關鍵字</h4>
                  <div className="keywords">
                    {semantic.suggestedKeywords.slice(0, 5).map((kw, i) => (
                      <button 
                        key={i} 
                        className="keyword suggested"
                        onClick={() => onKeywordClick(kw)}
                      >
                        {kw}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* 搜尋提示 */}
          <div className="panel tips">
            <h3>💡 搜尋提示</h3>
            <ul>
              <li>點擊關鍵字可快速搜尋</li>
              <li>點擊網站可查看子網頁</li>
              <li>分數越高表示越相關</li>
              <li>❤️ 收藏喜歡的活動</li>
            </ul>
          </div>
        </>
      )}

      {activeTab === 'favorites' && (
        <div className="panel favorites-panel animate-fade-in">
          <h3>❤️ 我的收藏</h3>
          {favorites.length === 0 ? (
            <p className="empty-favorites">還沒有收藏任何活動</p>
          ) : (
            <div className="favorites-list">
              {favorites.map((fav, i) => (
                <div key={i} className="favorite-item">
                  <a href={fav.url} target="_blank" rel="noopener noreferrer">
                    {fav.title}
                  </a>
                  <div className="favorite-meta">
                    <span>{fav.city || '台灣'}</span>
                    <button 
                      className="btn-remove"
                      onClick={() => onFavoriteRemove(fav.url)}
                    >
                      ✕
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </aside>
  );
}