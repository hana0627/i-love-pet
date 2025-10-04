import {Link, useNavigate} from "react-router-dom";
import {useState} from "react";

function SignUp() {
  const nav = useNavigate();
  const [userName, setUserName] = useState("");
  const [email, setEmail] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [loading, setLoading] = useState(false); // 중복 요청 방지

   async function handleSubmit() {
     if (loading) return;

     // 유효성 검사
     if (!userName.trim()) {
       alert("이름을 입력해주세요.");
       return;
     }
     if (!email.trim()) {
       alert("이메일을 입력해주세요.");
       return;
     }
     if (!phoneNumber.trim()) {
       alert("전화번호를 입력해주세요.");
       return;
     }

     setLoading(true);
     try {
       const response = await fetch("http://localhost:8000/user-service/api/users", {
         method: "POST",
         headers: { "Content-Type": "application/json" },
         body: JSON.stringify({ userName, email, phoneNumber }),
       });
       if (!response.ok) {
         throw new Error(`회원가입 실패: ${response.status}`);
       }
       alert("회원가입이 완료되었습니다.");
       nav("/"); // 홈으로 이동
     } catch (error) {
       alert("회원가입 중 오류가 발생했습니다.");
     } finally {
       setLoading(false);
     }
   }


  return (
    <div className="container">
      <div className="wrap">
        <div className="flex-between mb-6">
          <Link to="/" className="back-link">← 홈</Link>
          <h2>회원 가입</h2>
          <div style={{ width: 24 }} />
        </div>
        <div className="form vertical">
          <label>
            <span>이름</span>
            <input value={userName} onChange={(e) => setUserName(e.target.value)} required />
          </label>
          <label>
            <span>이메일</span>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </label>
          <label>
            <span>전화번호</span>
            <input value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} placeholder="숫자만입력해주세요" required />
          </label>
          <div className="grid2 mt-4">
            <button type="button" onClick={() => nav(-1)} className="btn gray">뒤로</button>
            <button onClick={() => handleSubmit()} className="btn dark">가입하기</button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default SignUp;
