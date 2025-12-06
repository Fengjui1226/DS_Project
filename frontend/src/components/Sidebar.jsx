import React from 'react';

export default function Sidebar({ favorites, onFavoriteRemove, T }) {
  return (
    <aside className="sidebar">
      {/* 收藏面板 */}
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

      {/* 搜尋小技巧 */}
      <div className="panel tips">
        <h3>💡 {T('searchTips')}</h3>
        <ul>
          {(T('tips') || []).map((tip, i) => (
            <li key={i}>{tip}</li>
          ))}
        </ul>
      </div>
    </aside>
  );
}