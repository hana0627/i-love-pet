import React from "react";
import {Link} from "react-router-dom";

function Home() {
  return (
    <div className="container">
      <div className="wrap">
        <h1>I Love Pet</h1>

        <div className="section">
          <p className="section-title">회원 기능</p>
          <div className="grid2">
            <Link to="/users/signup" className="btn gray">회원 가입</Link>
            <Link to="/users/list" className="btn gray">회원 목록</Link>
          </div>
        </div>

        <div className="section">
          <p className="section-title">상품 기능</p>
          <div className="grid2">

            <Link to="/products/create" className="btn dark">상품 등록</Link>
            <Link to="/products/list" className="btn dark">상품 목록</Link>
          </div>
        </div>

        <div className="section">
          <p className="section-title">주문 기능</p>
          <div className="grid2">
            <Link to="/orders/create" className="btn teal">상품 주문</Link>
            <Link to="/orders/list" className="btn teal">주문 내역</Link>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Home;
