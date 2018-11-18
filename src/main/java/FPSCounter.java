import java.util.ArrayDeque;

public class FPSCounter {
    private volatile int stampsCount;
    private volatile long sum;
    private volatile ArrayDeque<Long> stamps;

    private volatile long lastTime;
    private volatile float fps;

    public FPSCounter() {
        this(10);
    }

    public FPSCounter(int stampsCount) {
        this.stampsCount = stampsCount;
        this.stamps = new ArrayDeque<>();
        this.lastTime = System.currentTimeMillis();
    }

    public synchronized void countdown() {
        long currentTime = System.currentTimeMillis();
        fps = countFPS(currentTime - lastTime);
        lastTime = currentTime;
    }

    private float countFPS(long nextStamp) {
        sum += nextStamp;
        stamps.push(nextStamp);
        sum -= stamps.size() > stampsCount ? stamps.pollLast() : 0;
        return 1000 * (float)stamps.size() / (float)sum;
    }

    public synchronized float getFPS() {
        return fps;
    }
}
