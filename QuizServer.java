import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.security.KeyStore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

// -------- CLIENT HANDLER --------
class ClientHandler extends Thread {
    SSLSocket socket;
    BufferedReader in;
    PrintWriter out;
    String name;

    volatile int score = 0;
    volatile String lastAnswer = "";
    volatile long answerTime = Long.MAX_VALUE;

    private final Object lock = new Object();
    private CountDownLatch answerLatch;   // signals when this client answered
    private CountDownLatch readyLatch;    // signals when this client is ready for next Q

    public ClientHandler(SSLSocket socket) throws Exception {
        this.socket = socket;
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        // First message must be NAME|<name>
        String msg = in.readLine();
        if (msg == null || !msg.startsWith("NAME|"))
            throw new IOException("Bad handshake from client");
        name = msg.split("\\|")[1].trim();
        System.out.println("[+] " + name + " connected");
    }

    public void send(String msg) {
        out.println(msg);
    }

    /** Called before broadcasting each question */
    public void resetForQuestion(CountDownLatch answerLatch) {
        synchronized (lock) {
            lastAnswer = "";
            answerTime = Long.MAX_VALUE;
            this.answerLatch = answerLatch;
        }
    }

    /** Called before waiting for clients to be ready */
    public void resetForReady(CountDownLatch readyLatch) {
        synchronized (lock) {
            this.readyLatch = readyLatch;
        }
    }

    private void setAnswer(String ans) {
        synchronized (lock) {
            if (lastAnswer.isEmpty()) {
                lastAnswer = ans;
                answerTime = System.currentTimeMillis();
                if (answerLatch != null) answerLatch.countDown();
            }
        }
    }

    public String getAnswer() {
        synchronized (lock) { return lastAnswer; }
    }

    public long getAnswerTime() {
        synchronized (lock) { return answerTime; }
    }

