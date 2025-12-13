import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class QuickPasswordTest {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String dbHash = "$2b$10$5Zng5L.mHicdDHtQr3Tny.3q2DKHgXtHaD..W8noB0veT7o/.Z4Mi";
        String password = "password123";
        System.out.println("Testing password: " + password);
        System.out.println("Against hash: " + dbHash);
        System.out.println("Match result: " + encoder.matches(password, dbHash));
    }
}
