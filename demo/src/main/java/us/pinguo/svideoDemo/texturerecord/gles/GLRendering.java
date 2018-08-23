package us.pinguo.svideoDemo.texturerecord.gles;

import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import us.pinguo.svideoDemo.record.CameraPresenter;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class GLRendering {
    //region GL constants
    private static final int kPositionAttribute = 1;
    private static final int kTex0CoordAttribute = 2;

    private int _warper;
    private int[] _warpUniforms;
    private int _warpVBO, _warpIBO;
    private boolean enableFilter;

    public GLRendering() {
        initialise();
    }

    public void drawBackground(int textureName, int rotation) {
        GLES20.glUseProgram(_warper);
        float[] rotmat = new float[16];
        Matrix.setIdentityM(rotmat, 0);

        if (CameraPresenter.CAMERA_FACING == Camera.CameraInfo.CAMERA_FACING_BACK) {
            Matrix.orthoM(rotmat, 0, -1.0f, 1.0f, 1.0f, -1.0f, -50.0f, 100.0f);
            Matrix.rotateM(rotmat, 0, rotation + 180, 0, 0, 1);
        } else {
            Matrix.rotateM(rotmat, 0, rotation, 0, 0, 1);
        }

        GLES20.glUniformMatrix4fv(_warpUniforms[0], 1, false, rotmat, 0);
        GLES20.glUniform1i(_warpUniforms[3], enableFilter ? 1 : 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureName);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        glUtils.checkGLError("texture parameters");

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, _warpVBO);
        GLES20.glEnableVertexAttribArray(kPositionAttribute);
        GLES20.glVertexAttribPointer(kPositionAttribute, 3, GLES20.GL_FLOAT, false,
                5 * glUtils.BYTES_PER_FLOAT, 0);
        GLES20.glEnableVertexAttribArray(kTex0CoordAttribute);
        GLES20.glVertexAttribPointer(kTex0CoordAttribute, 2, GLES20.GL_FLOAT, false,
                5 * glUtils.BYTES_PER_FLOAT, 3 * glUtils.BYTES_PER_FLOAT);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, _warpIBO);
        glUtils.checkGLError("VBO setup");

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, 0);
        glUtils.checkGLError("Drawing background");

        GLES20.glDisableVertexAttribArray(kTex0CoordAttribute);
        GLES20.glDisableVertexAttribArray(kPositionAttribute);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        GLES20.glUseProgram(0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private void initialise() {
        //initialise the programs
        {
            final String basicWarpVertex =
                    "precision highp float;\n" +
                            "attribute vec4 position;\n" +
                            "attribute vec2 textureCoord;\n" +
                            "uniform mat4 matrix;\n" +
                            "uniform mat4 textureMatrix;\n" +
                            "varying highp vec2 texCoord;\n" +
                            "void main() {\n" +
                            "  texCoord = (textureMatrix * vec4(textureCoord.x, textureCoord.y, 0.0, 1.0)).xy;\n" +
                            "  gl_Position = matrix * position; \n" +
                            "}";

            final String basicWarpFragment =
                    "#extension GL_OES_EGL_image_external : require\n" +
                            "uniform samplerExternalOES texture;\n" +
                            "varying highp vec2 texCoord;\n" +
                            "uniform int enableFilter;\n" +
                            "void main() {\n" +
                            "  gl_FragColor = texture2D(texture, texCoord);\n" +
                            "  if(enableFilter>0)\n" +
                            "  gl_FragColor.r=1.0;\n" +
                            "}";

            String[] attributes = {"position", "textureCoord"};
            int[] attribLoc = {kPositionAttribute, kTex0CoordAttribute};
            String[] uniforms = {"matrix", "texture", "textureMatrix", "enableFilter"};

            _warpUniforms = new int[uniforms.length];

            _warper = glUtils.createProgram(basicWarpVertex, basicWarpFragment,
                    attributes, attribLoc, uniforms, _warpUniforms);
            if (_warper <= 0) {
                throw new RuntimeException("Error creating warp program");
            }
            GLES20.glUseProgram(_warper);
            GLES20.glUniformMatrix4fv(_warpUniforms[0], 1, false, glUtils.IDENTITY_MATRIX, 0);
            GLES20.glUniform1i(_warpUniforms[1], 2); //set the texture unit
            GLES20.glUniformMatrix4fv(_warpUniforms[2], 1, false, glUtils.IDENTITY_MATRIX, 0);
            glUtils.checkGLError("Creating program - setting uniform");

            int[] tmp = new int[2];
            GLES20.glGenBuffers(2, tmp, 0);
            _warpVBO = tmp[0];
            _warpIBO = tmp[1];
            float[] vertex = {
                    -1.0f, -1.0f, 1.0f, 0.0f, 0.0f,
                    1.0f, -1.0f, 1.0f, 1.0f, 0.0f,
                    1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
                    -1.0f, 1.0f, 1.0f, 0.0f, 1.0f
            };
            FloatBuffer ver = glUtils.createFloatBuffer(vertex);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, _warpVBO);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, ver.capacity() * glUtils.BYTES_PER_FLOAT,
                    ver, GLES20.GL_DYNAMIC_DRAW);
            glUtils.checkGLError("Creating array buffer");
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

            short triangles[] = {2, 1, 0, 0, 3, 2};
            ShortBuffer tri = glUtils.createShortBuffer(triangles);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, _warpIBO);
            glUtils.checkGLError("Creating element buffer");
            GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, tri.capacity() * glUtils.BYTES_PER_SHORT,
                    tri, GLES20.GL_STATIC_DRAW);
            glUtils.checkGLError("Creating element buffer");
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        GLES20.glLineWidth(3);
        GLES20.glBlendEquation(GLES20.GL_FUNC_ADD);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

    }

    public void setEnableFilter(boolean enableFilter) {
        this.enableFilter = enableFilter;
    }
}
