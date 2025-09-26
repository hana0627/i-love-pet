import {Link} from "react-router-dom";
import {useEffect, useState} from "react";

function UserList() {
  const [users, setUsers] = useState([]);

  useEffect(() => {
    getUsers().then(data =>
      setUsers(Array.isArray(data) ? data : [])
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

  return (
    <div className="container">
      <div className="wrap">
        <div className="flex-between mb-6">
          <Link to="/" className="back-link">← 홈</Link>
          <h2>회원 목록</h2>
          <div style={{ width: 24 }} />
        </div>
        <table className="table">
          <thead>
          <tr>
            <th>#</th>
            <th>이름</th>
            <th>이메일</th>
            <th>전화번호</th>
          </tr>
          </thead>
          <tbody>
          {users.map((u) => (
            <tr key={u.userId}>
              <td>{u.userId}</td>
              <td>{u.userName}</td>
              <td>{u.email}</td>
              <td>{u.phoneNumber}</td>
            </tr>
          ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default UserList;
