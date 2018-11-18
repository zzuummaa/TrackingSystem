import java.io.IOException;

public class SocketControl {

    public static void main(String[] args) throws IOException {
        Servo servoUD = new Servo(0);
        Servo servoLR = new Servo(1);

        ServerMachine serverMachine = new ServerMachine(7700, (command) -> {
            if (command.equals("exit")) {
                System.exit(0);
            }

            boolean status = ConsoleUtil.handleCommand(command, servoUD, servoLR);
            System.out.println(status ? "done" : "error");
        });

        while (true) {
            serverMachine.handleState();
        }
    }
}
