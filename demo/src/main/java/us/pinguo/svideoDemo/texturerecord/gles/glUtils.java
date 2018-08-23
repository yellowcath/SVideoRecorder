package us.pinguo.svideoDemo.texturerecord.gles;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class glUtils {
    private static final String TAG = "GL utils";
    private glUtils() {}

    public static final float[] IDENTITY_MATRIX;
    static {
        IDENTITY_MATRIX = new float[16];
        Matrix.setIdentityM(IDENTITY_MATRIX, 0);
    }
    public static final int BYTES_PER_FLOAT = 4;
//    public static final int BYTES_PER_INT = 4;
    public static final short BYTES_PER_SHORT = 2;

    public static void checkGLError(String msg) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            String str = msg + ": glError 0x" + Integer.toHexString(error);
            Log.e(TAG, str);
            int values[] = new int[2];
            GLES20.glGetIntegerv(GLES20.GL_ARRAY_BUFFER_BINDING, values, 0);
            GLES20.glGetIntegerv(GLES20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING, values, 1);
            Log.e(TAG, "Current bound array buffer: " + values[0]);
            Log.e(TAG, "Current bound vertex attrib: "+ values[1]);
            throw new RuntimeException(msg);
        }
    }

    public static FloatBuffer createFloatBuffer(int size) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(size * BYTES_PER_FLOAT);
        buffer.order(ByteOrder.nativeOrder());
        return buffer.asFloatBuffer();
    }
    /*public static IntBuffer createIntBuffer(int size) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(size * BYTES_PER_INT);
        buffer.order(ByteOrder.nativeOrder());
        return buffer.asIntBuffer();
    }*/
    public static FloatBuffer createFloatBuffer(float[] coords) {
        FloatBuffer fb = createFloatBuffer(coords.length);
        fb.put(coords);
        fb.position(0);
        return fb;
    }
    /*public static IntBuffer createIntBuffer(int[] data) {
        IntBuffer ib = createIntBuffer(data.length);
        ib.put(data);
        ib.position(0);
        return ib;
    }*/
    public static ShortBuffer createShortBuffer(short[] data) {
        ShortBuffer sb = ByteBuffer.allocateDirect(data.length * BYTES_PER_SHORT)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
        sb.put(data).position(0);
        return sb;
    }

    public static int createProgram(String vertSrc, String fragSrc,
                                    String[] attributeNames, int[] attributeBinding,
                                    String[] uniformNames, int[] uniformBinding) {

        int program = GLES20.glCreateProgram();

        int status = 1;
        int[] vertSh = new int[1];
        int[] fragSh = new int[1];
        status *= compileShader(GLES20.GL_VERTEX_SHADER, vertSrc, vertSh);
        status *= compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc, fragSh);
        checkGLError("Compiling shaders");

        GLES20.glAttachShader(program, vertSh[0]);
        checkGLError("Attach shader");
        GLES20.glAttachShader(program, fragSh[0]);
        checkGLError("Attach shader fragment");

        //Bind attributes
        for(int i=0; i<attributeNames.length; i++){
            GLES20.glBindAttribLocation(program, attributeBinding[i], attributeNames[i]);
            checkGLError("Bind attribute: " + attributeNames[i]);
        }
        status *= linkProgram(program);

        status *= validateProgram(program);

        //location of uniforms
        if (status > 0) {
            for (int i=0; i< uniformNames.length; i++) {
                //			if (uniformsLocations.at(i).first.length()) {
                int loc = GLES20.glGetUniformLocation(program,
                        uniformNames[i]);
                checkGLError("glGetUniformLocation - " + uniformNames[i]);
                if (loc < 0) Log.e(TAG, "Bad uniform " + uniformNames[i]);
                uniformBinding[i] = loc;
            }
        } else {
            GLES20.glDeleteProgram(program);
            program = 0;
        }

        if (vertSh[0] > 0) {
            GLES20.glDeleteShader(vertSh[0]);
            GLES20.glDetachShader(program, vertSh[0]);
        }
        if (fragSh[0] > 0) {
            GLES20.glDeleteShader(fragSh[0]);
            GLES20.glDetachShader(program, fragSh[0]);
        }
        checkGLError("Shaders deleted");
        return program;
    }

    private static int compileShader(int target, String source, int[] output) {
        output[0] = GLES20.glCreateShader(target);

        //	const GLchar *str = src.c_str();
        GLES20.glShaderSource(output[0], source);
        GLES20.glCompileShader(output[0]);
        checkGLError("Compile shader");
        int[] status = new int[1];
        GLES20.glGetShaderiv(output[0], GLES20.GL_COMPILE_STATUS, status, 0);
        if(status[0] == 0){
            Log.e(TAG, "Failed to compile shader: " + GLES20.glGetShaderInfoLog(output[0]));
            GLES20.glDeleteShader(output[0]);
        }
        return status[0];
    }

    private static int linkProgram(int program) {
        int[] status = new int[1];
        GLES20.glLinkProgram(program);
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Error linking program: " + GLES20.glGetProgramInfoLog(program));
            return 0;
        }
        return 1;
    }

    private static int validateProgram(int program) {
        int[] status = new int[1];
        GLES20.glValidateProgram(program);

        GLES20.glGetProgramiv(program, GLES20.GL_VALIDATE_STATUS, status, 0);
        if (status[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Error validating program: " + GLES20.glGetProgramInfoLog(program));
            return 0;
        }
        return 1;
    }
}
