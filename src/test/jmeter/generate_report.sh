#!/bin/bash

# 设置变量
JMETER_HOME="/Users/fly/software/apache-jmeter-5.6.3"
TEST_PLAN="src/test/jmeter/HSBC_Transaction_Test_Plan.jmx"
REPORT_DIR="target/jmeter-reports"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# 创建报告目录
mkdir -p "$REPORT_DIR"

# 运行 JMeter 测试并生成报告
"$JMETER_HOME/bin/jmeter" \
  -n \
  -t "$TEST_PLAN" \
  -l "$REPORT_DIR/results_$TIMESTAMP.jtl" \
  -e \
  -o "$REPORT_DIR/report_$TIMESTAMP"

echo "性能测试报告已生成在: $REPORT_DIR/report_$TIMESTAMP" 