import {Link} from "react-router-dom";
import {useEffect, useState} from "react";
import OrderDetailModal from "./OrderDetailModal";

function OrderList() {
  const [users, setUsers] = useState([]);
  const [orders, setOrders] = useState([]); // {id, userId, orderNo, status, price, createdAt, paymentId}
  const [itemsByOrder, setItemsByOrder] = useState({}); // { [orderId]: OrderItem[] }
  const [payments, setPayments] = useState({}); // { [paymentId]: Payment }
  const [logsByPayment, setLogsByPayment] = useState({}); // { [paymentId]: PaymentLog[] }

  const [filterUser, setFilterUser] = useState("ALL");
  const [filterStatus, setFilterStatus] = useState("ALL");
  const [query, setQuery] = useState("");

  const [open, setOpen] = useState(false);
  const [detail, setDetail] = useState(null); // { order, items, payment, logs }

  useEffect(() => {
    getUsers().then(data => {
        setUsers(data)
      }
    )

    // --- MOCK data since API not ready ---
    const now = new Date();
    const mockOrders = [
      { id: 1001, userId: 1, orderNo: "20250814-0001", status: "CONFIRMED", price: 75000, createdAt: new Date(now.getTime()-3600_000).toISOString(), paymentId: 501 },
      { id: 1002, userId: 1, orderNo: "20250814-0002", status: "FAIL",      price: 120000, createdAt: new Date(now.getTime()-7200_000).toISOString(), paymentId: 502 },
      { id: 1003, userId: 2, orderNo: "20250814-0003", status: "CREATED",   price: 35000, createdAt: new Date(now.getTime()-1800_000).toISOString(), paymentId: 503 },
      { id: 1004, userId: 3, orderNo: "20250813-0007", status: "CANCELED",  price: 38000, createdAt: new Date(now.getTime()-86400_000).toISOString(), paymentId: 504 },
    ];
    const mockItems = {
      1001: [ { id:1, productId:1, quantity:2, price:30000 }, { id:2, productId:2, quantity:1, price:15000 } ],
      1002: [ { id:3, productId:3, quantity:3, price:40000 } ],
      1003: [ { id:4, productId:1, quantity:1, price:35000 } ],
      1004: [ { id:5, productId:2, quantity:1, price:38000 } ],
    };
    const mockPayments = {
      501: { id:501, userId:1, orderId:1001, paymentKey:"P-AAA", amount:75000, status:"APPROVED", method:"카드", requestedAt:new Date(now.getTime()-3700_000).toISOString(), approvedAt:new Date(now.getTime()-3600_000).toISOString(), failedAt:null, canceledAt:null, failReason:null, updatedAt:new Date().toISOString(), description:"승인 완료" },
      502: { id:502, userId:1, orderId:1002, paymentKey:"P-BBB", amount:120000, status:"FAILED",  method:"카드", requestedAt:new Date(now.getTime()-7300_000).toISOString(), approvedAt:null, failedAt:new Date(now.getTime()-7200_000).toISOString(), canceledAt:null, failReason:"재고 부족: 3", updatedAt:new Date().toISOString(), description:"결제 실패" },
      503: { id:503, userId:2, orderId:1003, paymentKey:"P-CCC", amount:35000, status:"PENDING", method:"가상계좌", requestedAt:new Date(now.getTime()-1900_000).toISOString(), approvedAt:null, failedAt:null, canceledAt:null, failReason:null, updatedAt:new Date().toISOString(), description:"대기" },
      504: { id:504, userId:3, orderId:1004, paymentKey:"P-DDD", amount:38000, status:"CANCELED", method:"카드", requestedAt:new Date(now.getTime()-90000_000).toISOString(), approvedAt:null, failedAt:null, canceledAt:new Date(now.getTime()-86000_000).toISOString(), failReason:"사용자 취소", updatedAt:new Date().toISOString(), description:"취소됨" },
    };
    const mockLogs = {
      501: [
        { id: 1, paymentId:501, logType:"REQUEST",  message:"/approve 요청", createdAt:new Date(now.getTime()-3705_000).toISOString() },
        { id: 2, paymentId:501, logType:"RESPONSE", message:"승인 성공", createdAt:new Date(now.getTime()-3600_000).toISOString() },
      ],
      502: [
        { id: 3, paymentId:502, logType:"REQUEST",  message:"/approve 요청", createdAt:new Date(now.getTime()-7305_000).toISOString() },
        { id: 4, paymentId:502, logType:"ERROR",    message:"재고 부족: 3", createdAt:new Date(now.getTime()-7200_000).toISOString() },
      ],
      503: [ { id: 5, paymentId:503, logType:"REQUEST", message:"가상계좌 생성", createdAt:new Date(now.getTime()-1905_000).toISOString() } ],
      504: [ { id: 6, paymentId:504, logType:"REQUEST", message:"취소 요청", createdAt:new Date(now.getTime()-86500_000).toISOString() } ],
    };

    setOrders(mockOrders);
    setItemsByOrder(mockItems);
    setPayments(mockPayments);
    setLogsByPayment(mockLogs);
  }, []);

  async function getUsers() {
    const ac = new AbortController();

    try {
      const response = await fetch("http://localhost:8080/api/users", {
        method: "GET",
        signal: ac.signal,
      })

      return await response.json();
    } catch (error) {
      alert('회원 정보를 불러오지 못했습니다.')
    }
  }


  const statuses = ["ALL", "CREATED", "CONFIRMED", "FAIL", "CANCELED"];

  function statusPill(s) {
    const colors = {
      CREATED: "#64748b", // slate
      CONFIRMED: "#16a34a", // green
      FAIL: "#ef4444", // red
      CANCELED: "#a855f7", // violet
    };
    const bg = colors[s] || "#475569";
    return <span style={{padding:"2px 8px", borderRadius:999, background:bg, color:"#fff", fontSize:12}}>{s}</span>;
  }

  const filtered = orders.filter(o => {
    const okUser = filterUser === "ALL" ? true : String(o.userId) === String(filterUser);
    const okStatus = filterStatus === "ALL" ? true : o.status === filterStatus;
    const okQuery = query.trim() ? (o.orderNo?.includes(query.trim()) || String(o.id).includes(query.trim())) : true;

    return okUser && okStatus && okQuery;
  });

  function openDetail(o) {
    const payment = payments[o.paymentId];
    const logs = logsByPayment[o.paymentId] || [];
    const items = itemsByOrder[o.id] || [];
    setDetail({ order: o, payment, logs, items });
    setOpen(true);
  }

  return (
    <div className="container">
      <div className="wrap">
        <div className="flex-between mb-6">
          <Link to="/" className="back-link">← 홈</Link>
          <h2>주문 내역</h2>
          <div style={{ width: 24 }} />
        </div>

        {/* Filters */}
        <div className="form" style={{ gridTemplateColumns: "repeat(4,1fr)", gap: 8, marginBottom: 12 }}>
          <label>
            <span>사용자</span>
            <select value={filterUser} onChange={(e) => setFilterUser(e.target.value)}>
              <option value="ALL">전체</option>
              {users.map(u => <option key={u.userId} value={u.userId}>{u.name}(#{u.userId})</option>)}
            </select>
          </label>
          <label>
            <span>상태</span>
            <select value={filterStatus} onChange={(e) => setFilterStatus(e.target.value)}>
              {statuses.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </label>
          <label style={{ gridColumn: "span 2" }}>
            <span>주문번호/ID 검색</span>
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="20250814-0001 또는 1001" />
          </label>
        </div>

        <table className="table">
          <thead>
          <tr>
            <th>#</th>
            <th>주문번호</th>
            <th>사용자</th>
            <th>상태</th>
            <th>금액</th>
            <th>생성시각</th>
            <th>액션</th>
          </tr>
          </thead>
          <tbody>
          {filtered.map(o => (
            <tr key={o.id}>
              <td>{o.id}</td>
              <td>{o.orderNo}</td>
              <td>#{o.userId}</td>
              <td>{statusPill(o.status)}</td>
              <td>{(o.price||0).toLocaleString()}원</td>
              <td>{new Date(o.createdAt).toLocaleString()}</td>
              <td>
                <button className="btn gray" onClick={() => openDetail(o)} style={{ padding: "8px 10px" }}>상세</button>
              </td>
            </tr>
          ))}
          {filtered.length === 0 && (
            <tr>
              <td colSpan={7} style={{ padding: 16, color: "#64748b" }}>표시할 주문이 없습니다.</td>
            </tr>
          )}
          </tbody>
        </table>

        <OrderDetailModal open={open} detail={detail} onClose={() => setOpen(false)} statusPill={statusPill}/>

      </div>
    </div>
  );
}

export default OrderList;
