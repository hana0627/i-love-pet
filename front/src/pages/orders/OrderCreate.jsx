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
  const [paymentMethod, setPaymentMethod] = useState(null);
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
  // 렌더 함수 수정
  async function renderPaymentWidgets() {
    if (!widgets || renderedRef.current) return;

    const paymentMethodWidget = await widgets.renderPaymentMethods({
      selector: "#payment-method",
      variantKey: "DEFAULT",
    });

    await widgets.renderAgreement({
      selector: "#agreement",
      variantKey: "AGREEMENT",
    });

    // 결제수단 선택 이벤트 구독
    paymentMethodWidget.on('paymentMethodSelect', (event) => {
      setPaymentMethod(event);
    });

    setReady(true);
    renderedRef.current = true;

    // 위젯이 완전히 로드된 후 초기 선택된 결제수단 확인
    setTimeout(async () => {  // async 추가
      try {
        const initialSelectedMethod = await paymentMethodWidget.getSelectedPaymentMethod(); // await 추가
        if (initialSelectedMethod && !paymentMethod) {
          setPaymentMethod(initialSelectedMethod);
        }
      } catch (error) {
        console.warn('초기 결제수단 정보를 가져오는데 실패했습니다:', error);
      }
    }, 500); // 위젯이 완전히 로드될 시간 확보
  }

  // 토스페이먼츠 - end

  // 토스페이먼츠 결제요청 - start
  async function requestPayments() {
    // paymentMethod가 null이거나 undefined인 경우 처리
    if (!paymentMethod) {
      alert('결제수단이 선택되지 않았습니다. 결제수단을 선택해주세요.');
      return;
    }

    // paymentMethod 객체 구조에 따라 다르게 처리
    let selectedMethod;
    if (typeof paymentMethod === 'string') {
      selectedMethod = paymentMethod;
    } else if (paymentMethod.code) {
      selectedMethod = paymentMethod.code;
    } else if (paymentMethod.type) {
      selectedMethod = paymentMethod.type;
    } else {
      console.error('결제수단 정보 구조를 파악할 수 없습니다:', paymentMethod);
      selectedMethod = 'CARD';
    }


    try {
      const prepareResponse = await fetch("http://localhost:8082/api/orders/prepare", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
          userId: Number(selectedUser),
          items: selectedItems,
          method: selectedMethod,
        }),
      });

      if (!prepareResponse.ok) {
        alert(await prepareResponse.text().catch(() => "주문 생성 실패"));
        return;
      }

      const prepareResult = await prepareResponse.json();

      // 2단계: 상품 검증 완료까지 폴링
      const finalOrderData = await pollOrderStatus(prepareResult.orderId, prepareResult.eventId);

      if (!finalOrderData.amount) {
        alert('주문 처리 중 오류가 발생했습니다.');
        return;
      }
      // 3단계: 토스페이먼츠 결제 요청
      await widgets.setAmount({currency: "KRW", value: finalOrderData.amount});
      await widgets.requestPayment({
        orderId: finalOrderData.orderId,
        orderName: selectedItems.length === 1
          ? selectedItems[0].productName
          : `${selectedItems[0].productName} 외 ${selectedItems.length - 1}건`,
        successUrl: window.location.origin + "/payment-success",
        failUrl: window.location.origin + "/payment-fail",
      });

    } catch (error) {
      console.error('결제 처리 중 오류:', error);
      alert('결제 처리 중 오류가 발생했습니다.');
    }


    // const res = await fetch("http://localhost:8082/api/orders/prepare", {
    //   method: "POST",
    //   headers: {"Content-Type": "application/json"},
    //   body: JSON.stringify({
    //     userId: Number(selectedUser),
    //     items: selectedItems,
    //     method: selectedMethod,
    //   }),
    // });
    //
    // if (!res.ok) {
    //   alert(await res.text().catch(() => "주문 생성 실패"));
    //   return;
    // }
    //
    // const {orderId, amount} = await res.json();
    // await widgets.setAmount({currency: "KRW", value: amount});
    //
    // await widgets.requestPayment({
    //   orderId,
    //   orderName: selectedItems.length === 1
    //     ? selectedItems[0].productName
    //     : `${selectedItems[0].productName} 외 ${selectedItems.length - 1}건`,
    //   successUrl: window.location.origin + "/payment-success",
    //   failUrl: window.location.origin + "/payment-fail",
    // });
  }
  // 토스페이먼츠 결제요청 - end

  // 주문 상태 polling 함수 - start
  async function pollOrderStatus(orderNo, eventId) {
    const maxAttempts = 30; // 30초 대기
    let attempts = 0;

    // 로딩 UI
    showLoadingMessage('상품 정보를 확인하고 있습니다...');
    while (attempts < maxAttempts) {
      try {
        const statusRes = await fetch(`http://localhost:8082/api/orders/${orderNo}/status`);
        if (!statusRes.ok) {
          throw new Error('주문 상태 조회 실패');
        }
        const statusData = await statusRes.json();
        console.log(`폴링 ${attempts + 1}회차:`, statusData);

        if(statusData.status =="CREATED" || statusData.status =="VALIDATING") {

        }
        else if(statusData.status =="CONFIRMED") {
          hideLoadingMessage();
          return {
            orderId: statusData.orderNo || orderNo,
            amount: statusData.amount
          };
        }
        else {
          hideLoadingMessage();
          throw new Error("결제 준비에 실패했습니다.")
        }

        // 성공: 금액이 계산됨
        // if (statusData.amount && statusData.amount > 0) {
        //   hideLoadingMessage();
        //   return {
        //     orderId: statusData.orderNo || orderNo,
        //     amount: statusData.amount
        //   };
        // }

        // 실패: 에러 메시지가 있음
        // if (statusData.errorMessage) {
        //   hideLoadingMessage();
        //   throw new Error(statusData.errorMessage);
        // }

        // 아직 처리 중: 1초 후 재시도
        // await sleep(1000);
        await new Promise(resolve => setTimeout(resolve, 1000));
        attempts++;
      } catch (e) {
        hideLoadingMessage();
        console.error('주문 상태 조회 중 오류:', e);
        throw e;
      }
    }
    // 타임아웃
    hideLoadingMessage();
    throw new Error('주문 처리 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.');
  }
  // 주문 상태 polling 함수 - end


  // UI 관련 - start
  function showLoadingMessage(message) {
    // 로딩 스피너나 메시지 표시
    const loadingDiv = document.createElement('div');
    loadingDiv.id = 'order-loading';
    loadingDiv.style.cssText = `
    position: fixed;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    background: white;
    padding: 20px;
    border-radius: 10px;
    box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
    z-index: 9999;
    text-align: center;
  `;
    loadingDiv.innerHTML = `
    <div style="margin-bottom: 10px;">
      <div style="border: 4px solid #f3f3f3; border-top: 4px solid #3498db; border-radius: 50%; width: 30px; height: 30px; animation: spin 1s linear infinite; margin: 0 auto;"></div>
    </div>
    <div>${message}</div>
  `;

    // 스피너 애니메이션 CSS 추가
    if (!document.querySelector('#spinner-style')) {
      const style = document.createElement('style');
      style.id = 'spinner-style';
      style.textContent = `
      @keyframes spin {
        0% { transform: rotate(0deg); }
        100% { transform: rotate(360deg); }
      }
    `;
      document.head.appendChild(style);
    }

    document.body.appendChild(loadingDiv);
  }

  function hideLoadingMessage() {
    const loadingDiv = document.getElementById('order-loading');
    if (loadingDiv) {
      loadingDiv.remove();
    }
  }
  // UI 관련 - end


  async function createOrder() {
    if (submitting) return;
    setSubmitting(true);
    try {
      if (!selectedUser) return alert("사용자를 선택해주세요.");
      if (selectedItems.length === 0) return alert("상품을 선택하고 수량을 입력해주세요.");
      await widgets.setAmount({currency: "KRW", value: total});

      setOpenWidget(true)
      await renderPaymentWidgets()
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
