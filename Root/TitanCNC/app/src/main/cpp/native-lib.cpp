#include <jni.h>
#include <string>
#include <opencv2/opencv.hpp>

extern "C" JNIEXPORT jstring JNICALL
Java_com_titancnc_utils_ImageProcessor_nativeProcessImage(
        JNIEnv* env,
        jobject /* this */,
        jlong matAddr) {
    cv::Mat& mat = *(cv::Mat*)matAddr;
    
    // Example processing - convert to grayscale
    cv::Mat gray;
    cv::cvtColor(mat, gray, cv::COLOR_BGR2GRAY);
    
    std::string result = "Image processed: " + std::to_string(mat.cols) + "x" + std::to_string(mat.rows);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_titancnc_utils_ImageProcessor_nativeApplyDithering(
        JNIEnv* env,
        jobject /* this */,
        jlong matAddr,
        jint algorithm) {
    cv::Mat& mat = *(cv::Mat*)matAddr;
    
    // Convert to grayscale if needed
    if (mat.channels() > 1) {
        cv::cvtColor(mat, mat, cv::COLOR_BGR2GRAY);
    }
    
    // Apply threshold based on algorithm
    switch (algorithm) {
        case 0: // Floyd-Steinberg
            cv::threshold(mat, mat, 128, 255, cv::THRESH_BINARY);
            break;
        case 1: // Adaptive
            cv::adaptiveThreshold(mat, mat, 255, cv::ADAPTIVE_THRESH_GAUSSIAN_C, 
                                  cv::THRESH_BINARY, 11, 2);
            break;
        case 2: // Otsu
            cv::threshold(mat, mat, 0, 255, cv::THRESH_BINARY | cv::THRESH_OTSU);
            break;
        default:
            break;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_titancnc_utils_ImageProcessor_nativeFindContours(
        JNIEnv* env,
        jobject /* this */,
        jlong matAddr) {
    cv::Mat& mat = *(cv::Mat*)matAddr;
    
    std::vector<std::vector<cv::Point>> contours;
    std::vector<cv::Vec4i> hierarchy;
    
    cv::findContours(mat, contours, hierarchy, cv::RETR_TREE, cv::CHAIN_APPROX_SIMPLE);
    
    // Return number of contours found
    return static_cast<jlong>(contours.size());
}
