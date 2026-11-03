import os, sys
OUT = sys.argv[1] if len(sys.argv) > 1 else 'jstub'
S={}
def c(fq, body, kind="class", ext=""):
    S[fq]=(kind,body,ext)
# ---- android core
c("android.Manifest","public static final class permission { public static final String CAMERA=\"c\"; public static final String RECORD_AUDIO=\"a\"; }")
c("android.content.Context","""public static final String CAMERA_SERVICE="camera"; public static final String CLIPBOARD_SERVICE="clip"; public static final int MODE_PRIVATE=0;
public Object getSystemService(String n){return null;} public ContentResolver getContentResolver(){return null;}
public android.content.res.Resources getResources(){return null;} public Context getApplicationContext(){return this;}
public SharedPreferences getSharedPreferences(String n,int m){return null;} public java.util.concurrent.Executor getMainExecutor(){return null;}""", "abstract class")
c("android.content.SharedPreferences","""int getInt(String k,int d); boolean getBoolean(String k,boolean d); Editor edit();
interface Editor { Editor putInt(String k,int v); Editor putBoolean(String k,boolean v); void apply(); }""","interface")
c("android.content.res.Resources","public android.util.DisplayMetrics getDisplayMetrics(){return null;}")
c("android.content.res.ColorStateList","public static ColorStateList valueOf(int c){return null;}")
c("android.util.DisplayMetrics","public float density;")
c("android.util.Range","public Range(T a,T b){} public T getLower(){return null;} public T getUpper(){return null;}","class","<T extends Comparable<? super T>>")
c("android.util.Rational","public float floatValue(){return 0;} public int intValue(){return 0;} public long longValue(){return 0;} public double doubleValue(){return 0;}","class"," extends Number")
c("android.util.Size","public int getWidth(){return 0;} public int getHeight(){return 0;}")
c("android.content.ContentResolver","""public android.net.Uri insert(android.net.Uri u, ContentValues v){return null;}
public int update(android.net.Uri u, ContentValues v, String w, String[] a){return 0;}
public int delete(android.net.Uri u, String w, String[] a){return 0;}
public java.io.OutputStream openOutputStream(android.net.Uri u) throws java.io.FileNotFoundException {return null;}
public String getType(android.net.Uri u){return null;}""")
c("android.content.ContentValues","public void put(String k,String v){} public void put(String k,Integer v){} public void put(String k,Long v){}")
c("android.content.ClipData","public static ClipData newPlainText(CharSequence l, CharSequence t){return null;}")
c("android.content.ClipboardManager","public void setPrimaryClip(ClipData c){}")
c("android.content.Intent","""public static final String ACTION_VIEW="v"; public static final int FLAG_GRANT_READ_URI_PERMISSION=1;
public Intent(String a){} public Intent setDataAndType(android.net.Uri u,String t){return this;} public Intent addFlags(int f){return this;}""")
c("android.content.pm.PackageManager","public static final int PERMISSION_GRANTED=0;")
c("android.net.Uri","public static final Uri EMPTY=null;","abstract class")
c("android.os.Bundle","public String getString(String k){return null;} public boolean getBoolean(String k,boolean d){return d;} public int getInt(String k,int d){return d;} public void putString(String k,String v){} public void putBoolean(String k,boolean v){} public void putInt(String k,int v){}")
c("android.os.Handler","public Handler(Looper l){} public boolean postDelayed(Runnable r,long d){return true;} public void removeCallbacks(Runnable r){} public void removeCallbacksAndMessages(Object o){}")
c("android.os.Looper","public static Looper getMainLooper(){return null;}")
c("android.os.Build","public static final String MANUFACTURER=\"\"; public static final String MODEL=\"\"; public static final class VERSION { public static final String RELEASE=\"\"; public static final int SDK_INT=36; }")
c("android.os.Environment","public static final String DIRECTORY_DCIM=\"DCIM\"; public static final String DIRECTORY_MOVIES=\"Movies\";")
c("android.provider.MediaStore","""public interface MediaColumns { String DISPLAY_NAME="d"; String MIME_TYPE="m"; String RELATIVE_PATH="r"; String IS_PENDING="p"; }
public static final class Images { public static final class Media { public static final android.net.Uri EXTERNAL_CONTENT_URI=null; public static final String DATE_TAKEN="t"; } }
public static final class Video { public static final class Media { public static final android.net.Uri EXTERNAL_CONTENT_URI=null; } }""","final class")
c("android.graphics.Color","public static final int WHITE=-1; public static final int BLACK=0xff000000; public static int argb(int a,int r,int g,int b){return 0;}")
c("android.graphics.Typeface","public static final int BOLD=1;")
c("android.graphics.ImageFormat","public static final int JPEG=256; public static final int RAW_SENSOR=32;")
c("android.graphics.Matrix","public boolean postScale(float a,float b){return true;} public boolean postRotate(float d){return true;}")
c("android.graphics.Canvas","public void drawLine(float a,float b,float c,float d, Paint p){}")
c("android.graphics.Paint","public static final int ANTI_ALIAS_FLAG=1; public Paint(int f){} public void setColor(int c){} public int getColor(){return 0;} public void setStrokeWidth(float w){} public float getStrokeWidth(){return 0;}")
c("android.graphics.Bitmap","""public enum Config { ARGB_8888 } public enum CompressFormat { JPEG }
public int getWidth(){return 0;} public int getHeight(){return 0;} public void recycle(){}
public void getPixels(int[] p,int o,int s,int x,int y,int w,int h){}
public boolean compress(CompressFormat f,int q, java.io.OutputStream s){return true;}
public static Bitmap createBitmap(Bitmap s,int x,int y,int w,int h, Matrix m, boolean f){return null;}
public static Bitmap createBitmap(int[] c,int w,int h, Config cfg){return null;}""")
c("android.graphics.BitmapFactory","""public static class Options { public boolean inJustDecodeBounds; public int outWidth; public int outHeight; public int inSampleSize; public Bitmap.Config inPreferredConfig; }
public static Bitmap decodeByteArray(byte[] d,int o,int l, Options op){return null;}""")
c("android.hardware.camera2.CameraCharacteristics","""public static final class Key<T> {}
public <T> T get(Key<T> k){return null;}
public static final Key<Integer> LENS_FACING=null; public static final int LENS_FACING_FRONT=0;
public static final Key<float[]> LENS_INFO_AVAILABLE_FOCAL_LENGTHS=null;
public static final Key<int[]> REQUEST_AVAILABLE_CAPABILITIES=null; public static final int REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR=1; public static final int REQUEST_AVAILABLE_CAPABILITIES_RAW=3;
public static final Key<android.util.Range<Integer>> SENSOR_INFO_SENSITIVITY_RANGE=null;
public static final Key<android.util.Range<Long>> SENSOR_INFO_EXPOSURE_TIME_RANGE=null;
public static final Key<Integer> INFO_SUPPORTED_HARDWARE_LEVEL=null;
public static final Key<Boolean> FLASH_INFO_AVAILABLE=null;
public static final Key<int[]> LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION=null;
public static final Key<android.hardware.camera2.params.StreamConfigurationMap> SCALER_STREAM_CONFIGURATION_MAP=null;""")
c("android.hardware.camera2.params.StreamConfigurationMap","public android.util.Size[] getOutputSizes(int f){return null;}")
c("android.hardware.camera2.CameraManager","public CameraCharacteristics getCameraCharacteristics(String id) throws CameraAccessException {return null;}")
c("android.hardware.camera2.CameraAccessException","","class"," extends Exception")
c("android.hardware.camera2.CaptureRequest","""public static final class Key<T> {}
public static final Key<Integer> CONTROL_AE_MODE=null; public static final int CONTROL_AE_MODE_OFF=0;
public static final Key<Integer> SENSOR_SENSITIVITY=null; public static final Key<Long> SENSOR_EXPOSURE_TIME=null;""")
# views
c("android.view.View","""public static final int VISIBLE=0; public static final int GONE=8;
public View(android.content.Context c){} public android.content.res.Resources getResources(){return null;}
public void setVisibility(int v){} public int getVisibility(){return 0;} public void setEnabled(boolean e){} public boolean isEnabled(){return true;}
public void setAlpha(float a){} public float getAlpha(){return 1;} public void setBackgroundColor(int c){}
public void setPadding(int a,int b,int c,int d){} public void setOnClickListener(OnClickListener l){} public void setOnTouchListener(OnTouchListener l){}
public void setClickable(boolean b){} public boolean isClickable(){return false;} public void setFocusable(boolean b){} public boolean isFocusable(){return false;}
public void invalidate(){} public int getWidth(){return 0;} public int getHeight(){return 0;} protected void onDraw(android.graphics.Canvas c){}
public void setBackgroundTintList(android.content.res.ColorStateList t){} public android.content.res.ColorStateList getBackgroundTintList(){return null;}
public interface OnClickListener { void onClick(View v); }
public interface OnTouchListener { boolean onTouch(View v, MotionEvent e); }""")
c("android.view.ViewGroup","""public ViewGroup(android.content.Context c){super(c);} public void addView(View v){} public void addView(View v, LayoutParams p){} public void removeAllViews(){}
public static class LayoutParams { public LayoutParams(int w,int h){} }""","abstract class"," extends View")
c("android.view.Gravity","public static final int CENTER_VERTICAL=16; public static final int TOP=48;")
c("android.view.MotionEvent","public static final int ACTION_UP=1; public int getAction(){return 0;} public float getX(){return 0;} public float getY(){return 0;}")
c("android.view.OrientationEventListener","public OrientationEventListener(android.content.Context c){} public abstract void onOrientationChanged(int o); public boolean canDetectOrientation(){return true;} public void enable(){} public void disable(){}","abstract class")
c("android.view.Window","")
c("android.widget.TextView","""public TextView(android.content.Context c){super(c);} public void setText(CharSequence t){} public CharSequence getText(){return null;}
public void setTextSize(float s){} public float getTextSize(){return 0;} public void setTextColor(int c){} public void setGravity(int g){} public int getGravity(){return 0;}
public void setIncludeFontPadding(boolean b){} public boolean getIncludeFontPadding(){return true;} public void setTypeface(android.graphics.Typeface t,int s){}
public void setLetterSpacing(float f){} public float getLetterSpacing(){return 0;} public void setAllCaps(boolean b){} public boolean isAllCaps(){return false;}
public void setMinHeight(int h){} public int getMinHeight(){return 0;} public void setMinWidth(int h){} public int getMinWidth(){return 0;}
public void setMinimumHeight(int h){} public int getMinimumHeight(){return 0;} public void setMinimumWidth(int h){} public int getMinimumWidth(){return 0;}""","class"," extends android.view.View")
c("android.widget.Button","public Button(android.content.Context c){super(c);}","class"," extends TextView")
c("android.widget.LinearLayout","""public static final int VERTICAL=1; public static final int HORIZONTAL=0; public LinearLayout(android.content.Context c){super(c);}
public void setOrientation(int o){} public int getOrientation(){return 0;} public void setGravity(int g){} public int getGravity(){return 0;}
public static class LayoutParams extends android.view.ViewGroup.LayoutParams { public LayoutParams(int w,int h){super(w,h);} public LayoutParams(int w,int h,float wt){super(w,h);} }""","class"," extends android.view.ViewGroup")
c("android.widget.FrameLayout","""public FrameLayout(android.content.Context c){super(c);}
public static class LayoutParams extends android.view.ViewGroup.LayoutParams { public LayoutParams(int w,int h){super(w,h);} public LayoutParams(int w,int h,int g){super(w,h);} }""","class"," extends android.view.ViewGroup")
c("android.widget.HorizontalScrollView","public HorizontalScrollView(android.content.Context c){super(c);} public void setHorizontalScrollBarEnabled(boolean b){} public boolean isHorizontalScrollBarEnabled(){return true;}","class"," extends FrameLayout")
c("android.widget.SeekBar","""public SeekBar(android.content.Context c){super(c);} public void setMax(int m){} public int getMax(){return 0;} public void setProgress(int p){} public int getProgress(){return 0;}
public void setProgressTintList(android.content.res.ColorStateList t){} public android.content.res.ColorStateList getProgressTintList(){return null;}
public void setThumbTintList(android.content.res.ColorStateList t){} public android.content.res.ColorStateList getThumbTintList(){return null;}
public void setOnSeekBarChangeListener(OnSeekBarChangeListener l){}
public interface OnSeekBarChangeListener { void onProgressChanged(SeekBar s,int p,boolean u); void onStartTrackingTouch(SeekBar s); void onStopTrackingTouch(SeekBar s); }""","class"," extends android.view.View")
c("android.widget.Toast","public static final int LENGTH_SHORT=0; public static Toast makeText(android.content.Context c, CharSequence t,int d){return null;} public void show(){}")
c("android.app.Activity","""public android.view.Window getWindow(){return null;} public boolean isFinishing(){return false;} public boolean isDestroyed(){return false;}
public void setContentView(android.view.View v){} public void startActivity(android.content.Intent i){}
protected void onCreate(android.os.Bundle b){} protected void onStart(){} protected void onResume(){} protected void onStop(){} protected void onDestroy(){} protected void onSaveInstanceState(android.os.Bundle b){}""","class"," extends android.content.Context")
c("android.app.AlertDialog","""public static class Builder { public Builder(android.content.Context c){}
public Builder setTitle(CharSequence t){return this;} public Builder setMessage(CharSequence t){return this;} public Builder setView(android.view.View v){return this;}
public Builder setItems(CharSequence[] i, android.content.DialogInterface.OnClickListener l){return this;}
public Builder setPositiveButton(CharSequence t, android.content.DialogInterface.OnClickListener l){return this;}
public Builder setNegativeButton(CharSequence t, android.content.DialogInterface.OnClickListener l){return this;}
public Builder setNeutralButton(CharSequence t, android.content.DialogInterface.OnClickListener l){return this;}
public AlertDialog show(){return null;} }""")
c("android.content.DialogInterface","interface OnClickListener { void onClick(DialogInterface d,int w); }","interface")
# androidx misc
c("androidx.lifecycle.LifecycleOwner","","interface")
c("androidx.activity.ComponentActivity","""public final <I,O> androidx.activity.result.ActivityResultLauncher<I> registerForActivityResult(androidx.activity.result.contract.ActivityResultContract<I,O> c, androidx.activity.result.ActivityResultCallback<O> cb){return null;}""","class"," extends android.app.Activity implements androidx.lifecycle.LifecycleOwner")
c("androidx.activity.EdgeToEdge","public static void enable(ComponentActivity a){}")
c("androidx.activity.result.ActivityResultLauncher","public abstract void launch(I input);","abstract class","<I>")
c("androidx.activity.result.ActivityResultCallback","void onActivityResult(O o);","interface","<O>")
c("androidx.activity.result.contract.ActivityResultContract","","abstract class","<I,O>")
c("androidx.activity.result.contract.ActivityResultContracts","public static final class RequestPermission extends ActivityResultContract<String, Boolean> {}")
c("androidx.core.content.ContextCompat","public static int checkSelfPermission(android.content.Context c,String p){return 0;} public static java.util.concurrent.Executor getMainExecutor(android.content.Context c){return null;}")
c("androidx.core.view.ViewCompat","public static void setOnApplyWindowInsetsListener(android.view.View v, androidx.core.view.OnApplyWindowInsetsListener l){}")
c("androidx.core.view.OnApplyWindowInsetsListener","WindowInsetsCompat onApplyWindowInsets(android.view.View v, WindowInsetsCompat i);","interface")
c("androidx.core.view.WindowInsetsCompat","""public androidx.core.graphics.Insets getInsets(int t){return null;}
public static final class Type { public static int systemBars(){return 0;} }""")
c("androidx.core.graphics.Insets","public final int left=0, top=0, right=0, bottom=0;")
c("com.google.common.util.concurrent.ListenableFuture","void addListener(Runnable r, java.util.concurrent.Executor e);","interface","<V> extends java.util.concurrent.Future<V>")
# camerax core
c("androidx.camera.core.Camera","CameraControl getCameraControl(); CameraInfo getCameraInfo();","interface")
c("androidx.camera.core.CameraControl","""com.google.common.util.concurrent.ListenableFuture<Void> setLinearZoom(float z);
com.google.common.util.concurrent.ListenableFuture<Void> enableTorch(boolean t);
com.google.common.util.concurrent.ListenableFuture<Integer> setExposureCompensationIndex(int i);
com.google.common.util.concurrent.ListenableFuture<Object> startFocusAndMetering(FocusMeteringAction a);""","interface")
c("androidx.camera.core.CameraInfo","boolean hasFlashUnit(); ExposureState getExposureState();","interface")
c("androidx.camera.core.ExposureState","int getExposureCompensationIndex(); android.util.Range<Integer> getExposureCompensationRange(); android.util.Rational getExposureCompensationStep(); boolean isExposureCompensationSupported();","interface")
c("androidx.camera.core.CameraSelector","""public static final CameraSelector DEFAULT_BACK_CAMERA=null;
public static final class Builder { public Builder addCameraFilter(CameraFilter f){return this;} public CameraSelector build(){return null;} }""","final class")
c("androidx.camera.core.CameraFilter","java.util.List<CameraInfo> filter(java.util.List<CameraInfo> infos);","interface")
c("androidx.camera.core.DynamicRange","public static final DynamicRange SDR=null;","final class")
c("androidx.camera.core.MeteringPoint","")
c("androidx.camera.core.MeteringPointFactory","public final MeteringPoint createPoint(float x,float y){return null;}","abstract class")
c("androidx.camera.core.FocusMeteringAction","public static class Builder { public Builder(MeteringPoint p){} public Builder setAutoCancelDuration(long d, java.util.concurrent.TimeUnit u){return this;} public FocusMeteringAction build(){return null;} }")
c("androidx.camera.core.UseCase","","abstract class")
c("androidx.camera.core.Preview","""public void setSurfaceProvider(SurfaceProvider p){}
public interface SurfaceProvider {}
public static final class Builder { public Preview build(){return null;} }""","final class"," extends UseCase")
c("androidx.camera.core.ImageCaptureException","public ImageCaptureException(){}","class"," extends Exception")
c("androidx.camera.core.ImageInfo","int getRotationDegrees();","interface")
c("androidx.camera.core.ImageProxy","""void close(); int getFormat(); PlaneProxy[] getPlanes(); ImageInfo getImageInfo();
interface PlaneProxy { java.nio.ByteBuffer getBuffer(); }""","interface"," extends AutoCloseable")
c("androidx.camera.core.ImageCaptureCapabilities","java.util.Set<Integer> getSupportedOutputFormats();","interface")
c("androidx.camera.core.ImageCapture","""public static final int OUTPUT_FORMAT_JPEG=0, OUTPUT_FORMAT_JPEG_ULTRA_HDR=1, OUTPUT_FORMAT_RAW_JPEG=3;
public static final int FLASH_MODE_AUTO=0, FLASH_MODE_ON=1, FLASH_MODE_OFF=2;
public static final int CAPTURE_MODE_MAXIMIZE_QUALITY=0, CAPTURE_MODE_MINIMIZE_LATENCY=1;
public int getFlashMode(){return 0;} public void setFlashMode(int m){} public int getTargetRotation(){return 0;} public void setTargetRotation(int r){}
public static ImageCaptureCapabilities getImageCaptureCapabilities(CameraInfo i){return null;}
public void takePicture(OutputFileOptions o, java.util.concurrent.Executor e, OnImageSavedCallback c){}
public void takePicture(OutputFileOptions raw, OutputFileOptions jpeg, java.util.concurrent.Executor e, OnImageSavedCallback c){}
public void takePicture(java.util.concurrent.Executor e, OnImageCapturedCallback c){}
public static final class OutputFileOptions { public static final class Builder { public Builder(android.content.ContentResolver r, android.net.Uri u, android.content.ContentValues v){} public OutputFileOptions build(){return null;} } }
public static class OutputFileResults { public android.net.Uri getSavedUri(){return null;} }
public interface OnImageSavedCallback { void onImageSaved(OutputFileResults r); void onError(ImageCaptureException e); }
public abstract static class OnImageCapturedCallback { public void onCaptureSuccess(ImageProxy i){} public void onError(ImageCaptureException e){} }
public static final class Builder { public Builder setCaptureMode(int m){return this;} public Builder setJpegQuality(int q){return this;} public Builder setFlashMode(int m){return this;} public Builder setOutputFormat(int f){return this;} public Builder setTargetRotation(int r){return this;} public ImageCapture build(){return null;} }""","final class"," extends UseCase")
c("androidx.camera.camera2.interop.ExperimentalCamera2Interop","","@interface")
c("androidx.camera.camera2.interop.Camera2CameraInfo","public static Camera2CameraInfo from(androidx.camera.core.CameraInfo i){return null;} public String getCameraId(){return null;}","final class")
c("androidx.camera.camera2.interop.Camera2CameraControl","public static Camera2CameraControl from(androidx.camera.core.CameraControl c){return null;} public com.google.common.util.concurrent.ListenableFuture<Void> setCaptureRequestOptions(CaptureRequestOptions o){return null;} public com.google.common.util.concurrent.ListenableFuture<Void> clearCaptureRequestOptions(){return null;}","final class")
c("androidx.camera.camera2.interop.CaptureRequestOptions","public static final class Builder { public <T> Builder setCaptureRequestOption(android.hardware.camera2.CaptureRequest.Key<T> k, T v){return this;} public CaptureRequestOptions build(){return null;} }")
c("androidx.camera.extensions.ExtensionMode","public static final int NONE=0, BOKEH=1, HDR=2, NIGHT=3;","final class")
c("androidx.camera.extensions.ExtensionsManager","""public static com.google.common.util.concurrent.ListenableFuture<ExtensionsManager> getInstanceAsync(android.content.Context c, androidx.camera.core.CameraProvider p){return null;}
public boolean isExtensionAvailable(androidx.camera.core.CameraSelector s,int m){return false;}
public androidx.camera.core.CameraSelector getExtensionEnabledCameraSelector(androidx.camera.core.CameraSelector s,int m){return null;}""","final class")
c("androidx.camera.core.CameraProvider","java.util.List<CameraInfo> getAvailableCameraInfos(); CameraInfo getCameraInfo(CameraSelector s);","interface")
c("androidx.camera.lifecycle.ProcessCameraProvider","""public static com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> getInstance(android.content.Context c){return null;}
public java.util.List<androidx.camera.core.CameraInfo> getAvailableCameraInfos(){return null;} public androidx.camera.core.CameraInfo getCameraInfo(androidx.camera.core.CameraSelector s){return null;}
public void unbindAll(){} public androidx.camera.core.Camera bindToLifecycle(androidx.lifecycle.LifecycleOwner o, androidx.camera.core.CameraSelector s, androidx.camera.core.UseCase... u){return null;}""","final class"," implements androidx.camera.core.CameraProvider")
c("androidx.camera.video.Quality","public static final Quality UHD=null, FHD=null, HD=null, SD=null, LOWEST=null, HIGHEST=null;")
c("androidx.camera.video.FallbackStrategy","public static FallbackStrategy lowerQualityOrHigherThan(Quality q){return null;}")
c("androidx.camera.video.QualitySelector","public static QualitySelector fromOrderedList(java.util.List<Quality> q, FallbackStrategy f){return null;}","final class")
c("androidx.camera.video.VideoCapabilities","java.util.List<Quality> getSupportedQualities(androidx.camera.core.DynamicRange d);","interface")
c("androidx.camera.video.VideoOutput","","interface")
c("androidx.camera.video.Recorder","""public static VideoCapabilities getVideoCapabilities(androidx.camera.core.CameraInfo i){return null;}
public PendingRecording prepareRecording(android.content.Context c, MediaStoreOutputOptions o){return null;}
public static final class Builder { public Builder setQualitySelector(QualitySelector q){return this;} public Recorder build(){return null;} }""","final class"," implements VideoOutput")
c("androidx.camera.video.PendingRecording","public PendingRecording withAudioEnabled(){return this;} public Recording start(java.util.concurrent.Executor e, androidx.core.util.Consumer<VideoRecordEvent> l){return null;}","final class")
c("androidx.core.util.Consumer","void accept(T t);","interface","<T>")
c("androidx.camera.video.Recording","public void stop(){}","final class")
c("androidx.camera.video.RecordingStats","public long getRecordedDurationNanos(){return 0;}","abstract class")
c("androidx.camera.video.OutputResults","public android.net.Uri getOutputUri(){return null;}","abstract class")
c("androidx.camera.video.VideoRecordEvent","""public RecordingStats getRecordingStats(){return null;}
public static final class Start extends VideoRecordEvent {}
public static final class Status extends VideoRecordEvent {}
public static final class Finalize extends VideoRecordEvent {
 public static final int ERROR_NONE=0, ERROR_UNKNOWN=1, ERROR_FILE_SIZE_LIMIT_REACHED=2, ERROR_INSUFFICIENT_STORAGE=3, ERROR_SOURCE_INACTIVE=4, ERROR_DURATION_LIMIT_REACHED=9;
 public int getError(){return 0;} public Throwable getCause(){return null;} public OutputResults getOutputResults(){return null;} }""")
