import React from 'react';

export default function SearchHistory({ history, onSelect, onClear }) {
  if (!history || history.length === 0) return null;

  return (
    <div className="search-history">
      <div className="history-header">
        <span>🕐 最近搜尋</span>
        <button className="btn-clear" onClick={onClear}>清除</button>
      </div>
      <div className="history-tags">
        {history.slice(0, 5).map((item, i) => (
          <button 
            key={i}
            className="history-tag"
            onClick={() => onSelect(item)}
          >
            {item}
          </button>
        ))}
      </div>
    </div>
  );
}