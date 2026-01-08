import React, { useState } from 'react';

const flags = {
  'zh-TW': '🇹🇼',
  'en': '🇺🇸',
  'ja': '🇯🇵',
  'ko': '🇰🇷'
};

export default function LanguageSelector({ lang, onChange, T, compact }) {
  const [open, setOpen] = useState(false);

  const languages = ['zh-TW', 'en', 'ja', 'ko'];

  return (
    <div className={`lang-selector ${compact ? 'compact' : ''}`}>
      <button 
        className="lang-btn"
        onClick={() => setOpen(!open)}
        title={T('language')}
      >
        <span className="lang-flag">{flags[lang]}</span>
        {!compact && <span className="lang-name">{T(`languages.${lang}`)}</span>}
        <span className="lang-arrow">{open ? '▲' : '▼'}</span>
      </button>
      
      {open && (
        <div className="lang-dropdown">
          {languages.map(l => (
            <button
              key={l}
              className={`lang-option ${l === lang ? 'active' : ''}`}
              onClick={() => {
                onChange(l);
                setOpen(false);
              }}
            >
              <span className="lang-flag">{flags[l]}</span>
              <span>{T(`languages.${l}`)}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}