c("androidx.camera.video.VideoCapture","public static <T extends VideoOutput> VideoCapture<T> withOutput(T o){return null;} public T getOutput(){return null;} public int getTargetRotation(){return 0;} public void setTargetRotation(int r){}","final class","<T extends VideoOutput> extends androidx.camera.core.UseCase")
c("androidx.camera.video.MediaStoreOutputOptions","public static final class Builder { public Builder(android.content.ContentResolver r, android.net.Uri u){} public Builder setContentValues(android.content.ContentValues v){return this;} public MediaStoreOutputOptions build(){return null;} }","final class")
c("androidx.camera.view.PreviewView","""public enum ImplementationMode { COMPATIBLE, PERFORMANCE } public enum ScaleType { FILL_CENTER }
public PreviewView(android.content.Context c){super(c);}
public void setImplementationMode(ImplementationMode m){} public ImplementationMode getImplementationMode(){return null;}
public void setScaleType(ScaleType s){} public ScaleType getScaleType(){return null;}
public androidx.camera.core.Preview.SurfaceProvider getSurfaceProvider(){return null;}
public androidx.camera.core.MeteringPointFactory getMeteringPointFactory(){return null;}""","final class"," extends android.widget.FrameLayout")
c("ru.revvl.cameralab.BuildConfig","public static final String VERSION_NAME=\"0.3.1\";","final class")

