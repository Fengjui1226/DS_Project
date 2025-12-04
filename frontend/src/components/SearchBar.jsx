import React, { useRef, useEffect } from 'react';

export default function SearchBar({ 
  query, setQuery, city, setCity, cities, 
  onSearch, onLocate, loading, compact,
  suggestions, showSuggestions, setShowSuggestions,
  T
}) {
  const inputRef = useRef(null);
  const suggestionsRef = useRef(null);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (suggestionsRef.current && !suggestionsRef.current.contains(e.target) &&
          inputRef.current && !inputRef.current.contains(e.target)) {
        setShowSuggestions(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [setShowSuggestions]);

  const handleInputChange = (e) => {
    setQuery(e.target.value);
    setShowSuggestions(true);
  };

  const handleSuggestionClick = (text) => {
    setQuery(text);
    setShowSuggestions(false);
    onSearch(text, city);
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      setShowSuggestions(false);
      onSearch(query, city);
    } else if (e.key === 'Escape') {
      setShowSuggestions(false);
    }
  };

  return (
    <div className={`search-bar ${compact ? 'compact' : ''}`}>
      <div className="search-row">
        <div className="search-input-wrapper">
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={handleInputChange}
            onKeyDown={handleKeyDown}
            onFocus={() => query && setShowSuggestions(true)}
            placeholder={T('searchPlaceholder')}
            className="search-input"
            maxLength={100}
          />
          {showSuggestions && suggestions && suggestions.length > 0 && (
            <div ref={suggestionsRef} className="suggestions-dropdown">
              {suggestions.map((s, i) => (
                <div key={i} className={`suggestion-item ${s.type}`} onClick={() => handleSuggestionClick(s.text)}>
                  <span className="suggestion-icon">{s.type === 'history' ? '🕐' : '🔍'}</span>
                  <span className="suggestion-text">{s.text}</span>
                  {s.type === 'history' && <span className="suggestion-badge">{T('recentSearch')}</span>}
                </div>
              ))}
            </div>
          )}
        </div>
        <select value={city} onChange={e => setCity(e.target.value)} className="city-select">
          {cities.map(c => <option key={c} value={c}>{c}</option>)}
        </select>
      </div>
      <div className="btn-row">
        <button className="btn-locate" onClick={onLocate} title="📍">📍</button>
        <button className="btn-search" onClick={() => onSearch(query, city)} disabled={loading}>
          {loading ? (
            <span className="btn-loading">
              <span className="btn-spinner"></span>
              {T('searching')}
            </span>
          ) : (
            compact ? T('searchButtonCompact') : T('searchButton')
          )}
        </button>
      </div>
    </div>
  );
}