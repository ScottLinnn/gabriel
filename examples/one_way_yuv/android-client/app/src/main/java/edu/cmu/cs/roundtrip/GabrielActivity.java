package edu.cmu.cs.roundtrip;

import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;

import java.util.function.Consumer;

import edu.cmu.cs.gabriel.camera.CameraCapture;
import edu.cmu.cs.gabriel.camera.YuvToNv21Converter;
import edu.cmu.cs.gabriel.client.comm.ServerComm;
import edu.cmu.cs.gabriel.client.results.ErrorType;
import edu.cmu.cs.gabriel.protocol.Protos;
import edu.cmu.cs.gabriel.protocol.Protos.InputFrame;
import edu.cmu.cs.gabriel.protocol.Protos.ResultWrapper;
import edu.cmu.cs.gabriel.YUVProtos.ToServer;

public class GabrielActivity extends AppCompatActivity {
    private static final String TAG = "GabrielActivity";
    private static final String SOURCE = "camera_yuv";
    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;
    private static final int PORT = 9099;

    private ServerComm serverComm;
    private YuvToNv21Converter yuvToNv21Converter;
    private CameraCapture cameraCapture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_gabriel);

        PreviewView viewFinder = findViewById(R.id.viewFinder);

        Consumer<ResultWrapper> consumer = resultWrapper -> {
            // Ignore results from server
        };

        Consumer<ErrorType> onDisconnect = errorType -> {
            Log.e(TAG, "Disconnect Error:" + errorType.name());
            finish();
        };

        serverComm = ServerComm.createServerComm(
                consumer, BuildConfig.GABRIEL_HOST, PORT, getApplication(), onDisconnect);

        yuvToNv21Converter = new YuvToNv21Converter();
        cameraCapture = new CameraCapture(this, analyzer, WIDTH, HEIGHT, viewFinder);
    }

    // Based on
    // https://github.com/protocolbuffers/protobuf/blob/master/src/google/protobuf/compiler/java/java_message.cc#L1387
    public static Any pack(ToServer toServer) {
        return Any.newBuilder()
                .setTypeUrl("type.googleapis.com/yuv.ToServer")
                .setValue(toServer.toByteString())
                .build();
    }

    final private ImageAnalysis.Analyzer analyzer = new ImageAnalysis.Analyzer() {
        @Override
        public void analyze(@NonNull ImageProxy image) {
            serverComm.sendSupplier(() -> {
                ByteString nv21ByteString = yuvToNv21Converter.convertToBuffer(image);

                ToServer toServer = ToServer.newBuilder()
                        .setRotation(image.getImageInfo().getRotationDegrees())
                        .setHeight(image.getHeight())
                        .setWidth(image.getWidth())
                        .build();

                return InputFrame.newBuilder()
                        .setPayloadType(Protos.PayloadType.IMAGE)
                        .addPayloads(nv21ByteString)
                        .setExtras(GabrielActivity.pack(toServer))
                        .build();
            }, SOURCE, false);

            image.close();
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraCapture.shutdown();
    }
}
