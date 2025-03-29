package tv.danmaku.ijk.media.widget.media.opengl;

import android.graphics.SurfaceTexture;

public interface GLSurfaceTextureCallback {
    void onGLSurfaceCreated(SurfaceTexture surfaceTexture);
    void onGLSurfaceChanged(int width, int height);
}
