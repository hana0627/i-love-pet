function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 0) return null;

  const groupSize = 5;
  const groupStart = Math.floor(page / groupSize) * groupSize; // 0,5,10,..
  const groupEnd = Math.min(groupStart + groupSize, totalPages); // 미포함 상한

  const isFirst = page === 0;
  const isLast = page >= totalPages - 1;

  const toFirst = () => onChange(0);
  const toPrevGroup = () => onChange(Math.max(0, groupStart - groupSize));
  const toNextGroup = () => onChange(Math.min(totalPages - 1, groupStart + groupSize));
  const toLast = () => onChange(totalPages - 1);

  const pages = [];
  for (let p = groupStart; p < groupEnd; p++) pages.push(p);

  return (
    <div style={{ display:"flex", gap:8, justifyContent:"center", marginTop:16 }}>
      <button className="btn gray" onClick={toFirst} disabled={isFirst}>&laquo;</button>
      <button className="btn gray" onClick={toPrevGroup} disabled={groupStart === 0}>&lt;</button>

      {pages.map(p => (
        <button
          key={p}
          className="btn"
          onClick={() => onChange(p)}
          style={{
            padding:"6px 10px",
            borderRadius:8,
            ...(p === page
              ? { background:"#111827", color:"#fff" }
              : { background:"#e5e7eb", color:"#111827" })
          }}
        >
          {p + 1 /* 표시용 1-based */}
        </button>
      ))}

      <button className="btn gray" onClick={toNextGroup} disabled={groupEnd >= totalPages}>&gt;</button>
      <button className="btn gray" onClick={toLast} disabled={isLast}>&raquo;</button>
    </div>
  );
}
export default Pagination;