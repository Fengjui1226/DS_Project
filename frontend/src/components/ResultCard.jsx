import React, { useState } from 'react';

export default function ResultCard({ 
  result, 
  index,
  isFavorite, 
  onToggleFavorite,
  onSubpageQuery 
}) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div 
      className={`result-card animate-slide-up`}
      style={{ animationDelay: `${index * 0.05}s` }}
    >
      <div className="result-top">
        <span className="rank">#{result.rank}</span>
        <div className="result-actions">
          <button 
            className={`btn-icon btn-favorite ${isFavorite ? 'active' : ''}`}
            onClick={onToggleFavorite}
            title={isFavorite ? '取消收藏' : '加入收藏'}
          >
            {isFavorite ? '❤️' : '🤍'}
          </button>
          <button 
            className="btn-icon btn-share"
            onClick={() => {
              navigator.clipboard.writeText(result.url);
              alert('已複製連結！');
            }}
            title="複製連結"
          >
            📋
          </button>
          <span className="score">{result.score.toFixed(1)}</span>
        </div>
      </div>
      
      <h3>
        <a href={result.url} target="_blank" rel="noopener noreferrer">
          {result.title}
        </a>
      </h3>
      
      <div className="result-meta">
        {result.city && <span className="meta-tag">📍 {result.city}</span>}
        {result.eventDate && <span className="meta-tag">📅 {result.eventDate}</span>}
        <span 
          className="meta-tag domain-link"
          onClick={onSubpageQuery}
          title="查看此網站更多內容"
        >
          🌐 {result.domain}
        </span>
      </div>

      {result.snippet && (
        <p className="result-snippet">{result.snippet}</p>
      )}
      
      <div className="result-footer">
        <a href={result.url} target="_blank" rel="noopener noreferrer" className="result-link">
          前往官方網站 →
        </a>
        <button 
          className="btn-subpage"
          onClick={onSubpageQuery}
        >
          查看相關頁面
        </button>
      </div>
    </div>
  );
}