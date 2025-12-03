import React from 'react';

export default function Pagination({ currentPage, totalPages, onPageChange }) {
  const getPageNumbers = () => {
    const pages = [];
    const maxVisible = 5;
    
    let start = Math.max(1, currentPage - Math.floor(maxVisible / 2));
    let end = Math.min(totalPages, start + maxVisible - 1);
    
    if (end - start + 1 < maxVisible) {
      start = Math.max(1, end - maxVisible + 1);
    }
    
    for (let i = start; i <= end; i++) {
      pages.push(i);
    }
    
    return pages;
  };

  return (
    <div className="pagination">
      <button 
        className="page-btn"
        disabled={currentPage === 1}
        onClick={() => onPageChange(1)}
        title="第一頁"
      >
        ««
      </button>
      
      <button 
        className="page-btn"
        disabled={currentPage === 1}
        onClick={() => onPageChange(currentPage - 1)}
        title="上一頁"
      >
        «
      </button>
      
      {getPageNumbers().map(page => (
        <button
          key={page}
          className={`page-btn ${page === currentPage ? 'active' : ''}`}
          onClick={() => onPageChange(page)}
        >
          {page}
        </button>
      ))}
      
      <button 
        className="page-btn"
        disabled={currentPage === totalPages}
        onClick={() => onPageChange(currentPage + 1)}
        title="下一頁"
      >
        »
      </button>
      
      <button 
        className="page-btn"
        disabled={currentPage === totalPages}
        onClick={() => onPageChange(totalPages)}
        title="最後一頁"
      >
        »»
      </button>
      
      <span className="page-info">
        第 {currentPage} / {totalPages} 頁
      </span>
    </div>
  );
}