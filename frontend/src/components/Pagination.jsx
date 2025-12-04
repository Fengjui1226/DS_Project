import React from 'react';

export default function Pagination({ currentPage, totalPages, onPageChange, T }) {
  const getPageNumbers = () => {
    const pages = [];
    let start = Math.max(1, currentPage - 2);
    let end = Math.min(totalPages, start + 4);
    if (end - start + 1 < 5) start = Math.max(1, end - 4);
    for (let i = start; i <= end; i++) pages.push(i);
    return pages;
  };

  return (
    <div className="pagination">
      <button className="page-btn" disabled={currentPage === 1} onClick={() => onPageChange(1)}>««</button>
      <button className="page-btn" disabled={currentPage === 1} onClick={() => onPageChange(currentPage - 1)}>«</button>
      {getPageNumbers().map(page => (
        <button key={page} className={`page-btn ${page === currentPage ? 'active' : ''}`} onClick={() => onPageChange(page)}>{page}</button>
      ))}
      <button className="page-btn" disabled={currentPage === totalPages} onClick={() => onPageChange(currentPage + 1)}>»</button>
      <button className="page-btn" disabled={currentPage === totalPages} onClick={() => onPageChange(totalPages)}>»»</button>
      <span className="page-info">{T('page')} {currentPage} / {totalPages}</span>
    </div>
  );
}