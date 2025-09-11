import {useEffect, useState} from "react";
import {useNavigate, useSearchParams} from "react-router-dom";

function PaymentSuccess() {
  const [status, setStatus] = useState('confirming'); // 'confirming', 'success', 'failed'
  const [message, setMessage] = useState('');

  useEffect(() => {
    // 쿼리 파라미터에서 결제 정보 추출
    const urlParams = new URLSearchParams(window.location.search);
    const requestData = {
      orderId: urlParams.get("orderId"),
      amount: urlParams.get("amount"),
      paymentKey: urlParams.get("paymentKey"),
    };

  //   async function confirm() {
  //     try {
  //       const response = await fetch("http://localhost:8082/api/orders/confirm", {
  //         method: "PATCH",
  //         headers: {
  //           "Content-Type": "application/json",
  //         },
  //         body: JSON.stringify(requestData),
  //       });
  //
  //       const json = await response.json();
  //
  //       if (!response.ok) {
  //         setStatus('failed');
  //         setMessage(json.message || '주문 처리 중 오류가 발생했습니다.');
  //         return;
  //       }
  //
  //       if (json.success) {
  //         setStatus('success');
  //         setMessage('주문이 성공적으로 완료되었습니다.');
  //       } else {
  //         setStatus('failed');
  //         setMessage(json.message || '주문 처리에 실패했습니다.');
  //       }
  //     } catch (error) {
  //       setStatus('failed');
  //       setMessage('네트워크 오류가 발생했습니다.');
  //     }
  //   }
  //
  //   confirm();
  // }, []);

    async function confirmPayment() {
      try {
        // 1단계: 결제 확정 요청 (비동기 시작)
        const response = await fetch("http://localhost:8082/api/orders/confirm", {
          method: "PATCH",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(requestData),
        });

        if (!response.ok) {
          const errorData = await response.json().catch(() => ({}));
          setStatus('failed');
          setMessage(errorData.message || '결제 확정 요청 실패');
          return;
        }

        const confirmResponse = await response.json();

        // 요청이 성공적으로 접수되었다면 폴링 시작
        if (confirmResponse.success) {
          // 2단계: 결제 처리 완료까지 폴링
          const finalResult = await pollConfirmStatus(requestData.orderId);

          // 최종 결과 처리
          setStatus('success');
          setMessage('주문이 성공적으로 완료되었습니다.');
        } else {
          setStatus('failed');
          setMessage(confirmResponse.message || '결제 확정 요청이 거부되었습니다.');
        }

      } catch (error) {
        console.error('결제 확정 처리 중 오류:', error);
        setStatus('failed');
        setMessage('네트워크 오류가 발생했습니다.');
      }
    }

    confirmPayment();
  }, []);

// 결제 확정 상태 폴링 함수
  async function pollConfirmStatus(orderNo) {
    const maxAttempts = 60; // 60초 대기 (결제 확정은 시간이 더 걸릴 수 있음)
    let attempts = 0;

    // 로딩 UI 표시
    showLoadingMessage('결제를 처리하고 있습니다...');

    while (attempts < maxAttempts) {
      try {
        const statusRes = await fetch(`http://localhost:8082/api/orders/${orderNo}/status`);
        if (!statusRes.ok) {
          throw new Error('주문 상태 조회 실패');
        }

        const statusData = await statusRes.json();
        console.log(`결제 확정 폴링 ${attempts + 1}회차:`, statusData);

        // 🟢 계속 폴링해야 하는 상태 (처리 중)
        if (statusData.status === "PREPARED" ||
          statusData.status === "DECREASE_STOCK" ||
          statusData.status === "PAYMENT_PENDING") {

          // 상태별 사용자 피드백 메시지
          switch (statusData.status) {
            case "PREPARED":
              updateLoadingMessage('재고 확인 중입니다...');
              break;
            case "DECREASE_STOCK":
              updateLoadingMessage('재고 차감 처리 중입니다...');
              break;
            case "PAYMENT_PENDING":
              updateLoadingMessage('결제 승인 처리 중입니다...');
              break;
          }

          // 계속 폴링
        }
        // 🎉 성공 - 폴링 중단
        else if (statusData.status === "CONFIRMED") {
          hideLoadingMessage();
          return {
            orderId: statusData.orderNo || orderNo,
            paymentId: statusData.paymentId,
            message: '결제가 성공적으로 완료되었습니다.'
          };
        }
        // ❌ 실패 - 폴링 중단 및 에러 처리
        else if (statusData.status === "DECREASE_STOCK_FAIL" ||
          statusData.status === "PAYMENT_FAILED" ||
          statusData.status === "CANCELED" ||
          statusData.status === "FAIL" ||
          statusData.status === "VALIDATION_FAILED" ||
          statusData.status === "PROCESSING_FAILED" ||
          statusData.status === "PAYMENT_PREPARE_FAIL") {

          hideLoadingMessage();

          // 상태별 구체적인 에러 메시지
          let errorMessage;
          switch (statusData.status) {
            case "DECREASE_STOCK_FAIL":
              errorMessage = "재고가 부족하여 주문을 처리할 수 없습니다.";
              break;
            case "PAYMENT_FAILED":
              errorMessage = "결제 처리에 실패했습니다.";
              break;
            case "CANCELED":
              errorMessage = "주문이 취소되었습니다.";
              break;
            case "VALIDATION_FAILED":
              errorMessage = "상품 검증에 실패했습니다.";
              break;
            case "PROCESSING_FAILED":
              errorMessage = "주문 처리 중 오류가 발생했습니다.";
              break;
            case "PAYMENT_PREPARE_FAIL":
              errorMessage = "결제 준비 중 오류가 발생했습니다.";
              break;
            case "FAIL":
            default:
              errorMessage = statusData.errorMessage || "주문 처리에 실패했습니다.";
              break;
          }

          throw new Error(errorMessage);
        }
        // ⚠️ 예상하지 못한 상태 - 일단 계속 폴링하되 일반 메시지
        else {
          updateLoadingMessage('주문을 처리하고 있습니다...');
        }

        // 1초 후 재시도
        await new Promise(resolve => setTimeout(resolve, 1000));
        attempts++;

      } catch (e) {
        hideLoadingMessage();
        console.error('결제 확정 상태 조회 중 오류:', e);
        throw e;
      }
    }

    // 타임아웃
    hideLoadingMessage();
    throw new Error('결제 처리 시간이 초과되었습니다. 잠시 후 다시 확인해주세요.');
  }

// 로딩 메시지 업데이트 함수 (기존 로딩창의 메시지만 변경)
  function updateLoadingMessage(message) {
    const loadingDiv = document.getElementById('payment-loading');
    if (loadingDiv) {
      const messageDiv = loadingDiv.querySelector('.loading-message');
      if (messageDiv) {
        messageDiv.textContent = message;
      }
    }
  }

// 로딩 UI 표시 함수
  function showLoadingMessage(message) {
    // 기존 로딩창이 있으면 제거
    hideLoadingMessage();

    const loadingDiv = document.createElement('div');
    loadingDiv.id = 'payment-loading';
    loadingDiv.style.cssText = `
    position: fixed;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    background: white;
    padding: 30px;
    border-radius: 15px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
    z-index: 9999;
    text-align: center;
    min-width: 300px;
  `;

    loadingDiv.innerHTML = `
    <div style="margin-bottom: 15px;">
      <div style="border: 4px solid #f3f3f3; border-top: 4px solid #3498db; border-radius: 50%; width: 40px; height: 40px; animation: spin 1s linear infinite; margin: 0 auto;"></div>
    </div>
    <div class="loading-message" style="font-size: 16px; color: #333; font-weight: 500;">${message}</div>
    <div style="margin-top: 10px; font-size: 12px; color: #666;">잠시만 기다려주세요...</div>
  `;

    // 스피너 애니메이션 CSS 추가
    if (!document.querySelector('#payment-spinner-style')) {
      const style = document.createElement('style');
      style.id = 'payment-spinner-style';
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

// 로딩 UI 숨기기 함수
  function hideLoadingMessage() {
    const loadingDiv = document.getElementById('payment-loading');
    if (loadingDiv) {
      loadingDiv.remove();
    }
  }

  // 로딩 스피너 컴포넌트
  const LoadingSpinner = () => (
    <div className="spinner">
      <div className="spinner-inner"></div>
    </div>
  );

  // 성공 아이콘
  const SuccessIcon = () => (
    <div className="success-icon">
      <svg width="64" height="64" viewBox="0 0 64 64" fill="none">
        <circle cx="32" cy="32" r="32" fill="#10B981"/>
        <path d="M20 32l8 8 16-16" stroke="white" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"/>
      </svg>
    </div>
  );

  // 실패 아이콘
  const ErrorIcon = () => (
    <div className="error-icon">
      <svg width="64" height="64" viewBox="0 0 64 64" fill="none">
        <circle cx="32" cy="32" r="32" fill="#EF4444"/>
        <path d="M22 22l20 20M42 22l-20 20" stroke="white" strokeWidth="3" strokeLinecap="round"/>
      </svg>
    </div>
  );

  if (status === 'confirming') {
    return (
      <div style={{
        minHeight: '100vh',
        background: 'linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%)',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        padding: '20px',
        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      }}>
        <div style={{
          background: 'linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%)',
          borderRadius: '20px',
          padding: '48px',
          textAlign: 'center',
          boxShadow: '0 20px 60px rgba(0, 0, 0, 0.1)',
          maxWidth: '400px',
          width: '100%',
          position: 'relative',
          overflow: 'hidden'
        }}>
          <LoadingSpinner />
          <h2 style={{
            margin: '0 0 12px 0',
            fontSize: '28px',
            fontWeight: '700',
            color: '#1e293b'
          }}>결제 진행중</h2>
          <p style={{
            margin: '0 0 32px 0',
            fontSize: '16px',
            color: '#64748b',
            lineHeight: '1.6'
          }}>주문을 처리하고 있습니다.<br/>잠시만 기다려 주세요.</p>
          <div style={{
            width: '100%',
            height: '6px',
            background: '#e2e8f0',
            borderRadius: '3px',
            overflow: 'hidden',
            position: 'relative'
          }}>
            <div style={{
              height: '100%',
              background: 'linear-gradient(90deg, #3b82f6, #8b5cf6)',
              borderRadius: '3px',
              animation: 'progress 2s ease-in-out infinite',
              width: '0%'
            }}></div>
          </div>
        </div>

        <style>{`
          .spinner {
            width: 64px;
            height: 64px;
            margin: 0 auto 24px;
            position: relative;
          }

          .spinner-inner {
            width: 100%;
            height: 100%;
            border: 4px solid #e2e8f0;
            border-top: 4px solid #3b82f6;
            border-radius: 50%;
            animation: spin 1s linear infinite;
          }

          @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
          }

          @keyframes progress {
            0% { width: 0%; }
            50% { width: 70%; }
            100% { width: 100%; }
          }

          @keyframes bounceIn {
            0% { transform: scale(0.3); opacity: 0; }
            50% { transform: scale(1.05); }
            70% { transform: scale(0.9); }
            100% { transform: scale(1); opacity: 1; }
          }
        `}</style>
      </div>
    );
  }

  if (status === 'failed') {
    return (
      <div style={{
        minHeight: '100vh',
        background: 'linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%)',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        padding: '20px',
        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      }}>
        <div style={{
          background: 'linear-gradient(135deg, #fef2f2 0%, #fef1f1 100%)',
          borderRadius: '20px',
          padding: '48px',
          textAlign: 'center',
          boxShadow: '0 20px 60px rgba(0, 0, 0, 0.1)',
          maxWidth: '400px',
          width: '100%'
        }}>
          <div style={{ margin: '0 auto 24px', animation: 'bounceIn 0.6s ease-out' }}>
            <ErrorIcon />
          </div>
          <h2 style={{
            margin: '0 0 12px 0',
            fontSize: '28px',
            fontWeight: '700',
            color: '#dc2626'
          }}>주문 처리 실패</h2>
          <p style={{
            margin: '0 0 32px 0',
            fontSize: '16px',
            color: '#7f1d1d',
            lineHeight: '1.6'
          }}>{message}</p>
          <button
            style={{
              background: '#dc2626',
              color: 'white',
              border: 'none',
              padding: '12px 32px',
              borderRadius: '12px',
              fontSize: '16px',
              fontWeight: '600',
              cursor: 'pointer',
              transition: 'all 0.2s ease',
              boxShadow: '0 4px 12px rgba(220, 38, 38, 0.3)'
            }}
            onMouseOver={(e) => {
              e.target.style.background = '#b91c1c';
              e.target.style.transform = 'translateY(-2px)';
              e.target.style.boxShadow = '0 6px 20px rgba(220, 38, 38, 0.4)';
            }}
            onMouseOut={(e) => {
              e.target.style.background = '#dc2626';
              e.target.style.transform = 'translateY(0)';
              e.target.style.boxShadow = '0 4px 12px rgba(220, 38, 38, 0.3)';
            }}
            onClick={() => window.location.href = '/'}
          >
            메인으로 돌아가기
          </button>
        </div>
      </div>
    );
  }

  // status === 'success'
  return (
    <div style={{
      minHeight: '100vh',
      background: 'linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%)',
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      padding: '20px',
      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
    }}>
      <div style={{
        background: 'linear-gradient(135deg, #f0fdf4 0%, #ecfdf5 100%)',
        borderRadius: '20px',
        padding: '48px',
        textAlign: 'center',
        boxShadow: '0 20px 60px rgba(0, 0, 0, 0.1)',
        maxWidth: '400px',
        width: '100%'
      }}>
        <div style={{ margin: '0 auto 24px', animation: 'bounceIn 0.6s ease-out' }}>
          <SuccessIcon />
        </div>
        <h2 style={{
          margin: '0 0 12px 0',
          fontSize: '28px',
          fontWeight: '700',
          color: '#059669'
        }}>주문 완료!</h2>
        <p style={{
          margin: '0 0 24px 0',
          fontSize: '16px',
          color: '#065f46',
          lineHeight: '1.6'
        }}>주문이 성공적으로 처리되었습니다.</p>
        <div style={{
          background: 'rgba(16, 185, 129, 0.1)',
          borderRadius: '12px',
          padding: '16px',
          marginBottom: '32px'
        }}>
          <p style={{
            margin: '0',
            fontSize: '14px',
            fontWeight: '600',
            color: '#047857'
          }}>주문번호: {new URLSearchParams(window.location.search).get("orderId") || "ORDER-123456"}</p>
        </div>
        <div style={{
          display: 'flex',
          gap: '12px',
          justifyContent: 'center'
        }}>
          <button
            style={{
              background: 'white',
              color: '#059669',
              border: '2px solid #059669',
              padding: '12px 24px',
              borderRadius: '12px',
              fontSize: '16px',
              fontWeight: '600',
              cursor: 'pointer',
              transition: 'all 0.2s ease',
              flex: '1'
            }}
            onMouseOver={(e) => {
              e.target.style.background = '#059669';
              e.target.style.color = 'white';
              e.target.style.transform = 'translateY(-2px)';
            }}
            onMouseOut={(e) => {
              e.target.style.background = 'white';
              e.target.style.color = '#059669';
              e.target.style.transform = 'translateY(0)';
            }}
            onClick={() => window.location.href = '/orders/list'}
          >
            주문목록
          </button>
          <button
            style={{
              background: '#059669',
              color: 'white',
              border: 'none',
              padding: '12px 24px',
              borderRadius: '12px',
              fontSize: '16px',
              fontWeight: '600',
              cursor: 'pointer',
              transition: 'all 0.2s ease',
              boxShadow: '0 4px 12px rgba(5, 150, 105, 0.3)',
              flex: '1'
            }}
            onMouseOver={(e) => {
              e.target.style.background = '#047857';
              e.target.style.transform = 'translateY(-2px)';
              e.target.style.boxShadow = '0 6px 20px rgba(5, 150, 105, 0.4)';
            }}
            onMouseOut={(e) => {
              e.target.style.background = '#059669';
              e.target.style.transform = 'translateY(0)';
              e.target.style.boxShadow = '0 4px 12px rgba(5, 150, 105, 0.3)';
            }}
            onClick={() => window.location.href = '/'}
          >
            메인화면
          </button>
        </div>
      </div>
    </div>
  );
}

export default PaymentSuccess;
