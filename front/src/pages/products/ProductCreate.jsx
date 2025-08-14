import {Link, useNavigate} from "react-router-dom";
import {useEffect, useState} from "react";

function ProductCreate() {
  const nav = useNavigate();
  const [name, setName] = useState("");
  const [price, setPrice] = useState(0);
  const [stock, setStock] = useState(0);
  const [users, setUsers] = useState([]);
  const [selectedUser, setSelectedUser] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    getUsers().then(data => {
        setUsers(data)
        if (data.length > 0) setSelectedUser(data[0].userId);
      }
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

  async function handleSubmit() {
    if (loading) return;
    if (!name.trim()) return alert("상품명을 입력해주세요.");
    if (price <= 0) return alert("가격을 입력해주세요.");
    if (stock < 0) return alert("재고를 입력해주세요.");
    if (!selectedUser) return alert("사용자를 선택해주세요.");

    setLoading(true);
    try {
      const res = await fetch("http://localhost:8081/api/products", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-User-Id": String(selectedUser),
        },
        body: JSON.stringify({name, price, stock}),
      });
      if (!res.ok) throw new Error("상품 등록 실패");
      const data = await res.json();
      alert(`상품 등록 완료! ID: ${data.productId}`);
      nav("/");
    } catch (e) {
      alert("상품 등록 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="container">
      <div className="wrap">
        <div className="flex-between mb-6">
          <Link to="/" className="back-link">← 홈</Link>
          <h2>상품 등록</h2>
          <div style={{width: 24}}/>
        </div>

        <div className="form vertical" style={{marginBottom: 16}}>
          <label>
            <span>사용자 선택</span>
            <select value={selectedUser} onChange={(e) => setSelectedUser(e.target.value)}>
              {users.map((u) => (
                <option key={u.userId} value={u.userId}>{u.name} (#{u.userId})</option>
              ))}
            </select>
          </label>
          <label>
            <span>상품명</span>
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="로얄캐닌 고양이 사료"/>
          </label>
          <label>
            <span>가격</span>
            <input type="number" value={price} onChange={(e) => setPrice(Number(e.target.value))} placeholder="35000"/>
          </label>
          <label>
            <span>재고</span>
            <input type="number" value={stock} onChange={(e) => setStock(Number(e.target.value))} placeholder="1000"/>
          </label>
          <div className="grid2 mt-4">
            <button type="button" onClick={() => nav(-1)} className="btn gray">뒤로</button>
            <button type="button" onClick={handleSubmit} className="btn dark">등록하기</button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default ProductCreate;
