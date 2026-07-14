#!/bin/sh
# MySQL healthcheck script - 비밀번호를 환경변수로 안전하게 처리
# 이 방식은 비밀번호를 command-line argument로 노출하지 않습니다
export MYSQL_PWD="${DB_PASSWORD}"
mysqladmin ping -h localhost -u root >/dev/null 2>&1
exit $?
