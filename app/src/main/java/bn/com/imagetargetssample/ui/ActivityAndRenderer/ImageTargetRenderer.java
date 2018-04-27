/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package bn.com.imagetargetssample.ui.ActivityAndRenderer;

import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.vuforia.Device;
import com.vuforia.Matrix44F;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.Trackable;
import com.vuforia.TrackableResult;
import com.vuforia.Vuforia;

import bn.com.imagetargetssample.BnUtils.LoadUtil;
import bn.com.imagetargetssample.BnUtils.LoadedObjectVertexNormalTexture;
import bn.com.imagetargetssample.SampleApplication.SampleAppRenderer;
import bn.com.imagetargetssample.SampleApplication.SampleAppRendererControl;
import bn.com.imagetargetssample.SampleApplication.SampleApplicationSession;
import bn.com.imagetargetssample.SampleApplication.utils.LoadingDialogHandler;
import bn.com.imagetargetssample.SampleApplication.utils.SampleUtils;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


// 渲染类
public class ImageTargetRenderer implements GLSurfaceView.Renderer, SampleAppRendererControl
{
    private static final String LOGTAG = "ImageTargetRenderer";
    
    private SampleApplicationSession vuforiaAppSession;
    private ImageTargetsActivity mActivity;
    private SampleAppRenderer mSampleAppRenderer;

    private LoadedObjectVertexNormalTexture mTeapot;
    private int mTeapotTextureID;
    
    private float kBuildingScale = 0.012f;

    private boolean mIsActive = false;
    private boolean mModelIsLoaded = false;
    
    private static final float OBJECT_SCALE_FLOAT = 0.003f;
    
    
    public ImageTargetRenderer(ImageTargetsActivity activity, SampleApplicationSession session)
    {
        mActivity = activity;
        vuforiaAppSession = session;
        // SampleAppRenderer用来封装RenderingPrimitives中的设置信息
        // AR/VR、立体模式
        mSampleAppRenderer = new SampleAppRenderer(this, mActivity, Device.MODE.MODE_AR, false, 0.01f , 5f);
    }


    // 绘制
    @Override
    public void onDrawFrame(GL10 gl)
    {
        if (!mIsActive)
            return;
        
        // 调用渲染方法
        mSampleAppRenderer.render();
    }
    

    public void setActive(boolean active)
    {
        mIsActive = active;

        if(mIsActive)
            mSampleAppRenderer.configureVideoBackground();
    }


    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
    {
        Log.d(LOGTAG, "GLRenderer.onSurfaceCreated");

        // Vuforia渲染初始化函数
        // 第一次使用或者在OpenGL ES coontext对象丢失后 调用
        vuforiaAppSession.onSurfaceCreated();

        mSampleAppRenderer.onSurfaceCreated();
    }


    // 当画面改变尺寸时调用
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(LOGTAG, "GLRenderer.onSurfaceChanged");

        // 调用Vuforia中函数适应画面尺寸变化
        vuforiaAppSession.onSurfaceChanged(width, height);

        // RenderingPrimitives也需做出改变
        mSampleAppRenderer.onConfigurationChanged(mIsActive);

        initRendering();
    }
    
    
    // 初始化渲染
    private void initRendering()
    {
        // 设置清除颜色
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
                : 1.0f);

        if(!mModelIsLoaded) {
            mTeapot = LoadUtil.loadFromFile("ch_t.obj", mActivity.getResources(),mActivity.mGlView);
            mTeapotTextureID = LoadUtil.initTexture(bn.com.imagetargetssample.R.drawable.ghxp,mActivity.getResources());

            // Hide the Loading Dialog
            mActivity.loadingDialogHandler
                    .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
        }

    }

    public void updateConfiguration()
    {
        mSampleAppRenderer.onConfigurationChanged(mIsActive);
    }

    // 渲染方法
    // The render function called from SampleAppRendering by using RenderingPrimitives views.
    // The state is owned by SampleAppRenderer which is controlling it's lifecycle.
    // State should not be cached outside this method.
    public void renderFrame(State state, float[] projectionMatrix)
    {
        // 渲染图像背景
        mSampleAppRenderer.renderVideoBackground();

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);

        GLES30.glEnable(GLES30.GL_CULL_FACE);
        GLES30.glCullFace(GLES30.GL_BACK);

        for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++) {
            TrackableResult result = state.getTrackableResult(tIdx);
            Trackable trackable = result.getTrackable();
            printUserData(trackable);
            Matrix44F modelViewMatrix_Vuforia = Tool
                    .convertPose2GLMatrix(result.getPose());
            float[] modelViewMatrix = modelViewMatrix_Vuforia.getData();

            // 处理modelView矩阵和投影矩阵
            float[] modelViewProjection = new float[16];

            if (!mActivity.isExtendedTrackingActive()) {
                Matrix.translateM(modelViewMatrix, 0, 0.0f, 0.0f,
                        OBJECT_SCALE_FLOAT);
                Matrix.scaleM(modelViewMatrix, 0, OBJECT_SCALE_FLOAT,
                        OBJECT_SCALE_FLOAT, OBJECT_SCALE_FLOAT);
            } else {
                Matrix.rotateM(modelViewMatrix, 0, 90.0f, 1.0f, 0, 0);
                Matrix.scaleM(modelViewMatrix, 0, kBuildingScale,
                        kBuildingScale, kBuildingScale);
            }
            Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelViewMatrix, 0);
            if (!mActivity.isExtendedTrackingActive()) {
                mTeapot.drawSelf(mTeapotTextureID, modelViewProjection);
            }else {
                GLES30.glDisable(GLES20.GL_CULL_FACE);
                mTeapot.drawSelf(mTeapotTextureID, modelViewProjection);
            }

            SampleUtils.checkGLError("Render Frame");

        }

        GLES30.glDisable(GLES30.GL_DEPTH_TEST);

    }

    private void printUserData(Trackable trackable)
    {
        String userData = (String) trackable.getUserData();
        Log.d(LOGTAG, "UserData:Retreived User Data	\"" + userData + "\"");
    }
}
