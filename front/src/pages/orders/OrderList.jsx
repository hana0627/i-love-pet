import {Link} from "react-router-dom";
import {useEffect, useRef, useState} from "react";
import OrderDetailModal from "./OrderDetailModal";
import Pagination from "../../component/Pagination";

function OrderList() {
  const [users, setUsers] = useState([]);
  const [orders, setOrders] = useState([]);

  const [selectedUser, setSelectedUser] = useState("ALL");
  const [selectedStatus, setSelectedStatus] = useState("ALL");
  const [searchOrderNo, setSearchOrderNo] = useState("");


  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);


  const [open, setOpen] = useState(false);
  const [detail, setDetail] = useState(null);


  const [items, setItems] = useState({});
  const [itemsLoading, setItemsLoading] = useState(null);
  const [payments, setPayments] = useState({});
  const [paymentLoading, setPaymentLoading] = useState(null);
  const [paymentLogs, setPaymentLogs] = useState({})
  const [paymentLogsLoading, setPaymentLogsLoading] = useState(null);
  const [loading, setLoading] = useState(false);

  const didMountRef = useRef(false);


  useEffect(() => {
    getUsers().then(data => setUsers(Array.isArray(data) ? data : []));
    handleSearch();
  }, []);


  useEffect(() => {
    if (!didMountRef.current) {
      didMountRef.current = true
      return;
    }
    setPage(0);
    handleSearch()
  }, [selectedStatus, selectedUser])

  useEffect(() => {
    if (!didMountRef.current) {
      didMountRef.current = true
      return;
    }
    handleSearch();
  }, [page, size]);


  function handleSearch() {
    const userId = selectedUser === "ALL" ? undefined : selectedUser;
    const status = selectedStatus === "ALL" ? undefined : selectedStatus;
    const searchQuery = searchOrderNo?.trim() ? searchOrderNo.trim() : undefined;

    setLoading(true)
    getOrders({userId: userId, status: status, searchQuery: searchQuery, page, size}).then(
      data => {
        setOrders(data?.content ?? [])

        setTotalPages(data?.totalPages ?? 0);
        setTotalElements(data?.totalElements ?? 0);

        setPage(data?.number ?? page);
        setSize(data?.size ?? size);
      }
    ).finally(
      () => setLoading(false)
    )
  }

  const onQueryKeyDown = (e) => {
    if (e.key === "Enter") {
      handleSearch();
    }
  };

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

  async function getOrders({userId, status, searchQuery, page = 0, size = 20}) {
    const ac = new AbortController();
    const params = new URLSearchParams();
    if (userId) params.append("userId", userId);
    if (status) params.append("status", status);
    if (searchQuery) params.append("searchQuery", searchQuery);
    params.append("page", String(page))
    params.append("size", String(size))

    const url = `http://localhost:8082/api/orders?${params.toString()}`;

    try {
      const response = await fetch(url, {
        method: "GET",
        signal: ac.signal,
      })
      return await response.json();
    } catch (error) {
      alert('주문 정보를 불러오지 못했습니다.')
    }
  }

  async function getOrderItems(orderNo) {
    const ac = new AbortController();
    const url = `http://localhost:8082/api/orders/${orderNo}/items`;
    try {
      const res = await fetch(url, {method: "GET", signal: ac.signal});
      if (!res.ok) throw new Error(`failed: ${res.status}`);
      return await res.json();
    } catch (e) {
      alert("주문 상품 정보를 불러오지 못했습니다.");
      return [];
    }
  }

  async function getPayment(paymentId) {
    if (!paymentId && paymentId !== 0) return null;
    const ac = new AbortController();
    const url = `http://localhost:8083/api/payments/${paymentId}`;
    try {
      const res = await fetch(url, {method: "GET", signal: ac.signal});
      if (!res.ok) throw new Error(`failed: ${res.status}`);
      return await res.json();
    } catch (e) {
      alert("결제 정보를 불러오지 못했습니다.");
      return null;
    }
  }
  async function getPaymentLogs(paymentId) {
    if (!paymentId && paymentId !== 0) return null;
    const ac = new AbortController();
    const url = `http://localhost:8083/api/payments/${paymentId}/logs`;
    try {
      const res = await fetch(url, {method: "GET", signal: ac.signal});
      if (!res.ok) throw new Error(`failed: ${res.status}`);
      return await res.json();
    } catch (e) {
      alert("결제내역 정보를 불러오지 못했습니다.");
      return null;
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
    return <span
      style={{padding: "2px 8px", borderRadius: 999, background: bg, color: "#fff", fontSize: 12}}>{s}</span>;
  }

  function openDetail(o) {
    const cachedPayment = payments[o.paymentId];
    const cachedPaymentLogs = paymentLogs[o.paymentId];
    const cachedItems = items[o.orderNo];

    setDetail({
      order: o,
      payment: cachedPayment || null,
      paymentLogs: cachedPaymentLogs || [],
      items: cachedItems || []
    });
    setOpen(true);

    if (!cachedPayment && o.paymentId) {
      setPaymentLoading(o.paymentId);
      getPayment(o.paymentId).then(p => {
        if (p) {
          setPayments(prev => ({...prev, [o.paymentId]: p}));
          setDetail(cur => {
            if (!cur || cur.order.paymentId !== o.paymentId) return cur; // 레이스 방지
            return {...cur, payment: p};
          });
        }
      }).finally(() => setPaymentLoading(null));
    }

    if (!cachedPaymentLogs && o.paymentId) {
      setPaymentLogsLoading(o.paymentId);
      getPaymentLogs(o.paymentId).then(paymentLogs => {
        setPaymentLogs(prev => ({...prev, [o.paymentId]: paymentLogs}));
        setDetail(cur => {
          if (!cur || cur.order.paymentId !== o.paymentId) return cur;
          return {...cur, paymentLogs};
        });
      }).finally(() => setPaymentLogsLoading(null));
    }

    if (!cachedItems) {
      setItemsLoading(o.orderNo);
      getOrderItems(o.orderNo).then(items => {
        setItems(prev => ({...prev, [o.orderNo]: items}));
        setDetail(cur => {
          if (!cur || cur.order.orderNo !== o.orderNo) return cur;
          return {...cur, items};
        });
      }).finally(() => setItemsLoading(null));
    }
  }

  return (
    <div className="container">
      <div className="wrap">
        <div className="flex-between mb-6">
          <Link to="/" className="back-link">← 홈</Link>
          <h2>주문 내역</h2>
          <div style={{width: 24}}/>
        </div>

        <div className="form" style={{gridTemplateColumns: "repeat(4,1fr)", gap: 8, marginBottom: 12}}>
          <label>
            <span>사용자</span>
            <select value={selectedUser} onChange={(e) => setSelectedUser(e.target.value)}>
              <option value="ALL">전체</option>
              {users.map(u => <option key={u.userId} value={u.userId}>{u.userName}(#{u.userId})</option>)}
            </select>
          </label>
          <label>
            <span>상태</span>
            <select value={selectedStatus} onChange={(e) => setSelectedStatus(e.target.value)}>
              {statuses.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </label>
          {/* 🔎 검색 인풋 + 버튼 */}
          <label style={{gridColumn: "span 2"}}>
            <span>주문번호 검색</span>
            <div style={{display: "flex", gap: 8}}>
              <input
                value={searchOrderNo}
                onChange={(e) => setSearchOrderNo(e.target.value)}
                onKeyDown={onQueryKeyDown}
                placeholder="주문번호를 입력하세요"
                style={{flex: 1}}
              />
              <button className="btn dark" onClick={handleSearch} disabled={loading}>
                {loading ? "검색중..." : "검색"}
              </button>
            </div>
          </label>
        </div>

        <table className="table">
          <thead>
          <tr>
            <th>주문번호</th>
            <th style={{width: 100}}>사용자</th>
            <th>상태</th>
            <th style={{width: 120}}>금액</th>
            <th>생성시각</th>
            <th style={{width: 80}}>액션</th>
          </tr>
          </thead>
          <tbody>
          {orders.map(o => (
            <tr key={o.id}>
              <td>{o.orderNo}</td>
              <td>{o.userName}(#{o.userId})</td>
              <td>{statusPill(o.status)}</td>
              <td>{(o.price || 0).toLocaleString()}원</td>
              <td>{new Date(o.createdAt).toLocaleString()}</td>
              <td>
                <button className="btn gray" onClick={() => openDetail(o)} style={{padding: "8px 10px"}}>상세</button>
              </td>
            </tr>
          ))}
          {orders.length === 0 && (
            <tr>
              <td colSpan={7} style={{padding: 16, color: "#64748b"}}>표시할 주문이 없습니다.</td>
            </tr>
          )}
          </tbody>
        </table>

        <Pagination
          page={page}
          totalPages={totalPages}
          onChange={(nextPage) => setPage(nextPage)}
        />

        <OrderDetailModal open={open} detail={detail} onClose={
          () => setOpen(false)
        }
          statusPill={statusPill}
          isLoadingPayment={detail ? paymentLoading === (detail.payment?.paymentId ?? detail.order.paymentId) : false}
          isLoadingPaymentLogs={detail ? paymentLogsLoading === (detail.payment?.paymentId ?? detail.order.paymentId) : false}
          isLoadingItems={detail ? itemsLoading === detail.order.orderNo : false}
        />

      </div>
    </div>
  );
}

export default OrderList;