# ---- UI v2 (0.3.4) additions / overrides
c("android.view.View","""public static final int VISIBLE=0; public static final int GONE=8;
public View(android.content.Context c){} public android.content.res.Resources getResources(){return null;}
public void setVisibility(int v){} public int getVisibility(){return 0;} public void setEnabled(boolean e){} public boolean isEnabled(){return true;}
public void setAlpha(float a){} public float getAlpha(){return 1;} public void setBackgroundColor(int c){}
public void setBackground(android.graphics.drawable.Drawable d){} public android.graphics.drawable.Drawable getBackground(){return null;}
public void setPadding(int a,int b,int c,int d){} public void setOnClickListener(OnClickListener l){} public void setOnTouchListener(OnTouchListener l){}
public void setOnLongClickListener(OnLongClickListener l){}
public void setClickable(boolean b){} public boolean isClickable(){return false;} public void setFocusable(boolean b){} public boolean isFocusable(){return false;}
public void setContentDescription(CharSequence d){} public CharSequence getContentDescription(){return null;}
public void setClipToOutline(boolean b){} public boolean getClipToOutline(){return false;}
public void setRotation(float r){} public float getRotation(){return 0;} public android.view.ViewPropertyAnimator animate(){return null;}
public boolean isPressed(){return false;} protected void drawableStateChanged(){}
public boolean performHapticFeedback(int c){return true;}
public boolean postDelayed(Runnable r,long d){return true;} public boolean removeCallbacks(Runnable r){return true;}
public void invalidate(){} public void requestLayout(){} public int getWidth(){return 0;} public int getHeight(){return 0;} protected void onDraw(android.graphics.Canvas c){}
protected void onMeasure(int w,int h){}
public void setBackgroundTintList(android.content.res.ColorStateList t){} public android.content.res.ColorStateList getBackgroundTintList(){return null;}
public interface OnClickListener { void onClick(View v); }
public interface OnLongClickListener { boolean onLongClick(View v); }
public interface OnTouchListener { boolean onTouch(View v, MotionEvent e); }
public static class MeasureSpec { public static final int EXACTLY=1073741824; public static int getSize(int s){return 0;} public static int makeMeasureSpec(int s,int m){return 0;} }""")
c("android.view.ViewPropertyAnimator","public ViewPropertyAnimator rotation(float r){return this;} public ViewPropertyAnimator setDuration(long d){return this;} public void start(){}")
c("android.view.ViewGroup","""public ViewGroup(android.content.Context c){super(c);} public void addView(View v){} public void addView(View v, LayoutParams p){} public void removeAllViews(){}
public static class LayoutParams { public LayoutParams(int w,int h){} }
public static class MarginLayoutParams extends LayoutParams { public int bottomMargin, topMargin; public MarginLayoutParams(int w,int h){super(w,h);} public void setMargins(int l,int t,int r,int b){} public void setMarginEnd(int m){} public int getMarginEnd(){return 0;} }""","abstract class"," extends View")
c("android.widget.LinearLayout","""public static final int VERTICAL=1; public static final int HORIZONTAL=0; public LinearLayout(android.content.Context c){super(c);}
public void setOrientation(int o){} public int getOrientation(){return 0;} public void setGravity(int g){} public int getGravity(){return 0;}
public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams { public LayoutParams(int w,int h){super(w,h);} public LayoutParams(int w,int h,float wt){super(w,h);} }""","class"," extends android.view.ViewGroup")
c("android.widget.FrameLayout","""public FrameLayout(android.content.Context c){super(c);}
public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams { public LayoutParams(int w,int h){super(w,h);} public LayoutParams(int w,int h,int g){super(w,h);} }""","class"," extends android.view.ViewGroup")
c("android.widget.ImageView","public enum ScaleType { CENTER_CROP } public ImageView(android.content.Context c){super(c);} public void setScaleType(ScaleType s){} public ScaleType getScaleType(){return null;} public void setImageBitmap(android.graphics.Bitmap b){}","class"," extends android.view.View")
c("android.widget.TextView","""public TextView(android.content.Context c){super(c);} public void setText(CharSequence t){} public CharSequence getText(){return null;}
public void setTextSize(float s){} public float getTextSize(){return 0;} public void setTextColor(int c){} public void setGravity(int g){} public int getGravity(){return 0;}
public void setIncludeFontPadding(boolean b){} public boolean getIncludeFontPadding(){return true;} public void setTypeface(android.graphics.Typeface t,int s){}
public void setTypeface(android.graphics.Typeface t){} public android.graphics.Typeface getTypeface(){return null;}
public void setMaxLines(int m){} public int getMaxLines(){return 1;}
public void setLetterSpacing(float f){} public float getLetterSpacing(){return 0;} public void setAllCaps(boolean b){} public boolean isAllCaps(){return false;}
public void setMinHeight(int h){} public int getMinHeight(){return 0;} public void setMinWidth(int h){} public int getMinWidth(){return 0;}
public void setMinimumHeight(int h){} public int getMinimumHeight(){return 0;} public void setMinimumWidth(int h){} public int getMinimumWidth(){return 0;}""","class"," extends android.view.View")
c("android.view.Gravity","public static final int CENTER_VERTICAL=16; public static final int TOP=48; public static final int BOTTOM=80; public static final int START=8388611; public static final int END=8388613; public static final int CENTER=17; public static final int CENTER_HORIZONTAL=1;")
c("android.view.MotionEvent","public static final int ACTION_DOWN=0; public static final int ACTION_UP=1; public int getAction(){return 0;} public int getActionMasked(){return 0;} public int getPointerCount(){return 1;} public float getX(){return 0;} public float getY(){return 0;}")
c("android.view.KeyEvent","public static final int KEYCODE_VOLUME_UP=24, KEYCODE_VOLUME_DOWN=25, KEYCODE_CAMERA=27; public int getRepeatCount(){return 0;}")
c("android.view.HapticFeedbackConstants","public static final int VIRTUAL_KEY=1;")
c("android.view.ScaleGestureDetector","""public ScaleGestureDetector(android.content.Context c, OnScaleGestureListener l){} public boolean onTouchEvent(MotionEvent e){return true;} public float getScaleFactor(){return 1;}
public interface OnScaleGestureListener { boolean onScale(ScaleGestureDetector d); boolean onScaleBegin(ScaleGestureDetector d); void onScaleEnd(ScaleGestureDetector d); }
public static class SimpleOnScaleGestureListener implements OnScaleGestureListener { public boolean onScale(ScaleGestureDetector d){return false;} public boolean onScaleBegin(ScaleGestureDetector d){return true;} public void onScaleEnd(ScaleGestureDetector d){} }""")
c("android.graphics.Typeface","public static final int NORMAL=0; public static final int BOLD=1; public static final Typeface DEFAULT_BOLD=null; public static Typeface create(String f,int s){return null;}")
c("android.graphics.RectF","public void set(float l,float t,float r,float b){}")
c("android.graphics.Canvas","public void drawLine(float a,float b,float c,float d, Paint p){} public void drawCircle(float x,float y,float r,Paint p){} public void drawArc(RectF o,float s,float w,boolean c,Paint p){} public void drawRoundRect(RectF r,float rx,float ry,Paint p){} public void drawText(String t,float x,float y,Paint p){}")
c("android.graphics.Paint","""public static final int ANTI_ALIAS_FLAG=1; public enum Style { STROKE, FILL } public enum Cap { ROUND } public enum Align { CENTER }
public Paint(int f){} public void setColor(int c){} public int getColor(){return 0;} public void setStrokeWidth(float w){} public float getStrokeWidth(){return 0;}
public void setStyle(Style s){} public Style getStyle(){return null;} public void setStrokeCap(Cap c){} public Cap getStrokeCap(){return null;}
public void setTextAlign(Align a){} public Align getTextAlign(){return null;} public Typeface setTypeface(Typeface t){return t;} public Typeface getTypeface(){return null;}
public void setTextSize(float s){} public float getTextSize(){return 0;} public float descent(){return 0;} public float ascent(){return 0;}""")
c("android.graphics.drawable.Drawable","","abstract class")
c("android.graphics.drawable.GradientDrawable","public static final int RECTANGLE=0; public void setShape(int s){} public int getShape(){return 0;} public void setCornerRadius(float r){} public float getCornerRadius(){return 0;} public void setColor(int c){}","class"," extends Drawable")
c("android.util.Size","public Size(int w,int h){} public int getWidth(){return 0;} public int getHeight(){return 0;}")
c("android.os.CancellationSignal","")
c("android.content.ContentResolver","""public android.net.Uri insert(android.net.Uri u, ContentValues v){return null;}
public int update(android.net.Uri u, ContentValues v, String w, String[] a){return 0;}
public int delete(android.net.Uri u, String w, String[] a){return 0;}
public java.io.OutputStream openOutputStream(android.net.Uri u) throws java.io.FileNotFoundException {return null;}
public String getType(android.net.Uri u){return null;}
public android.graphics.Bitmap loadThumbnail(android.net.Uri u, android.util.Size s, android.os.CancellationSignal c) throws java.io.IOException {return null;}""")
c("android.app.Activity","""public android.view.Window getWindow(){return null;} public boolean isFinishing(){return false;} public boolean isDestroyed(){return false;}
public void setContentView(android.view.View v){} public void startActivity(android.content.Intent i){} public final void runOnUiThread(Runnable r){}
public boolean onKeyDown(int k, android.view.KeyEvent e){return false;}
protected void onCreate(android.os.Bundle b){} protected void onStart(){} protected void onResume(){} protected void onStop(){} protected void onDestroy(){} protected void onSaveInstanceState(android.os.Bundle b){}""","class"," extends android.content.Context")
c("androidx.core.view.WindowInsetsCompat","""public androidx.core.graphics.Insets getInsets(int t){return null;}
public static final class Type { public static int systemBars(){return 0;} public static int displayCutout(){return 0;} }""")
c("androidx.lifecycle.LiveData","public T getValue(){return null;}","abstract class","<T>")
c("androidx.camera.core.ZoomState","float getZoomRatio(); float getMaxZoomRatio(); float getMinZoomRatio(); float getLinearZoom();","interface")
c("androidx.camera.core.CameraInfo","boolean hasFlashUnit(); ExposureState getExposureState(); androidx.lifecycle.LiveData<ZoomState> getZoomState();","interface")
c("androidx.camera.core.CameraControl","""com.google.common.util.concurrent.ListenableFuture<Void> setLinearZoom(float z);
com.google.common.util.concurrent.ListenableFuture<Void> setZoomRatio(float r);
com.google.common.util.concurrent.ListenableFuture<Void> enableTorch(boolean t);
com.google.common.util.concurrent.ListenableFuture<Integer> setExposureCompensationIndex(int i);
com.google.common.util.concurrent.ListenableFuture<Object> startFocusAndMetering(FocusMeteringAction a);""","interface")
c("androidx.camera.core.resolutionselector.AspectRatioStrategy","public static final AspectRatioStrategy RATIO_4_3_FALLBACK_AUTO_STRATEGY=null; public static final AspectRatioStrategy RATIO_16_9_FALLBACK_AUTO_STRATEGY=null;","final class")
c("androidx.camera.core.resolutionselector.ResolutionSelector","public static final class Builder { public Builder setAspectRatioStrategy(AspectRatioStrategy s){return this;} public ResolutionSelector build(){return null;} }","final class")
c("androidx.camera.core.Preview","""public void setSurfaceProvider(SurfaceProvider p){}
public interface SurfaceProvider {}
public static final class Builder { public Builder setResolutionSelector(androidx.camera.core.resolutionselector.ResolutionSelector r){return this;} public Preview build(){return null;} }""","final class"," extends UseCase")


