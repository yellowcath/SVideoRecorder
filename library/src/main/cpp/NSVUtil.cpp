#include <jni.h>
#include <string>
extern "C"

JNIEXPORT void JNICALL
Java_us_pinguo_svideo_utils_NSVUtil_NV12ToNV21(JNIEnv *env, jobject obj, jbyteArray nv12Buffer, jint iWidth, jint iHeight,
                                                                 jint iLength) {

  unsigned char *nv12_buffer = (unsigned char *) env->GetByteArrayElements(
              nv12Buffer, 0);

      unsigned char *pUV = nv12_buffer + iWidth * iHeight;
      unsigned char iTemp;
      for (int i = 0; i < iWidth * iHeight / 2; i += 2) {
          unsigned char *pU = pUV;
          pUV++;
          unsigned char *pV = pUV;

          iTemp = *pU;
          *pU = *pV;
          *pV = iTemp;

          pUV++;
      }


      env->ReleaseByteArrayElements(nv12Buffer, (jbyte *) nv12_buffer, 0);
}

JNIEXPORT void JNICALL
Java_us_pinguo_svideo_utils_NSVUtil_NV12To420P(JNIEnv *env, jobject obj, jbyteArray nv12Buffer, jint iWidth, jint iHeight,
                                                                 jint iLength) {

  unsigned char *nv12_buffer = (unsigned char *) env->GetByteArrayElements(
              nv12Buffer, 0);

      unsigned char *pUV = nv12_buffer + iWidth * iHeight;
      unsigned char *pUVTemp = pUV;

      unsigned char *pUVPannel = new unsigned char[(iWidth * iHeight) / 2];

      unsigned char *pUPannel = pUVPannel;
      unsigned char *pVPannel = pUVPannel + (iWidth * iHeight) / 4;

      for (int i = 0; i < iWidth * iHeight / 2; i += 2) {
          *(pVPannel++) = *(pUV++);
          *(pUPannel++) = *(pUV++);
      }

      memcpy(pUVTemp, pUVPannel, (iWidth * iHeight) / 2);

      env->ReleaseByteArrayElements(nv12Buffer, (jbyte *) nv12_buffer, 0);

}