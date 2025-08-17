import {useEffect, useState} from "react";
import {Link} from "react-router-dom";

function ProductList() {
  const [products, setProducts] = useState([]);

  useEffect(() => {
    getProducts().then(data =>
      setProducts(data)
    )
  }, []);

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


  return (
    <div className="container">
      <div className="wrap">
        <div className="flex-between mb-6">
          <Link to="/" className="back-link">← 홈</Link>
          <h2>상품 목록</h2>
          <div style={{ width: 24 }} />
        </div>
        <table className="table">
          <thead>
          <tr>
            <th>#</th>
            <th>상품명</th>
            <th>가격</th>
            <th>재고</th>
          </tr>
          </thead>
          <tbody>
          {products.map((p, i) => (
            <tr key={i}>
              <td>{p.productId}</td>
              <td>{p.productName}</td>
              <td>{p.price?.toLocaleString?.() ?? p.price}</td>
              <td>{p.stock}</td>
            </tr>
          ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default ProductList;
