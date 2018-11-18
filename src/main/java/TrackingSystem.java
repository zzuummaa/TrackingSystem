import io.reactivex.Observable;
import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import ru.zuma.rx.RxClassifier;
import ru.zuma.rx.RxVideoConsumer;
import ru.zuma.rx.RxVideoSource2;
import ru.zuma.utils.ConsoleUtil;
import ru.zuma.utils.Pair;
import ru.zuma.utils.ResourceLoader;
import ru.zuma.video.VideoConsumer;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static org.bytedeco.javacpp.avformat.av_register_all;

public class TrackingSystem {

    public static void main(String[] args) {
        try {
//            avutil.av_log_set_level(avutil.AV_LOG_ERROR);
            av_register_all();
            run(args);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    protected static void run(String[] args) throws IOException {
        try {
            ResourceLoader.configureFromClass(TrackingSystem.class);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        RxVideoSource2 videoSource = ConsoleUtil.createVideoSource(args);
        RxClassifier classifier = ConsoleUtil.createClassifier();
        RxVideoConsumer videoConsumer = createVideoConsumer(args);
        Observable<Pair<opencv_core.Mat, opencv_core.RectVector>> classifierPairObserver;

        videoSource
                .throttleFirst(2000, TimeUnit.MILLISECONDS)
                .subscribe(classifier);

        FPSCounter counter = new FPSCounter();
        videoSource.subscribe(mat -> {
            counter.countdown();
        });

        videoSource
                .throttleFirst(1000, TimeUnit.MILLISECONDS)
                .subscribe(mat -> {
                    System.out.println("FPS:" + counter.getFPS());
                });

        classifierPairObserver = Observable.combineLatest(
                videoSource, classifier,
                Pair::new
        );
        classifierPairObserver
                .map((pair) -> pair.first())
                .subscribe(videoConsumer);
    }

    protected static RxVideoConsumer createVideoConsumer(String[] args) {
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
        recorder.setVideoBitrate(10*1024*1024);
        recorder.setPixelFormat(avutil.AV_PIX_FMT_YUVJ422P);
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_MJPEG);
//        recorder.setFrameRate(30);

        consumer = new VideoConsumer(recorder);

        if (consumer.isOpened()) {
            System.out.println("Video consumer successfully started!");
            return new RxVideoConsumer(consumer);
        } else {
            System.out.println("Video consumer starting failed. Please launch ffserver with feed http://localhost:8090/camera.ffm");
            consumer.release();
            return null;
        }
    }
}
