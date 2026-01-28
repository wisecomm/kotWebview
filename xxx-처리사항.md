

- 다운로드 페이지 작성
- 자동 업그레이드 추가 및 테스트

####################### 사용 예 ####################### 
javascript
// 웹 프론트엔드 예시 코드
function sendToNative(action, data = {}) {
    const payload = { action, data };
    
    if (window.AndroidBridge) {
        // Android: JSON 문자열로 전송
        window.AndroidBridge.postMessage(JSON.stringify(payload));
    } else if (window.webkit?.messageHandlers?.AppBridge) {
        // iOS: 객체 그대로 전송 (iOS WKWebView 표준 방식)
        window.webkit.messageHandlers.AppBridge.postMessage(payload);
    }
}
// 사용 예시
sendToNative("LOGOUT");
sendToNative("SHOW_TOAST", { message: "안녕하세요" });

####################### 사용 예 ####################### 
javascript
// 이렇게 호출하면 응답을 기다립니다.
const appInfo = await sendToNative("GET_APP_VERSION"); 
console.log(appInfo.versionName); // "1.0.0"
안드로이드 쪽에는 GET_APP_VERSION 케이스를 추가해 두었으니, 테스트해 보실 수 있습니다. iOS 개발 시에도 window.onNativeResponse만 호출해 주면 똑같이 동작합니다.


####################### 사용 예 ####################### 
Native: 알람이 울리면 sendEvent("ALARM", { time: "12:00" })를 호출합니다.
Web: 웹페이지는 미리 window.onNativeEvent 리스너를 켜두고 기다립니다.

useEffect(() => {
    // "ALARM" 이벤트가 오면 이 함수가 실행됨
    const unsubscribe = Bridge.onNativeEvent("ALARM", (data) => {
        console.log("알람 발생!", data.time);
    });
    return () => unsubscribe(); // 페이지 나갈 때 리스너 해제
}, []);

네, 이벤트의 성격에 따라 위치가 다릅니다.

전역적인 이벤트 (예: 푸시 알림, 배터리 부족, 앱 버전 체크)
추천 위치: layout.tsx (Next.js 13+ App Router) 또는 _app.tsx (Pages Router) 같은 최상위 레이아웃 컴포넌트에 넣는 것이 정석입니다.
이유: 사용자가 어떤 페이지(/home, /profile, /settings)에 있더라도 항상 알림을 받고 팝업을 띄워줘야 하기 때문입니다.
특정 페이지 전용 이벤트 (예: QR 코드 스캔 결과)
추천 위치: 해당 페이지(예: ScanPage.tsx)의 useEffect 안에 넣습니다.
이유: QR 스캔 화면이 아닐 때는 스캔 결과를 받을 필요가 없거나, 오히려 다른 페이지에서 반응하면 버그가 될 수 있기 때문입니다.
질문하신 '알람'의 경우, 앱 어디서든 울려야 하는 알람이라면 메인 레이아웃(layout.tsx 등) 에 넣어두고 전역 상태 관리(Zustand, Recoil 등)나 Toast 알림 라이브러리와 연동하여 UI를 띄워주는 방식이 가장 일반적입니다.

