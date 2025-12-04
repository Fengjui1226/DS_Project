import React, { useState } from 'react';

export default function Sidebar({ semantic, tree, favorites, onKeywordClick, onDomainClick, onFavoriteRemove, T }) {
  const [activeTab, setActiveTab] = useState('analysis');

  return (
    <aside className="sidebar">
      <div className="sidebar-tabs">
        <button className={`tab ${activeTab === 'analysis' ? 'active' : ''}`} onClick={() => setActiveTab('analysis')}>
          🔬 {T('analysis')}
        </button>
        <button className={`tab ${activeTab === 'favorites' ? 'active' : ''}`} onClick={() => setActiveTab('favorites')}>
          ❤️ {T('favorites')} {favorites.length > 0 && `(${favorites.length})`}
        </button>
      </div>

      {activeTab === 'analysis' && (
        <>
          {tree && tree.domains && (
            <div className="panel animate-fade-in">
              <h3>📊 {T('siteStructure')}</h3>
              <div className="domain-list">
                {tree.domains.map((d, i) => (
                  <div key={i} className="domain-item" onClick={() => onDomainClick(d.domain)}>
                    <span className="domain-name">{d.domain}</span>
                    <div className="domain-info">
                      <span className="domain-count">{d.pageCount} {T('page')}</span>
                      <span className="domain-score">{d.totalScore.toFixed(1)}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
          {semantic && (
            <div className="panel animate-fade-in">
              <h3>🧠 {T('semanticAnalysis')}</h3>
              {semantic.extractedKeywords?.length > 0 && (
                <div className="keyword-section">
                  <h4>{T('extractedKeywords')}</h4>
                  <div className="keywords">
                    {semantic.extractedKeywords.slice(0, 8).map((kw, i) => (
                      <button key={i} className="keyword" onClick={() => onKeywordClick(kw)}>{kw}</button>
                    ))}
                  </div>
                </div>
              )}
              {semantic.suggestedKeywords?.length > 0 && (
                <div className="keyword-section">
                  <h4>💡 {T('suggestedKeywords')}</h4>
                  <div className="keywords">
                    {semantic.suggestedKeywords.slice(0, 5).map((kw, i) => (
                      <button key={i} className="keyword suggested" onClick={() => onKeywordClick(kw)}>{kw}</button>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
          <div className="panel tips">
            <h3>💡 {T('searchTips')}</h3>
            <ul>
              {(T('tips') || []).map((tip, i) => (
                <li key={i}>{tip}</li>
              ))}
            </ul>
          </div>
        </>
      )}

      {activeTab === 'favorites' && (
        <div className="panel favorites-panel animate-fade-in">
          <h3>❤️ {T('myFavorites')}</h3>
          {favorites.length === 0 ? (
            <p className="empty-favorites">{T('noFavorites')}</p>
          ) : (
            <div className="favorites-list">
              {favorites.map((fav, i) => (
                <div key={i} className="favorite-item">
                  <a href={fav.url} target="_blank" rel="noopener noreferrer">{fav.title}</a>
                  <div className="favorite-meta">
                    <span>{fav.city || 'Taiwan'}</span>
                    <button className="btn-remove" onClick={() => onFavoriteRemove(fav.url)}>✕</button>
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