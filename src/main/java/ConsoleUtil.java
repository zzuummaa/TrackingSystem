public class ConsoleUtil {
    public static boolean handleCommand(String command, Servo servoUD, Servo servoLR) {
        String[] lineArgs = command.split(" ");
        if (lineArgs.length < 2) return false;

        float angle;
        try {
            angle = Float.parseFloat(lineArgs[1]) / 180 * Servo.PI;
        } catch (NumberFormatException e) {
            return false;
        }

        boolean status;

        switch (lineArgs[0]) {
            case "up": status = servoUD.rotate(-angle); break;
            case "down": status = servoUD.rotate(angle); break;
            case "left": status = servoLR.rotate(angle); break;
            case "right": status = servoLR.rotate(-angle); break;
            default: status = false;
        }

        return status;
    }
}
