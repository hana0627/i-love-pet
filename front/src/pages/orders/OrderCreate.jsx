import {Link, useNavigate} from "react-router-dom";
import {useEffect, useRef, useState} from "react";
import {loadTossPayments} from "@tosspayments/tosspayments-sdk";

function OrderCreate() {
  const clientKey = process.env.REACT_APP_TOSS_CLIENT_KEY;

  const nav = useNavigate();
  const [users, setUsers] = useState([]);
  const [selectedUser, setSelectedUser] = useState(null);
  const [products, setProducts] = useState([]);
  const [selected, setSelected] = useState({});
  const [submitting, setSubmitting] = useState(false);

  // 토스페이먼츠 - start
  const [ready, setReady] = useState(false);
  const [widgets, setWidgets] = useState(null);

  const [openWidget, setOpenWidget] = useState(false);

  const renderedRef = useRef(false);
  // 토스페이먼츠 - end


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
      productId: p.productId,
      productName: p.productName,
      price: Number(p.price),
      quantity: Number(selected[p.productId])
    }));

  const total = selectedItems.reduce((sum, it) => sum + (it.price * it.quantity), 0);

  // 토스페이먼츠 - start
  // 토스페이먼츠 SDK 객체 불러오기
  useEffect(() => {
    async function fetchPaymentWidgets() {
      if (!clientKey || !selectedUser) return;
      // ------  결제위젯 초기화 ------
      const tossPayments = await loadTossPayments(clientKey);
      const widget = tossPayments.widgets({
        // customerKey는 구매자를 구분하는 값.
        customerKey: `${selectedUser}_`,
      });

      setWidgets(widget);
    }

    fetchPaymentWidgets();
  }, [clientKey, selectedUser]);


  // 토스페이먼츠 위젯 그리기
  async function renderPaymentWidgets() {
    if (!widgets || renderedRef.current) return;
    await Promise.all([
      // ------  결제 UI 렌더링 ------
      widgets.renderPaymentMethods({
        selector: "#payment-method",
        variantKey: "DEFAULT",
      }),
      // ------  이용약관 UI 렌더링 ------
      widgets.renderAgreement({
        selector: "#agreement",
        variantKey: "AGREEMENT",
      }),
    ]);
    setReady(true);
    renderedRef.current = true;
  }
  // 토스페이먼츠 - end

  // 토스페이먼츠 결제요청 - start
  function requestPayments() {

    // 여기서 서버호출
    // 결제를 요청하기 전에 orderId, amount를 서버에 저장하세요.
    // --> orderId 생성하기.

    // 아래는 기존에 Fake 결제를 위한 api
    // try {
    //   const res = await fetch("http://localhost:8082/api/orders", {
    //     method: "POST",
    //     headers: {"Content-Type": "application/json"},
    //     body: JSON.stringify({
    //         userId: Number(selectedUser),
    //         items: selectedItems
    //       }
    //     ),
    //   });
    //
    //   if (!res.ok) {
    //     let msg = "주문 생성 실패";
    //     try {
    //       const err = await res.json();
    //       if (err?.message) msg = err.message;
    //     } catch {
    //     }
    //     alert(msg);
    //     return;
    //   }
    //   const data = await res.json();
    //   alert(`주문 생성 완료! 주문번호: ${data.orderId}`);
    //   nav("/");
    // } catch (e) {
    //   alert("주문 생성 중 오류가 발생했습니다.");
    // } finally {
    //   setSubmitting(false);
    // }


    widgets.requestPayment({
      orderId: `demo_${Date.now()}`,
      orderName: selectedItems.length === 1
        ? selectedItems[0].productName
        : `${selectedItems[0].productName} 외 ${selectedItems.length - 1}건`,
      successUrl: window.location.origin + "/success",
      failUrl: window.location.origin + "/fail",
    })
  }

  // 토스페이먼츠 결제요청 - end


  async function createOrder() {
    if (submitting) return;
    setSubmitting(true);

    if (!selectedUser) return alert("사용자를 선택해주세요.");
    if (selectedItems.length === 0) return alert("상품을 선택하고 수량을 입력해주세요.");
    await widgets.setAmount({currency: "KRW", value: total});

    setOpenWidget(true)
    await renderPaymentWidgets()

    setSubmitting(false);
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
                <option key={u.userId} value={u.userId}>{u.userName} (#{u.userId})</option>
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
                <td>{p.productName}</td>
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

        <div
          style={{
            position: 'fixed', inset: 0, background: 'rgba(0,0,0,.45)',
            display: openWidget ? 'flex' : 'none',   // ← 여기만 바뀜
            alignItems: 'center', justifyContent: 'center', zIndex: 1000
          }}
          onClick={() => setOpenWidget(false)}>
          <div
            style={{
              background: '#fff', borderRadius: 12, padding: 20,
              width: 600, maxWidth: '92vw', position: 'relative'
            }}
            onClick={(e) => e.stopPropagation()}>
            <button
              style={{zIndex: 2000}} aria-label="닫기" className="toss-close"
              onClick={() => {
                setOpenWidget(false);
              }}
            >×
            </button>
            <div className="wrapper">
              <div className="box_section">
                {/* 결제 UI */}
                <div id="payment-method"/>
                {/* 이용약관 UI */}
                <div id="agreement"/>
                {/* 결제하기 버튼 */}
                <button
                  className="toss-btn primary"
                  disabled={!ready || total <= 0}
                  onClick={requestPayments}>
                  결제하기
                </button>

              </div>
            </div>
          </div>
        </div>

        <div className="flex-between mb-6" style={{marginTop: 12}}>
          <div className="section-title">총액: {total.toLocaleString()}원</div>
          <div style={{display: "flex", gap: 8}}>
            <button className="btn gray" onClick={() => nav(-1)}>뒤로</button>
            <button className="btn teal" onClick={createOrder} disabled={submitting}>주문하기</button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default OrderCreate
