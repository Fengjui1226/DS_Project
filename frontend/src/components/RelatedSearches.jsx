import React from 'react';

/**
 * RelatedSearches - Google 風格的「其他人也搜尋了」
 */
export default function RelatedSearches({ suggestions, onSearch, T }) {
  if (!suggestions || suggestions.length === 0) {
    return null;
  }

  return (
    <div className="related-searches animate-fade-in">
      <h3>{T('relatedSearches') || '其他人也搜尋了以下項目'}</h3>
      <div className="related-grid">
        {suggestions.map((item, index) => (
          <button
            key={index}
            className="related-item"
            onClick={() => onSearch(item.text || item)}
          >
            <span className="related-text">{item.text || item}</span>
            <span className="related-icon">🔍</span>
          </button>
        ))}
      </div>
    </div>
  );
}