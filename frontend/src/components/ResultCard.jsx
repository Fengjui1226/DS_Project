import React from 'react';

export default function ResultCard({ result, index, isFavorite, onToggleFavorite, onSubpageQuery, T }) {
  return (
    <div className="result-card animate-slide-up" style={{ animationDelay: `${index * 0.05}s` }}>
      <div className="result-top">
        <span className="rank">#{result.rank}</span>
        <div className="result-actions">
          <button 
            className={`btn-icon btn-favorite ${isFavorite ? 'active' : ''}`} 
            onClick={onToggleFavorite} 
            title={isFavorite ? T('removeFavorite') : T('addFavorite')}
          >
            {isFavorite ? '❤️' : '🤍'}
          </button>
          <button 
            className="btn-icon btn-share" 
            onClick={() => { 
              navigator.clipboard.writeText(result.url); 
              alert(T('linkCopied')); 
            }} 
            title={T('copyLink')}
          >
            📋
          </button>
          <span className="score">{result.score.toFixed(1)}</span>
        </div>
      </div>
      <h3><a href={result.url} target="_blank" rel="noopener noreferrer">{result.title}</a></h3>
      <div className="result-meta">
        {result.city && <span className="meta-tag">📍 {result.city}</span>}
        {result.eventDate && <span className="meta-tag">📅 {result.eventDate}</span>}
        <span className="meta-tag domain-link" onClick={onSubpageQuery} title={T('viewSubpages')}>
          🌐 {result.domain}
        </span>
      </div>
      {result.snippet && <p className="result-snippet">{result.snippet}</p>}
      <div className="result-footer">
        <a href={result.url} target="_blank" rel="noopener noreferrer" className="result-link">
          {T('visitSite')} →
        </a>
        <button className="btn-subpage" onClick={onSubpageQuery}>
          {T('viewSubpages')}
        </button>
      </div>
    </div>
  );
}