type PaginationProps = {
  page: number;
  totalPages: number;
  disabled?: boolean;
  onPageChange: (page: number) => void;
};

export function Pagination({ page, totalPages, disabled = false, onPageChange }: PaginationProps) {
  const pageCount = Math.max(1, totalPages);
  const visiblePages = Array.from(new Set([
    0,
    pageCount - 1,
    page - 1,
    page,
    page + 1,
  ].filter((value) => value >= 0 && value < pageCount))).sort((a, b) => a - b);

  return (
    <nav aria-label="Request pages" className="flex flex-wrap items-center justify-end gap-2">
      <button type="button" aria-label="Previous page" onClick={() => onPageChange(page - 1)} disabled={disabled || page === 0} className="h-9 w-9 rounded-[9px] border border-divider text-sm text-foreground disabled:cursor-not-allowed disabled:opacity-40">
        &lsaquo;
      </button>
      {visiblePages.map((value, index) => (
        <span key={value} className="flex items-center gap-2">
          {index > 0 && value - visiblePages[index - 1] > 1 && <span aria-hidden="true" className="px-1 text-secondary">...</span>}
          <button type="button" onClick={() => onPageChange(value)} disabled={disabled || value === page} aria-label={`Page ${value + 1}`} aria-current={value === page ? "page" : undefined} className={`h-9 min-w-9 rounded-[9px] px-2 text-sm disabled:cursor-default ${value === page ? "bg-nav-active font-medium text-accent" : "border border-divider text-foreground hover:bg-nav-hover"}`}>
            {value + 1}
          </button>
        </span>
      ))}
      <button type="button" aria-label="Next page" onClick={() => onPageChange(page + 1)} disabled={disabled || page >= pageCount - 1} className="h-9 w-9 rounded-[9px] border border-divider text-sm text-foreground disabled:cursor-not-allowed disabled:opacity-40">
        &rsaquo;
      </button>
    </nav>
  );
}
