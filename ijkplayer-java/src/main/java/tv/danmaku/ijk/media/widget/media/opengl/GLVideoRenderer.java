package tv.danmaku.ijk.media.widget.media.opengl;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLVideoRenderer implements GLSurfaceView.Renderer
        , SurfaceTexture.OnFrameAvailableListener {

    private final String TAG = getClass().getSimpleName();
    private int aPositionLocation;
    private int programId;
    private FloatBuffer vertexBuffer;
    private final float[] vertexData = {
            1f, -1f, 0f,
            -1f, -1f, 0f,
            1f, 1f, 0f,
            -1f, 1f, 0f
    };

    private final float[] projectionMatrix = new float[16];
    private int uMatrixLocation;

    private final float[] textureVertexData = {
            1f, 0f,
            0f, 0f,
            1f, 1f,
            0f, 1f
    };

    private String vertexShader = "attribute vec4 aPosition;\n" +
            "attribute vec4 aTexCoord;\n" +
            "varying vec2 vTexCoord;\n" +
            "uniform mat4 uMatrix;\n" +
            "uniform mat4 uSTMatrix;\n" +
            "void main() {\n" +
            "    vTexCoord = (uSTMatrix * aTexCoord).xy;\n" +
            "    gl_Position = uMatrix*aPosition;\n" +
            "}";
    private String fragmentShader = "#extension GL_OES_EGL_image_external : require\n" +
            "varying vec2 vTexCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "uniform mediump float brightness;\n" +
            "uniform mediump float contrast;\n" +
            "uniform mediump float saturation;\n" +
            "const mediump vec3 luminanceWeighting = vec3(0.2125, 0.7154, 0.0721);\n" +
            "void main() {\n" +
            "    lowp vec4 textureColor = texture2D(sTexture, vTexCoord);\n" +
            "    mediump vec3 color = textureColor.rgb + vec3(brightness);\n" +
            "    color = ((color - vec3(0.5)) * contrast + vec3(0.5));\n" +
            "    color = mix(vec3(dot(color, luminanceWeighting)), color, saturation);\n" +
            "    gl_FragColor = vec4(color, textureColor.w);\n" +
            "}";

    private FloatBuffer textureVertexBuffer;
    private int uTextureSamplerLocation;
    private int aTextureCoordLocation;
    private int textureId;
    private int brightnessLocation;
    private int contrastLocation;
    private int saturationLocation;

    private GLSurfaceTextureCallback mGLSurfaceTextureCallback;
    private SurfaceTexture surfaceTexture;
    private boolean isFrameAvailable;

    private float[] mSTMatrix = new float[16];
    private int uSTMMatrixHandle;

    private int screenWidth, screenHeight;

    private float brightness = 0.0f;
    private float contrast = 1.0f;
    private float saturation = 1.0f;

    /**
     * 亮度范围 [-1.0, 1.0], 默认为 0.0
     *
     * @param brightness
     */
    public void setBrightness(float brightness) {
        if (brightness < -1.0f) {
            brightness = -1.0f;
        } else if (brightness > 1.0f) {
            brightness = 1.0f;
        }
        this.brightness = brightness;
    }

    /**
     * 对比度 [0.0,2.0], 默认为 1.0
     *
     * @param contrast
     */
    public void setContrast(float contrast) {

        if (contrast < 0.0f) {
            contrast = 0.0f;
        } else if (contrast > 2.0f) {
            contrast = 2.0f;
        }
        this.contrast = contrast;
    }

    /**
     * 饱和度 [0.0, 2.0], 默认为 1.0
     *
     * @param saturation
     */
    public void setSaturation(float saturation) {
        if (saturation < 0.0f) {
            saturation = 0.0f;
        } else if (saturation > 2.0f) {
            saturation = 2.0f;
        }
        this.saturation = saturation;
    }

    public GLVideoRenderer(GLSurfaceTextureCallback callback) {
        this.mGLSurfaceTextureCallback = callback;
        synchronized (this) {
            isFrameAvailable = false;
        }
        vertexBuffer = ByteBuffer.allocateDirect(vertexData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertexData);
        vertexBuffer.position(0);

        textureVertexBuffer = ByteBuffer.allocateDirect(textureVertexData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(textureVertexData);
        textureVertexBuffer.position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {

        programId = createProgram(vertexShader, fragmentShader);
        aPositionLocation = GLES20.glGetAttribLocation(programId, "aPosition");

        uMatrixLocation = GLES20.glGetUniformLocation(programId, "uMatrix");
        uSTMMatrixHandle = GLES20.glGetUniformLocation(programId, "uSTMatrix");
        uTextureSamplerLocation = GLES20.glGetUniformLocation(programId, "sTexture");
        aTextureCoordLocation = GLES20.glGetAttribLocation(programId, "aTexCoord");
        brightnessLocation = GLES20.glGetUniformLocation(programId, "brightness");
        contrastLocation = GLES20.glGetUniformLocation(programId, "contrast");
        saturationLocation = GLES20.glGetUniformLocation(programId, "saturation");

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);

        textureId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        checkGlError("glBindTexture mTextureID");
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);

        surfaceTexture = new SurfaceTexture(textureId);
        surfaceTexture.setOnFrameAvailableListener(this);

        if (mGLSurfaceTextureCallback != null) {
            mGLSurfaceTextureCallback.onGLSurfaceCreated(surfaceTexture);
        }
    }


    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        screenWidth = width;
        screenHeight = height;
        updateVideoSize(width, height);
        if (mGLSurfaceTextureCallback != null) {
            mGLSurfaceTextureCallback.onGLSurfaceChanged(width, height);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        synchronized (this) {
            if (isFrameAvailable) {
                surfaceTexture.updateTexImage();
                surfaceTexture.getTransformMatrix(mSTMatrix);
                isFrameAvailable = false;
            }
        }
        GLES20.glUseProgram(programId);
        GLES20.glUniformMatrix4fv(uMatrixLocation, 1, false, projectionMatrix, 0);
        GLES20.glUniformMatrix4fv(uSTMMatrixHandle, 1, false, mSTMatrix, 0);

        GLES20.glUniform1f(brightnessLocation, brightness);
        GLES20.glUniform1f(contrastLocation, contrast);
        GLES20.glUniform1f(saturationLocation, saturation);

        vertexBuffer.position(0);
        GLES20.glEnableVertexAttribArray(aPositionLocation);
        GLES20.glVertexAttribPointer(aPositionLocation, 3, GLES20.GL_FLOAT, false,
                12, vertexBuffer);

        textureVertexBuffer.position(0);
        GLES20.glEnableVertexAttribArray(aTextureCoordLocation);
        GLES20.glVertexAttribPointer(aTextureCoordLocation, 2, GLES20.GL_FLOAT, false, 8, textureVertexBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);


        GLES20.glUniform1i(uTextureSamplerLocation, 0);
        GLES20.glViewport(0, 0, screenWidth, screenHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    @Override
    synchronized public void onFrameAvailable(SurfaceTexture surface) {
        isFrameAvailable = true;
    }

    public void updateVideoSize(int videoWidth, int videoHeight) {

        float screenRatio = (float) screenWidth / screenHeight;
        float videoRatio = (float) videoWidth / videoHeight;
        if (videoRatio > screenRatio) {
            Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -videoRatio / screenRatio, videoRatio / screenRatio, -1f, 1f);
        } else
            Matrix.orthoM(projectionMatrix, 0, -screenRatio / videoRatio, screenRatio / videoRatio, -1f, 1f, -1f, 1f);
    }

    public void checkGlError(String label) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, label + ": glError " + error);
            throw new RuntimeException(label + ": glError " + error);
        }
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return 0;
        }
        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (pixelShader == 0) {
            return 0;
        }

        int program = GLES20.glCreateProgram();
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");
            GLES20.glAttachShader(program, pixelShader);
            checkGlError("glAttachShader");
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: ");
                Log.e(TAG, GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        return program;
    }


    private int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0) {
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader " + shaderType + ":");
                Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
        }
        return shader;
    }
}