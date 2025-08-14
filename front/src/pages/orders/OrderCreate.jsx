import {Link, useNavigate} from "react-router-dom";
import {useEffect, useState} from "react";

function OrderCreate() {
  const nav = useNavigate();
  const [users, setUsers] = useState([]);
  const [selectedUser, setSelectedUser] = useState(null);
  const [products, setProducts] = useState([]);
  const [selected, setSelected] = useState({}); // { [productId]: quantity }
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {

    getUsers().then(data => {
        setUsers(data)
        if (data.length > 0) setSelectedUser(data[0].userId);
      }
    )
    getProducts().then(data =>
      setProducts(data)
    )
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


  async function getProducts() {
    const ac = new AbortController();

    try {
      const response = await fetch("http://localhost:8081/api/products", {
        method: "GET",
        signal: ac.signal,
      })

      return await response.json();
    } catch (error) {
      alert("상품 목록을 불러오지 못했습니다.")
    }
  }

  const toggleCheck = (productId) => {
    setSelected((prev) => {
      const next = {...prev};
      if (next[productId]) {
        delete next[productId];
      } else {
        next[productId] = 1; // default qty
      }
      return next;
    });
  };

  const updateQty = (productId, qty) => {
    const v = Math.max(1, Number(qty) || 1);
    setSelected((prev) => ({...prev, [productId]: v}));
  };

  const selectedItems = products
    .filter((p) => selected[p.productId] != null)
    .map((p) => ({
      productId: p.productId ?? p.id ?? p._id ?? p.name,
      price: Number(p.price),
      quantity: Number(selected[p.productId])
    }));

  const total = selectedItems.reduce((sum, it) => sum + (it.price * it.quantity), 0);

  async function submitOrder() {
    if (submitting) return;
    if (!selectedUser) return alert("사용자를 선택해주세요.");
    if (selectedItems.length === 0) return alert("상품을 선택하고 수량을 입력해주세요.");

    setSubmitting(true);
    try {
      const res = await fetch("http://localhost:8082/api/orders", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
            userId: Number(selectedUser),
            items: selectedItems
          }
        ),
      });

      if (!res.ok) {
        let msg = "주문 생성 실패";
        try {
          const err = await res.json();
          if (err?.message) msg = err.message;
        } catch {
        }
        alert(msg);
        return;
      }
      const data = await res.json();
      alert(`주문 생성 완료! 주문번호: ${data.orderId}`);
      nav("/");
    } catch (e) {
      alert("주문 생성 중 오류가 발생했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="container">
      <div className="wrap">
        <div className="flex-between mb-6">
          <Link to="/" className="back-link">← 홈</Link>
          <h2>상품 주문</h2>
          <div style={{width: 24}}/>
        </div>

        <div className="form vertical" style={{marginBottom: 16}}>
          <label>
            <span>사용자</span>
            <select value={selectedUser ?? ""} onChange={(e) => setSelectedUser(e.target.value)}>
              {users.map((u) => (
                <option key={u.userId} value={u.userId}>{u.name} (#{u.userId})</option>
              ))}
            </select>
          </label>
        </div>

        <table className="table">
          <thead>
          <tr>
            <th style={{width: 60}}></th>
            <th>상품명</th>
            <th>가격</th>
            <th>재고</th>
            <th style={{width: 120}}>수량</th>
          </tr>
          </thead>
          <tbody>
          {products.map((p, i) => {
            const pid = p.productId ?? i + 1;
            const checked = selected[pid] != null;
            return (
              <tr key={pid}>
                <td>
                  <input type="checkbox" checked={checked} onChange={() => toggleCheck(pid)}/>
                </td>
                <td>{p.name}</td>
                <td>{p.price?.toLocaleString?.() ?? p.price}</td>
                <td>{p.stock}</td>
                <td>
                  <input type="number" min={1} value={checked ? selected[pid] : 1} disabled={!checked}
                         onChange={(e) => updateQty(pid, e.target.value)}
                         style={{width: "100%", padding: "8px 10px", border: "1px solid #cbd5e1", borderRadius: 12}}
                  />
                </td>
              </tr>
            );
          })}
          </tbody>
        </table>

        <div className="flex-between mb-6" style={{marginTop: 12}}>
          <div className="section-title">총액: {total.toLocaleString()}원</div>
          <div style={{display: "flex", gap: 8}}>
            <button className="btn gray" onClick={() => nav(-1)}>뒤로</button>
            <button className="btn teal" onClick={submitOrder} disabled={submitting}>주문하기</button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default OrderCreate
