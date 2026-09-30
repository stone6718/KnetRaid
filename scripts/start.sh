#!/bin/bash
# =========================================================
#  KnetRaid 서버 실행 스크립트 (Linux/macOS)
#  paper.jar 파일명은 실제 다운로드한 Paper 서버 jar 이름으로 바꿔주세요.
#
#  대부분의 Linux 배포판은 로케일이 이미 UTF-8이지만, 명시적으로 지정해두면
#  콘솔에 한글 명령어(예: /약탈관리 시즌 시작 <한글이름>)를 입력했을 때
#  글자가 깨지는 문제를 방지할 수 있습니다.
# =========================================================

export LANG=C.UTF-8

java \
  -Dfile.encoding=UTF-8 \
  -Dstdout.encoding=UTF-8 \
  -Dstderr.encoding=UTF-8 \
  -Dstdin.encoding=UTF-8 \
  -Xms2G -Xmx4G \
  -jar paper.jar --nogui
