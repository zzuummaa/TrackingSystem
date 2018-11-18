import io.reactivex.Observable;
import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import ru.zuma.rx.RxAsyncVideoConsumer;
import ru.zuma.rx.RxClassifier;
import ru.zuma.rx.RxVideoSource2;
import ru.zuma.utils.ConsoleUtil;
import ru.zuma.utils.ImageMarker;
import ru.zuma.utils.Pair;
import ru.zuma.utils.ResourceLoader;
import ru.zuma.video.VideoConsumer;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static org.bytedeco.javacpp.avformat.av_register_all;
import static org.bytedeco.javacpp.opencv_core.*;

public class TrackingSystem {
    private Servo servoUD;
    private Servo servoLR;
    private float fieldOfView;

    //
    // Threshold of correcting angles as part of rect size
    private float sensitivity;

    public TrackingSystem() {
        this(70);
    }

    public TrackingSystem(int fieldOfViewDeg) {
        this.servoLR = new Servo(1);
        this.servoUD = new Servo(0);
        this.fieldOfView = fieldOfViewDeg / 180f * (float)Math.PI;
        this.sensitivity = 0.2f;
    }

    public static void main(String[] args) {
        try {
            av_register_all();
            run(args);
            Thread.sleep(2000);
            avutil.av_log_set_level(avutil.AV_LOG_ERROR);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected static void run(String[] args) throws IOException {
        TrackingSystem trackingSystem = new TrackingSystem();


        ResourceLoader.configureFromClass(TrackingSystem.class);
        RxVideoSource2 videoSource = ConsoleUtil.createVideoSource(args);
        RxClassifier classifier = ConsoleUtil.createClassifier();
        RxAsyncVideoConsumer videoConsumer = createVideoConsumer(args);
        Observable<Pair<Mat, RectVector>> classifierPairObserver;

        videoSource
                .throttleFirst(200, TimeUnit.MILLISECONDS)
                .subscribe(classifier);

        FPSCounter cameraFPS = new FPSCounter(20);
        FPSCounter recorderFPS = new FPSCounter(20);
        videoSource.subscribe(mat -> {
            cameraFPS.countdown();
        });

        videoSource
                .throttleFirst(1500, TimeUnit.MILLISECONDS)
                .subscribe(mat -> {
                    System.out.println("Camera FPS: " + cameraFPS.getFPS() + ", Recorder FPS: " + recorderFPS.getFPS());
                });

        classifierPairObserver = Observable.combineLatest(
                videoSource, classifier,
                Pair::new
        );

        // Control servos
        classifierPairObserver
                .throttleFirst(200, TimeUnit.MILLISECONDS)
                .subscribe(pair -> {
                    if (pair.second().empty()) return;

                    Rect largerRect = pair.second().get(0);
                    for (int i = 0; i < pair.second().size(); i++) {
                        Rect curRect = pair.second().get(i);
                        if (largerRect.width() + largerRect.height() < curRect.width() + curRect.height()) {
                            largerRect = curRect;
                        }
                    }
                    trackingSystem.rotateToCenter(pair.first(), largerRect);
                });

        // Record stream
        Mat markedImage = new Mat();
        classifierPairObserver
                .subscribe(pair -> {
                    if (videoConsumer != null && videoConsumer.isReadyForNext()) {
                        pair.first().copyTo(markedImage);
                        ImageMarker.markRects(markedImage, pair.second());
                        videoConsumer.onNext(markedImage);
                        recorderFPS.countdown();
                    }
                });
    }

    protected static RxAsyncVideoConsumer createVideoConsumer(String[] args) {
        VideoConsumer consumer;
        FFmpegFrameRecorder recorder;

        String videoConsumerURL = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-o")) {
                videoConsumerURL = i+1 < args.length ? args[i+1] : null;
                break;
            }
        }

        System.out.println("Starting video consumer...");
        if (videoConsumerURL != null) {
            recorder = new FFmpegFrameRecorder(videoConsumerURL, 640, 480, 0);
        } else {
            recorder = new FFmpegFrameRecorder("http://127.0.0.1:8090/camera.ffm", 640, 480, 0);
        }

        recorder.setOption("protocol_whitelist", "file,http,tcp");
//        recorder.setVideoBitrate(2 * 1024 * 1024);
        recorder.setFrameRate(15);
//        recorder.setInterleaved(true);
        recorder.setVideoBitrate(4*1024*1024);
        recorder.setPixelFormat(avutil.AV_PIX_FMT_YUVJ420P);
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_MJPEG);
//        recorder.setFrameRate(30);

        consumer = new VideoConsumer(recorder);

        if (consumer.isOpened()) {
            System.out.println("Video consumer successfully started!");
            return new RxAsyncVideoConsumer(consumer);
        } else {
            System.out.println("Video consumer starting failed. Please launch ffserver with feed http://localhost:8090/camera.ffm");
            consumer.release();
            return null;
        }
    }

    public void rotateToCenter(Mat img, Rect rect) {
        // Calculate center of tracked rect
        float xCenter = rect.x() + rect.width() / 2f;
        float yCenter = rect.y() + rect.height() / 2f;

        // Calculate dif between center of rect and image
        float dx = xCenter - img.cols() / 2f;
        float dy = yCenter - img.rows() / 2f;

        if (Math.abs(dx) < rect.width() * sensitivity &
            Math.abs(dy) < rect.height() * sensitivity) return;

        // Calculate angular diff
        float azim = dx / img.cols() * fieldOfView;
        float elev = dy / img.rows() * fieldOfView;

        servoLR.rotate(-azim);
        servoUD.rotate(elev);
    }
}
