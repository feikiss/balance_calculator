# 实时余额计算系统

## 项目介绍
本项目是一个基于Spring Boot的实时余额计算系统，支持账户余额管理和交易处理，具备高可用性和弹性伸缩能力。

## 技术栈
- **后端**：Java 8, Spring Boot 2.7.x
- **数据库**：MySQL 8.0
- **缓存**：Redis
- **部署**：Kubernetes (EKS/GKE/ACK)
- **测试**：JUnit, JMeter

## 架构图
```
+------------------+     +------------------+     +------------------+
|   Load Balancer  |     |   K8s Service    |     |   K8s Pods      |
+------------------+     +------------------+     +------------------+
        |                       |                        |
        v                       v                        v
+------------------+     +------------------+     +------------------+
|   Spring Boot    |     |   Redis Cache    |     |   MySQL DB       |
+------------------+     +------------------+     +------------------+
```

## 功能特性
- 实时处理金融交易并更新账户余额
- 支持并发交易处理
- 高可用性和弹性伸缩
- 失败交易重试机制
- 分布式缓存支持

## 快速开始

### 本地开发
1. 克隆项目
   ```bash
   git clone <repository-url>
   cd hsbchomework
   ```

2. 配置数据库和Redis
   - 修改 `src/main/resources/application.yml` 中的数据库和Redis配置

3. 运行应用
   ```bash
   mvn spring-boot:run
   ```

4. 生成测试数据
   ```bash
   mvn spring-boot:run -Dspring-boot.run.arguments=mockdata
   ```

### Kubernetes部署
1. 构建Docker镜像
   ```bash
   mvn clean package
   docker build -t balance-calculator:latest .
   ```

2. 配置K8s资源
   - 修改 `k8s/configmap.yaml` 中的数据库和Redis连接信息
   - 修改 `k8s/secret.yaml` 中的数据库凭证（Base64编码）

3. 部署到K8s集群
   ```bash
   kubectl apply -f k8s/configmap.yaml
   kubectl apply -f k8s/secret.yaml
   kubectl apply -f k8s/deployment.yaml
   kubectl apply -f k8s/service.yaml
   kubectl apply -f k8s/hpa.yaml
   ```

## API接口

### 账户管理
- `POST /api/accounts` - 创建账户
- `GET /api/accounts/{accountNumber}` - 查询账户
- `PUT /api/accounts/{accountNumber}/balance` - 更新余额

### 交易管理
- `POST /api/transactions` - 创建交易
- `POST /api/transactions/{transactionId}/process` - 处理交易
- `GET /api/transactions/{transactionId}` - 查询交易
- `POST /api/transactions/{transactionId}/retry` - 重试失败交易

## 测试
1. 单元测试
   ```bash
   mvn test
   ```

2. 性能测试
   - 使用JMeter进行负载测试
   - 测试脚本位于 `scripts/jmeter/`

## 监控
- 应用指标：Spring Boot Actuator
- 容器指标：Kubernetes Metrics Server
- 日志：ELK Stack

## 贡献指南
1. Fork 项目
2. 创建特性分支
3. 提交变更
4. 推送到分支
5. 创建 Pull Request

## 许可证
MIT License 