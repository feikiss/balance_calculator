#!/bin/bash

# 设置变量
JMETER_HOME="/usr/local/apache-jmeter-5.5"
TEST_PLAN="scripts/jmeter/balance-calculator-test.jmx"
REPORT_DIR="reports/performance"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# 创建报告目录
mkdir -p "$REPORT_DIR"

# 运行JMeter测试并生成HTML报告
echo "开始性能测试..."
"$JMETER_HOME/bin/jmeter" -n \
    -t "$TEST_PLAN" \
    -l "$REPORT_DIR/results_$TIMESTAMP.jtl" \
    -e -o "$REPORT_DIR/report_$TIMESTAMP"

# 生成汇总报告
echo "生成汇总报告..."
"$JMETER_HOME/bin/jmeter" -g "$REPORT_DIR/results_$TIMESTAMP.jtl" \
    -o "$REPORT_DIR/summary_$TIMESTAMP"

# 复制最新的报告到固定位置
cp -r "$REPORT_DIR/report_$TIMESTAMP" "$REPORT_DIR/latest"
cp -r "$REPORT_DIR/summary_$TIMESTAMP" "$REPORT_DIR/latest_summary"

echo "性能测试完成！"
echo "报告位置: $REPORT_DIR/latest"
echo "汇总报告位置: $REPORT_DIR/latest_summary" 