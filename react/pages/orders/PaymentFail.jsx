import { useSearchParams } from "react-router-dom";
import {useEffect} from "react";

///fail?code={ERROR_CODE}&message={ERROR_MESSAGE}
// &orderId={ORDER_ID}
function PaymentFail() {
  const [searchParams] = useSearchParams();

  useEffect(() => {

    const requestData = {
      orderId: searchParams.get("orderId"),
      code: searchParams.get("code"),
      message: searchParams.get("message"),
    };

    async function fail() {
      const response = await fetch("http://localhost:8082/api/orders/fail", {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(requestData),
      });

      // const json = await response.json()
      // if (!response.ok) {
        // 결제 실패 비즈니스 로직을 구현하세요.
        // navigate(`/fail?message=${json.message}&code=${json.code}`);
        // return;
      // }

    }

    fail()
  }, []);


  return (
    <div className="result wrapper">
      <div className="box_section">
        <h2>
          결제 실패
        </h2>
        <p>{`에러 코드: ${searchParams.get("code")}`}</p>
        <p>{`실패 사유: ${searchParams.get("message")}`}</p>
      </div>
    </div>
  );
}

export default PaymentFail;

