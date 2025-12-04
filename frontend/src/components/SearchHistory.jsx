import React from 'react';

export default function SearchHistory({ history, onSelect, onClear, T }) {
  if (!history || history.length === 0) return null;
  
  return (
    <div className="search-history">
      <div className="history-header">
        <span>🕐 {T('recentSearch')}</span>
        <button className="btn-clear" onClick={onClear}>{T('clearHistory')}</button>
      </div>
      <div className="history-tags">
        {history.slice(0, 5).map((item, i) => (
          <button key={i} className="history-tag" onClick={() => onSelect(item)}>{item}</button>
        ))}
      </div>
    </div>
  );
}