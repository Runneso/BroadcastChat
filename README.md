# BroadcastChat

## Архитектура

- Балансировщик (chat.balancer)
  - TCP‑прокси: принимает клиентские подключения и проксирует поток на один из живых серверов (Round‑Robin).
  - Health‑check: периодически проверяет доступность серверов из `config.json` и поддерживает список живых.
  - Обновления кластера: рассылает серверам список живых узлов (BALANCER_MESSAGE_TYPE).

- Сервер (chat.server)
  - Принимает клиентов и peer‑соединения по одному TCP‑порту (host:port).
  - На сообщении от клиента формирует ChatMessage, рассылает локальным клиентам и отправляет peer‑нодам.
  - На сообщении от peer делает только локальную рассылку.
  - Дедупликация сообщений по messageId (in‑memory, TTL 5 минут).

- Клиент (chat.client)
  - Раздельные потоки отправки и приёма, автопереподключение.
  - Печатает только ChatMessage, пришедшие от сервера (без локального «эхо»).

## Форматы сообщений (JSON)

- BalancerMessage
```json
{
  "type": "BALANCER_MESSAGE_TYPE",
  "servers": [
    {"serverId":"A","host":"127.0.0.1","port":9001},
    {"serverId":"B","host":"127.0.0.1","port":9002}
  ]
}
```

- ClientMessage
```json
{
  "type": "CLIENT_MESSAGE_TYPE",
  "username": "alice",
  "content": "hello",
  "timestamp": 1634567890000
}
```

- ChatMessage
```json
{
  "type": "CHAT_MESSAGE_TYPE",
  "messageId": "uuid",
  "serverId": "A",
  "username": "alice",
  "content": "hello",
  "timestamp": 1634567890000
}
```

- HealthcheckMessage
```json
{
  "type": "HEALTHCHECK_MESSAGE_TYPE",
  "serverId": "A",
  "timestamp": 1634567890000
}
```

## Инструкции по запуску

1) Сборка (однократно):
```bat
mvn -DskipTests package
```

2) Настройка балансировщика (файл `config.json` в корне):
```json
{
  "servers": [
    {"serverId":"A","host":"127.0.0.1","port":9001},
    {"serverId":"B","host":"127.0.0.1","port":9002}
  ]
}
```

3) Запуск серверов (в отдельных окнах):
```bat
mvn exec:java "-Dexec.mainClass=chat.server.Main" "-Dexec.args=A 127.0.0.1 9001"
mvn exec:java "-Dexec.mainClass=chat.server.Main" "-Dexec.args=B 127.0.0.1 9002"
```

4) Запуск балансировщика (например 5000-ый порт):
```bat
mvn exec:java "-Dexec.mainClass=chat.balancer.Main" "-Dexec.args=5000 config.json"
```

5) Запуск клиентов (подключаются к балансировщику):
```bat
mvn exec:java "-Dexec.mainClass=chat.client.Main" "-Dexec.args=127.0.0.1 5000 @alekksseeii"
mvn exec:java "-Dexec.mainClass=chat.client.Main" "-Dexec.args=127.0.0.1 5000 @runneso"
```


## Промпты LLM 
- Сгенерируй картинку для архитектуры проекта (так и не получилось нормально сгенерировать)
- Оцени структуру папок и деление на компоненты
- Предложи улучшения, чтобы код был чище и более "по-джавовски"

## P.S.
- Это было самое трудное ДЗ за весь ДРИП, очень трудно, когнитивно сложно, но очень интересно!