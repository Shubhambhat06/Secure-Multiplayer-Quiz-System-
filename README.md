# 🔐 Secure Multiplayer Quiz System

A real-time, terminal-based multiplayer quiz application using **secure SSL/TLS communication** between a Java server and Python clients.

This project demonstrates:

* Network programming
* Multithreading & synchronization
* Secure communication (SSL/TLS)
* Real-time game coordination

---

## 🚀 Features

### 🖥️ Server (Java)

* SSL/TLS encrypted communication
* Supports multiple players
* Thread-per-client architecture
* Time-limited questions
* Time-based scoring system
* Real-time leaderboard updates
* Synchronization using:

  * `CountDownLatch`
  * Thread-safe collections

### 💻 Client (Python)

* Secure SSL connection to server
* Interactive CLI-based quiz interface
* Separate threads for:

  * Listening to server
  * Sending user input
* Clean UI for:

  * Questions
  * Results
  * Leaderboard
* Input validation (A/B/C/D answers)

---

## 📂 Project Structure

```
📦 Secure Quiz System
 ┣ 📜 QuizServer.java      # Java SSL Quiz Server
 ┣ 📜 client.py            # Python SSL Client
 ┣ 📜 serverkeystore.jks   # Java keystore for SSL
 ┣ 📜 servercert.pem       # Certificate for client verification
 ┗ 📜 README.md
```

---

## ⚙️ How It Works

### 🔄 Flow

1. Client connects securely using SSL
2. Sends username → `NAME|<name>`
3. Server waits for all players
4. Quiz starts:

   * Server sends question
   * Clients respond within time limit
5. Server evaluates:

   * Correct answer → points + time bonus
6. Leaderboard is updated after each question
7. Final winner is announced 🎉

---

## 🔐 Communication Protocol

| Message Type | Format       |                 |          |           |            |
| ------------ | ------------ | --------------- | -------- | --------- | ---------- |
| Name         | `NAME        | <username>`     |          |           |            |
| Question     | `QUESTION    | <num>           | <text>   | <options> | <time>`    |
| Answer       | `ANSWER      | <option>`       |          |           |            |
| Result       | `RESULT      | <Correct/Wrong> | <points> | <score>   | <correct>` |
| Leaderboard  | `LEADERBOARD | name:score,...` |          |           |            |
| Ready        | `READY`      |                 |          |           |            |
| Winner       | `WINNER      | <name>          | <score>` |           |            |
| End          | `END`        |                 |          |           |            |

---

## 🧠 Scoring System

* Correct Answer:

  * Base points: **200**
  * Bonus: up to **800 (based on speed)**
* Wrong Answer:

  * **0 points**

---

## ▶️ How to Run

### 1️⃣ Start Server (Java)

```bash
javac QuizServer.java
java QuizServer
```

Make sure:

* `serverkeystore.jks` is present
* Password matches in code (`123456`)

---

### 2️⃣ Run Client (Python)

```bash
python client.py
```

Make sure:

* `servercert.pem` is present

---

### 3️⃣ Play 🎮

* Enter your name
* Wait for other players
* Answer questions within time
* Compete on leaderboard!

---

## 🧵 Key Concepts Used

* Multithreading
* Synchronization (Locks, Latches)
* Socket Programming
* SSL/TLS Security
* Client-Server Architecture

---

## 📌 Future Improvements

* GUI (JavaFX / Web UI)
* Database for persistent scores
* Dynamic question loading
* More players scalability
* Chat feature

---

## 🏆 Example Gameplay

```
Question 1:
What is 2+2?
A) 3
B) 4
C) 5
D) 6

Your answer: B

✔ Correct +800 points
Leaderboard:
🥇 Alice: 800
🥈 Bob: 600
```

---

## 👨‍💻 Author

Developed as a networking & systems project to demonstrate secure real-time multiplayer applications.

---

## 📜 License

This project is open-source and available under the MIT License.
