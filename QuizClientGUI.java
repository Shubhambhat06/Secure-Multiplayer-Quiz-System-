import javax.swing.*;
import java.awt.*;
import java.io.*;
import javax.net.ssl.*;

public class QuizClientGUI {

    private SSLSocket socket;
    private BufferedReader in;
    private PrintWriter out;

    private JFrame frame;
    private JTextArea display;
    private JTextField input;
    private JButton sendBtn;

    public QuizClientGUI() {
        setupGUI();
        connect();
        startListener();
    }

    // ---------------- GUI ----------------
    private void setupGUI() {
        frame = new JFrame("Quiz Client");
        display = new JTextArea();
        display.setEditable(false);

        input = new JTextField();
        sendBtn = new JButton("Send");

        sendBtn.addActionListener(e -> sendAnswer());

        frame.setLayout(new BorderLayout());
        frame.add(new JScrollPane(display), BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(input, BorderLayout.CENTER);
        panel.add(sendBtn, BorderLayout.EAST);

        frame.add(panel, BorderLayout.SOUTH);

        frame.setSize(500, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    // ---------------- CONNECT ----------------
    private void connect() {
        try {
            System.out.println("Connecting...");

            // TRUST ALL CERTS (for testing only)
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());

            SSLSocketFactory factory = sc.getSocketFactory();
            socket = (SSLSocket) factory.createSocket("172.20.10.3", 5000);

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            display.append("✅ Connected to server\n");

            // 🔥 IMPORTANT: SEND NAME IMMEDIATELY
            String name = JOptionPane.showInputDialog(frame, "Enter your name:");
            if (name == null || name.isEmpty()) name = "Player";

            out.println("NAME|" + name);
            display.append("Sent: NAME|" + name + "\n");

        } catch (Exception e) {
            e.printStackTrace();
            display.append("❌ Connection failed\n");
        }
    }

    // ---------------- LISTENER ----------------
    private void startListener() {
        new Thread(() -> {
            try {
                String msg;
                while ((msg = in.readLine()) != null) {

                    display.append("\nSERVER: " + msg + "\n");

                    // OPTIONAL parsing
                    if (msg.startsWith("QUESTION|")) {
                        display.append("👉 Answer using A/B/C/D\n");
                    }

                    if (msg.startsWith("LEADERBOARD|")) {
                        out.println("READY");
                    }
                }
            } catch (Exception e) {
                display.append("⚠ Connection lost\n");
            }
        }).start();
    }

    // ---------------- SEND ANSWER ----------------
    private void sendAnswer() {
        String ans = input.getText().trim().toUpperCase();
        if (ans.matches("[ABCD]")) {
            out.println("ANSWER|" + ans);
            display.append("You: " + ans + "\n");
            input.setText("");
        } else {
            display.append("⚠ Enter A/B/C/D only\n");
        }
    }

    public static void main(String[] args) {
        new QuizClientGUI();
    }
}