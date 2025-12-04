import React from 'react';

export default function SubpageModal({ domain, data, onClose, T }) {
  if (!data) return null;
  
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal animate-scale-in" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h2>🌐 {domain}</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <p className="modal-info">
            {T('foundPages').replace('{count}', data.count)}
          </p>
          <div className="subpage-list">
            {data.subpages && data.subpages.map((page, i) => (
              <a key={i} href={page.url} target="_blank" rel="noopener noreferrer" className="subpage-item">
                <div className="subpage-title">{page.title}</div>
                <div className="subpage-meta">
                  <span className="subpage-path">{page.path}</span>
                  <span className="subpage-score">{page.score.toFixed(1)}</span>
                </div>
              </a>
            ))}
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn-secondary" onClick={onClose}>{T('close')}</button>
        </div>
      </div>
    </div>
  );
}