# 시간표 알람 (Android)

시간표를 카메라로 찍거나 갤러리에서 고르면 **기기 안에서(오프라인) 한국어 OCR**로 요일·시간·과목을 읽고,
**매주 수업 시작 N분 전**에 알람을 울려 주는 앱입니다.

## 주요 기능
- 📷 촬영 / 🖼 사진 선택 → ML Kit 한국어 텍스트 인식 (무료, 인터넷 불필요)
- 표 구조 자동 복원
  - 중·고등학교형: `1교시 08:50~09:35` 같은 행 라벨 → 칸마다 과목
  - 교시 번호만 있는 표: 설정의 "교시별 시작 시각" 사용
  - 대학(에브리타임형): 왼쪽 시각 눈금(9, 10, 11 …)으로 세로 위치 → 시각 환산
  - 칸 안에 `15:00-16:15` 같은 시간이 적혀 있으면 그 시간을 우선
  - 표를 못 읽으면 `월 1교시 국어`, `화요일 09:00 경영학` 같은 줄 단위 텍스트로 재시도
  - 같은 과목 연강은 하나로 합쳐 알람 1번
  - 사진이 옆으로 누워 있으면 90°/270° 회전해 다시 시도
- ✏ 인식 결과 확인 화면에서 과목·요일·시간 수정/삭제/추가 후 저장
- ⏰ 매주 반복 알람 (기본 10분 전, 0~180분 조정), 잠금화면 위 전체 화면 알람, 소리 반복 + 진동, 1분 후 자동 종료
- 재부팅·시간대 변경 후 자동 재등록, 수업별 알람 켜기/끄기, 전체 알람 스위치
- 설정의 **테스트 알람**(5초 후)으로 알람 동작 확인

## 빌드 & 설치

### 방법 A — Android Studio (가장 쉬움)
1. [Android Studio](https://developer.android.com/studio) 설치 (Ladybug 2024.2 이상 권장)
2. `File > Open` → 이 폴더(`TimetableAlarm`) 선택 → Gradle 동기화가 끝날 때까지 대기
3. 휴대폰에서 **개발자 옵션 → USB 디버깅** 켜고 PC에 연결
4. ▶ Run 버튼 → 휴대폰에 바로 설치됨
   - APK 파일만 필요하면 `Build > Build App Bundle(s) / APK(s) > Build APK(s)`

### 방법 B — GitHub에서 자동 빌드 (PC에 개발 도구 없이)
1. 이 폴더를 GitHub 저장소에 올림 (`.github/workflows/build-apk.yml` 포함)
2. 저장소의 **Actions** 탭 → `Build APK` 실행이 끝나면 `timetable-alarm-apk` 아티팩트 다운로드
3. 휴대폰으로 APK를 옮겨 설치 ("출처를 알 수 없는 앱 설치" 허용 필요)

### 명령줄
```bash
./gradlew testDebugUnitTest   # 파서·알람 시각 단위 테스트
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```
요구 사항: JDK 17, Android SDK 35 (`local.properties`에 `sdk.dir` 또는 `ANDROID_HOME`).

## 처음 실행 시 허용할 것
| 항목 | 이유 |
|---|---|
| 알림 허용 | 알람 표시 |
| 알람 및 리마인더 (Android 12) | 정확한 시각에 울리기 (Android 13+는 자동 허용) |
| 전체 화면 알림 (Android 14+) | 잠금화면 위에 알람 화면 띄우기 |

앱 상단에 ⚠ 경고가 보이면 눌러서 해당 설정으로 이동하세요.
삼성·샤오미 등 일부 기기는 **설정 → 배터리 → 앱 절전 예외**에 이 앱을 추가해야 알람이 확실히 울립니다.

## 사진 잘 찍는 요령
- 요일 행(월 화 수 목 금)과 왼쪽 교시/시간 열이 **모두 나오게**, 표를 화면에 꽉 차게, 반듯하게
- 그림자·반사 피하기, 흐리면 다시 찍기
- 앱 캡처 화면(에브리타임 등)은 스크린샷을 그대로 선택하면 가장 정확합니다
- 인식은 100% 정확하지 않으므로 **저장 전 확인 화면에서 꼭 검토**하세요

## 구조
```
app/src/main/java/com/example/timetablealarm/
├── MainActivity.java      시간표 목록, 촬영/선택, 권한 안내
├── ReviewActivity.java    OCR 결과 확인·수정 후 저장
├── OcrHelper.java         이미지 로드(EXIF 회전·축소) + ML Kit 한국어 OCR
├── TimetableParser.java   OCR 단어 좌표 → 요일/시간/과목 (순수 Java, 단위 테스트 있음)
├── AlarmTime.java         다음 알람 시각 계산 (순수 Java, 단위 테스트 있음)
├── AlarmScheduler.java    AlarmManager.setAlarmClock 예약/취소
├── AlarmReceiver.java     알람 수신 → 서비스 시작 + 다음 주 재예약
├── AlarmService.java      알람음 반복·진동 포그라운드 서비스
├── AlarmActivity.java     잠금화면 위 알람 화면
├── BootReceiver.java      재부팅/시간 변경 시 재예약
├── Storage.java           SharedPreferences(JSON) 저장
├── Dialogs.java           수업 편집·설정·원문 보기
└── ClassAdapter.java      목록 어댑터
```

## 참고
- 개인 설치용으로 release 빌드도 디버그 키로 서명합니다. Play 스토어에 올리려면 별도 서명 키와
  `USE_EXACT_ALARM` 권한 정책(알람 앱 용도) 검토가 필요합니다.
- 패키지명 `com.example.timetablealarm`은 원하는 이름으로 바꿔도 됩니다 (`app/build.gradle`의 `applicationId`).