# ---- PRO tools (0.3.5)
S["android.view.View"]=(S["android.view.View"][0], S["android.view.View"][1]+"\npublic android.view.ViewGroup.LayoutParams getLayoutParams(){return null;} public void setLayoutParams(android.view.ViewGroup.LayoutParams p){}", S["android.view.View"][2])
c("android.view.HapticFeedbackConstants","public static final int VIRTUAL_KEY=1; public static final int LONG_PRESS=0;")
c("android.view.GestureDetector","""public GestureDetector(android.content.Context c, OnGestureListener l){} public boolean onTouchEvent(MotionEvent e){return true;}
public interface OnGestureListener { boolean onDown(MotionEvent e); boolean onSingleTapUp(MotionEvent e); void onLongPress(MotionEvent e); }
public static class SimpleOnGestureListener implements OnGestureListener { public boolean onDown(MotionEvent e){return false;} public boolean onSingleTapUp(MotionEvent e){return false;} public void onLongPress(MotionEvent e){} }""")
c("android.graphics.Color","public static final int WHITE=-1; public static final int BLACK=0xff000000; public static final int GRAY=0xff888888; public static int argb(int a,int r,int g,int b){return 0;}")
c("android.graphics.Paint","""public static final int ANTI_ALIAS_FLAG=1; public enum Style { STROKE, FILL } public enum Cap { ROUND } public enum Align { CENTER }
public Paint(){} public Paint(int f){} public void setColor(int c){} public int getColor(){return 0;} public void setStrokeWidth(float w){} public float getStrokeWidth(){return 0;}
public void setStyle(Style s){} public Style getStyle(){return null;} public void setStrokeCap(Cap c){} public Cap getStrokeCap(){return null;}
public void setTextAlign(Align a){} public Align getTextAlign(){return null;} public Typeface setTypeface(Typeface t){return t;} public Typeface getTypeface(){return null;}
public void setTextSize(float s){} public float getTextSize(){return 0;} public float descent(){return 0;} public float ascent(){return 0;}""")
c("android.graphics.Canvas","public void drawLine(float a,float b,float c,float d, Paint p){} public void drawCircle(float x,float y,float r,Paint p){} public void drawArc(RectF o,float s,float w,boolean c,Paint p){} public void drawRoundRect(RectF r,float rx,float ry,Paint p){} public void drawRect(RectF r,Paint p){} public void drawText(String t,float x,float y,Paint p){}")
c("android.hardware.camera2.CameraCharacteristics","""public static final class Key<T> {}
public <T> T get(Key<T> k){return null;}
public static final Key<Integer> LENS_FACING=null; public static final int LENS_FACING_FRONT=0;
public static final Key<float[]> LENS_INFO_AVAILABLE_FOCAL_LENGTHS=null;
public static final Key<Float> LENS_INFO_MINIMUM_FOCUS_DISTANCE=null;
public static final Key<Integer> LENS_INFO_FOCUS_DISTANCE_CALIBRATION=null; public static final int LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED=0;
public static final Key<Boolean> CONTROL_AE_LOCK_AVAILABLE=null; public static final Key<Boolean> CONTROL_AWB_LOCK_AVAILABLE=null;
public static final Key<int[]> CONTROL_AWB_AVAILABLE_MODES=null;
public static final Key<int[]> REQUEST_AVAILABLE_CAPABILITIES=null; public static final int REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR=1; public static final int REQUEST_AVAILABLE_CAPABILITIES_RAW=3;
public static final Key<android.util.Range<Integer>> SENSOR_INFO_SENSITIVITY_RANGE=null;
public static final Key<android.util.Range<Long>> SENSOR_INFO_EXPOSURE_TIME_RANGE=null;
public static final Key<Integer> INFO_SUPPORTED_HARDWARE_LEVEL=null;
public static final Key<Boolean> FLASH_INFO_AVAILABLE=null;
public static final Key<int[]> LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION=null;
public static final Key<android.hardware.camera2.params.StreamConfigurationMap> SCALER_STREAM_CONFIGURATION_MAP=null;""")
c("android.hardware.camera2.CaptureRequest","""public static final class Key<T> {}
public static final Key<Integer> CONTROL_AE_MODE=null; public static final int CONTROL_AE_MODE_OFF=0;
public static final Key<Boolean> CONTROL_AE_LOCK=null; public static final Key<Boolean> CONTROL_AWB_LOCK=null;
public static final Key<Integer> CONTROL_AWB_MODE=null;
public static final Key<Integer> CONTROL_AF_MODE=null; public static final int CONTROL_AF_MODE_OFF=0;
public static final Key<Float> LENS_FOCUS_DISTANCE=null;
public static final Key<Integer> SENSOR_SENSITIVITY=null; public static final Key<Long> SENSOR_EXPOSURE_TIME=null;""")
c("androidx.camera.core.FocusMeteringAction","""public static final int FLAG_AF=1, FLAG_AE=2, FLAG_AWB=4;
public static class Builder { public Builder(MeteringPoint p){} public Builder(MeteringPoint p,int flags){} public Builder setAutoCancelDuration(long d, java.util.concurrent.TimeUnit u){return this;} public Builder disableAutoCancel(){return this;} public FocusMeteringAction build(){return null;} }""")
c("androidx.camera.core.CameraControl","""com.google.common.util.concurrent.ListenableFuture<Void> setLinearZoom(float z);
com.google.common.util.concurrent.ListenableFuture<Void> setZoomRatio(float r);
com.google.common.util.concurrent.ListenableFuture<Void> enableTorch(boolean t);
com.google.common.util.concurrent.ListenableFuture<Integer> setExposureCompensationIndex(int i);
com.google.common.util.concurrent.ListenableFuture<Object> startFocusAndMetering(FocusMeteringAction a);
com.google.common.util.concurrent.ListenableFuture<Void> cancelFocusAndMetering();""","interface")
c("androidx.camera.core.ImageProxy","""void close(); int getFormat(); int getWidth(); int getHeight(); PlaneProxy[] getPlanes(); ImageInfo getImageInfo();
interface PlaneProxy { java.nio.ByteBuffer getBuffer(); int getRowStride(); int getPixelStride(); }""","interface"," extends AutoCloseable")
c("androidx.camera.core.ImageAnalysis","""public static final int STRATEGY_KEEP_ONLY_LATEST=0;
public interface Analyzer { void analyze(ImageProxy image); }
public void setAnalyzer(java.util.concurrent.Executor e, Analyzer a){}
public static final class Builder { public Builder setBackpressureStrategy(int s){return this;} public Builder setResolutionSelector(androidx.camera.core.resolutionselector.ResolutionSelector r){return this;} public ImageAnalysis build(){return null;} }""","final class"," extends UseCase")
c("androidx.camera.core.resolutionselector.ResolutionStrategy","public static final int FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER=1; public ResolutionStrategy(android.util.Size s,int rule){}","final class")
c("androidx.camera.core.resolutionselector.ResolutionSelector","public static final class Builder { public Builder setAspectRatioStrategy(AspectRatioStrategy s){return this;} public Builder setResolutionStrategy(ResolutionStrategy s){return this;} public ResolutionSelector build(){return null;} }","final class")


