# Monolith Chat

## 目标

- 单体服务聊天入口
- 智能体先识别意图，再调用 MySQL/ES 工具
- 多源结果汇总后返回结构化结论

## 启动

```bash
mvn -pl monolith-chat spring-boot:run
```

## 环境变量

- `DASHSCOPE_API_KEY`
- `MYSQL_URL`
- `MYSQL_USERNAME`
- `MYSQL_PASSWORD`
- `ES_ENABLED`
- `ES_BASE_URL`

## 接口

- `POST /api/chat` SSE
- `DELETE /api/chat/session/{sessionId}`
- `POST /api/chat/interrupt/{sessionId}`
- `GET /api/tools`
- `GET /api/health`

## 请求示例

```json
{
  "sessionId": "demo",
  "message": "请帮我查询上周订单量并在知识库中检索对应活动策略"
}
```
