@echo off
REM =========================================================
REM  KnetRaid 서버 실행 스크립트 (Windows)
REM  paper.jar 파일명은 실제 다운로드한 Paper 서버 jar 이름으로 바꿔주세요.
REM
REM  왜 필요한가:
REM  Windows 콘솔은 기본적으로 시스템 코드페이지(예: CP949)를 사용합니다.
REM  아래 -Dstdin.encoding / -Dstdout.encoding / -Dstderr.encoding 옵션이 없으면
REM  "/약탈관리 시즌 시작 <한글이름>"처럼 콘솔에 한글을 직접 입력했을 때
REM  글자가 깨져서 DB에 잘못 저장될 수 있습니다.
REM =========================================================

chcp 65001 > nul

java ^
  -Dfile.encoding=UTF-8 ^
  -Dstdout.encoding=UTF-8 ^
  -Dstderr.encoding=UTF-8 ^
  -Dstdin.encoding=UTF-8 ^
  -Xms2G -Xmx4G ^
  -jar paper.jar --nogui

pause