# ---- diagnostics (0.3.6)
c("android.os.SystemClock","public static long elapsedRealtime(){return 0;}","final class")
c("android.os.Handler","public Handler(Looper l){} public boolean post(Runnable r){return true;} public boolean postDelayed(Runnable r,long d){return true;} public void removeCallbacks(Runnable r){} public void removeCallbacksAndMessages(Object o){}")
c("android.os.Environment","public static final String DIRECTORY_DCIM=\"DCIM\"; public static final String DIRECTORY_MOVIES=\"Movies\"; public static final String DIRECTORY_DOWNLOADS=\"Download\";")
c("android.provider.MediaStore","""public interface MediaColumns { String DISPLAY_NAME="d"; String MIME_TYPE="m"; String RELATIVE_PATH="r"; String IS_PENDING="p"; }
public static final class Images { public static final class Media { public static final android.net.Uri EXTERNAL_CONTENT_URI=null; public static final String DATE_TAKEN="t"; } }
public static final class Video { public static final class Media { public static final android.net.Uri EXTERNAL_CONTENT_URI=null; } }
public static final class Downloads { public static final android.net.Uri EXTERNAL_CONTENT_URI=null; }""","final class")
c("android.content.Intent","""public static final String ACTION_VIEW="v"; public static final String ACTION_SEND="s"; public static final String EXTRA_TEXT="t"; public static final String EXTRA_SUBJECT="j"; public static final int FLAG_GRANT_READ_URI_PERMISSION=1;
public Intent(String a){} public Intent setDataAndType(android.net.Uri u,String t){return this;} public Intent addFlags(int f){return this;}
public Intent setType(String t){return this;} public String getType(){return null;} public Intent putExtra(String k,String v){return this;}
public static Intent createChooser(Intent t, CharSequence title){return null;}""")
c("androidx.camera.core.CameraState","public StateError getError(){return null;} public static abstract class StateError { public abstract int getCode(); }","abstract class")
c("androidx.camera.core.CameraInfo","boolean hasFlashUnit(); ExposureState getExposureState(); androidx.lifecycle.LiveData<ZoomState> getZoomState(); androidx.lifecycle.LiveData<CameraState> getCameraState();","interface")
c("androidx.camera.core.ImageCaptureException","public ImageCaptureException(){} public int getImageCaptureError(){return 0;}","class"," extends Exception")