    public void run() {
        try {
            while (true) {
                String input = in.readLine();
                if (input == null) break;
                input = input.trim();

                if (input.startsWith("ANSWER|")) {
                    String ans = input.split("\\|")[1].trim();
                    setAnswer(ans);

                } else if (input.equals("READY")) {
                    // Client finished reading result, ready for next question
                    synchronized (lock) {
                        if (readyLatch != null) readyLatch.countDown();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[-] " + name + " disconnected: " + e.getMessage());
        }
    }
}

// -------- QUESTION CLASS --------
class Question {
    String question;
    String[] options;
    String correct;

    public Question(String q, String[] opt, String c) {
        question = q;
        options = opt;
        correct = c;
    }
}

// -------- MAIN SERVER --------
public class QuizServer {

    static List<ClientHandler> clients =
            Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws Exception {

        int port = 5000;
        int maxClients = 2;       // change as needed
        int timeLimit = 15;       // seconds per question

        String keystoreFile = "serverkeystore.jks";
        String keystorePassword = "123456";

        // SSL setup
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(keystoreFile), keystorePassword.toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, keystorePassword.toCharArray());

        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(kmf.getKeyManagers(), null, null);

        SSLServerSocketFactory ssf = sc.getServerSocketFactory();
        SSLServerSocket server = (SSLServerSocket) ssf.createServerSocket(port);

        System.out.println("🔐 Secure Server started on port " + port);
        System.out.println("Waiting for " + maxClients + " player(s)...");

        // Accept exactly maxClients before starting
        while (clients.size() < maxClients) {
            SSLSocket socket = (SSLSocket) server.accept();
            try {
                ClientHandler ch = new ClientHandler(socket);
                clients.add(ch);
                ch.start();
                System.out.println("Players connected: " + clients.size() + "/" + maxClients);
            } catch (Exception e) {
                System.out.println("Bad client, ignoring: " + e.getMessage());
            }
        }

        System.out.println("All players connected. Starting quiz!\n");
        runQuiz(timeLimit);
    }

    // -------- QUIZ LOGIC --------
    static void runQuiz(int timeLimit) throws Exception {

        List<Question> quiz = new ArrayList<>();
        quiz.add(new Question("What is 2+2?",
                new String[]{"A) 3", "B) 4", "C) 5", "D) 6"}, "B"));
        quiz.add(new Question("Capital of India?",
                new String[]{"A) Mumbai", "B) Delhi", "C) Chennai", "D) Kolkata"}, "B"));
        quiz.add(new Question("Which is a programming language?",
                new String[]{"A) HTTP", "B) HTML", "C) Python", "D) URL"}, "C"));
        quiz.add(new Question("5 * 6 = ?",
                new String[]{"A) 30", "B) 25", "C) 20", "D) 35"}, "A"));
        quiz.add(new Question("Binary of 2?",
                new String[]{"A) 10", "B) 01", "C) 11", "D) 00"}, "A"));

        int questionNumber = 0;

        for (Question q : quiz) {
            questionNumber++;
            System.out.println("\n--- Question " + questionNumber + " ---");

            // 1. Create a latch for answers (one per client)
            CountDownLatch answerLatch = new CountDownLatch(clients.size());

            long start = System.currentTimeMillis();

            // 2. Prepare each client and send the question
            synchronized (clients) {
                for (ClientHandler c : clients) {
                    c.resetForQuestion(answerLatch);

                    StringBuilder msg = new StringBuilder();
                    msg.append("QUESTION|")
                       .append(questionNumber).append("|")
                       .append(q.question).append("|");
                    for (String opt : q.options) {
                        msg.append(opt).append(";");
                    }
                    msg.append("|").append(timeLimit);

                    c.send(msg.toString());
                }
            }

            // 3. Wait for all clients to answer OR timeout
            boolean allAnswered = answerLatch.await(timeLimit, TimeUnit.SECONDS);
            System.out.println(allAnswered
                    ? "All players answered."
                    : "Time's up! Not all players answered.");

            // 4. Evaluate answers and send results
            CountDownLatch readyLatch = new CountDownLatch(clients.size());

            synchronized (clients) {
                for (ClientHandler c : clients) {
                    c.resetForReady(readyLatch);

                    String ans = c.getAnswer();
                    long time = c.getAnswerTime();
                    boolean correct = ans.equalsIgnoreCase(q.correct);

                    if (correct) {
                        // Time-based bonus: max 800 bonus + 200 base = 1000
                        long elapsed = time - start;
                        int bonus = (int) Math.max(0, 800 - elapsed);
                        int points = 200 + bonus;
                        c.score += points;
                        System.out.println(c.name + " answered correctly (+)" + points + " pts)");
                        c.send("RESULT|Correct|+" + points + "|Score:" + c.score + "|Correct:" + q.correct);
                    } else {
                        String display = ans.isEmpty() ? "No answer" : ans;
                        System.out.println(c.name + " answered wrong (" + display + ")");
                        c.send("RESULT|Wrong|+0|Score:" + c.score + "|Correct:" + q.correct);
                    }
                }
            }

            // 5. Send leaderboard to everyone
            sendLeaderboard();

            // 6. Wait for all clients to send READY before next question
            System.out.println("Waiting for players to be ready...");
            boolean allReady = readyLatch.await(30, TimeUnit.SECONDS);
            if (!allReady) {
                System.out.println("Warning: not all clients sent READY, proceeding anyway.");
            }

            // Small buffer between questions
            Thread.sleep(500);
        }

        // Announce winner
        synchronized (clients) {
            clients.sort((a, b) -> b.score - a.score);
            ClientHandler winner = clients.get(0);
            System.out.println("\n🏆 Winner: " + winner.name + " with " + winner.score + " pts");

            for (ClientHandler c : clients) {
                c.send("WINNER|" + winner.name + "|" + winner.score);
                c.send("END");
            }
        }
    }

    // -------- LEADERBOARD --------
    static void sendLeaderboard() {
        synchronized (clients) {
            clients.sort((a, b) -> b.score - a.score);

            StringBuilder lb = new StringBuilder("LEADERBOARD|");
            for (ClientHandler c : clients) {
                lb.append(c.name).append(":").append(c.score).append(",");
            }

            String msg = lb.toString();
            for (ClientHandler c : clients) {
                c.send(msg);
            }
        }
    }
}