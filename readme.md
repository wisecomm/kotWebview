
adb device
- 설치 
./gradlew installDebug


3. 웹뷰(WebView) 전용 디버깅 (강력 추천!)
웹뷰 앱 개발 시 가장 유용한 방법은 Chrome 브라우저를 사용하는 것입니다:

폰을 Mac과 연결하고 앱을 실행합니다.
Mac의 크롬 브라우저 주소창에 chrome://inspect/#devices를 입력합니다.
목록에 있는 주인님의 폰 모델명 아래 'inspect' 버튼을 누릅니다.
**웹 브라우저 개발자 도구(F12)**와 똑같은 화면이 뜨며, 여기서 자바스크립트 수정, 네트워크 체크, 콘솔 로그 확인을 실시간으로 할 수 있습니다.


코드 수정 (여기서 저와 함께)
설치: ./gradlew installDebug
- 디바이스 연결 확인 : adb devices

# 폰앱에서 localhost:3000으로 접속
- adb reverse tcp:3000 tcp:3000

로그 (내 앱만 보기):
- 검색(grep): adb logcat | grep com.example.kotwebview
- 패키지 기준: adb logcat --pid=$(adb shell pidof -s com.example.kotwebview)

./gradlew installDebug && adb shell am start -n com.example.kotwebview/.MainActivity
