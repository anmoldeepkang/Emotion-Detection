package com.example.emotiondetectionv1;

import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.affectiva.android.affdex.sdk.Frame;
import com.affectiva.android.affdex.sdk.detector.CameraDetector;
import com.affectiva.android.affdex.sdk.detector.Detector;
import com.affectiva.android.affdex.sdk.detector.Face;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class MainActivity extends Activity implements Detector.ImageListener, CameraDetector.CameraEventListener {

    private static final int RC_HANDLE_GMS = 0;
    CameraSource mCamera,mCameraBack;
    CameraPreview mPreviewFront,mPreviewBack;
    CameraDetector cameraDetector,cameraDetectorBack;
    CameraSourcePreview preview;
    FrameLayout frameLayout;
    private static int CAMERA_PERMISSION=1;
    TextView smile,attention,faceDetected,engagement,eyeClosure,eyeWiden,jawDrop,anger,fear,joy,disgust,contempt,sadness,surprise;
    Button btn;
    ImageView switchCamera;
    int previewWidth = 0;
    int previewHeight = 0;
    private boolean FRONT_CAM_ACTIVE=false;
    private ProgressBar progressBar;
    ProgressDialog pd;
    boolean emotionDetectionOn=false;
    DatabaseReference databaseReference= FirebaseDatabase.getInstance().getReference();
    List<Map<String,Object>> objectForFirebase=new ArrayList<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preview=findViewById(R.id.camera_preview);
        btn=findViewById(R.id.startButton);
        progressBar=findViewById(R.id.progressBar);
        switchCamera=findViewById(R.id.switchCamera);
        frameLayout=findViewById(R.id.frameLayout);
        initializeViews();

        // Create an instance of Camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        }
        createCameraSource(CameraSource.CAMERA_FACING_BACK);
        //startCameraSource();
        final SurfaceView sfV = new SurfaceView(this) {
            @Override
            public void onMeasure(int widthSpec, int heightSpec) {
                int measureWidth = MeasureSpec.getSize(widthSpec);
                int measureHeight = MeasureSpec.getSize(heightSpec);
                int width;
                int height;
                if (previewHeight == 0 || previewWidth == 0) {
                    width = measureWidth;
                    height = measureHeight;
                } else {
                    float viewAspectRatio = (float)measureWidth/measureHeight;
                    float cameraPreviewAspectRatio = (float) previewWidth/previewHeight;

                    if (cameraPreviewAspectRatio > viewAspectRatio) {
                        width = measureWidth;
                        height =(int) (measureWidth / cameraPreviewAspectRatio);
                    } else {
                        width = (int) (measureHeight * cameraPreviewAspectRatio);
                        height = measureHeight;
                    }
                }
                setMeasuredDimension(width,height);
            }

        };
        //preview.addView(sfV);
        cameraDetector=new CameraDetector(this, CameraDetector.CameraType.CAMERA_BACK, sfV);
        cameraDetector.setDetectAllExpressions(true);
        cameraDetector.setDetectAllEmotions(true);
        cameraDetector.setImageListener(this);
        cameraDetector.setOnCameraEventListener(this);
        cameraDetectorBack=new CameraDetector(this, CameraDetector.CameraType.CAMERA_FRONT, sfV);
        cameraDetectorBack.setDetectAllExpressions(true);
        cameraDetectorBack.setDetectAllEmotions(true);
        cameraDetectorBack.setImageListener(this);
        cameraDetectorBack.setOnCameraEventListener(this);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            if(btn.getText().equals("Start Analyzing")) {
                btn.setText("Stop Analyzing");
                frameLayout.removeViewAt(0);
                frameLayout.addView(sfV,0);
                emotionDetectionOn=true;
                final AsyncTask asyncTask;
                asyncTask = new AsyncTask<Object,Void,Void>() {


                    @Override
                    protected void onPostExecute(Void aVoid) {
                        pd.dismiss();
                        Toast.makeText(getApplicationContext(),"Started",Toast.LENGTH_LONG).show();
                    }


                    @Override
                    protected Void doInBackground(Object... voids) {
                        if(!FRONT_CAM_ACTIVE && !cameraDetector.isRunning()) {
                            cameraDetector.start();
                        } else if(FRONT_CAM_ACTIVE && !cameraDetectorBack.isRunning()) {
                            cameraDetectorBack.start();
                        }
                        return null;
                    };

                    @Override
                    protected void onPreExecute() {
                        pd=ProgressDialog.show(MainActivity.this, "", "Please Wait...",
                                true, false);

                    }
                };
                asyncTask.execute();
            } else if(btn.getText().equals("Stop Analyzing")) {
                btn.setText("Start Analyzing");
                resetMetrics();
                int cameraType;
                if(cameraDetector.isRunning() || cameraDetectorBack.isRunning()) {
                    frameLayout.removeViewAt(0);
                    frameLayout.addView(preview,0);
                }
                if(cameraDetector.isRunning()) {
                    cameraDetector.stop();
                    Toast.makeText(getApplicationContext(),"Stopped analyzing.",Toast.LENGTH_LONG).show();
                }
                if(cameraDetectorBack.isRunning()) {
                    cameraDetectorBack.stop();
                    Toast.makeText(getApplicationContext(),"Stopped analyzing.",Toast.LENGTH_LONG).show();
                }
                if (FRONT_CAM_ACTIVE) {
                    cameraType = CameraSource.CAMERA_FACING_FRONT;
                    FRONT_CAM_ACTIVE=true;
                } else {
                    FRONT_CAM_ACTIVE=false;
                    cameraType = CameraSource.CAMERA_FACING_BACK;
                }
                if(mCamera!=null)
                    mCamera.stop();
                createCameraSource(cameraType);
                startCameraSource();
            }


            }
        });
        switchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int cameraType;
                resetMetrics();
                if(cameraDetector.isRunning() || cameraDetectorBack.isRunning()) {
                    frameLayout.removeViewAt(0);
                    frameLayout.addView(preview,0);
                }
                if(cameraDetector.isRunning()) {
                    cameraDetector.stop();
                    Toast.makeText(getApplicationContext(),"Stopped analyzing.",Toast.LENGTH_LONG).show();
                }
                if(cameraDetectorBack.isRunning()) {
                    cameraDetectorBack.stop();
                    Toast.makeText(getApplicationContext(),"Stopped analyzing.",Toast.LENGTH_LONG).show();
                }
                if (!FRONT_CAM_ACTIVE) {
                    cameraType = CameraSource.CAMERA_FACING_FRONT;
                    FRONT_CAM_ACTIVE=true;
                } else {
                    FRONT_CAM_ACTIVE=false;
                    cameraType = CameraSource.CAMERA_FACING_BACK;
                }
                if(mCamera!=null)
                    mCamera.stop();
                createCameraSource(cameraType);
                startCameraSource();

            }
        });

    }

    private void resetMetrics() {
        if(!objectForFirebase.isEmpty()) {
            for(Map<String,Object> entry:objectForFirebase) {
                databaseReference.push().setValue(entry);
            }
            objectForFirebase.clear();
        }
        faceDetected.setText("No");
        smile.setText("");
        attention.setText("");
        eyeClosure.setText("");
        eyeWiden.setText("");
        jawDrop.setText("");
        anger.setText("");
        fear.setText("");
        joy.setText("");
        disgust.setText("");
        contempt.setText("");
        sadness.setText("");
        surprise.setText("");
        engagement.setText("");
        //glasses.setText("");
        //age.setText("");
        //ethnicity.setText("");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if ( pd!=null && pd.isShowing() ){
            pd.cancel();
        }
    }

    private void startCameraSource() {
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCamera != null) {
            try {

                preview.start(mCamera);
            } catch (IOException e) {
                //Log.e(TAG, "Unable to start camera source.", e);
                mCamera.release();
                mCamera = null;
            }
        }
    }

    private void initializeViews() {
        smile=findViewById(R.id.smile);
        attention=findViewById(R.id.attention);
        engagement=findViewById(R.id.engagement);
        //age=findViewById(R.id.age);
        //glasses=findViewById(R.id.glasses);
        //ethnicity=findViewById(R.id.ethnicity);
        eyeClosure=findViewById(R.id.eyeClosure);
        eyeWiden=findViewById(R.id.eyeWiden);
        jawDrop=findViewById(R.id.jawDrop);
        faceDetected=findViewById(R.id.faceDetected);
        anger=findViewById(R.id.anger);fear=findViewById(R.id.fear);joy=findViewById(R.id.joy);disgust=findViewById(R.id.disgust);contempt=findViewById(R.id.contempt);sadness=findViewById(R.id.sadness);surprise=findViewById(R.id.surprise);
    }

    @Override
    protected void onResume() {
        super.onResume();

        startCameraSource();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Cannot run application because camera service permission have not been granted", Toast.LENGTH_SHORT).show();
                }
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            break;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(cameraDetector.isRunning()) {
            cameraDetector.stop();
        }
        releaseCamera();              // release the camera immediately on pause event
    }



    private void releaseCamera(){
        if (mCamera != null){
            if(cameraDetector.isRunning())
                cameraDetector.stop();
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }



    @Override
    public void onCameraSizeSelected(int i, int i1, Frame.ROTATE rotate) {

        //mPreview.requestLayout();
    }

    @Override
    public void onImageResults(List<Face> list, Frame frame, float v) {
        if (list == null) {
            return;
        }
        if (list.size() == 0) {
            resetMetrics();
        } else {
            faceDetected.setText("Yes");
            Map<String,Object> temp=new LinkedHashMap<>();
            Face face = list.get(0);
            temp.put("Smile",face.expressions.getSmile());
            temp.put("Attention",face.expressions.getAttention());
            temp.put("Eye closure",face.expressions.getEyeClosure());
            temp.put("Eye widen",face.expressions.getEyeWiden());
            temp.put("Jaw drop",face.expressions.getJawDrop());
            temp.put("Anger",face.emotions.getAnger());
            temp.put("Fear",face.emotions.getFear());
            temp.put("Joy",face.emotions.getJoy());
            temp.put("Disgust",face.emotions.getDisgust());
            temp.put("Contempt",face.emotions.getContempt());
            temp.put("Sadness",face.emotions.getSadness());
            temp.put("Surprise",face.emotions.getSurprise());
            temp.put("Engagement",face.emotions.getEngagement());
            temp.put("createdAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(new Date()));
            objectForFirebase.add(temp);
            smile.setText(String.valueOf(face.expressions.getSmile()));
            attention.setText(String.valueOf(face.expressions.getAttention()));
            eyeClosure.setText(String.valueOf(face.expressions.getEyeClosure()));
            eyeWiden.setText(String.valueOf(face.expressions.getEyeWiden()));
            jawDrop.setText(String.valueOf(face.expressions.getJawDrop()));

            anger.setText(String.valueOf(face.emotions.getAnger()));
            fear.setText(String.valueOf(face.emotions.getFear()));
            joy.setText(String.valueOf(face.emotions.getJoy()));
            disgust.setText(String.valueOf(face.emotions.getDisgust()));
            contempt.setText(String.valueOf(face.emotions.getContempt()));
            sadness.setText(String.valueOf(face.emotions.getSadness()));
            surprise.setText(String.valueOf(face.emotions.getSurprise()));
            engagement.setText(String.valueOf(face.emotions.getEngagement()));
            /*if(face.appearance.getGlasses().toString().equals("NO")) {
                glasses.setText("No");
            } else {
                glasses.setText("Yes");
            }*/
            //Face.AGE ag=face.appearance.getAge();
            //age.setText(face.appearance.getAge().toString());
            //ethnicity.setText(face.appearance.getEthnicity().toString());
        }
    }
    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private void createCameraSource(int cameraType) {

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setProminentFaceOnly(true)
                .build();

        detector.setProcessor(new com.google.android.gms.vision.Detector.Processor<com.google.android.gms.vision.face.Face>() {
            @Override
            public void release() {

            }

            @Override
            public void receiveDetections(com.google.android.gms.vision.Detector.Detections<com.google.android.gms.vision.face.Face> detections) {

            }
        });



        mCamera = new CameraSource.Builder(context, detector)
                .setRequestedPreviewSize(640, 480)
                .setFacing(cameraType)
                .setRequestedFps(30.0f).setAutoFocusEnabled(true).setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)
                .build();

    }

}


