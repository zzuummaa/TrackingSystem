import java.util.Scanner;

public class ConsoleControl {
    public static void main(String[] args) {
        Servo servoUD = new Servo(0);
        Servo servoLR = new Servo(1);

        Scanner scanner = new Scanner(System.in);

        String line;
        do {
            line = scanner.nextLine().toLowerCase();
            boolean status = ConsoleUtil.handleCommand(line, servoUD,servoLR);
            System.out.println(status ? "done" : "error");

        } while (!line.equals("exit"));
    }
}
