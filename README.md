# Caro RMI + JavaFX (Server-Authoritative) + MySQL

Bạn chọn:
- Server-authoritative (server validate move + kết luận thắng/hòa)
- MySQL persistence
- Luật: thắng 5 liên tiếp

Project này đã tách package rõ ràng và UI dùng FXML riêng:
- login.fxml
- lobby.fxml
- game.fxml

## 1) Chuẩn bị MySQL
Chạy file: `server/src/main/resources/schema.sql`

Ví dụ:
```sql
SOURCE /path/to/schema.sql;
```

## 2) Build
```bash
mvn -q -DskipTests package
```

## 3) Run server
Cấu hình DB bằng system properties:
```bash
java \
  -Ddb.url="jdbc:mysql://127.0.0.1:3306/caro?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
  -Ddb.user=root \
  -Ddb.pass=YOURPASS \
  -Drmi.port=1099 \
  -Drmi.name=CaroLobby \
  -jar server/target/server-2.0.0.jar
```

Admin console trên terminal server:
- help
- online
- rooms
- broadcast <message>
- warn <user> <message>
- ban <user> <minutes> <reason>
- exit

## 4) Run client (khuyến nghị)
```bash
mvn -q -pl client javafx:run
```

Run 2 client để test tạo phòng/join/quick play.

## 5) Gợi ý nâng cấp tiếp (đồ án hoàn chỉnh)
- Hash mật khẩu (BCrypt), chính sách đăng ký
- Persist room (tuỳ yêu cầu) + history match chi tiết
- Spectator mode / timer / undo (nếu được phép)
- Moderation: rate-limit chat, blacklist từ, log audit
- ELO chuẩn (expected score + K-factor)


## Lưu ý Maven
- Chạy từ root với plugin tọa độ đầy đủ:
```bash
mvn -q -pl client -am org.openjfx:javafx-maven-plugin:0.0.8:run
```


## Chạy server (fat jar)
Sau khi build, chạy file **server/target/server-2.0.0-all.jar** (đã đóng gói cả common + mysql driver):
```bash
java \
  -Ddb.url="jdbc:mysql://127.0.0.1:3306/caro?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
  -Ddb.user=root \
  -Ddb.pass=YOURPASS \
  -Drmi.port=1099 \
  -Drmi.name=CaroLobby \
  -jar server/target/server-2.0.0-all.jar
```


## UI Pro
- Sidebar + views (Rooms/Chat/Friends/Leaderboard/AI)
- Dark theme CSS.

### Run
1) Start server:
```bash
java -Ddb.url="jdbc:mysql://127.0.0.1:3306/caro_1?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" -Ddb.user="root" -Ddb.pass='YOURPASS' -Drmi.port=1099 -Drmi.name=CaroLobby -jar server/target/server-2.0.0-all.jar
```
2) Start client:
```bash
cd client
mvn -q javafx:run
```
3) Play vs AI: open "Chơi với máy (AI)" (offline).
