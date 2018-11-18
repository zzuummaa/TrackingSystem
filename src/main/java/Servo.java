import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Servo {
    public static final float PI = (float)3.141592;

    private final int servoNum;
    private final Path devPath;
    private boolean isAccessible;
    private float angle;
    private float delta;
    private float resolution;

    public Servo(int servoNum) {
        synchronized (this) {
            this.servoNum = servoNum;
            resolution = PI / 90;
            angle = PI / 2;

            devPath = Paths.get("/dev/servoblaster");
            isAccessible = Files.isWritable(devPath);
            if (isAccessible) {
                isAccessible = setAngle(angle);
            }
        }
    }

    public synchronized boolean isAccessible() {
        return isAccessible;
    }

    public synchronized boolean rotate(float dAngle) {
        delta += dAngle;
        if (Math.abs(delta) > resolution) {
            angle += delta;
            delta = 0;
            return setAngle(angle);
        } else {
            return true;
        }
    }

    public synchronized boolean setAngle(float angle) {
        angle = angle > PI ? PI : angle;
        angle = angle < 0 ? 0 : angle;

        int percentage = (int)(angle / PI * 100);
        String command = servoNum + "=" + percentage + "%";
        System.out.println(command);
        try {
            Files.write(devPath, (command + "\n").getBytes());
            this.angle = angle;
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public synchronized float getAngle() {
        return angle;
    }
}