# ---- instrumented tests (0.3.7)
c("android.util.Log","public static int i(String t,String m){return 0;}","final class")
c("android.content.ContentUris","public static long parseId(android.net.Uri u){return 0;}")
c("android.database.Cursor","boolean moveToFirst(); int getInt(int i);","interface"," extends java.io.Closeable")
S["android.content.ContentResolver"]=(S["android.content.ContentResolver"][0], S["android.content.ContentResolver"][1]+"\npublic java.io.InputStream openInputStream(android.net.Uri u) throws java.io.FileNotFoundException {return null;}\npublic android.database.Cursor query(android.net.Uri u,String[] p,String s,String[] a,String o){return null;}", S["android.content.ContentResolver"][2])
c("android.app.Instrumentation","public android.content.Context getTargetContext(){return null;}")
c("androidx.lifecycle.Lifecycle","public enum State { DESTROYED, INITIALIZED, CREATED, STARTED, RESUMED }","abstract class")
c("androidx.test.platform.app.InstrumentationRegistry","public static android.app.Instrumentation getInstrumentation(){return null;}","final class")
c("androidx.test.ext.junit.runners.AndroidJUnit4","")
c("androidx.test.rule.GrantPermissionRule","public static GrantPermissionRule grant(String... p){return null;}")
c("androidx.test.core.app.ActivityScenario","""public static <A extends android.app.Activity> ActivityScenario<A> launch(Class<A> c){return null;}
public ActivityScenario<A> moveToState(androidx.lifecycle.Lifecycle.State s){return this;} public ActivityScenario<A> recreate(){return this;}
public ActivityScenario<A> onActivity(ActivityAction<A> a){return this;} public void close(){}
public interface ActivityAction<A extends android.app.Activity> { void perform(A a); }""","final class","<A extends android.app.Activity> implements AutoCloseable")

for fq,(kind,body,ext) in S.items():
    pkg,name=fq.rsplit('.',1)
    d=os.path.join(OUT,*pkg.split('.')); os.makedirs(d,exist_ok=True)
    k=kind
    open(os.path.join(d,name+'.java'),'w').write(f"package {pkg};\npublic {k} {name}{ext} {{\n{body}\n}}\n")
print(len(S))
