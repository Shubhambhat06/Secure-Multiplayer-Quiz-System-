import socket
import ssl
import threading
import sys

SERVER_IP = "127.0.0.1"
PORT = 5000

# ---------------- SSL SETUP ----------------
context = ssl.create_default_context(ssl.Purpose.SERVER_AUTH)
context.load_verify_locations("servercert.pem")
context.check_hostname = False

# ---------------- SYNCHRONIZATION PRIMITIVES ----------------
send_lock = threading.Lock()

# Event set by listener when a question arrives → unblocks input thread
question_ready = threading.Event()

# Event set by input thread after answer is sent → unblocks listener (so it
# won't print the next question while user is still typing this one's answer)
answer_sent = threading.Event()

# Signals main thread that quiz has ended
quiz_done = threading.Event()


# ---------------- SEND HELPER ----------------
def send(sock, msg):
    with send_lock:
        sock.send((msg + "\n").encode())


# ---------------- LISTENER THREAD ----------------
def listen(sock):
    buffer = ""

    while True:
        try:
            data = sock.recv(4096).decode()
            if not data:
                print("\nServer closed the connection.")
                quiz_done.set()
                break

            buffer += data

            while "\n" in buffer:
                msg, buffer = buffer.split("\n", 1)
                msg = msg.strip()
                if not msg:
                    continue

                # -------- QUESTION --------
                if msg.startswith("QUESTION|"):
                    parts = msg.split("|")
                    # Format: QUESTION|<num>|<text>|<opts>|<timelimit>
                    q_num    = parts[1]
                    question = parts[2]
                    options  = [o for o in parts[3].split(";") if o.strip()]
                    t_limit  = parts[4] if len(parts) > 4 else "?"

                    print(f"\n{'='*40}")
                    print(f"  Question {q_num}  (time limit: {t_limit}s)")
                    print(f"{'='*40}")
                    print(f"  {question}")
                    for opt in options:
                        print(f"  {opt}")
                    print(f"{'='*40}")

                    # Reset answer_sent BEFORE signalling question_ready
                    answer_sent.clear()
                    question_ready.set()   # unblock input thread

                # -------- RESULT --------
                elif msg.startswith("RESULT|"):
                    # Wait until the input thread has finished sending the answer
                    # (handles edge case where result arrives before input completes)
                    answer_sent.wait(timeout=2)

                    parts = msg.split("|")
                    verdict  = parts[1]                          # Correct / Wrong
                    pts      = parts[2]                          # e.g. +200
                    score    = parts[3].replace("Score:", "")    # strip prefix → 1400
                    correct  = parts[4].replace("Correct:", "") if len(parts) > 4 else ""

                    print(f"\n  ➤ {verdict}  {pts} points  |  Your score: {score}")
                    if verdict == "Wrong" and correct:
                        print(f"  ✓ Correct answer was: {correct}")

                # -------- LEADERBOARD --------
                elif msg.startswith("LEADERBOARD|"):
                    entries = [e for e in msg.split("|")[1].split(",") if e.strip()]
                    print("\n  📊 Leaderboard:")
                    for i, entry in enumerate(entries, 1):
                        name, score = entry.split(":")
                        prefix = "🥇" if i == 1 else ("🥈" if i == 2 else "🥉")
                        print(f"    {prefix} {name}: {score} pts")

                    # Tell server we're done reading results → server sends next Q
                    send(sock, "READY")

                # -------- WINNER --------
                elif msg.startswith("WINNER|"):
                    parts = msg.split("|")
                    winner = parts[1]
                    pts    = parts[2] if len(parts) > 2 else ""
                    print(f"\n{'='*40}")
                    print(f"  🏆  Winner: {winner}  ({pts} pts)")
                    print(f"{'='*40}")

                # -------- END --------
                elif msg.strip() == "END":
                    print("\n  Quiz finished! Thanks for playing.\n")
                    quiz_done.set()
                    return

        except Exception as e:
            print(f"\nConnection error: {e}")
            quiz_done.set()
            break


# ---------------- INPUT THREAD ----------------
def send_answers(sock):
    while not quiz_done.is_set():
        # Block here until a question arrives
        triggered = question_ready.wait(timeout=1)
        if not triggered:
            continue
        question_ready.clear()

        if quiz_done.is_set():
            break

        # Read answer from user
        while True:
            try:
                ans = input("\n  Your answer (A/B/C/D): ").strip().upper()
            except EOFError:
                quiz_done.set()
                return

            if ans in ("A", "B", "C", "D"):
                break
            print("  ⚠ Invalid input — please enter A, B, C or D.")

        send(sock, f"ANSWER|{ans}")
        answer_sent.set()   # tell listener the answer is on the wire


# ---------------- MAIN ----------------
def main():
    # Connect
    try:
        raw_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        client = context.wrap_socket(raw_socket, server_hostname=SERVER_IP)
        client.connect((SERVER_IP, PORT))
        print("🔐 Secure connection established ✅")
    except Exception as e:
        print(f"Connection failed ❌: {e}")
        sys.exit(1)

    # Handshake: send name
    name = input("Enter your name: ").strip() or "Player"
    send(client, f"NAME|{name}")

    print("\nWaiting for other players to join...")

    # Start threads
    listener_thread = threading.Thread(target=listen, args=(client,), daemon=True)
    input_thread    = threading.Thread(target=send_answers, args=(client,), daemon=True)

    listener_thread.start()
    input_thread.start()

    # Block main thread until quiz ends
    try:
        quiz_done.wait()
    except KeyboardInterrupt:
        print("\nExiting...")
    finally:
        client.close()


if __name__ == "__main__":
    main()