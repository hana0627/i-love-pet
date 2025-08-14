import {useEffect} from "react";

function OrderDetailModal({ open, detail, onClose, statusPill }) {
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (e) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [open, onClose]);

  if (!open || !detail) return null;

  const { order, payment, items, logs } = detail;

  return (
    <div
      style={{
        position:"fixed", inset:0, background:"rgba(0,0,0,.4)",
        display:"flex", alignItems:"center", justifyContent:"center", padding:16, zIndex:9999
      }}
      onClick={onClose} // 바깥 클릭 닫기
    >
      <div
        style={{
          background:"#fff", borderRadius:16, width:"min(900px, 96vw)",
          maxHeight:"90vh", overflow:"auto", boxShadow:"0 20px 40px rgba(0,0,0,.2)"
        }}
        onClick={(e)=>e.stopPropagation()} // 내부 클릭 전파 방지
        role="dialog" aria-modal="true" aria-labelledby="order-detail-title"
      >
        <div className="flex-between" style={{ padding:16, borderBottom:"1px solid #e2e8f0" }}>
          <h3 id="order-detail-title" style={{ margin:0 }}>주문 상세 • {order.orderNo}</h3>
          <button className="btn gray" onClick={onClose} style={{ padding:"8px 10px" }}>닫기</button>
        </div>

        <div style={{ padding:16 }}>
          <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:12, marginBottom:12 }}>
            <div>
              <p className="section-title">주문 정보</p>
              <table className="table"><tbody>
              <tr><td>주문ID</td><td>{order.id}</td></tr>
              <tr><td>주문번호</td><td>{order.orderNo}</td></tr>
              <tr><td>사용자</td><td>#{order.userId}</td></tr>
              <tr><td>상태</td><td>{statusPill(order.status)}</td></tr>
              <tr><td>금액</td><td>{(order.price||0).toLocaleString()}원</td></tr>
              <tr><td>생성</td><td>{new Date(order.createdAt).toLocaleString()}</td></tr>
              </tbody></table>
            </div>

            <div>
              <p className="section-title">결제 정보</p>
              <table className="table"><tbody>
              <tr><td>결제ID</td><td>{payment?.id ?? '-'}</td></tr>
              <tr><td>상태</td><td>{payment?.status ?? '-'}</td></tr>
              <tr><td>금액</td><td>{payment?.amount?.toLocaleString?.() ?? '-'}</td></tr>
              <tr><td>방식</td><td>{payment?.method ?? '-'}</td></tr>
              <tr><td>사유</td><td>{payment?.failReason ?? '-'}</td></tr>
              </tbody></table>
            </div>
          </div>

          <p className="section-title">주문 상품</p>
          <table className="table" style={{ marginBottom:12 }}>
            <thead><tr><th>상품ID</th><th>수량</th><th>단가</th><th>합계</th></tr></thead>
            <tbody>
            {(items||[]).map(it => (
              <tr key={it.id}>
                <td>{it.productId}</td>
                <td>{it.quantity}</td>
                <td>{it.price.toLocaleString()}원</td>
                <td>{(it.price*it.quantity).toLocaleString()}원</td>
              </tr>
            ))}
            </tbody>
          </table>

          <p className="section-title">결제 로그</p>
          <table className="table">
            <thead><tr><th>시간</th><th>타입</th><th>메시지</th></tr></thead>
            <tbody>
            {(logs||[]).map(l => (
              <tr key={l.id}>
                <td>{new Date(l.createdAt).toLocaleString()}</td>
                <td>{l.logType}</td>
                <td>{l.message}</td>
              </tr>
            ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

export default OrderDetailModal;
